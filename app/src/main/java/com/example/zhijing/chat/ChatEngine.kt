package com.example.zhijing.chat

import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import com.example.zhijing.chat.RoutePolicy.RouteMode as RouteMode
import kotlinx.coroutines.flow.Flow

/**
 * [ConversationManager] 真正需要的那部分引擎能力。
 *
 * 提炼成接口而不是直接依赖 [InferenceEngine]，是为了让端侧（llama.cpp）和
 * 云端（OpenAI 兼容 API）都能驱动同一套会话编排——懒加载、上下文压缩、RAG
 * 注入、落库的代码只写一遍。
 *
 * 端侧直接实现本接口：[InferenceEngine] 的方法签名恰好一致，用
 * [asChatEngine] 适配，零改动、零包装开销。
 */
interface ChatEngine {
    /** 清空上下文，保留引擎本身。 */
    suspend fun resetConversation()

    /** 把一条历史写入上下文，不触发采样。 */
    suspend fun prefillMessage(role: String, content: String): Int

    /** 发送一次提问，流式返回增量文本。 */
    fun sendUserPrompt(message: String, predictLength: Int): Flow<String>

    /** 请求停止正在进行的生成（非阻塞）。 */
    fun requestCancelGeneration()

    /** 当前是否已加载可用（端侧=模型就绪；云端=配置完整）。 */
    val isReady: Boolean

    /** 嵌入向量维度；0 表示没有嵌入能力，RAG 将跳过。 */
    fun embeddingDim(): Int

    /** 文本向量化；没有嵌入能力时返回 null。 */
    suspend fun embed(text: String): FloatArray?
}

/**
 * 把端侧 [InferenceEngine] 适配成 [ChatEngine]。
 *
 * 不做任何转发包装，直接委托，保留原有行为（包括 `embeddingDim()` 的
 * native 直调语义）。
 */
class LocalChatEngine(private val engine: InferenceEngine) : ChatEngine {
    override suspend fun resetConversation() = engine.resetConversation()
    override suspend fun prefillMessage(role: String, content: String): Int =
        engine.prefillMessage(role, content)
    override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> =
        engine.sendUserPrompt(message, predictLength)
    override fun requestCancelGeneration() = engine.requestCancelGeneration()
    override val isReady: Boolean get() = engine.state.value.isModelLoaded
    override fun embeddingDim(): Int = engine.embeddingDim()
    override suspend fun embed(text: String): FloatArray? = engine.embed(text)
}

/**
 * 推理路由：决定这一次请求走端侧还是云端。
 *
 * 默认 [RouteMode.LOCAL_ONLY]，也就是说**不配置云端时，行为与纯离线版本完全
 * 一致**。云端是显式开启的能力，不会在用户不知情时被调用。
 */
interface RoutePolicy {
    /** 当前路由模式。 */
    val mode: RouteMode

    /**
     * 这一次请求该走哪里。
     *
     * @param hasCloud 云端是否可用（已配置且校验通过）
     */
    fun route(hasCloud: Boolean): RouteMode

    enum class RouteMode {
        /** 只用端侧。0 网络请求。 */
        LOCAL_ONLY,

        /** 优先云端，失败自动回退端侧。 */
        CLOUD_FIRST,

        /** 只用云端（用户明确选择，用于对比/长文档场景）。 */
        CLOUD_ONLY,
    }
}

/** 简单实现：返回固定模式；云端不可用时一律降级为纯端侧。 */
class FixedRoutePolicy(override val mode: RouteMode) : RoutePolicy {
    override fun route(hasCloud: Boolean): RouteMode = when {
        !hasCloud -> RouteMode.LOCAL_ONLY
        else -> mode
    }
}
