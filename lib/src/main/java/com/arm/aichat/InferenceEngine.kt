package com.arm.aichat

import com.arm.aichat.InferenceEngine.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Interface defining the core LLM inference operations.
 */
interface InferenceEngine {
    /**
     * Current state of the inference engine
     */
    val state: StateFlow<State>

    /**
     * Load a model from the given path.
     *
     * @param contextSize requested context window in tokens. `0` means "use the
     *        model's own trained context", which is the most portable choice when
     *        swapping models. Prefer a value sized to the device's memory.
     * @throws UnsupportedArchitectureException if model architecture not supported
     */
    suspend fun loadModel(pathToModel: String, contextSize: Int = 0)

    /**
     * Context window actually in effect for the loaded model (tokens).
     */
    fun contextWindow(): Int

    /**
     * Tokens currently occupied by the system prompt + conversation history.
     */
    fun contextOccupied(): Int

    /**
     * Sends a system prompt to the loaded model
     */
    suspend fun setSystemPrompt(systemPrompt: String)


    //清空对话状态，保留已经加载的模型，后面可以用[prefillMessage]重建历史
    suspend fun resetConversation()

    //把一条历史信息写入上下文，不触发采样，用于压缩后重建会话历史，调用前先调用[resetConversation]
    suspend fun prefillMessage(role:String,content:String):Int

    /**
     * Sends a user prompt to the loaded model and returns a Flow of generated tokens.
     */
    fun sendUserPrompt(message: String, predictLength: Int = DEFAULT_PREDICT_LENGTH): Flow<String>

    /**
     * Requests the in-flight generation to stop after the current token.
     *
     * Non-blocking: only sets a flag; the generation loop checks it between
     * tokens and then completes the [sendUserPrompt] flow normally, leaving the
     * engine back in [State.ModelReady]. Safe to call when nothing is generating.
     */
    fun requestCancelGeneration()

    /**
     * Runs a benchmark with the specified parameters.
     */
    suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1): String

    // --- Embedding (RAG) -----------------------------------------------------

    /**
     * Loads a BERT-style embedding model into its own context, independent of
     * the generative model. Both stay resident, which costs only tens of MB.
     *
     * @throws IllegalStateException if an embedding model is already loaded
     * @throws IllegalArgumentException if the file is missing or unusable
     */
    suspend fun loadEmbeddingModel(path: String)

    /**
     * Embeds one piece of text into an L2-normalized vector.
     *
     * @return the vector, or null when no embedding model is loaded or the
     *         input could not be processed
     */
    suspend fun embed(text: String): FloatArray?

    /** Dimension of the embedding vector; 0 when no model is loaded. */
    fun embeddingDim(): Int

    /** Releases the embedding model. No-op when nothing is loaded. */
    suspend fun unloadEmbeddingModel()

    /**
     * Unloads the currently loaded model.
     */
    fun cleanUp()

    /**
     * Cleans up resources when the engine is no longer needed.
     */
    fun destroy()

    /**
     * States of the inference engine
     */
    sealed class State {
        object Uninitialized : State()
        object Initializing : State()
        object Initialized : State()

        object LoadingModel : State()
        object UnloadingModel : State()
        object ModelReady : State()

        object Benchmarking : State()
        object ProcessingSystemPrompt : State()
        object ProcessingUserPrompt : State()

        object Generating : State()

        data class Error(val exception: Exception) : State()
    }

    companion object {
        const val DEFAULT_PREDICT_LENGTH = 1024
    }
}

val State.isUninterruptible
    get() = this is State.Initializing ||
        this is State.LoadingModel ||
        this is State.UnloadingModel ||
        this is State.Benchmarking ||
        this is State.ProcessingSystemPrompt ||
        this is State.ProcessingUserPrompt

val State.isModelLoaded: Boolean
    get() = this is State.ModelReady ||
        this is State.Benchmarking ||
        this is State.ProcessingSystemPrompt ||
        this is State.ProcessingUserPrompt ||
        this is State.Generating

/**
 * Stops any in-flight generation and suspends until the engine is back in
 * [State.ModelReady], so callers may safely touch the context (reset, prefill).
 *
 * If nothing is generating this returns almost immediately.
 */
suspend fun InferenceEngine.cancelGenerationAndAwaitReady(timeoutMillis: Long = 5_000) {
    requestCancelGeneration()
    withTimeoutOrNull(timeoutMillis) {
        state.first { it is State.ModelReady || it is State.Error }
    }
}

class UnsupportedArchitectureException(message: String? = null) : Exception(message)
