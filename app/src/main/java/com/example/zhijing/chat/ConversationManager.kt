package com.example.zhijing.chat

import com.arm.aichat.InferenceEngine
import com.zhijing.data.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 会话编排：把「归档区（数据库）→ 工作记忆（内存）→ KV cache（引擎）」串起来。
 *
 * 职责边界：
 * - 归档：每条消息都落库，永不丢失
 * - 工作记忆：[working]，只保留当前上下文窗口放得下的部分
 * - 压缩：超预算时丢弃最老的轮次，用规则抽取成提要
 * - 重建：压缩后清空 KV cache，按 system → 提要 → 历史 重新灌入
 */
class ConversationManager(
    private val conversations: ConversationRepository,
    private val engine: InferenceEngine,
    private val budget: ContextBudget,
    private val predictLength: Int,
) {
    private val compressor = ContextCompressor(budget)

    private var conversationId: Long = 0
    private var working: MutableList<Turn> = mutableListOf()
    private var digest: String = ""
    private var systemPrompt: String = ""

    /**
     * 打开已有会话，或新建一个。
     * 从归档区恢复工作记忆并重建 KV cache。
     *
     * @param existingId 已有会话 ID；传 null 表示新建
     * @return 实际使用的会话 ID
     */
    suspend fun open(existingId: Long?, prompt: String): Long {
        systemPrompt = prompt

        val convId = existingId ?: conversations.createConversation("新对话", prompt)
        conversationId = convId

        val snapshot = conversations.findConversation(convId)
            ?: error("Conversation $convId not found")

        digest = snapshot.digest ?: ""

        // 只取提要边界之后的消息；更早的内容已经并入 digest
        val history = conversations.messagesAfter(convId, snapshot.digestUpToSeq)
        working = history.map {
            Turn(roleOf(it.role), it.content, it.tokenCount, it.seq)
        }.toMutableList()

        rebuildContext()
        return convId
    }

    /**
     * 发送一条用户消息，流式返回助手回复。
     *
     * 流程：写归档 → 必要时压缩 → 生成 → 写归档
     */
    fun send(text: String): Flow<String> = flow {
        val userTokens = estimateTokens(text)
        // 先落库（拿回真实 seq），再动内存；即使后面出错，用户消息也不会丢
        val userSeq = conversations.appendMessage(conversationId, "user", text, userTokens)
        working.add(Turn(Role.USER, text, userTokens, userSeq))

        compressIfNeeded()

        val buffer = StringBuilder()
        try {
            engine.sendUserPrompt(text, predictLength).collect { token ->
                buffer.append(token)
                emit(token)
            }
        } finally {
            val answer = buffer.toString()
            if (answer.isNotEmpty()) {
                val answerTokens = estimateTokens(answer)
                val answerSeq = conversations.appendMessage(conversationId, "assistant", answer, answerTokens)
                working.add(Turn(Role.ASSISTANT, answer, answerTokens, answerSeq))
            }
        }
    }

    /**
     * 工作记忆超出预算时，丢弃最老的轮次并重建上下文。
     */
    private suspend fun compressIfNeeded() {
        if (working.sumOf { it.tokens } <= budget.history) return

        val kept = compressor.trim(working)
        if (kept.size == working.size) return

        val droppedCount = working.size - kept.size
        val dropped = working.take(droppedCount)

        val facts = compressor.extractFacts(dropped)
        if (facts.isNotEmpty()) {
            digest = if (digest.isEmpty()) facts else "$digest；$facts"
        }

        working = kept.toMutableList()

        // 边界 = 保留的第一条之前那条的 seq；为空说明全部并入提要
        val boundary = kept.firstOrNull()?.seq?.minus(1) ?: dropped.lastOrNull()?.seq ?: 0
        conversations.updateDigest(conversationId, digest, boundary)

        rebuildContext()
    }

    /**
     * 清空 KV cache，按 system → 提要 → 历史 的顺序重新灌入。
     *
     * 调用 prefillMessage 而不是 sendUserPrompt，因为这些历史消息不需要重新生成。
     */
    private suspend fun rebuildContext() {
        engine.resetConversation()
        engine.prefillMessage("system", systemPrompt)
        if (digest.isNotEmpty()) {
            engine.prefillMessage("system", "【此前对话的提要】$digest")
        }
        for (turn in working) {
            engine.prefillMessage(
                when (turn.role) {
                    Role.SYSTEM -> "system"
                    Role.USER -> "user"
                    Role.ASSISTANT -> "assistant"
                },
                turn.content,
            )
        }
    }

    private fun roleOf(role: String): Role = when (role) {
        "system" -> Role.SYSTEM
        "assistant" -> Role.ASSISTANT
        else -> Role.USER
    }
}
