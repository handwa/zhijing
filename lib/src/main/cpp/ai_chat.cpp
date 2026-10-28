#include <android/log.h>
#include <jni.h>
#include <algorithm>
#include <chrono>
#include <iomanip>
#include <cmath>
#include <string>
#include <vector>
#include <unistd.h>
#include <sampling.h>

#include "logging.h"
#include "chat.h"
#include "common.h"
#include "llama.h"

template<class T>
static std::string join(const std::vector<T> &values, const std::string &delim) {
    std::ostringstream str;
    for (size_t i = 0; i < values.size(); i++) {
        str << values[i];
        if (i < values.size() - 1) { str << delim; }
    }
    return str.str();
}

/**
 * LLama resources: context, model, batch and sampler
 */
constexpr int   N_THREADS_MIN           = 2;
constexpr int   N_THREADS_MAX           = 6;

constexpr int   FALLBACK_CONTEXT_SIZE   = 4096;
constexpr int   MIN_CONTEXT_SIZE        = 512;
constexpr int   MAX_CONTEXT_SIZE        = 131072;
constexpr int   OVERFLOW_HEADROOM       = 4;
constexpr int   BATCH_SIZE              = 512;
constexpr float DEFAULT_SAMPLER_TEMP    = 0.3f;

static llama_model                      * g_model;
static llama_context                    * g_context;
static llama_batch                        g_batch;
static common_chat_templates_ptr          g_chat_templates;
static common_sampler                   * g_sampler;

/**
 * Embedding side channel.
 *
 * A separate model + context that lives alongside the generative one, so RAG
 * indexing never has to unload the chat model. BERT-style embedders are tiny
 * (bge-small-zh is ~48 MB f16) so keeping both resident is cheap.
 */
constexpr int   EMBED_CONTEXT_SIZE      = 512;   // bge models are trained at 512
constexpr int   EMBED_BATCH_SIZE        = 512;

static llama_model                      * g_emb_model = nullptr;
static llama_context                    * g_emb_context = nullptr;
static llama_batch                        g_emb_batch;
static int                                g_emb_dim = 0;

/**
 * Runtime context size (tokens).
 *
 * This used to be a compile-time `constexpr`, which forced every model to run
 * with the same window. It is now resolved in `prepare(nCtx)` from, in order:
 *   1. the value requested by the host (ModelSpec.contextSize),
 *   2. the model's own trained context (`llama_model_n_ctx_train`),
 * so switching models no longer requires touching this file.
 *
 * Before `prepare()` runs on a freshly loaded model it holds the fallback used
 * only for logging.
 */
static int g_n_ctx = FALLBACK_CONTEXT_SIZE;

/** Decode steps since load; used to throttle per-step timing logs. */
static int g_decode_counter = 0;

/**
 * Long-term conversation state.
 *
 * Declared up here (before the JNI entry points) so that query helpers such as
 * `contextUsed()` can read them without forward declarations. The values
 * themselves are only written by the chat-prompt entry points below.
 */
