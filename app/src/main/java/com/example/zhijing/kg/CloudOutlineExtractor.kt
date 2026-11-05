package com.example.zhijing.kg

import com.example.zhijing.cloud.CloudMessage
import com.example.zhijing.cloud.CloudProvider
import com.example.zhijing.cloud.RemoteChatClient
import kotlinx.coroutines.flow.toList

/**
 * 广度图谱抽取器：把一段文本交给云端切成「内容块」，本地算法再加工成大纲与卡片。
 *
 * 与 [CloudClaimExtractor] 的分工：
 *
 * | | 广度（本类） | 深度（CloudClaimExtractor） |
 * |---|---|---|
 * | 回答 | 这个主题**由哪些部分**组成 | 这个点**到底是什么** |
 * | 产出 | 块的标题、摘要、原文区间 | 命题、条件、错点 |
 * | 不许产出 | 任何判断（对/错、成立/不成立） | 任何组织性内容 |
 *
 * **提示词必须通用**：不出现任何领域词，换领域不改一个字。见 `kg/prompts/extract_outline.md`。
 */
class CloudOutlineExtractor(
    private val client: RemoteChatClient = RemoteChatClient()
) {

    /**
     * 抽取一段文本的大纲与卡片。
     *
     * @param provider 用户配置的云服务商
     * @param content 待切分的原文
     * @param maxTokens 生成上限，12 块的标题与摘要 900 token 足够
     * @return 加工好的大纲与卡片（已做区间校验、层级推断、正文截取）
     */
    suspend fun extract(
        provider: CloudProvider,
        content: String,
        maxTokens: Int = DEFAULT_MAX_TOKENS
    ): OutlineExtraction {
        provider.readyFailureReason()?.let { reason ->
            throw IllegalArgumentException("云端配置不完整：$reason")
        }
        require(content.isNotBlank()) { "待抽取文本为空" }

        val text = client.streamChat(
            provider = provider,
            messages = buildMessages(content),
            maxTokens = maxTokens
        ).toList().joinToString("")

        val parsed = OutlineOutputParser.parse(text)
        val built = OutlineBuilder.build(content.trim(), parsed.blocks)

        return OutlineExtraction(
            outline = built.outline,
            cards = built.cards,
            warnings = parsed.warnings + built.warnings
        )
    }

    /** 一次广度抽取的结果。 */
    data class OutlineExtraction(
        val outline: List<OutlineBuilder.OutlineNode>,
        val cards: List<OutlineBuilder.Card>,
        val warnings: List<String>
    ) {
        val isEmpty: Boolean get() = cards.isEmpty()
    }

    internal fun buildMessages(content: String): List<CloudMessage> = listOf(
        CloudMessage("system", SYSTEM_PROMPT),
        CloudMessage("user", userPrompt(content))
    )

    companion object {
        /** 12 块的标题与摘要足够。 */
        const val DEFAULT_MAX_TOKENS = 900

        /** 建议的单次输入上限；更长先分段。 */
        const val MAX_CONTENT_CHARS = 4000

        /**
         * system prompt，与 `kg/prompts/extract_outline.md` 第 3 节逐字一致。
         *
         * 刻意**没有任何领域词**：示例用的是抽象占位符。
         * `OutlineBuilderTest` 里有一条断言扫描本字符串，出现领域词即失败。
         */
        val SYSTEM_PROMPT: String = """
            你是文本结构化器，只输出 JSON，不解释、不使用 Markdown、不加任何评论。
            任务：把用户给的一段文本切分成若干「内容块」，并给每块写一句摘要。

            一个内容块 = 原文里连续的一段，讲同一件事。切分规则：
            1. 按语义边界切，不要按字数机械切。一个块通常 1–3 段。
            2. 每块必须在原文里连续存在，不得跨段拼接、不得改写。
            3. 块的数量 1–12；原文很短时只切 1 块。

            每块输出三个字段：
            - title：这一段讲什么。用原文的措辞，不要自己造名词，不超过 20 字。
            - summary：一句话概括，不超过 40 字。只概括，不评价、不补充原文没有的信息。
            - bodyStart / bodyEnd：该块在原文中的字符区间（从 0 开始计）。
              bodyStart 必须指向该块第一个字符，bodyEnd 指向最后一个字符之后（不包含）。
              相邻块之间可以有未归属的空白，但**不得重叠**。

            硬性约束：
            - title 与 summary 用原文的语言书写（原文是中文就用中文）。
            - 不要输出任何判断性内容：不写「正确」「错误」「成立」「不成立」「应该」「必须」这类评价。
            - 不要输出分类标签、领域名、关键词列表 —— 这些由后续程序计算，不是你的任务。
            - 不要合并相隔很远的内容。宁可多切几块，也不要拼成一个块。

            输出格式：
            {"blocks":[{"title":"...","summary":"...","bodyStart":0,"bodyEnd":120}]}

            无法切分时输出 {"blocks":[]}。
        """.trimIndent()

        /** 必须把原文字数告诉模型，否则它算不准字符区间。 */
        internal fun userPrompt(content: String): String = buildString {
            append("原文（共 ").append(content.trim().length).append(" 字符）：\n\"\"\"\n")
            append(content.trim())
            append("\n\"\"\"")
        }
    }
}

