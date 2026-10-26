package com.example.zhijing.chat

enum class Role { SYSTEM, USER, ASSISTANT }

/**
 * 工作记忆中的一条消息。
 *
 * [seq] 对应 message_table 里的序号，0 表示尚未落库（刚加入内存的）。
 * 有了它，压缩时能精确算出提要边界，不用靠内容比对。
 */
data class Turn(
    val role: Role,
    val content: String,
    val tokens: Int,
    val seq: Int = 0,
)

/**
 * 粗略估算 token 数。
 * 中文按 1 字约 1.5 token，英文按 4 字符约 1 token，另加模板开销。
 * 只用于上下文预算，不要求精确。
 */
fun estimateTokens(text: String): Int {
    if (text.isEmpty()) return 0
    val cjk = text.count { it.code in 0x4E00..0x9FFF }
    val other = text.length - cjk
    return (cjk * 1.5).toInt() + (other / 4) + 4
}
