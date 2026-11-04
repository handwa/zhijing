package com.example.zhijing.chat

import com.zhijing.data.repository.ConversationEntitySnapshot
import com.zhijing.data.repository.ConversationRepository
import com.zhijing.data.repository.MessageSnapshot
import com.example.zhijing.chat.RoutePolicy.RouteMode as RouteMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ConversationManager] 的端云路由行为。
 *
 * 重点锁住两个曾经踩过的坑：
 * 1. **路由在两个引擎之间切换时必须重建新引擎的上下文**——否则本地引擎还停在
 *    旧会话，云端失败回退后会答非所问。
 * 2. **云端失败时回退端侧**，且回退后本地生成用的是补齐过的上下文。
 */
class ConversationManagerRoutingTest {

    /** 记录调用的假引擎。 */
    private class FakeEngine(
        override val isReady: Boolean = true,
        private val embedDim: Int = 0,
        private val failWith: Exception? = null,
        private val reply: String = "好的",
    ) : ChatEngine {
        val prefilled = mutableListOf<Pair<String, String>>()
        var resets = 0
        var sends = 0

        override suspend fun resetConversation() {
            resets++
            prefilled.clear()
        }

        override suspend fun prefillMessage(role: String, content: String): Int {
            prefilled.add(role to content)
            return 0
        }

        override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> {
            sends++
            failWith?.let { throw it }
            return flowOf(reply)
        }

        override fun requestCancelGeneration() = Unit
        override fun embeddingDim(): Int = embedDim
        override suspend fun embed(text: String): FloatArray? = null

        fun empty() = prefilled.isEmpty()
    }

    /** 内存版仓库，只实现本用例需要的部分。 */
    private class FakeRepo : ConversationRepository {
        private var nextSeq = 1
        private val messages = mutableListOf<MessageSnapshot>()
        var digestUpToSeq = 0

        override suspend fun createConversation(title: String, systemPrompt: String): Long = 1L

        override suspend fun findConversation(id: Long): ConversationEntitySnapshot = ConversationEntitySnapshot(
            id = 1L,
            title = "t",
            systemPrompt = "sys",
            digest = null,
            digestUpToSeq = digestUpToSeq,
            createdAt = 0,
            updatedAt = 0,
        )

        override fun observeConversations(): Flow<List<ConversationEntitySnapshot>> = flowOf(emptyList())
        override suspend fun deleteConversation(id: Long) = Unit

        override suspend fun appendMessage(
            conversationId: Long,
            role: String,
            content: String,
            tokenCount: Int,
        ): Int {
            val seq = nextSeq++
            messages.add(MessageSnapshot(seq.toLong(), role, content, tokenCount, seq, 0))
            return seq
        }

        override suspend fun messagesAfter(conversationId: Long, afterSeq: Int): List<MessageSnapshot> =
            messages.filter { it.seq > afterSeq }

        override suspend fun allMessages(conversationId: Long): List<MessageSnapshot> = messages.toList()
        override suspend fun updateDigest(conversationId: Long, digest: String, upToSeq: Int) {
            digestUpToSeq = upToSeq
        }

        override suspend fun renameConversation(conversationId: Long, title: String) = Unit
    }

    private fun budget() = ContextBudget.from(
        window = 8192,
        predictLength = 256,
        systemPrompt = "sys",
    )

    @Test
    fun `local only never touches cloud`() = runBlocking {
        val local = FakeEngine(reply = "本地回答")
        val cloud = FakeEngine(reply = "云端回答")
        val m = ConversationManager(
            conversations = FakeRepo(),
            local = local,
            cloud = cloud,
            budget = budget(),
            predictLength = 64,
            routePolicy = FixedRoutePolicy(RouteMode.LOCAL_ONLY),
        )
        m.open(null, "sys")
        val out = m.send("你好").toList()

        assertEquals(listOf("本地回答"), out)
        assertEquals(0, cloud.sends)
        assertEquals(1, local.sends)
        assertEquals(RouteMode.LOCAL_ONLY, m.lastRoute)
    }

