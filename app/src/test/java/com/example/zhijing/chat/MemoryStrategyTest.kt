package com.example.zhijing.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记忆策略测试。
 *
 * 守护三件事：
 * 1. 端侧与云端的**预算与提要上限不同**（端侧紧、云端宽）；
 * 2. 摘要失败返回 null 让调用方降级，**不抛异常**（用户正在等回答）；
 * 3. 提要有明确长度上限，防止「digest 无限增长」这个旧实现的坑。
 */
class MemoryStrategyTest {

    /** 可控的假引擎：记录收到的 prompt，可配置返回内容或抛错。 */
    private class FakeEngine(
        private val reply: String = "摘要",
        private val failWith: Exception? = null,
        private val returnEmpty: Boolean = false,
    ) : ChatEngine {
        val prompts = mutableListOf<String>()
        override val isReady: Boolean = true

        override suspend fun resetConversation() = Unit
        override suspend fun prefillMessage(role: String, content: String): Int = 0

        override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> {
            prompts += message
            failWith?.let { return flow<String> { throw it } }
            return if (returnEmpty) flowOf("") else flowOf(reply)
        }

        override fun requestCancelGeneration() = Unit
        override fun embeddingDim(): Int = 0
        override suspend fun embed(text: String): FloatArray? = null
    }

    private fun budget(history: Int, window: Int = 2048) = ContextBudget(
        window = window,
        reserveOut = 0,
        reserveSys = 0
    )

    private fun turns(vararg texts: String): List<Turn> = texts.mapIndexed { i, t ->
        Turn(if (i % 2 == 0) Role.USER else Role.ASSISTANT, t, estimateTokens(t), i + 1)
    }

    private val local = LocalMemoryStrategy(historyBudget = budget(1200))
    private val cloud = CloudMemoryStrategy(historyBudget = budget(8192))

    @Test
    fun `端侧与云端的提要上限不同`() {
        assertTrue(local.digestMaxChars < cloud.digestMaxChars)
        assertEquals(400, local.digestMaxChars)
        assertEquals(2000, cloud.digestMaxChars)
        assertEquals("local", local.name)
        assertEquals("cloud", cloud.name)
    }

    @Test
    fun `端侧摘要成功时返回模型输出`() = runBlocking {
        val engine = FakeEngine(reply = "用户想了解 ReLU 的梯度行为")
        val result = local.summarize(turns("ReLU 为什么在负半轴梯度为 0？"), engine)
        assertEquals("用户想了解 ReLU 的梯度行为", result)
    }

    @Test
    fun `摘要失败返回null而不是抛异常`() = runBlocking {
        val engine = FakeEngine(failWith = IllegalStateException("模型超时"))
        assertNull(local.summarize(turns("问题"), engine))
        assertNull(cloud.summarize(turns("问题"), engine))
    }

    @Test
    fun `摘要为空串也视为失败`() = runBlocking {
        val engine = FakeEngine(returnEmpty = true)
        assertNull(local.summarize(turns("问题"), engine))
    }

    @Test
    fun `空轮次不调用模型`() = runBlocking {
        val engine = FakeEngine()
        assertNull(local.summarize(emptyList(), engine))
        assertTrue(engine.prompts.isEmpty())
    }

    @Test
    fun `云端摘要要求结构化四类`() = runBlocking {
        val engine = FakeEngine()
        cloud.summarize(turns("用户：我想学 Transformer", "助手：可以从注意力开始"), engine)
        val prompt = engine.prompts.single()
        assertTrue(prompt.contains("用户的目标与偏好"))
        assertTrue(prompt.contains("已确认的事实与结论"))
        assertTrue(prompt.contains("待办"))
        assertTrue(prompt.contains("尚未解决"))
    }

    @Test
    fun `端侧摘要限制输入长度以控制推理耗时`() = runBlocking {
        val engine = FakeEngine()
        val long = "很长的内容".repeat(500)
        local.summarize(turns(long), engine)
        // 每轮最多喂 200 字，prompt 不应包含整段长文
        assertTrue(engine.prompts.single().length < long.length)
    }

    @Test
    fun `提要未超限时compact直接拼接`() = runBlocking {
        val engine = FakeEngine(reply = "压缩后")
        val result = local.compactDigest(existing = "短提要", incoming = "新增要点", engine)
        assertEquals("短提要；新增要点", result)
        assertTrue(engine.prompts.isEmpty())
    }

    @Test
    fun `提要超限时compact调用模型`() = runBlocking {
        val engine = FakeEngine(reply = "合并后的摘要")
        val long = "x".repeat(local.digestMaxChars + 100)
        val result = local.compactDigest(existing = long, incoming = "新要点", engine)
        assertEquals("合并后的摘要", result)
        assertEquals(1, engine.prompts.size)
    }

    @Test
    fun `云端compact在自身上限内不调用模型`() = runBlocking {
        val engine = FakeEngine()
        val content = "y".repeat(cloud.digestMaxChars - 10)
        val result = cloud.compactDigest(existing = content, incoming = "", engine)
        assertEquals(content, result)
        assertTrue(engine.prompts.isEmpty())
    }
}
