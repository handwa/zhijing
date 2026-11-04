package com.example.zhijing.chat

import kotlinx.coroutines.flow.Flow

/**
 * 记忆策略：决定「工作记忆怎么压缩、提要怎么生成」。
 *
 * ## 为什么需要这层
 *
 * [ConversationManager] 的编排逻辑（选路、重建、RAG、落库）两端共用，**不该分叉**。
 * 但记忆的约束两端差异很大：
 *
 * | | 端侧 | 云端 |
 * |---|---|---|
 * | 上下文窗口 | 1–4K token，很紧 | 8K 以上，宽松 |
 * | 压缩时机 | 阈值低，频繁压缩 | 阈值高，少压缩 |
 * | 提要成本 | 本地推理慢，且占显存 | 按 token 计费，但质量高 |
 * | 提要长度 | 必须很短，否则又挤爆窗口 | 可以到几百字 |
 *
 * 所以分的是**策略**，不是复制 300 行编排代码。
 *
 * ## 降级约定
 *
 * [summarize] 允许失败：端侧模型可能超时、可能吐空。失败时调用方应回退到
 * [ContextCompressor.extractFacts] 的规则抽取 —— **摘要质量可以降，但记忆不能丢。
 */
interface MemoryStrategy {

    /** 给 UI / 日志用的名字，如 `local` / `cloud`。 */
    val name: String

    /** 保留历史时允许占用的 token 上限。 */
    val historyBudget: ContextBudget

    /**
     * 提要的字符上限。
     *
     * 超过就必须再压一层，否则多次压缩后 digest 会重新撑爆窗口 ——
     * 这是旧实现（`digest = digest + "；" + facts`）最大的隐患。
     */
    val digestMaxChars: Int

    /**
     * 把被挤出窗口的轮次压成提要。
     *
     * 实现**必须**在失败时返回 null 让调用方降级，而不是抛异常：
     * 用户正在等回答，不该因为摘要失败就整轮失败。
     *
     * @param dropped 被丢弃的轮次（按时间顺序）
     * @param engine 当前可用的推理引擎；端侧策略应只用本地引擎
     * @return 提要文本；失败返回 null
     */
    suspend fun summarize(dropped: List<Turn>, engine: ChatEngine): String?

    /** 把多条提要合并压短（分层摘要：digest 超限时调用）。 */
    suspend fun compactDigest(existing: String, incoming: String, engine: ChatEngine): String?
}

/**
 * 端侧记忆策略：紧预算、短提要、本地小模型摘要。
 *
 * 端侧的现实约束：
 * - 0.5B–3B 模型的摘要能力弱，**不能要求它做复杂压缩**，prompt 要极简；
 * - 本地推理慢，摘要会明显拖慢首次响应，因此限定输出长度；
 * - 显存紧张，提要必须短，否则压缩后仍然超窗口。
 */
class LocalMemoryStrategy(
    override val historyBudget: ContextBudget,
    /** 摘要输出上限（token）。本地小模型给得越少越快。 */
    private val summarizeTokens: Int = 96
) : MemoryStrategy {

    override val name: String = "local"

    /**
     * 端侧提要上限 400 字。
     *
     * 按 [estimateTokens] 约 1.5 token/字估算，400 字 ≈ 600 token；
     * 在 2K 窗口下这是能接受的上限，再大就挤占历史空间了。
     */
    override val digestMaxChars: Int = 400

    override suspend fun summarize(dropped: List<Turn>, engine: ChatEngine): String? {
        if (dropped.isEmpty()) return null
        val transcript = dropped.joinToString("\n") { turn ->
            "${roleLabel(turn.role)}：${turn.content.take(MAX_TURN_CHARS)}"
        }
        // prompt 刻意极简：小模型对复杂指令遵循度差，句子越短越稳
        val prompt = buildString {
            append("把下面的对话压缩成不超过 80 字的要点，只保留：用户的目标、已确定的结论、未解决的问题。\n")
            append(transcript)
        }
        return runCatching {
            engine.sendUserPrompt(prompt, summarizeTokens).collectToString()
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    override suspend fun compactDigest(existing: String, incoming: String, engine: ChatEngine): String? {
        // incoming 为空表示「只压缩既有提要」，不应凭空多出分隔符
        val merged = when {
            incoming.isEmpty() -> existing
            existing.isEmpty() -> incoming
            else -> "$existing；$incoming"
        }
        if (merged.length <= digestMaxChars) return merged
        val prompt = "把下面的要点合并成不超过 80 字的摘要：\n$merged"
        return runCatching {
            engine.sendUserPrompt(prompt, summarizeTokens).collectToString()
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        /** 每轮喂给摘要模型的字符上限，避免端侧输入过长拖慢推理。 */
        const val MAX_TURN_CHARS = 200
    }
}

/**
 * 云端记忆策略：宽预算、长提要、大模型结构化摘要。
 *
 * 云端可以做得更细：摘要里要求保留「用户偏好、提及的事实、待办、未决问题」四类，
 * 这样跨轮次的连续性明显好于端侧的极简摘要。
 */
class CloudMemoryStrategy(
    override val historyBudget: ContextBudget,
    private val summarizeTokens: Int = 320
) : MemoryStrategy {

    override val name: String = "cloud"

    /**
     * 云端提要上限 2000 字。
     *
     * 云端窗口通常 ≥8K，2000 字 ≈ 3000 token 仍在安全范围内，
     * 且摘要质量更高 —— 更好的记忆本来就是为了少丢信息。
     */
    override val digestMaxChars: Int = 2000

    override suspend fun summarize(dropped: List<Turn>, engine: ChatEngine): String? {
        if (dropped.isEmpty()) return null
        val transcript = dropped.joinToString("\n") { turn ->
            "${roleLabel(turn.role)}：${turn.content}"
        }
        val prompt = buildString {
            append("把下面这段对话压缩成结构化要点（不超过 200 字），分四类：\n")
            append("1. 用户的目标与偏好  2. 已确认的事实与结论  ")
            append("3. 待办或未完成的事  4. 尚未解决的问题\n")
            append("不要复述原文，不要加入对话里没有的信息。\n\n")
            append(transcript)
        }
        return runCatching {
            engine.sendUserPrompt(prompt, summarizeTokens).collectToString()
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    override suspend fun compactDigest(existing: String, incoming: String, engine: ChatEngine): String? {
        // incoming 为空表示「只压缩既有提要」，不应凭空多出分隔符
        val merged = when {
            incoming.isEmpty() -> existing
            existing.isEmpty() -> incoming
            else -> "$existing\n$incoming"
        }
        if (merged.length <= digestMaxChars) return merged
        val prompt = "把下面的要点合并成不超过 200 字的结构化摘要，保留目标、事实、待办、未决问题：\n$merged"
        return runCatching {
            engine.sendUserPrompt(prompt, summarizeTokens).collectToString()
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/** 把流式输出收集成完整字符串。 */
private suspend fun Flow<String>.collectToString(): String {
    val sb = StringBuilder()
    collect { sb.append(it) }
    return sb.toString()
}

private fun roleLabel(role: Role): String = when (role) {
    Role.SYSTEM -> "系统"
    Role.USER -> "用户"
    Role.ASSISTANT -> "助手"
}