    @Test
    fun `cloud first uses cloud when available`() = runBlocking {
        val local = FakeEngine(reply = "本地回答")
        val cloud = FakeEngine(reply = "云端回答")
        val m = ConversationManager(
            conversations = FakeRepo(),
            local = local,
            cloud = cloud,
            budget = budget(),
            predictLength = 64,
            routePolicy = FixedRoutePolicy(RouteMode.CLOUD_FIRST),
        )
        m.open(null, "sys")
        val out = m.send("你好").toList()

        assertEquals(listOf("云端回答"), out)
        assertEquals(0, local.sends)
        assertEquals(RouteMode.CLOUD_FIRST, m.lastRoute)
    }

    @Test
    fun `cloud failure falls back to local and syncs local context`() = runBlocking {
        val local = FakeEngine(reply = "本地兜底")
        val cloud = FakeEngine(failWith = IllegalStateException("网络断了"))
        val m = ConversationManager(
            conversations = FakeRepo(),
            local = local,
            cloud = cloud,
            budget = budget(),
            predictLength = 64,
            routePolicy = FixedRoutePolicy(RouteMode.CLOUD_FIRST),
        )
        m.open(null, "sys")
        val out = m.send("第一个问题").toList()

        // 云端失败 → 端侧顶上
        assertEquals(listOf("本地兜底"), out)
        assertEquals(RouteMode.LOCAL_ONLY, m.lastRoute)
        assertEquals("网络断了", m.lastFallbackReason)
        // 回退前必须重建本地上下文，否则它可能是空的/过期的
        assertEquals(1, local.sends)
        assertFalse("回退时应重建本地上下文", local.empty())
        // 重建内容包含 system prompt 与历史（首个问题之前的历史为空）
        assertTrue(local.prefilled.first() == ("system" to "sys"))
    }

    @Test
    fun `cloud only does not silently fall back`() = runBlocking {
        val local = FakeEngine(reply = "本地回答")
        val cloud = FakeEngine(failWith = IllegalStateException("密钥无效"))
        val m = ConversationManager(
            conversations = FakeRepo(),
            local = local,
            cloud = cloud,
            budget = budget(),
            predictLength = 64,
            routePolicy = FixedRoutePolicy(RouteMode.CLOUD_ONLY),
        )
        m.open(null, "sys")

        val err = runCatching { m.send("你好").toList() }.exceptionOrNull()
        assertTrue("显式选云端时不应悄悄换成本地结果，实际：$err", err is IllegalStateException)
        assertEquals(0, local.sends)
    }

    @Test
    fun `switching route rebuilds the newly selected engine`() = runBlocking {
        val local = FakeEngine(reply = "本地回答")
        val cloud = FakeEngine(reply = "云端回答")
        val repo = FakeRepo()

        // 第一轮：走云端
        val m1 = ConversationManager(
            conversations = repo,
            local = local,
            cloud = cloud,
            budget = budget(),
            predictLength = 64,
            routePolicy = FixedRoutePolicy(RouteMode.CLOUD_FIRST),
        )
        repo.appendMessage(5, "user", "第一轮问题", 6)
        repo.appendMessage(5, "assistant", "第一轮回答", 6)
        m1.open(5L, "sys")
        m1.send("第二轮问题").toList()

        // 第二轮：用户切到纯端侧，必须重建本地上下文（本地还停在 open 时的空状态已被清）
        local.prefilled.clear()
        local.resets = 0
        val m2 = ConversationManager(
            conversations = repo,
            local = local,
            cloud = cloud,
            budget = budget(),
            predictLength = 64,
            routePolicy = FixedRoutePolicy(RouteMode.LOCAL_ONLY),
        )
        m2.open(5L, "sys")
        m2.send("第三轮问题").toList()

        assertTrue("切换到端侧后应重建本地上下文", local.resets >= 1)
        assertFalse(local.empty())
        // 重建内容应包含既有历史，而不是只有 system
        val contents = local.prefilled.map { it.second }
        assertTrue(contents.any { it.contains("第一轮问题") })
        assertTrue(contents.any { it.contains("第一轮回答") })
    }

    @Test
    fun `no engine available yields clear error`() = runBlocking {
        val m = ConversationManager(
            conversations = FakeRepo(),
            local = null,
            cloud = null,
            budget = budget(),
            predictLength = 64,
        )
        m.open(null, "sys")
        val err = runCatching { m.send("你好").toList() }.exceptionOrNull()
        assertTrue(err is IllegalStateException)
        assertTrue(err!!.message!!.contains("没有可用的推理引擎"))
    }
}
