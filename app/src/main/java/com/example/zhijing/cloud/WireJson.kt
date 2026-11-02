package com.example.zhijing.cloud

/**
 * 从一段 LLM 响应 JSON 里取出需要的内容。
 *
 * **为什么不直接用 `org.json` 解析整棵树**：
 * 1. 我们只关心两个位置（`choices[0].delta.content` 与 `choices[0].message.content`），
 *    为它们引入一棵完整 DOM 解析不划算——流式场景下每帧都要解析，开销敏感。
 * 2. `org.json` 是 Android 平台类，纯 JVM 单测里是空桩，无法验证。这里的实现
 *    只依赖 JDK 自带的字符操作，因此能被完整测试覆盖。
 * 3. 各服务商字段有差异（有无 `delta`、`choices` 为空、增量放在 `message` 里），
 *    手写解析对缺失字段更宽容，不会因为 schema 漂移就崩掉整段回答。
 *
 * 限制：不是通用 JSON 解析器。字符串值内部的 `\uXXXX` 转义会被正确还原，
 * 但对象/数组层面的嵌套只沿固定路径查找，这正是本场景需要的。
 */
internal object WireJson {

    /**
     * 取增量文本（流式帧）。
     *
     * @return 正文；结束帧或字段缺失时返回空串
     */
    fun deltaText(payload: String): String {
        val choice = firstChoice(payload) ?: return ""
        val delta = objectValue(choice, "delta") ?: objectValue(choice, "message") ?: return ""
        return stringValue(delta, "content") ?: ""
    }

    /** 取完整正文（非流式响应）。 */
    fun fullText(payload: String): String {
        val choice = firstChoice(payload) ?: return ""
        val message = objectValue(choice, "message") ?: return ""
        return stringValue(message, "content") ?: ""
    }

    /** 是否包含 `choices` 字段（用于区分错误帧与正常帧）。 */
    fun hasChoices(payload: String): Boolean = findValueStart(payload, "choices") != null

    /**
     * 提取错误信息，尽量给出可读原因。
     *
     * 形如 `{"error":{"message":"..."}}` 时取内层 message；
     * 否则退回原始响应前 300 字符（HTML 错误页也能看出大概）。
     */
    fun errorMessage(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return "服务端未返回错误详情"
        objectValue(trimmed, "error")?.let { err ->
            stringValue(err, "message")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        stringValue(trimmed, "message")?.takeIf { it.isNotBlank() }?.let { return it }
        return trimmed.take(300)
    }

    // --- 基础查找 -------------------------------------------------------------

    /** `choices` 数组的第一个元素对象。 */
    private fun firstChoice(payload: String): String? {
        val arrStart = findValueStart(payload, "choices") ?: return null
        if (payload.getOrNull(arrStart) != '[') return null
        // 跳过 '[' 之后的空白，应立刻是 '{'
        var i = skipWs(payload, arrStart + 1)
        if (payload.getOrNull(i) != '{') return null
        val end = matchObject(payload, i) ?: return null
        return payload.substring(i, end + 1)
    }

    /** 取某个 key 对应的对象值（不含外层大括号）。 */
    private fun objectValue(json: String, key: String): String? {
        val start = findValueStart(json, key) ?: return null
        if (json.getOrNull(start) != '{') return null
        val end = matchObject(json, start) ?: return null
        return json.substring(start + 1, end)
    }

    /**
     * 取字符串值，已还原转义。
     *
     * 只认"第一层"的 key：进入对象后逐层跳过嵌套的 `{...}` / `[...]`，
     * 避免把子对象里的同名 key 误当成当前层的。
     */
    private fun stringValue(json: String, key: String): String? {
        val idx = findKeyAtDepth(json, key, depth = 0) ?: return null
        var i = skipWs(json, idx + key.length + 2) // 跳过 "key":
        // 跳过 key 与值之间的冒号
        while (i < json.length && json[i] != ':') i++
        i = skipWs(json, i + 1)
        if (json.getOrNull(i) != '"') return null
        val sb = StringBuilder()
        i++
        while (i < json.length) {
            val c = json[i]
            when {
                c == '\\' -> {
                    val nxt = json.getOrNull(i + 1) ?: break
                    when (nxt) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'u' -> {
                            val hex = json.getOrNull(i + 2)?.let { _ ->
                                if (i + 5 < json.length) json.substring(i + 2, i + 6) else null
                            }
                            val code = hex?.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 4
                            } else {
                                sb.append(nxt)
                            }
                        }

                        else -> sb.append(nxt)
                    }
                    i += 2
                }

                c == '"' -> return sb.toString()
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return null
    }

    /**
     * 找到 key 在"对象第一层"的第一处出现位置。
     *
     * 只跟踪对象层级（`{}`），不把数组 `[]` 计入深度：这样
     * `{"a":[{"content":X}],"content":Y}` 里取的是最外层的 Y，
     * 而不是数组元素里的 X。
     */
    private fun findKeyAtDepth(json: String, key: String, depth: Int): Int? {
        var i = 0
        var level = 0
        // 记录"最近一次开始扫描时"的层级，用于判断当前是否处于目标层
        while (i < json.length) {
            when (json[i]) {
                '"' -> {
                    val end = endOfString(json, i) ?: return null
                    val name = json.substring(i + 1, end)
                    if (level == depth && name == key) return i
                    i = end + 1
                    continue
                }

                '{' -> level++
                '}' -> level--
            }
            i++
        }
        return null
    }

    /** 找到 `"key":` 之后第一个非空白字符（即值的起点）。 */
    private fun findValueStart(json: String, key: String): Int? {
        val needle = "\"$key\""
        var from = 0
        while (true) {
            val idx = json.indexOf(needle, from)
            if (idx < 0) return null
            var i = skipWs(json, idx + needle.length)
            if (json.getOrNull(i) == ':') {
                return skipWs(json, i + 1)
            }
            from = idx + needle.length
        }
    }

    /** 从 `{` 起找到配对的 `}`，正确处理字符串内的括号与转义。 */
    private fun matchObject(json: String, start: Int): Int? {
        var depth = 0
        var i = start
        while (i < json.length) {
            when (json[i]) {
                '"' -> {
                    i = (endOfString(json, i) ?: return null) + 1
                    continue
                }

                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    /** 返回字符串结束引号的下标。 */
    private fun endOfString(json: String, start: Int): Int? {
        var i = start + 1
        while (i < json.length) {
            when (json[i]) {
                '\\' -> i += 2
                '"' -> return i
                else -> i++
            }
        }
        return null
    }

    private fun skipWs(s: String, from: Int): Int {
        var i = from
        while (i < s.length && s[i].isWhitespace()) i++
        return i
    }

    private fun String.getOrNull(index: Int): Char? = if (index in indices) this[index] else null
}
