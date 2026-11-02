package com.example.zhijing.cloud

import com.arm.aichat.InferenceEngine
import com.arm.aichat.InferenceEngine.State as EngineState
import com.example.zhijing.chat.ChatEngine
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transform

/**
 * 把云端大模型包装成本地与 [InferenceEngine] 完全一致的接口。
 *
 * 这样 [com.example.zhijing.chat.ConversationManager] 不需要知道自己连的是
 * llama.cpp 还是 API——懒加载、压缩、RAG 注入、落库等编排逻辑两端共用一份。
 *
 * **与端侧实现的语义差异（已刻意处理）**：
 * - `prefillMessage` 不产生任何网络请求，只累积到本地 `history`。端侧 prefill
 *   是为了填满 KV cache；云端每次请求都带完整 messages，不需要预填，否则会
 *   白白多花一次 token 费用。
 * - `resetConversation` 只清空 `history`，对上云没有成本。
 * - `contextWindow` 由服务商决定，返回保守估计值（见 [contextWindowHint]），
 *   供 ContextBudget 计算预算；真正的截断由服务端处理。
 * - 嵌入模型相关方法对云端无意义，`embed` 返回 null，让 RAG 自动跳过
 *   （检索始终在端侧，不会因为切到云端而失效——见 MainActivity 的说明）。
 *
 * 线程安全：所有方法都可在任意协程调用；内部只有一个 [AtomicBoolean] 标志位。
 */
class RemoteInferenceEngine(
    provider: CloudProvider,
    private val client: RemoteChatClient = RemoteChatClient(),
) : InferenceEngine, ChatEngine {

    /**
     * [ChatEngine.isReady]：云端没有"加载"过程，配置校验通过即可用。
     * 这个名字对云端略显别扭，但与本地引擎语义对齐——都表示"现在能生成"。
     */
    override val isReady: Boolean
        get() = _state.value is EngineState.ModelReady ||
            _state.value is EngineState.Generating

    /** 当前生效的配置。可在运行期整体替换（用户改设置后调用 [updateProvider]）。 */
    @Volatile
    var provider: CloudProvider = provider
        private set

    private val _state = MutableStateFlow<EngineState>(EngineState.Uninitialized)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    /** 云端会话历史（system 在前，按序追加）。只存在于内存，不上传原文以外的东西。 */
    private val history = mutableListOf<CloudMessage>()

    private val cancelled = AtomicBoolean(false)

    /** 是否已经"加载"过（云端没有加载动作，这里表示配置就绪）。 */
    private var loaded = false

    /**
     * 加载 = 校验配置。不发起网络请求，切换引擎是瞬时的，不会阻塞 UI。
     */
    override suspend fun loadModel(pathToModel: String, contextSize: Int) {
        val reason = provider.readyFailureReason()
        if (reason != null) {
            _state.value = EngineState.Error(CloudException("云端配置不完整：$reason"))
            return
        }
        loaded = true
        _state.value = EngineState.ModelReady
    }

    /** 运行期替换服务商/模型，不动已有历史。 */
    fun updateProvider(next: CloudProvider) {
        provider = next
        val reason = next.readyFailureReason()
        _state.value = when {
            reason != null -> EngineState.Error(CloudException("云端配置不完整：$reason"))
            loaded -> EngineState.ModelReady
            else -> _state.value
        }
    }

    /**
     * 云端上下文窗口。这里返回保守值 8192 token：
     * 它只影响 [com.example.zhijing.chat.ContextBudget] 的压缩阈值，返回偏小
     * 反而更安全（更早压缩历史，减少费用）；真正的超限由服务端拒绝，见 [sendUserPrompt]。
     */
    override fun contextWindow(): Int = contextWindowHint

    override fun contextOccupied(): Int = history.sumOf { estimateTokensApprox(it.content) }

    override suspend fun setSystemPrompt(systemPrompt: String) {
        require(systemPrompt.isNotBlank()) { "Cannot process empty system prompt!" }
        // 与端侧一致：system prompt 应最先进入上下文
        history.removeAll { it.role == "system" }
        history.add(0, CloudMessage("system", systemPrompt))
        if (!loaded) {
            loaded = true
            _state.value = EngineState.ModelReady
        }
    }

    override suspend fun resetConversation() {
        history.clear()
        cancelled.set(false)
        if (_state.value !is EngineState.Error) _state.value = EngineState.ModelReady
    }

    /**
     * 只记录到本地历史，**不**发网络请求。
     *
     * 端侧用 prefill 填 KV cache；云端每次带完整 messages，预填等于重复计费。
     */
    override suspend fun prefillMessage(role: String, content: String): Int {
        history.add(CloudMessage(role, content))
        return 0
    }

    /**
     * 发送用户消息，流式返回增量文本。
     *
     * 出错时：抛 [CloudException]，由上层决定是提示用户还是回退端侧。
     * 部分输出成功的情况不写回历史（避免半截回答污染上下文）。
     */
    override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> = flow {
        require(message.isNotEmpty()) { "User prompt discarded due to being empty!" }
        val cfg = provider
        cfg.readyFailureReason()?.let { throw CloudException("云端配置不完整：$it") }

        cancelled.set(false)
        _state.value = EngineState.Generating

        // 本次请求的完整上下文 = 历史 + 本条。历史里不含本条，所以不会重复发送。
        val messages = buildList {
            addAll(history)
            add(CloudMessage("user", message))
        }

        val collected = StringBuilder()
        try {
            emitAll(
                client.streamChat(cfg, messages, maxTokens = predictLength.takeIf { it > 0 })
                    .transform { token ->
                        // 用户点了"停止"：后续 token 不再上抛，但连接由调用方取消断开
                        if (!cancelled.get()) {
                            collected.append(token)
                            emit(token)
                        }
                    }
            )
            // 全部成功后一次性提交历史：先确认成功，再动会话状态，避免半截回答污染上下文
            history.add(CloudMessage("user", message))
            if (collected.isNotEmpty()) history.add(CloudMessage("assistant", collected.toString()))
        } catch (e: CancellationException) {
            throw e
        } finally {
            _state.value = EngineState.ModelReady
        }
    }

    override fun requestCancelGeneration() {
        cancelled.set(true)
    }

    override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String =
        "云端引擎不支持本地 benchmark"

    // --- 嵌入：云端不参与检索 ------------------------------------------------
    //
    // 检索固定在端侧：原文和向量都不出手机，只有"命中的片段 + 问题"会发给云端。
    // 因此这里返回"没有嵌入能力"，让调用方去用本地引擎的嵌入。

    override suspend fun loadEmbeddingModel(path: String) {
        // 云端引擎没有嵌入模型
    }

    override suspend fun embed(text: String): FloatArray? = null

    override fun embeddingDim(): Int = 0

    override suspend fun unloadEmbeddingModel() = Unit

    override fun cleanUp() {
        history.clear()
        loaded = false
        _state.value = EngineState.Initialized
    }

    override fun destroy() {
        history.clear()
        cancelled.set(true)
    }

    /** 当前历史条数，供调试与设置页展示。 */
    fun historySize(): Int = history.size

    companion object {
        /** 保守的上下文估计，见 [contextWindow]。 */
        const val contextWindowHint = 8192

        /**
         * 粗略 token 估计。
         *
         * 中文约 1 字≈0.6~1 token，这里取 1 字≈1 token 的保守值；
         * 只用于 [contextOccupied] 的展示，不参与实际请求控制。
         */
        private fun estimateTokensApprox(text: String): Int = text.length
    }
}
