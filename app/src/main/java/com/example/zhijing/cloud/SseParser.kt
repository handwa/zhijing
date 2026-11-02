package com.example.zhijing.cloud

/**
 * 极简 SSE（Server-Sent Events）帧解析器。
 *
 * 为什么不用第三方库：只需要"从字节流里切出一个个事件"，逻辑不到 100 行，
 * 引一个网络库反而增加 APK 体积和依赖面。这里只实现 OpenAI 兼容接口实际
 * 会用到的子集：
 *
 * ```
 * event: foo        ← 可选，本类忽略（OpenAI 不用）
 * data: {...}       ← 可以多行，按 \n 拼接
 * data: {...}
 *                   ← 空行 = 一个事件结束
 * ```
 *
 * 设计要点：
 * 1. **按字符增量解析**，不依赖调用方按行喂入。网关/代理把多帧合并进一个
 *    TCP 包是常见情况，按行喂会丢帧。
 * 2. **行尾兼容** `\r\n`、`\n`、`\r`：紧跟 `\r` 的 `\n` 视为同一个换行。
 * 3. **多行 data 用 `\n` 拼接**，符合 SSE 规范。
 * 4. 流结束若还有未以空行收尾的 data，由 [flush] 补发（部分服务端不补空行）。
 */
class SseParser {

    private val dataBuffer = StringBuilder()
    private val lineBuffer = StringBuilder()

    /** 上一个字符是 '\r'，用于把 "\r\n" 当成一个换行而不是两个。 */
    private var pendingLineFeed = false

    /** 喂入一段文本，返回解析出的完整事件（可能为空）。 */
    fun feed(chunk: String): List<String> {
        val events = ArrayList<String>()
        for (ch in chunk) {
            when (ch) {
                '\r' -> {
                    emitLine(events)
                    pendingLineFeed = true
                }

                '\n' -> {
                    if (pendingLineFeed) pendingLineFeed = false else emitLine(events)
                }

                else -> {
                    pendingLineFeed = false
                    lineBuffer.append(ch)
                }
            }
        }
        return events
    }

    /** 流结束时调用：把没有空行收尾的最后一个事件补发出去。 */
    fun flush(): List<String> {
        val events = ArrayList<String>()
        if (lineBuffer.isNotEmpty()) emitLine(events)
        flushEvent(events)
        return events
    }

    private fun emitLine(events: MutableList<String>) {
        val line = lineBuffer.toString()
        lineBuffer.setLength(0)

        when {
            line.isEmpty() -> flushEvent(events)
            line.startsWith(":") -> Unit                       // SSE 注释/心跳
            line.startsWith("data:") -> {
                // 规范要求剥掉冒号后的一个空格，"data:text" 也合法
                var value = line.substring(5)
                if (value.startsWith(" ")) value = value.substring(1)
                if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                dataBuffer.append(value)
            }
            // event: / id: / retry: 等字段本场景不用，直接丢弃
            else -> Unit
        }
    }

    private fun flushEvent(events: MutableList<String>) {
        if (dataBuffer.isEmpty()) return
        val payload = dataBuffer.toString()
        dataBuffer.setLength(0)
        if (payload.isNotEmpty()) events.add(payload)
    }
}
