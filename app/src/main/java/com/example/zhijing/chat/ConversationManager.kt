package com.example.zhijing.chat

import android.util.Log
import com.arm.aichat.InferenceEngine
import com.zhijing.data.repository.ConversationEntitySnapshot
import com.zhijing.data.repository.ConversationRepository
import com.zhijing.data.repository.ContentRepository
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
    /** 本地收藏检索数据源；为 null 时不带资料。 */
    private val contents: ContentRepository? = null,
    /** 检索策略；与 [contents] 同时提供才会启用。 */
    private val retrieval: RetrievalConfig? = null,
) {
    private val compressor = ContextCompressor(budget)

    private var conversationId: Long = 0
    private var working: MutableList<Turn> = mutableListOf()
    private var digest: String = ""
    private var systemPrompt: String = ""

    /**
     * 上下文尚未按当前会话重建。
     *
     * 切换/新建会话只清空 KV cache 并读好历史，真正的 prefill 推迟到下一次
     * [send]，这样切换是瞬时的，卡顿挪到用户本来就在等待的生成阶段。
     */
    private var pendingRebuild = false

    /**
     * 打开已有会话，或新建一个。
     * 从归档区恢复工作记忆，但**不**立刻重建 KV cache（懒加载）。
     *
     * 切换是瞬时的；上下文在下次 [send] 时按需重建。
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

        // 清掉上一个会话的上下文，避免串味；具体的 prefill 推迟到 send
        engine.resetConversation()
        pendingRebuild = true
        return convId
    }

    /**
     * 新建一个会话并切换过去，返回新会话 ID。
     *
     * 会丢弃当前工作记忆，并让 KV cache 只保留新会话的 system prompt。
     * 调用前应先确保没有正在进行的生成（见 InferenceEngine.cancelGenerationAndAwaitReady）。
     */
    suspend fun newConversation(title: String = "新对话"): Long {
        val id = conversations.createConversation(title, systemPrompt)
        open(id, systemPrompt)
        return id
    }

    /** 当前会话 ID；0 表示尚未打开任何会话。 */
    fun currentId(): Long = conversationId

    /** 会话列表（按最近活跃排序），供 UI 展示。 */
    fun observeConversations(): Flow<List<ConversationEntitySnapshot>> =
        conversations.observeConversations()

    /**
     * 当前会话在上下文窗口内的消息，供 UI 回填。
     * 不含 system prompt，也不含已被压缩进 digest 的更早消息。
     */
    fun currentMessages(): List<Turn> = working.toList()

    /** 用第一条用户消息给会话起名，方便在列表里辨认。 */
    private suspend fun maybeRenameAfterFirstMessage(text: String) {
        if (working.count { it.role == Role.USER } != 1) return
        val title = text.trim().take(16)
        if (title.isNotEmpty()) {
            conversations.renameConversation(conversationId, title)
        }
    }

    /**
     * 发送一条用户消息，流式返回助手回复。
     *
     * 流程：补齐上下文 → 写归档 → 按需压缩 → 生成 → 写归档
     *
     * 关键约束：上下文里只能出现一次用户消息。rebuildContext 灌的是 [working]
     * 的历史，而 sendUserPrompt 会自己把本次消息喂进 KV cache，所以本次消息
     * 绝不能同时存在于 [working] 的 prefill 内容和 sendUserPrompt 的入参里。
     */
    fun send(text: String): Flow<String> = flow {
        val userTokens = estimateTokens(text)

        // 懒加载：把切换会话时跳过的 prefill 补上（此时 working 还不含本次消息）
        if (pendingRebuild) {
            val t0 = System.currentTimeMillis()
            rebuildContext()
            Log.i(
                "ConversationManager",
                "lazy rebuild: ${working.size} turns, digest=${digest.length} chars, " +
                    "took ${System.currentTimeMillis() - t0} ms",
            )
            pendingRebuild = false
        }

        // 先落库（拿回真实 seq），再动内存；即使后面出错，用户消息也不会丢
        val userSeq = conversations.appendMessage(conversationId, "user", text, userTokens)
        maybeRenameAfterFirstMessage(text)

        // 压缩基于"加入本条之前的窗口"判断，避免把本条灌两遍；
        // 需要重建时，重建内容同样不含本条，本条由 sendUserPrompt 补上。
        val compressed = compressIfNeeded()
        if (compressed) {
            Log.i("ConversationManager", "compressed: kept ${working.size} turns")
        }
        working.add(Turn(Role.USER, text, userTokens, userSeq))

        // RAG：把本地收藏里相关的片段拼进本次提问。
        // 只影响这一次 sendUserPrompt 的入参，不写进 working，也不落库，
        // 因此不会污染对话历史，也不会在重建上下文时重复注入。
        val prompt = buildAugmentedPrompt(text)

        val buffer = StringBuilder()
        try {
            engine.sendUserPrompt(prompt, predictLength).collect { token ->
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
     * 检索本地收藏并把命中片段拼成带资料的提问文本。
     *
     * 没有配置检索、检索失败或没有命中时，原样返回 [query]，
     * 保证 RAG 只是增强手段，出问题也不会让对话不可用。
     */
    private suspend fun buildAugmentedPrompt(query: String): String {
        val store = contents ?: return query
        val config = retrieval ?: return query

        // 依赖注入的检索器缺失或嵌入模型未加载时静默降级
        if (engine.embeddingDim() == 0) return query

        return try {
            val queryVector = engine.embed(query) ?: run {
                Log.w("ConversationManager", "RAG: embed(query) returned null, skip")
                return query
            }
            val hits = store.searchSimilar(
                queryVector = queryVector,
                topK = config.topK,
                threshold = config.threshold,
                includeChunks = config.includeChunks,
            )
            Log.i(
                "ConversationManager",
                "RAG: query='${query.take(20)}' hits=${hits.size} " +
                    "threshold=${config.threshold} " +
                    "scores=${hits.joinToString { "%.3f".format(it.score) }}",
            )
            if (hits.isEmpty()) return query

            val snippets = StringBuilder()
            var used = 0
            for (hit in hits) {
                val body = (hit.matchedText ?: hit.rawText).trim()
                if (body.isEmpty()) continue
                val remaining = config.maxChars - used
                if (remaining <= 0) break
                val piece = if (body.length <= remaining) body else body.take(remaining)
                if (snippets.isNotEmpty()) snippets.append('\n')
                snippets.append("- ").append(piece)
                used += piece.length
            }
            if (snippets.isEmpty()) return query

            Log.i(
                "ConversationManager",
                "RAG: ${hits.size} hits, injected ${used} chars " +
                    "(top score=${"%.3f".format(hits.first().score)})",
            )

            buildString {
                append("以下是用户本地收藏中与本问题相关的资料，请优先依据它们作答")
                append("（如果资料不足以回答，就直接说明不知道，不要编造）：\n")
                append(snippets)
                append("\n\n问题：")
                append(query)
            }
        } catch (e: Exception) {
            Log.w("ConversationManager", "RAG retrieval failed, falling back to plain prompt", e)
            query
        }
    }

    /**
     * 工作记忆超出预算时，丢弃最老的轮次并重建上下文。
     *
     * 调用前 [working] 不应包含本轮刚发的用户消息，否则它会被
     * [rebuildContext] 和后续的 sendUserPrompt 各灌一次。
     *
     * @return true 表示确实发生了压缩与重建
     */
    private suspend fun compressIfNeeded(): Boolean {
        if (working.sumOf { it.tokens } <= budget.history) return false

        val kept = compressor.trim(working)
        if (kept.size == working.size) return false

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
        return true
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
