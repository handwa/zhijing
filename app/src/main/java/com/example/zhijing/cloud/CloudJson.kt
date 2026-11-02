package com.example.zhijing.cloud

/**
 * OpenAI 兼容接口的 JSON 编解码。
 *
 * 请求体用字符串拼接 + 一处转义函数生成（字段固定、无嵌套），响应解析交给
 * [WireJson]。这样整个链路只依赖 JDK 字符操作，**不使用 `org.json`**：
 *
 * - 流式场景每帧都要解析，避免构造完整 DOM；
 * - `org.json` 是 Android 平台类，纯 JVM 单测里是空桩，无法验证；
 * - 对字段缺失、`null`、额外字段更宽容，换服务商时不会因 schema 差异崩溃。
 */
internal object CloudJson {

    /** 请求体。`stream=false` 时服务端返回单个完整 JSON 而不是 SSE。 */
    fun requestBody(
        model: String,
        messages: List<CloudMessage>,
        stream: Boolean,
        temperature: Double = 0.7,
        maxTokens: Int? = null,
    ): String = buildString {
        append('{')
        append("\"model\":").append(quote(model))
        append(",\"messages\":[")
        messages.forEachIndexed { i, m ->
            if (i > 0) append(',')
            append('{')
            append("\"role\":").append(quote(m.role))
            append(",\"content\":").append(quote(m.content))
            append('}')
        }
        append(']')
        append(",\"stream\":").append(stream)
        append(",\"temperature\":").append(temperature)
        if (maxTokens != null) append(",\"max_tokens\":").append(maxTokens)
        append('}')
    }

    /**
     * 从 SSE 的一个 `data:` 载荷里取出增量文本。
     *
     * 正常帧：`{"choices":[{"delta":{"content":"你"}}]}` → `"你"`
     * 结束帧：`{"choices":[{"finish_reason":"stop"}]}` → `""`
     * 兼容部分服务商的 `delta.reasoning_content`（推理模型）与 `choices` 为空。
     */
    fun deltaText(payload: String): String = runCatching { WireJson.deltaText(payload) }.getOrDefault("")

    /** 非流式响应的完整文本。 */
    fun fullText(payload: String): String = runCatching { WireJson.fullText(payload) }.getOrDefault("")

    /** 响应是否带有 `choices` 字段（没有则多半是错误帧）。 */
    fun hasChoices(payload: String): Boolean =
        runCatching { WireJson.hasChoices(payload) }.getOrDefault(false)

    /**
     * 把服务端错误体尽量翻译成人话。
     * OpenAI 系是 `{"error":{"message":"..."}}`，个别服务商直接返回字符串或 HTML。
     */
    fun errorMessage(body: String): String =
        runCatching { WireJson.errorMessage(body) }
            .getOrElse { body.trim().take(300).ifEmpty { "服务端未返回错误详情" } }

    /**
     * JSON 字符串转义。
     *
     * 中文等非 ASCII 字符**保持原样**（UTF-8 直接写），只有必须转义的字符
     * 才加反斜杠：双引号、反斜杠、控制字符。这样请求体更短，也避免
     * `\uXXXX` 在不同服务端的理解差异。
     */
    internal fun quote(raw: String): String = buildString(raw.length + 2) {
        append('"')
        for (c in raw) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c < ' ') {
                    append("\\u").append(String.format("%04x", c.code))
                } else {
                    append(c)
                }
            }
        }
        append('"')
    }
}
