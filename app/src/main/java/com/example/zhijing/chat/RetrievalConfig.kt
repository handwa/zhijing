package com.example.zhijing.chat

/**
 * RAG 检索策略。
 *
 * 参数取舍都围绕一件事：**注入的资料会挤占上下文窗口**。
 * 3B 模型窗口 8192，历史预算约 7800 token；每注入 100 字约 150 token，
 * 所以默认只取 3 段、总长 600 字，把代价控制在 900 token 以内。
 */
data class RetrievalConfig(
    /** 命中条数上限。 */
    val topK: Int = 3,
    /** 相似度阈值，低于此值视为不相关。 */
    val threshold: Float = 0.45f,
    /** 注入正文的总字数上限，超出按相关性截断。 */
    val maxChars: Int = 600,
    /** 是否在分段级别检索（命中更精准，能给出原文片段）。 */
    val includeChunks: Boolean = true,
) {
    init {
        require(topK > 0) { "topK must be positive" }
        require(threshold in 0f..1f) { "threshold must be in [0, 1]" }
        require(maxChars > 0) { "maxChars must be positive" }
    }

    companion object {
        /** 默认策略。 */
        val DEFAULT = RetrievalConfig()
    }
}