static llama_pos                          system_prompt_position = 0;
static llama_pos                          current_position       = 0;

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_init(JNIEnv *env, jobject /*unused*/, jstring nativeLibDir) {
    // Set llama log handler to Android
    llama_log_set(aichat_android_log_callback, nullptr);

    // The CPU backend is linked statically for Android. Let llama.cpp register
    // it through its normal initialization path instead of scanning the APK's
    // native library directory for dynamic plugins.
    llama_backend_init();
    LOGi("Backend initiated; Log handler set.");
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_load(JNIEnv *env, jobject, jstring jmodel_path) {
    aichat_last_native_log.clear();
    llama_model_params model_params = llama_model_default_params();

    // Pin weights in RAM. With plain mmap the kernel may evict weight pages
    // under memory pressure, turning every decode step into disk IO; locking
    // trades that for a higher chance of the system killing other apps instead.
    model_params.load_mode = LLAMA_LOAD_MODE_MMAP_MLOCK;

    const auto *model_path = env->GetStringUTFChars(jmodel_path, 0);
    LOGd("%s: Loading model from: \n%s\n", __func__, model_path);

    auto *model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);
    if (!model) {
        return 1;
    }
    g_model = model;
    return 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_lastNativeError(JNIEnv *env, jobject) {
    return env->NewStringUTF(aichat_last_native_log.c_str());
}

static llama_context *init_context(llama_model *model, const int n_ctx) {
    if (!model) {
        LOGe("%s: model cannot be null", __func__);
        return nullptr;
    }

    // Multi-threading setup.
    // Decode is memory-bandwidth bound, so threads past the core count only add
    // contention. Use every logical core available, capped at N_THREADS_MAX
    // (which stays below the core count of big.LITTLE phones, where small cores
    // would otherwise gate each step).
    const int cores = (int) sysconf(_SC_NPROCESSORS_ONLN);
    const int n_threads = std::max(N_THREADS_MIN, std::min(N_THREADS_MAX, cores));
    LOGi("%s: Using %d threads (%d logical cores available)", __func__, n_threads, cores);
    // Context parameters setup
    llama_context_params ctx_params = llama_context_default_params();
    const int trained_context_size = llama_model_n_ctx_train(model);
    if (n_ctx > trained_context_size) {
        LOGw("%s: Model was trained with only %d context size! Enforcing %d context size...",
             __func__, trained_context_size, n_ctx);
    }
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;

    // Quantize the KV cache to Q8_0: halves its footprint (288 -> 144 MiB at
    // 8192 ctx) and makes memory operations such as clearing proportionally
    // cheaper. Q8_0 is the safest quantization for CPU backends; Q4_0 would
    // need flash attention and loses noticeably more quality.
    ctx_params.type_k = GGML_TYPE_Q8_0;
    ctx_params.type_v = GGML_TYPE_Q8_0;

    auto *context = llama_init_from_model(model, ctx_params);
    if (context == nullptr) {
        LOGe("%s: llama_new_context_with_model() returned null)", __func__);
        return nullptr;
    }

    // Log what actually took effect; requested versus effective values can
    // differ (e.g. n_ubatch is clamped by the model's UBatch size).
    LOGi("%s: effective n_batch=%u n_ubatch=%u n_threads=%u n_threads_batch=%u",
         __func__,
         (unsigned) llama_n_batch(context),
         (unsigned) llama_n_ubatch(context),
         (unsigned) llama_n_threads(context),
         (unsigned) llama_n_threads_batch(context));
    return context;
}

/**
 * Resolve the effective context size for the currently loaded model.
 *
 * `requested` <= 0 means "no preference": use the model's trained context
 * (clamped to [MIN_CONTEXT_SIZE, MAX_CONTEXT_SIZE]). Otherwise honour the
 * request, but never ask for less than a sane minimum.
 */
static int resolve_context_size(llama_model *model, const int requested) {
    const int trained = model ? std::max(llama_model_n_ctx_train(model), MIN_CONTEXT_SIZE)
                              : FALLBACK_CONTEXT_SIZE;
    const int base = requested > 0 ? requested : trained;
    return std::max(MIN_CONTEXT_SIZE, std::min(base, MAX_CONTEXT_SIZE));
}

static common_sampler *new_sampler(float temp) {
    common_params_sampling sparams;
    sparams.temp = temp;
    return common_sampler_init(g_model, sparams);
}

/**
 * Run one tiny decode so the first real request is not the one that pays for
 * first-touch costs.
 *
 * The first llama_decode after loading takes 20s+ on a phone/emulator: mmap'd
 * weights get faulted in, ggml builds its compute graph for the shape, and the
 * worker threads start up. Doing that here moves the cost into the "loading
 * model" phase the user already expects to wait for.
 *
 * Context is cleared afterwards, so warmup leaves no trace in the conversation.
 */
static void warmup_context(llama_context *context, llama_batch &batch) {
    if (!context) return;

    const auto t0 = std::chrono::steady_clock::now();

    // Feed a short prompt-shaped batch of harmless tokens. We do not care about
    // the output, only that the kernels actually execute. Prefer the model's BOS;
    // Qwen-family vocabularies may not define one, so fall back to any low id.
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    llama_token warm = llama_vocab_bos(vocab);
    if (warm == LLAMA_TOKEN_NULL || warm < 0) {
        warm = llama_vocab_eos(vocab);
    }
    if (warm == LLAMA_TOKEN_NULL || warm < 0) {
        warm = 1;   // token ids below the special-token range are always plain text
    }

    std::vector<llama_token> tokens(8, warm);

    common_batch_clear(batch);
    for (size_t i = 0; i < tokens.size(); i++) {
        common_batch_add(batch, tokens[i], (llama_pos) i, {0}, i == tokens.size() - 1);
    }

    if (llama_decode(context, batch) != 0) {
        LOGw("%s: warmup decode failed, continuing anyway", __func__);
    } else {
        const auto ms = std::chrono::duration<double, std::milli>(
                std::chrono::steady_clock::now() - t0).count();
        LOGi("%s: warmup decode took %.0f ms", __func__, ms);
    }

    // Undo any state the warmup introduced.
    llama_memory_seq_rm(llama_get_memory(context), 0, 0, (llama_pos) tokens.size());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_prepare(
        JNIEnv * /*env*/, jobject /*unused*/, jint requested_context_size) {
    if (!g_model) {
        LOGe("%s: model not loaded", __func__);
        return 1;
    }

    const int n_ctx = resolve_context_size(g_model, (int) requested_context_size);
    LOGi("%s: requested n_ctx=%d, effective n_ctx=%d (trained=%d)",
         __func__, (int) requested_context_size, n_ctx, llama_model_n_ctx_train(g_model));

    auto *context = init_context(g_model, n_ctx);
    if (context) {
        g_context = context;
        g_n_ctx = n_ctx;
    } else {
        // Keep any previously working context rather than leaving a half-baked state.
        LOGe("%s: failed to create context with n_ctx=%d", __func__, n_ctx);
        return 2;
    }

    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);
    g_chat_templates = common_chat_templates_init(g_model, "");
    g_sampler = new_sampler(DEFAULT_SAMPLER_TEMP);
    g_decode_counter = 0;

    // Pay the first-touch cost now instead of on the user's first message.
    warmup_context(g_context, g_batch);

    return 0;
}

static std::string get_backend() {
    std::vector<std::string> backends;
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        auto *reg = ggml_backend_reg_get(i);
        std::string name = ggml_backend_reg_name(reg);
        if (name != "CPU") {
            backends.push_back(ggml_backend_reg_name(reg));
        }
    }
    return backends.empty() ? "CPU" : join(backends, ",");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_systemInfo(JNIEnv *env, jobject /*unused*/) {
    return env->NewStringUTF(llama_print_system_info());
}

/**
 * Actual context window in use (tokens). Resolved by prepare(); host code can
 * size its own history / trimming policy against this instead of hard-coding.
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_contextSize(JNIEnv *, jobject /*unused*/) {
    return (jint) (g_context ? llama_n_ctx(g_context) : g_n_ctx);
}