/**
 * 广度抽取输出的解析器。
 *
 * 与 [ClaimOutputParser] 同级容错：剥代码围栏、容忍前后夹带解释、截断的数组保留完整条目。
 * 不复用 ClaimOutputParser 是因为输出结构不同（`blocks` vs `claims`），
 * 强行复用会让那个类变成「什么都能解析」的万能类。
 */
object OutlineOutputParser {

    data class Result(
        val blocks: List<OutlineBuilder.RawBlock>,
        val warnings: List<String> = emptyList()
    )

    private const val MAX_BLOCKS = 12

    fun parse(raw: String): Result {
        val warnings = ArrayList<String>()
        var text = raw.trim()
        if (text.isEmpty()) return Result(emptyList(), listOf("模型返回为空"))

        // 剥围栏
        if (text.contains("```")) {
            Regex("```(?:json|JSON)?\\s*\\n([\\s\\S]*?)\\n?```").find(text)?.let { m ->
                text = m.groupValues[1].trim()
                warnings += "已剥除 Markdown 代码围栏"
            }
        }
        // 取第一个 { 到最后一个 }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) {
            return Result(emptyList(), warnings + listOf("未找到 JSON 对象"))
        }
        val payload = text.substring(start, end + 1)

        val keyIdx = findKey(payload, "blocks")
        if (keyIdx < 0) return Result(emptyList(), warnings + listOf("未找到 blocks 数组"))
        var cursor = keyIdx + "\"blocks\"".length
        while (cursor < payload.length && payload[cursor] != ':') cursor++
        cursor = skipWs(payload, cursor + 1)
        if (payload.getOrNull(cursor) != '[') {
            return Result(emptyList(), warnings + listOf("blocks 的值不是数组"))
        }
        val arrEnd = matchBracket(payload, cursor)
        if (arrEnd == null) warnings += "blocks 数组未闭合，按截断处理"
        val body = payload.substring(cursor + 1, arrEnd ?: payload.length)

