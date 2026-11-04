package com.example.zhijing.chat

import android.util.Log
import com.arm.aichat.InferenceEngine
import com.zhijing.data.repository.ConversationEntitySnapshot
import com.zhijing.data.repository.ConversationRepository
import com.zhijing.data.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 会话编排：把「归档区（数据库）→ 工作记忆（内存）→ 引擎上下文」串起来。
 *
 * 职责边界：
 * - 归档：每条消息都落库，永不丢失
 * - 工作记忆：[working]，只保留当前上下文窗口放得下的部分
 * - 压缩：超预算时丢弃最老的轮次，用规则抽取成提要
 * - 重建：压缩后清空上下文，按 system → 提要 → 历史 重新灌入
 * - 路由：端侧 / 云端由 [RoutePolicy] 决定；两端共用同一套编排逻辑
 *
 * 端侧引擎为 [ChatEngine]（由 [LocalChatEngine] 适配 llama.cpp），云端引擎为
 * [com.example.zhijing.cloud.RemoteInferenceEngine]；两者行为差异（云端不需要
 * prefill、没有嵌入能力）都在各自实现里吸收，本类不做特判。
 */
class ConversationManager(
    private val conversations: ConversationRepository,
    /** 端侧引擎；null 表示未加载（此时只有云端可走）。 */
    private val local: ChatEngine?,
    /** 云端引擎；null 表示用户没有配置云服务。 */
    private val cloud: ChatEngine?,
    private val budget: ContextBudget,
    private val predictLength: Int,
    /** 本地下收藏检索数据源；为 null 时不带资料。 */
    private val contents: ContentRepository? = null,
    /** 检索策略；与 [contents] 同时提供才会启用。 */
    private val retrieval: RetrievalConfig? = null,
    /** 路由策略；默认纯端侧，云端需显式选择。 */
    private val routePolicy: RoutePolicy = FixedRoutePolicy(RoutePolicy.RouteMode.LOCAL_ONLY),
    /** 端侧记忆策略；null 时用 [budget] 构造默认的 [LocalMemoryStrategy]。 */
    private val localMemory: MemoryStrategy = LocalMemoryStrategy(budget),
    /** 云端记忆策略；null 时用 [budget] 构造默认的 [CloudMemoryStrategy]。 */
    private val cloudMemory: MemoryStrategy = CloudMemoryStrategy(budget),
) {
    private val compressor = ContextCompressor(budget)

    private var conversationId: Long = 0
    private var working: MutableList<Turn> = mutableListOf()
    private var digest: String = ""
    private var systemPrompt: String = ""

    /**
     * 生成当前 [digest] 的那条记忆路径（`local` / `cloud`）。
     *
     * 端侧与云端的提要粒度不同（端侧 400 字、云端 2000 字），**混用会让提要被反复重写**。
     * 记下来源，切到另一条路径时就知道该不该重压一次。
     * 空串表示还没有提要。
     */
    private var digestBy: String = ""

    /**
     * 上下文尚未按当前会话重建。
     *
     * 切换/新建会话只清空上下文并读好历史，真正的 prefill 推迟到下一次
     * [send]，这样切换是瞬时的，卡顿挪到用户本来就在等待的生成阶段。
     */
    private var pendingRebuild = false

    /**
     * 最近一次完成上下文重建的引擎。
     *
     * 上下文状态是**每个引擎各自持有**的（端侧是 KV cache，云端是 messages 列表）。
     * 路由在两次提问之间可能换引擎（用户切换模式、或云端失败回退），一旦换了，
     * 新引擎的上下文还是旧的，必须先重建再生成，否则会答非所问。
     */
    private var syncedEngine: ChatEngine? = null

    /** 本次会话是否发生过云端回退（用于提示用户"已自动切换端侧"）。 */
    var lastFallbackReason: String? = null
        private set

    /**
     * 最近一次回答实际走了哪条路，供 UI 展示「端侧/云端」标记。
     * null 表示还没有生成过。
     */
    var lastRoute: RoutePolicy.RouteMode? = null
        private set

    /**
     * 打开已有会话，或新建一个。
     * 从归档区恢复工作记忆，但**不**立刻重建上下文（懒加载）。
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
        // 恢复时不知道原提要由哪条路径生成，标为 unknown；
        // 之后若切到粒度为「粗」的那条（端侧），会按上限重新压一次。
        digestBy = if (digest.isEmpty()) "" else UNKNOWN_DIGEST_SOURCE

        // 只取提要边界之后的消息；更早的内容已经并入 digest
        val history = conversations.messagesAfter(convId, snapshot.digestUpToSeq)
        working = history.map {
            Turn(roleOf(it.role), it.content, it.tokenCount, it.seq)
        }.toMutableList()

        // 清掉上一个会话的上下文，避免串味；具体 prefill 推迟到 send。
        // 两端都清：云端历史也要跟着会话切换，否则会把别的会话内容发出去。
        local?.resetConversation()
        cloud?.resetConversation()
        syncedEngine = null
        lastRoute = null
        lastFallbackReason = null
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
     * 流程：选路 → 补齐上下文 → 写归档 → 按需压缩 → 生成 → 写归档
     *
     * 关键约束：上下文里只能出现一次用户消息。rebuildContext 灌的是 [working]
     * 的历史，而 sendUserPrompt 会自己把本次消息喂进引擎，所以本次消息
     * 绝不能同时存在于 [working] 的 prefill 内容和 sendUserPrompt 的入参里。
     *
     * 路由规则：
     * - 云端可用且策略选云端 → 先试云端；云端失败且端侧就绪 → 自动回退端侧
     * - 云端不可用或策略为纯端侧 → 走端侧（与离线版行为完全一致）
     */
    fun send(text: String): Flow<String> = flow {
        val userTokens = estimateTokens(text)

        // 选路。云端可用 = 已配置且当前策略允许；端侧可用 = 引擎就绪。
        val hasCloud = cloud != null && routePolicy.route(hasCloud = true) != RoutePolicy.RouteMode.LOCAL_ONLY
        val mode = if (cloud == null) RoutePolicy.RouteMode.LOCAL_ONLY else routePolicy.route(hasCloud = true)
        val primary: ChatEngine? = when (mode) {
            RoutePolicy.RouteMode.LOCAL_ONLY -> local
            RoutePolicy.RouteMode.CLOUD_FIRST, RoutePolicy.RouteMode.CLOUD_ONLY -> cloud
        }
        val active = primary ?: local ?: cloud
            ?: error("没有可用的推理引擎：端侧未加载，云端未配置")

        lastFallbackReason = null

        // 懒加载：把切换会话时跳过的 prefill 补上（此时 working 还不含本次消息）。
        // 另外：如果这次要用的引擎和上次重建过的不是同一个（路由切换导致），
        // 它的上下文同样是过期的，必须一并重建。
        val engineChanged = syncedEngine != null && syncedEngine !== active
        if (pendingRebuild || engineChanged) {
            val t0 = System.currentTimeMillis()
            rebuildContext(active)
            Log.i(
                "ConversationManager",
                "rebuild on ${engineName(active)} (pending=$pendingRebuild, switched=$engineChanged): " +
                    "${working.size} turns, digest=${digest.length} chars, " +
                    "took ${System.currentTimeMillis() - t0} ms",
            )
            pendingRebuild = false
        }
        // 先落库（拿回真实 seq），再动内存；即使后面出错，用户消息也不会丢
        val userSeq = conversations.appendMessage(conversationId, "user", text, userTokens)
        maybeRenameAfterFirstMessage(text)

        // 压缩基于"加入本条之前的窗口"判断，避免把本条灌两遍；
        // 需要重建时，重建内容同样不含本条，本条由 sendUserPrompt 补上。
        val compressed = compressIfNeeded(active)
        if (compressed) {
            Log.i("ConversationManager", "compressed: kept ${working.size} turns")
        }
        working.add(Turn(Role.USER, text, userTokens, userSeq))

        // RAG：把本地收藏里相关的片段拼进本次提问。
        // 只影响这一次 sendUserPrompt 的入参，不写进 working，也不落库，
        // 因此不会污染对话历史，也不会在重建上下文时重复注入。
        val prompt = buildAugmentedPrompt(text)

        val buffer = StringBuilder()
        var running = active
        try {
            try {
                running.sendUserPrompt(prompt, predictLength).collect { token ->
                    buffer.append(token)
                    emit(token)
                }
                lastRoute = if (running === cloud) RoutePolicy.RouteMode.CLOUD_FIRST else RoutePolicy.RouteMode.LOCAL_ONLY
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 云端失败 → 端侧兜底。只有 CLOUD_FIRST 才回退：用户显式选择
                // CLOUD_ONLY 时应当看到真实错误，而不是被悄悄换成本地结果。
                val canFallback = running === cloud &&
                    mode == RoutePolicy.RouteMode.CLOUD_FIRST &&
                    local != null && local.isReady && buffer.isEmpty()
                if (!canFallback) throw e

                Log.w("ConversationManager", "cloud failed, falling back to local: ${e.message}")
                lastFallbackReason = e.message
                lastRoute = RoutePolicy.RouteMode.LOCAL_ONLY
                running = local

                // 回退前必须让本地上下文追上：若之前几次都走了云端，本地引擎
                // 还停在旧状态，直接生成会答非所问。重建用当前 working（已含本轮提问
                // 之外的完整历史），prompt 由 sendUserPrompt 单独补上。
                rebuildContext(local)
                local.sendUserPrompt(prompt, predictLength).collect { token ->
                    buffer.append(token)
                    emit(token)
                }
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

    private fun engineName(engine: ChatEngine): String =
        if (engine === cloud) "cloud" else "local"

    companion object {
        /**
         * 从数据库恢复的提要，来源未知。
         *
         * 存在的原因是：提要与生成它的策略绑定（端侧 400 字 / 云端 2000 字）。
         * 恢复时无法从库里区分，只能标为 unknown，由后续压缩按当前策略校正。
         */
        private const val UNKNOWN_DIGEST_SOURCE = "unknown"
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

        // 检索**始终**在端侧完成：向量化必须用本地嵌入模型，即使本次回答走云端。
        // 这样切到云端时 RAG 依然可用，同时也意味着检索结果只在本机产生。
        val embeddingEngine = local ?: cloud ?: return query
        if (embeddingEngine.embeddingDim() == 0) return query

        return try {
            val queryVector = embeddingEngine.embed(query) ?: run {
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
     * 记忆策略按 [engine] 选择：端侧走 [localMemory]（紧预算、短提要），
     * 云端走 [cloudMemory]（宽预算、结构化摘要）。摘要失败时**自动降级**到
     * 规则抽取（[ContextCompressor.extractFacts]），保证记忆不丢。
     *
     * @return true 表示确实发生了压缩与重建
     */
    private suspend fun compressIfNeeded(engine: ChatEngine): Boolean {
        val memory = memoryFor(engine)

        if (working.sumOf { it.tokens } <= memory.historyBudget.history) {
            // 没有新轮次被挤出，但既有提要可能已经超限（上一轮用宽策略生成的）
            compactOversizedDigest(memory, engine)
            return false
        }

        val kept = compressor.trim(working)
        if (kept.size == working.size) return false

        val droppedCount = working.size - kept.size
        val dropped = working.take(droppedCount)

        // 先试模型摘要；失败/超时退回规则抽取 —— 质量可以降，记忆不能丢
        val facts = summarizeFor(memory, dropped, engine)
            ?: compressor.extractFacts(dropped).takeIf { it.isNotEmpty() }

        val merged = when {
            facts.isNullOrEmpty() -> digest
            digest.isEmpty() -> facts
            else -> "$digest；$facts"
        }
        digest = compactIfNeeded(memory, merged, engine)

        working = kept.toMutableList()

        // 边界 = 保留的第一条之前那条的 seq；为空说明全部并入提要
        val boundary = kept.firstOrNull()?.seq?.minus(1) ?: dropped.lastOrNull()?.seq ?: 0
        conversations.updateDigest(conversationId, digest, boundary)

        rebuildContext(engine)
        return true
    }

    /** 按引擎选记忆策略：云端用云端策略，其余一律端侧策略。 */
    private fun memoryFor(engine: ChatEngine): MemoryStrategy =
        if (engine === cloud) cloudMemory else localMemory

    /**
     * 生成提要，失败返回 null（调用方据此降级）。
     *
     * 记下提要来源 [digestBy]，供切换模式时判断是否需要重压。
     */
    private suspend fun summarizeFor(
        memory: MemoryStrategy,
        dropped: List<Turn>,
        engine: ChatEngine
    ): String? {
        val summary = memory.summarize(dropped, engine)
        if (summary == null) {
            Log.w("ConversationManager", "记忆摘要失败（${memory.name}），降级为规则抽取")
            return null
        }
        digestBy = memory.name
        return summary
    }

    /**
     * 提要超长时压缩。**这是防「digest 无限增长」的关键**：
     * 旧实现只会 `digest + "；" + facts`，压几次就又把窗口挤爆了。
     *
     * @param merged 待写入的完整提要
     * @return 压缩后的提要；压缩失败则截断保留末尾（新信息更可能有用）
     */
    private suspend fun compactIfNeeded(
        memory: MemoryStrategy,
        merged: String,
        engine: ChatEngine
    ): String {
        if (merged.length <= memory.digestMaxChars) return merged
        val compacted = memory.compactDigest(existing = "", incoming = merged, engine = engine)
        if (!compacted.isNullOrEmpty()) {
            digestBy = memory.name
            return compacted.take(memory.digestMaxChars)
        }
        Log.w("ConversationManager", "提要压缩失败（${memory.name}），截断保留末尾")
        return merged.takeLast(memory.digestMaxChars)
    }

    /** 既有提要超出当前策略上限时（例如从云端切回端侧）压一次。 */
    private suspend fun compactOversizedDigest(memory: MemoryStrategy, engine: ChatEngine) {
        if (digest.length <= memory.digestMaxChars) return
        digest = compactIfNeeded(memory, digest, engine)
        // 边界不变：这里只改提要文本，不改变已压缩范围
        val boundary = working.firstOrNull()?.seq?.minus(1) ?: 0
        conversations.updateDigest(conversationId, digest, boundary)
    }

    /**
     * 清空引擎上下文，按 system → 提要 → 历史 的顺序重新灌入。
     *
     * 调用 prefillMessage 而不是 sendUserPrompt，因为这些历史消息不需要重新生成。
     * 对云端引擎而言 prefill 只是累积 messages，不发网络请求（见
     * [com.example.zhijing.cloud.RemoteInferenceEngine]），因此两端共用这段逻辑。
     */
    private suspend fun rebuildContext(engine: ChatEngine) {
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
        syncedEngine = engine
    }

    private fun roleOf(role: String): Role = when (role) {
        "system" -> Role.SYSTEM
        "assistant" -> Role.ASSISTANT
        else -> Role.USER
    }
}
