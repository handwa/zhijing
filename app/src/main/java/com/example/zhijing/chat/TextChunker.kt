package com.example.zhijing.chat

import com.zhijing.data.model.ChunkPayload

/**
 * 把长文切成适合嵌入的分段。
 *
 * 嵌入模型 bge-small-zh 的训练上下文是 512 token，中文约 1 字 1.5 token，
 * 所以单个分段控制在 300 字左右，留足余量避免截断丢信息。
 *
 * 分段策略：
 * 1. 优先在段落边界（换行）切，其次句号/问号/感叹号，最后才硬切；
 * 2. 相邻分段保留 [overlap] 字重叠，避免关键信息正好落在切口上。
 */
object TextChunker {

    /** 单段目标长度（字符）。 */
    const val DEFAULT_SIZE = 300

    /** 相邻分段重叠（字符）。 */
    const val DEFAULT_OVERLAP = 50

    /** 段落分隔符，按优先级尝试。 */
    private val BREAKS = listOf("\n\n", "\n", "。", "！", "？", "；", ". ", "! ", "? ", "; ")

    /**
     * 切分 [text]，返回带原文偏移的分段。
     *
     * @throws IllegalArgumentException 当 [size] <= 0 或 [overlap] >= [size]
     */
    fun split(
        text: String,
        size: Int = DEFAULT_SIZE,
        overlap: Int = DEFAULT_OVERLAP,
    ): List<ChunkPayload> {
        require(size > 0) { "size must be positive" }
        require(overlap >= 0 && overlap < size) { "overlap must be in [0, size)" }

        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= size) {
            return listOf(
                ChunkPayload(
                    text = trimmed,
                    startOffset = text.indexOf(trimmed),
                    endOffset = text.indexOf(trimmed) + trimmed.length,
                )
            )
        }

        val chunks = mutableListOf<ChunkPayload>()
        var cursor = 0

        while (cursor < trimmed.length) {
            var end = minOf(cursor + size, trimmed.length)

            if (end < trimmed.length) {
                end = findBreak(trimmed, cursor, end)
            }

            val piece = trimmed.substring(cursor, end).trim()
            if (piece.isNotEmpty()) {
                val offset = trimmed.indexOf(piece, cursor)
                chunks += ChunkPayload(
                    text = piece,
                    startOffset = offset,
                    endOffset = offset + piece.length,
                )
            }

            if (end >= trimmed.length) break

            // 前进：正常一整段，或回退 overlap；保证至少前进 1 个字符
            val next = end - overlap
            cursor = if (next > cursor) next else end
        }

        return chunks
    }

    /**
     * 在 [from, to) 内找最后一个自然断点，找不到就返回 [to]（硬切）。
     */
    private fun findBreak(text: String, from: Int, to: Int): Int {
        // 只在后半段找断点，避免切出过短的段
        val searchFrom = from + (to - from) / 2
        var best = to
        for (marker in BREAKS) {
            val idx = text.lastIndexOf(marker, to - 1)
            if (idx >= searchFrom) {
                // 断点包含分隔符本身，切在它之后
                val candidate = idx + marker.length
                if (candidate in searchFrom..to && candidate < best) best = candidate
            }
        }
        return best
    }
}
