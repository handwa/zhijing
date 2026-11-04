package com.example.zhijing.kg

/**
 * 出厂图谱的最小 JSON 解析器。
 *
 * 为什么不用 `org.json`：Android 平台类在纯 JVM 单测里是空桩，无法被测试覆盖；
 * 而图谱加载恰恰是「云端增量合并」的地基，必须可回归。
 *
 * 为什么不用 kotlinx-serialization / Gson：项目刻意零额外依赖（`data` 模块只用
 * 标准库 + Room + coroutines），为了一份结构固定的 130KB 文件引入序列化框架不划算。
 *
 * 支持范围就是 JSON 的完整语法：对象、数组、字符串（含转义与 `\uXXXX`）、数字、
 * `true` / `false` / `null`。不支持的输入抛 [GraphFormatException]，附带出错位置。
 */
internal object MiniJson {

    /** 解析失败。**带上位置**，否则排查 130KB 文件里的格式错误会非常痛苦。 */
    class GraphFormatException(
        message: String,
        val offset: Int,
        val near: String
    ) : Exception("$message（位置 $offset，附近：$near）")

    /** 解析入口：返回 Map / List / String / Double / Boolean / null 组成的树。 */
    fun parse(text: String): Any? {
        val reader = Reader(text, 0)
        reader.skipWhitespace()
        val value = reader.readValue()
        reader.skipWhitespace()
        if (!reader.atEnd()) throw reader.fail("JSON 结束后仍有多余内容")
        return value
    }

    private class Reader(private val text: String, private var pos: Int) {

        fun atEnd(): Boolean = pos >= text.length

        fun fail(message: String): GraphFormatException =
            GraphFormatException(message, pos, text.substring(pos.coerceAtMost(text.length), minOf(pos + 20, text.length)))

        fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun readValue(): Any? {
            if (atEnd()) throw fail("JSON 意外结束")
            return when (val c = text[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else throw fail("无法识别的值起始字符 '$c'")
            }
        }

        private fun readLiteral(literal: String, value: Any?): Any? {
            if (!text.startsWith(literal, pos)) throw fail("期望字面量 $literal")
            pos += literal.length
            return value
        }

        private fun readNumber(): Double {
            val start = pos
            if (text[pos] == '-') pos++
            while (pos < text.length && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
            val raw = text.substring(start, pos)
            return raw.toDoubleOrNull() ?: throw GraphFormatException("数字格式错误：$raw", start, raw)
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw fail("字符串未闭合")
                when (val c = text[pos]) {
                    '"' -> {
                        pos++
                        return sb.toString()
                    }

                    '\\' -> {
                        val next = text.getOrNull(pos + 1) ?: throw fail("转义未完成")
                        when (next) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'u' -> {
                                val hex = if (pos + 6 <= text.length) text.substring(pos + 2, pos + 6) else null
                                val code = hex?.toIntOrNull(16) ?: throw fail("\\u 转义不合法")
                                sb.append(code.toChar())
                                pos += 4
                            }

                            else -> sb.append(next)
                        }
                        pos += 2
                    }

                    else -> {
                        sb.append(c)
                        pos++
                    }
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            skipWhitespace()
            if (text.getOrNull(pos) == ']') {
                pos++
                return out
            }
            while (true) {
                skipWhitespace()
                out += readValue()
                skipWhitespace()
                when (text.getOrNull(pos)) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return out
                    }

                    else -> throw fail("数组缺少逗号或右方括号")
                }
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (text.getOrNull(pos) == '}') {
                pos++
                return out
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                out[key] = readValue()
                skipWhitespace()
                when (text.getOrNull(pos)) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return out
                    }

                    else -> throw fail("对象缺少逗号或右花括号")
                }
            }
        }

        private fun expect(ch: Char) {
            if (text.getOrNull(pos) != ch) throw fail("期望 '$ch'")
            pos++
        }

        private fun String.getOrNull(index: Int): Char? = if (index in indices) this[index] else null
    }
}

/*
 * 取值辅助：把 JSON 树当成弱类型结构读，缺失字段一律返回默认值而不是抛异常。
 *
 * 出厂图谱由脚本生成、字段齐全，但**云端增量或未来版本可能缺字段**，
 * 读侧不能因为少一个可选字段就整份加载失败。
 */

internal fun Any?.asMap(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

internal fun Any?.asList(): List<Any?> = this as? List<Any?> ?: emptyList()

internal fun Any?.asString(): String? = this as? String

internal fun Any?.asStringOrEmpty(): String = asString() ?: ""

internal fun Any?.asBool(): Boolean? = this as? Boolean

internal fun Any?.asInt(): Int? = (this as? Double)?.toInt()

internal fun Any?.asStringList(): List<String> =
    asList().mapNotNull { it as? String }

internal fun Map<String, Any?>.obj(key: String): Map<String, Any?> = this[key].asMap()

internal fun Map<String, Any?>.list(key: String): List<Any?> = this[key].asList()

internal fun Map<String, Any?>.str(key: String): String? = this[key].asString()

internal fun Map<String, Any?>.strOrEmpty(key: String): String = this[key].asStringOrEmpty()

internal fun Map<String, Any?>.bool(key: String): Boolean? = this[key].asBool()

internal fun Map<String, Any?>.int(key: String): Int? = this[key].asInt()

internal fun Map<String, Any?>.strings(key: String): List<String> = this[key].asStringList()