/** Tokens currently occupied in the context (system prompt + history so far). */
extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_contextUsed(JNIEnv *, jobject /*unused*/) {
    return (jint) (g_context ? current_position : 0);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_benchModel(JNIEnv *env, jobject /*unused*/, jint pp, jint tg,
                                                      jint pl, jint nr) {
    // Bench with a throw-away context of `pp` tokens; the live context stays untouched.
    const int bench_n_ctx = std::max(MIN_CONTEXT_SIZE, (int) pp);
    auto *context = init_context(g_model, bench_n_ctx);
    if (!context) {
        const auto *const err_msg = "Fail to init_context! Bench aborted.";
        LOGe(err_msg);
        return env->NewStringUTF(err_msg);
    }

    auto pp_avg = 0.0;
    auto tg_avg = 0.0;
    auto pp_std = 0.0;
    auto tg_std = 0.0;

    const uint32_t n_ctx = llama_n_ctx(context);
    LOGi("n_ctx = %d", n_ctx);

    int i, j;
    int nri;
    for (nri = 0; nri < nr; nri++) {
        LOGi("Benchmark prompt processing (pp = %d)", pp);

        common_batch_clear(g_batch);

        const int n_tokens = pp;
        for (i = 0; i < n_tokens; i++) {
            common_batch_add(g_batch, 0, i, {0}, false);
        }

        g_batch.logits[g_batch.n_tokens - 1] = true;
        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp_start = ggml_time_us();
        if (llama_decode(context, g_batch) != 0) {
            LOGe("llama_decode() failed during prompt processing");
        }
        const auto t_pp_end = ggml_time_us();

        // bench text generation

        LOGi("Benchmark text generation (tg = %d)", tg);

        llama_memory_clear(llama_get_memory(context), false);
        const auto t_tg_start = ggml_time_us();
        for (i = 0; i < tg; i++) {
            common_batch_clear(g_batch);
            for (j = 0; j < pl; j++) {
                common_batch_add(g_batch, 0, i, {j}, true);
            }

            if (llama_decode(context, g_batch) != 0) {
                LOGe("llama_decode() failed during text generation");
            }
        }
        const auto t_tg_end = ggml_time_us();

        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp = double(t_pp_end - t_pp_start) / 1000000.0;
        const auto t_tg = double(t_tg_end - t_tg_start) / 1000000.0;

        const auto speed_pp = double(pp) / t_pp;
        const auto speed_tg = double(pl * tg) / t_tg;

        pp_avg += speed_pp;
        tg_avg += speed_tg;

        pp_std += speed_pp * speed_pp;
        tg_std += speed_tg * speed_tg;

        LOGi("pp %f t/s, tg %f t/s", speed_pp, speed_tg);
    }

    llama_free(context);

    pp_avg /= double(nr);
    tg_avg /= double(nr);

    if (nr > 1) {
        pp_std = sqrt(pp_std / double(nr - 1) - pp_avg * pp_avg * double(nr) / double(nr - 1));
        tg_std = sqrt(tg_std / double(nr - 1) - tg_avg * tg_avg * double(nr) / double(nr - 1));
    } else {
        pp_std = 0;
        tg_std = 0;
    }

    char model_desc[128];
    llama_model_desc(g_model, model_desc, sizeof(model_desc));

    const auto model_size = double(llama_model_size(g_model)) / 1024.0 / 1024.0 / 1024.0;
    const auto model_n_params = double(llama_model_n_params(g_model)) / 1e9;

    const auto backend = get_backend();
    std::stringstream result;
    result << std::setprecision(3);
    result << "| model | size | params | backend | test | t/s |\n";
    result << "| --- | --- | --- | --- | --- | --- |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | pp " << pp << " | " << pp_avg << " ± " << pp_std << " |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | tg " << tg << " | " << tg_avg << " ± " << tg_std << " |\n";
    return env->NewStringUTF(result.str().c_str());
}


/**
 * Completion loop's long-term states:
 * - chat management
 * - position tracking
 */
constexpr const char *ROLE_SYSTEM       = "system";
constexpr const char *ROLE_USER         = "user";
constexpr const char *ROLE_ASSISTANT    = "assistant";

static std::vector<common_chat_msg> chat_msgs;
// system_prompt_position / current_position are declared near the top of this file.

static void reset_long_term_states(const bool clear_kv_cache = true) {
    // Remember how much was actually written before zeroing the cursors.
    // llama_memory_clear() walks every cell of the whole 8192-slot cache
    // (36 layers x 8192 x K/V), which costs 10+ seconds on a phone even when
    // almost nothing was used. Removing only the used range is proportional
    // to real work and is a no-op for a fresh conversation.
    const llama_pos used = current_position;

    chat_msgs.clear();
    system_prompt_position = 0;
    current_position = 0;

    if (clear_kv_cache && used > 0) {
        llama_memory_seq_rm(llama_get_memory(g_context), 0, 0, used);
        LOGi("%s: dropped KV range [0, %d)", __func__, used);
    }
}

/**
 * TODO-hyin: implement sliding-window version as a better alternative
 *
 * Context shifting by discarding the older half of the tokens appended after system prompt:
 * - take the [system_prompt_position] first tokens from the original prompt
 * - take half of the last (system_prompt_position - system_prompt_position) tokens
 * - recompute the logits in batches
 */
static void shift_context() {
    const int n_discard = (current_position - system_prompt_position) / 2;
    LOGi("%s: Discarding %d tokens", __func__, n_discard);
    llama_memory_seq_rm(llama_get_memory(g_context), 0, system_prompt_position, system_prompt_position + n_discard);
    llama_memory_seq_add(llama_get_memory(g_context), 0, system_prompt_position + n_discard, current_position, -n_discard);
    current_position -= n_discard;
    LOGi("%s: Context shifting done! Current position: %d", __func__, current_position);
}

static std::string chat_add_and_format(const std::string &role, const std::string &content) {
    common_chat_msg new_msg;
    new_msg.role = role;
    new_msg.content = content;
    auto formatted = common_chat_format_single(
            g_chat_templates.get(), chat_msgs, new_msg, role == ROLE_USER, /* use_jinja */ false);
    chat_msgs.push_back(new_msg);
    LOGi("%s: Formatted and added %s message: \n%s\n", __func__, role.c_str(), formatted.c_str());
    return formatted;
}

/**
 * Completion loop's short-term states:
 * - stop generation position
 * - token chars caching
 * - current assistant message being generated
 */
static llama_pos stop_generation_position;
static std::string cached_token_chars;
static std::ostringstream assistant_ss;

static void reset_short_term_states() {
    stop_generation_position = 0;
    cached_token_chars.clear();
    assistant_ss.str("");
}

static int decode_tokens_in_batches(
        llama_context *context,
        llama_batch &batch,
        const llama_tokens &tokens,
        const llama_pos start_pos,
        const bool compute_last_logit = false) {
    // Process tokens in batches using the global batch
    LOGd("%s: Decode %d tokens starting at position %d", __func__, (int) tokens.size(), start_pos);
    for (int i = 0; i < (int) tokens.size(); i += BATCH_SIZE) {
        const int cur_batch_size = std::min((int) tokens.size() - i, BATCH_SIZE);
        common_batch_clear(batch);
        LOGv("%s: Preparing a batch size of %d starting at: %d", __func__, cur_batch_size, i);

        // Shift context if current batch cannot fit into the context
        if (start_pos + i + cur_batch_size >= g_n_ctx - OVERFLOW_HEADROOM) {
            LOGw("%s: Current batch won't fit into context! Shifting...", __func__);
            shift_context();
        }

        // Add tokens to the batch with proper positions
        for (int j = 0; j < cur_batch_size; j++) {
            const llama_token token_id = tokens[i + j];
            const llama_pos position = start_pos + i + j;
            const bool want_logit = compute_last_logit && (i + j == tokens.size() - 1);
            common_batch_add(batch, token_id, position, {0}, want_logit);
        }

        // Decode this batch
        const int decode_result = llama_decode(context, batch);
        if (decode_result) {
            LOGe("%s: llama_decode failed w/ %d", __func__, decode_result);
            return 1;
        }
    }
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processSystemPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring jsystem_prompt
) {
    // Reset long-term & short-term states
    reset_long_term_states();
    reset_short_term_states();

    // Obtain system prompt from JEnv
    const auto *system_prompt = env->GetStringUTFChars(jsystem_prompt, nullptr);
    LOGd("%s: System prompt received: \n%s", __func__, system_prompt);
    std::string formatted_system_prompt(system_prompt);

    // Format system prompt if applicable
    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_chat_template) {
        formatted_system_prompt = chat_add_and_format(ROLE_SYSTEM, system_prompt);
    }
    env->ReleaseStringUTFChars(jsystem_prompt, system_prompt);

    // Tokenize system prompt
    const auto system_tokens = common_tokenize(g_context, formatted_system_prompt,
                                               has_chat_template, has_chat_template);
    for (auto id: system_tokens) {
        LOGv("token: `%s`\t -> `%d`", common_token_to_piece(g_context, id).c_str(), id);
    }

    // Handle context overflow
    const int max_batch_size = g_n_ctx - OVERFLOW_HEADROOM;
    if ((int) system_tokens.size() > max_batch_size) {
        LOGe("%s: System prompt too long for context! %d tokens, max: %d",
             __func__, (int) system_tokens.size(), max_batch_size);
        return 1;
    }

    // Decode system tokens in batches
    if (decode_tokens_in_batches(g_context, g_batch, system_tokens, current_position)) {
        LOGe("%s: llama_decode() failed!", __func__);
        return 2;
    }

    // Update position
    system_prompt_position = current_position = (int) system_tokens.size();
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processUserPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring juser_prompt,
        jint n_predict
) {
    // Reset short-term states
    reset_short_term_states();

    // Obtain and tokenize user prompt
    const auto *const user_prompt = env->GetStringUTFChars(juser_prompt, nullptr);
    LOGd("%s: User prompt received: \n%s", __func__, user_prompt);
    std::string formatted_user_prompt(user_prompt);

    // Format user prompt if applicable
    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_chat_template) {
        formatted_user_prompt = chat_add_and_format(ROLE_USER, user_prompt);
    }
    env->ReleaseStringUTFChars(juser_prompt, user_prompt);

    // Decode formatted user prompts
    auto user_tokens = common_tokenize(g_context, formatted_user_prompt, has_chat_template, has_chat_template);
    for (auto id: user_tokens) {
        LOGv("token: `%s`\t -> `%d`", common_token_to_piece(g_context, id).c_str(), id);
    }

    // Ensure user prompt doesn't exceed the context size by truncating if necessary.
    const int user_prompt_size = (int) user_tokens.size();
    const int max_batch_size = g_n_ctx - OVERFLOW_HEADROOM;
    if (user_prompt_size > max_batch_size) {
        const int skipped_tokens = user_prompt_size - max_batch_size;
        user_tokens.resize(max_batch_size);
        LOGw("%s: User prompt too long! Skipped %d tokens!", __func__, skipped_tokens);
    }

    // Decode user tokens in batches
    if (decode_tokens_in_batches(g_context, g_batch, user_tokens, current_position, true)) {
        LOGe("%s: llama_decode() failed!", __func__);
        return 2;
    }

    // Update position
    current_position += user_prompt_size;
    stop_generation_position = current_position + n_predict;
    return 0;
}

