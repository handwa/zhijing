package com.zhijing.data.converter

import androidx.room.TypeConverter

/**
 * 字符串列表字段的类型转换器。
 *
 * 知识图谱里的 `misconceptions`（误解注记）与 `conditions`（成立条件）都是**短句列表**，
 * 元素里可能出现逗号、顿号等分隔符，因此**不能**沿用 [ListConverters] 的逗号拼接，
 * 这里序列化为 JSON 数组字符串，避免分隔符歧义。
 *
 * 刻意不引入 Gson / kotlinx-serialization：结构极简单，手写编解码既能零新增依赖，
 * 也能在遇到脏数据时安全降级为空列表而不是抛异常。
 */
internal class StringListConverters {

    @TypeConverter
    fun stringListToJson(values: List<String>?): String =
        values?.takeIf { it.isNotEmpty() }?.let { entries ->
            buildString {
                append('[')
                entries.forEachIndexed { index, entry ->
                    if (index > 0) append(',')
                    append('"')
                    for (ch in entry) {
                        when (ch) {
                            '"' -> append("\\\"")
                            '\\' -> append("\\\\")
                            '\n' -> append("\\n")
                            '\r' -> append("\\r")
                            '\t' -> append("\\t")
                            else -> append(ch)
                        }
                    }
                    append('"')
                }
                append(']')
            }
        } ?: "[]"

    @TypeConverter
    fun jsonToStringList(raw: String?): List<String> =
        raw?.trim()?.let(::parse) ?: emptyList()

    /** 解析 `["a","b"]`；格式不合法时返回空列表，避免一列脏数据让整表读取失败。 */
    private fun parse(text: String): List<String> {
        if (text.length < 2 || text.first() != '[' || text.last() != ']') return emptyList()
        val body = text.substring(1, text.length - 1).trim()
        if (body.isEmpty()) return emptyList()

        val out = ArrayList<String>()
        val current = StringBuilder()
        var inString = false
        var escaping = false
        for (ch in body) {
            when {
                escaping -> {
                    current.append(
                        when (ch) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            else -> ch
                        }
                    )
                    escaping = false
                }

                ch == '\\' && inString -> escaping = true
                ch == '"' -> {
                    if (inString) {
                        out.add(current.toString())
                        current.setLength(0)
                    }
                    inString = !inString
                }

                inString -> current.append(ch)
                ch == ',' || ch.isWhitespace() -> Unit
                else -> return emptyList()
            }
        }
        return if (inString) emptyList() else out
    }
}