        val objects = splitObjects(body)
        val blocks = ArrayList<OutlineBuilder.RawBlock>()
        objects.take(MAX_BLOCKS).forEachIndexed { i, obj ->
            val title = stringField(obj, "title")?.trim().orEmpty()
            if (title.isEmpty()) {
                warnings += "#${i + 1} 缺少 title，已丢弃"
                return@forEachIndexed
            }
            val start = intField(obj, "bodyStart")
            val end = intField(obj, "bodyEnd")
            if (start == null || end == null) {
                warnings += "#${i + 1}「$title」缺少 bodyStart/bodyEnd，已丢弃"
                return@forEachIndexed
            }
            blocks += OutlineBuilder.RawBlock(
                title = title,
                summary = stringField(obj, "summary")?.trim().orEmpty(),
                bodyStart = start,
                bodyEnd = end
            )
        }
        if (objects.size > MAX_BLOCKS) {
            warnings += "块数超过上限 $MAX_BLOCKS，已截断"
        }
        return Result(blocks, warnings)
    }

    // --- 宽松 JSON 读取 ------------------------------------------------------

    private fun stringField(obj: String, key: String): String? {
        val v = valueStartOf(obj, key) ?: return null
        if (obj.getOrNull(v) != '"') return null
        return readString(obj, v)
    }

    private fun intField(obj: String, key: String): Int? {
        val v = valueStartOf(obj, key) ?: return null
        var i = v
        while (i < obj.length && obj[i] !in ",}]" && !obj[i].isWhitespace()) i++
        return obj.substring(v, i).trim().toIntOrNull()
    }

    private fun valueStartOf(obj: String, key: String): Int? {
        val idx = findKey(obj, key)
        if (idx < 0) return null
        var i = idx + key.length + 2
        i = skipWs(obj, i)
        if (obj.getOrNull(i) != ':') return null
        return skipWs(obj, i + 1)
    }

    /** 找 key 作为「对象键」出现的位置（内容相等且紧跟冒号）。 */
    private fun findKey(obj: String, key: String): Int {
        var i = 0
        while (i < obj.length) {
            if (obj[i] == '"') {
                val end = endOfString(obj, i)
                if (end == null) return -1
                if (obj.substring(i + 1, end) == key) {
                    val after = skipWs(obj, end + 1)
                    if (obj.getOrNull(after) == ':') return i
                }
                i = end + 1
                continue
            }
            i++
        }
        return -1
    }

    /**
     * 把一个数组体拆成顶层对象。
     *
     * 注意：数组里可能混入非对象元素（模型偶尔把字符串或数字塞进来）。
     * 跳过它们时**必须保证 i 前进**，否则遇到逗号会原地死循环 ——
     * 这正是本函数曾经卡死的原因（`body[i] == ','` 时内层 while 立即退出）。
     */
    private fun splitObjects(body: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < body.length) {
            i = skipWs(body, i)
            if (i >= body.length) break
            if (body[i] != '{') {
                // 跳过当前元素：一直走到下一个逗号
                while (i < body.length && body[i] != ',') i++
                // 吃掉逗号本身，保证下一轮从新位置开始
                if (i < body.length) i++
                continue
            }
            val end = matchBrace(body, i) ?: break
            out += body.substring(i, end + 1)
            i = end + 1
        }
        return out
    }

    /** 栈式匹配：闭括号必须与栈顶同类。 */
    private fun matchBracket(s: String, start: Int): Int? {
        val stack = ArrayDeque<Char>()
        var i = start
        while (i < s.length) {
            when (s[i]) {
                '"' -> {
                    i = (endOfString(s, i) ?: return null) + 1
                    continue
                }

                '[', '{' -> stack.addLast(s[i])
                ']', '}' -> {
                    val expect = if (s[i] == ']') '[' else '{'
                    if (stack.isEmpty() || stack.removeLast() != expect) return null
                    if (stack.isEmpty()) return i
                }
            }
            i++
        }
        return null
    }

    private fun matchBrace(s: String, start: Int): Int? = matchBracket(s, start)

    private fun readString(s: String, start: Int): String? {
        var i = start + 1
        val sb = StringBuilder()
        while (i < s.length) {
            when (s[i]) {
                '\\' -> {
                    val n = s.getOrNull(i + 1) ?: return sb.toString()
                    sb.append(
                        when (n) {
                            'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'
                            'b' -> '\b'; 'f' -> '\u000C'
                            else -> n
                        }
                    )
                    i += 2
                }

                '"' -> return sb.toString()
                else -> {
                    sb.append(s[i])
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun endOfString(s: String, start: Int): Int? {
        var i = start + 1
        while (i < s.length) {
            when (s[i]) {
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

    private fun String.getOrNull(i: Int): Char? = if (i in indices) this[i] else null
}