static bool is_valid_utf8(const char *string) {
    if (!string) { return true; }

    const auto *bytes = (const unsigned char *) string;
    int num;

    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            // U+0000 to U+007F
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            // U+0080 to U+07FF
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            // U+0800 to U+FFFF
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            // U+10000 to U+10FFFF
            num = 4;
        } else {
            return false;
        }

        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_generateNextToken(
        JNIEnv *env,
        jobject /*unused*/
) {
    // Infinite text generation via context shifting
    if (current_position >= g_n_ctx - OVERFLOW_HEADROOM) {
        LOGw("%s: Context full! Shifting...", __func__);
        shift_context();
    }

    // Stop if reaching the marked position
    if (current_position >= stop_generation_position) {
        LOGw("%s: STOP: hitting stop position: %d", __func__, stop_generation_position);
        return nullptr;
    }

    // Sample next token
    const auto new_token_id = common_sampler_sample(g_sampler, g_context, -1);
    common_sampler_accept(g_sampler, new_token_id, true);

    // Populate the batch with new token, then decode
    common_batch_clear(g_batch);
    common_batch_add(g_batch, new_token_id, current_position, {0}, true);

    // Wall-clock timing for this decode step, sampled to keep log noise low.
    const auto t0 = std::chrono::steady_clock::now();
    const int decode_result = llama_decode(g_context, g_batch);
    const double decode_ms = std::chrono::duration<double, std::milli>(
            std::chrono::steady_clock::now() - t0).count();

    if (decode_result != 0) {
        LOGe("%s: llama_decode() failed for generated token", __func__);
        return nullptr;
    }

    if (++g_decode_counter % 16 == 0) {
        LOGi("%s: decode step took %.1f ms", __func__, decode_ms);
    }

    // Update position
    current_position++;

    // Stop if next token is EOG
    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token_id)) {
        LOGd("id: %d,\tIS EOG!\nSTOP.", new_token_id);
        chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());
        return nullptr;
    }

    // If not EOG, convert to text
    auto new_token_chars = common_token_to_piece(g_context, new_token_id);
    cached_token_chars += new_token_chars;

    // Create and return a valid UTF-8 Java string
    jstring result = nullptr;
    if (is_valid_utf8(cached_token_chars.c_str())) {
        result = env->NewStringUTF(cached_token_chars.c_str());
        LOGv("id: %d,\tcached: `%s`,\tnew: `%s`", new_token_id, cached_token_chars.c_str(), new_token_chars.c_str());

        assistant_ss << cached_token_chars;
        cached_token_chars.clear();
    } else {
        LOGv("id: %d,\tappend to cache", new_token_id);
        result = env->NewStringUTF("");
    }
    return result;
}

/*
 * Clear conversation state, keeping the loaded model and KV cache allocation.
 * Used when the host rebuilds history after context compression.
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeResetConversation(
        JNIEnv * /*env*/, jobject /*unused*/) {
    reset_long_term_states(true);
    reset_short_term_states();
    LOGi("%s: conversation state cleared", __func__);
}

/*
 * Append one message to the context without running the sampling loop.
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativePrefillMessage(
        JNIEnv *env, jobject /*unused*/, jstring jrole, jstring jcontent) {
    if (!g_context) {
        LOGe("%s: context not ready", __func__);
        return 1;
    }

    const auto *role_chars = env->GetStringUTFChars(jrole, nullptr);
    const auto *content_chars = env->GetStringUTFChars(jcontent, nullptr);
    std::string role_str(role_chars);
    std::string content_str(content_chars);
    env->ReleaseStringUTFChars(jrole, role_chars);
    env->ReleaseStringUTFChars(jcontent, content_chars);

    const auto formatted = chat_add_and_format(role_str, content_str);

    const auto t_tokenize_start = std::chrono::steady_clock::now();
    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    auto tokens = common_tokenize(g_context, formatted, has_chat_template, has_chat_template);
    const double tokenize_ms = std::chrono::duration<double, std::milli>(
            std::chrono::steady_clock::now() - t_tokenize_start).count();

    const int max_batch_size = g_n_ctx - OVERFLOW_HEADROOM;
    if ((int) tokens.size() > max_batch_size) {
        LOGw("%s: message too long, truncating %d -> %d tokens",
             __func__, (int) tokens.size(), max_batch_size);
        tokens.resize(max_batch_size);
    }

    const auto t_decode_start = std::chrono::steady_clock::now();
    const int decode_rc = decode_tokens_in_batches(g_context, g_batch, tokens, current_position);
    const double decode_ms = std::chrono::duration<double, std::milli>(
            std::chrono::steady_clock::now() - t_decode_start).count();

    if (decode_rc) {
        LOGe("%s: decode failed", __func__);
        return 2;
    }

    current_position += (int) tokens.size();

    if (role_str == ROLE_SYSTEM) {
        system_prompt_position = current_position;
    }

    LOGi("%s: prefilled %s, %d tokens (tokenize %.1f ms, decode %.1f ms), position now %d",
         __func__, role_str.c_str(), (int) tokens.size(),
         tokenize_ms, decode_ms, current_position);
    return 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_unload(JNIEnv * /*unused*/, jobject /*unused*/) {
    // Reset long-term & short-term states
    reset_long_term_states();
    reset_short_term_states();

    // Free up resources
    common_sampler_free(g_sampler);
    g_chat_templates.reset();
    llama_batch_free(g_batch);
    llama_free(g_context);
    llama_model_free(g_model);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_shutdown(JNIEnv *, jobject /*unused*/) {
    llama_backend_free();
}

// ============================================================================
// Embedding model: separate from the generative one so both can stay resident.
// ============================================================================

/*
 * Load a BERT-style embedding model (e.g. bge-small-zh-v1.5) into its own
 * context. Returns 0 on success.
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeLoadEmbeddingModel(
        JNIEnv *env, jobject /*unused*/, jstring jmodel_path) {
    if (g_emb_context) {
        LOGe("%s: embedding model already loaded", __func__);
        return 3;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    const auto *path = env->GetStringUTFChars(jmodel_path, nullptr);
    LOGi("%s: loading embedding model from: %s", __func__, path);
    llama_model *model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, path);

    if (!model) {
        LOGe("%s: failed to load embedding model", __func__);
        return 1;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx        = EMBED_CONTEXT_SIZE;
    ctx_params.n_batch      = EMBED_BATCH_SIZE;
    ctx_params.n_ubatch     = EMBED_BATCH_SIZE;
    ctx_params.n_threads    = std::max(1, std::min(4, (int) sysconf(_SC_NPROCESSORS_ONLN)));
    ctx_params.n_threads_batch = ctx_params.n_threads;
    // BERT embedders need causal attention off and pooled (CLS) embeddings on.
    ctx_params.embeddings   = true;
    ctx_params.attention_type = LLAMA_ATTENTION_TYPE_NON_CAUSAL;
    ctx_params.pooling_type = LLAMA_POOLING_TYPE_CLS;

    llama_context *context = llama_init_from_model(model, ctx_params);
    if (!context) {
        LOGe("%s: failed to create embedding context", __func__);
        llama_model_free(model);
        return 2;
    }

    g_emb_model   = model;
    g_emb_context = context;
    g_emb_batch   = llama_batch_init(EMBED_BATCH_SIZE, 0, 1);
    g_emb_dim     = (int) llama_model_n_embd(model);

    LOGi("%s: embedding model ready, dim=%d, n_ctx=%d",
         __func__, g_emb_dim, llama_n_ctx(context));
    return 0;
}

/*
 * Embed one text, returning a normalized float[n_embd] vector, or null on error.
 */
extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeEmbedText(
        JNIEnv *env, jobject /*unused*/, jstring jtext) {
    if (!g_emb_context || !g_emb_model) {
        LOGe("%s: embedding model not loaded", __func__);
        return nullptr;
    }

    const auto *text = env->GetStringUTFChars(jtext, nullptr);
    const std::string input(text);
    env->ReleaseStringUTFChars(jtext, text);

    if (input.empty()) {
        LOGe("%s: empty input", __func__);
        return nullptr;
    }

    // Fresh state per call; the embedder has no cross-call memory.
    llama_memory_clear(llama_get_memory(g_emb_context), true);

    auto tokens = common_tokenize(g_emb_context, input, true, true);
    if (tokens.empty()) {
        LOGe("%s: tokenization produced nothing", __func__);
        return nullptr;
    }
    const int max_tokens = EMBED_CONTEXT_SIZE - 2;
    if ((int) tokens.size() > max_tokens) {
        LOGw("%s: truncating %d -> %d tokens", __func__, (int) tokens.size(), max_tokens);
        tokens.resize(max_tokens);
    }

    common_batch_clear(g_emb_batch);
    for (size_t i = 0; i < tokens.size(); i++) {
        // logits=false: we only need the pooled embedding, not per-token logits.
        common_batch_add(g_emb_batch, tokens[i], (llama_pos) i, {0}, false);
    }

    if (llama_decode(g_emb_context, g_emb_batch) != 0) {
        LOGe("%s: llama_decode failed", __func__);
        return nullptr;
    }

    const float *embd = llama_get_embeddings_seq(g_emb_context, 0);
    if (!embd) {
        // Fall back to the single-sequence accessor on backends that do not
        // expose pooled results by seq id.
        embd = llama_get_embeddings(g_emb_context);
    }
    if (!embd) {
        LOGe("%s: no embeddings returned", __func__);
        return nullptr;
    }

    // L2 normalize so cosine similarity degrades to a dot product.
    std::vector<float> out(embd, embd + g_emb_dim);
    double norm = 0.0;
    for (float v : out) norm += (double) v * v;
    norm = std::sqrt(norm);
    if (norm > 1e-12) {
        const float inv = (float) (1.0 / norm);
        for (float &v : out) v *= inv;
    }

    jfloatArray result = env->NewFloatArray(g_emb_dim);
    if (result) {
        env->SetFloatArrayRegion(result, 0, g_emb_dim, out.data());
    }
    return result;
}

/** Embedding vector dimension; 0 when no model is loaded. */
extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeEmbeddingDim(JNIEnv *, jobject /*unused*/) {
    return (jint) g_emb_dim;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeUnloadEmbeddingModel(JNIEnv *, jobject /*unused*/) {
    if (g_emb_context) {
        llama_batch_free(g_emb_batch);
        llama_free(g_emb_context);
        g_emb_context = nullptr;
    }
    if (g_emb_model) {
        llama_model_free(g_emb_model);
        g_emb_model = nullptr;
    }
    g_emb_dim = 0;
    LOGi("%s: embedding model unloaded", __func__);
}
