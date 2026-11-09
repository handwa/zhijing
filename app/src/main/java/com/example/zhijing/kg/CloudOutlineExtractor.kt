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
     * 生成大纲与卡片。**统一的入口，不分模式**。
     *
     * 是否给 `source` 决定了两条路径，但对外只有一个方法：
     *
     * | | 给了 source | 没给 source |
     * |---|---|---|
     * | 模型做什么 | 只切分，给区间 | 规划结构 + 撰写正文 |
     * | 正文来源 | source 逐字截取 | 模型生成 |
     * | `verified` | `true` | `false` |
     *
     * @param provider 用户配置的云服务商
     * @param topic 学习主题
     * @param notes 补充说明，可空
     * @param source 原文，可空。给了就按原文切分，不改写
     * @param maxTokens 生成上限
     */
    suspend fun extract(
        provider: CloudProvider,
        topic: String,
        notes: String = "",
        source: String = "",
        maxTokens: Int = DEFAULT_MAX_TOKENS
    ): OutlineExtraction {
        provider.readyFailureReason()?.let { reason ->
            throw IllegalArgumentException("云端配置不完整：$reason")
        }
        require(topic.isNotBlank() || source.isNotBlank()) { "主题与原文都为空" }

        val hasSource = source.isNotBlank()
        val text = client.streamChat(
            provider = provider,
            messages = listOf(
                CloudMessage("system", SYSTEM_PROMPT),
                CloudMessage(
                    "user",
                    userPrompt(
                        topic = topic,
                        notes = notes,
                        source = source.take(MAX_CONTENT_CHARS),
                        maxBlocks = MAX_BLOCKS
                    )
                )
            ),
            maxTokens = maxTokens
        ).toList().joinToString("")

        val parsed = OutlineOutputParser.parse(text, maxBlocks = MAX_BLOCKS)
        // 有原文：正文从原文截取；无原文：正文用模型给的
        val built = if (hasSource) {
            OutlineBuilder.build(source.trim(), parsed.blocks)
        } else {
            OutlineBuilder.buildFromBodies(parsed.blocks)
        }

        return OutlineExtraction(
            outline = built.outline,
            cards = built.cards,
            warnings = parsed.warnings + built.warnings,
            prerequisites = built.prerequisites,
            verified = hasSource
        )
    }

    /** 一次广度抽取的结果。 */
    data class OutlineExtraction(
        val outline: List<OutlineBuilder.OutlineNode>,
        val cards: List<OutlineBuilder.Card>,
        val warnings: List<String>,
        /** 卡片前置关系（端点已是卡片下标）。 */
        val prerequisites: List<OutlineBuilder.ResolvedPrerequisite> = emptyList(),
        /**
         * 正文是否可溯源。
         *
         * `true` = 从原文逐字截取；`false` = 模型生成，**未经核实**。
         */
        val verified: Boolean = true
    ) {
        val isEmpty: Boolean get() = cards.isEmpty()
    }

    internal fun buildMessages(content: String): List<CloudMessage> = listOf(
        CloudMessage("system", SYSTEM_PROMPT),
        CloudMessage("user", userPrompt(content))
    )

    companion object {
        /**
         * 有原文模式的输出上限。
         *
         * 该模式模型**不写正文**，每块只有 title/summary/区间/requires。
         * 实测 50 块约 3500 token，取 4000 留余量。
         */
        const val DEFAULT_MAX_TOKENS = 4000

        /**
         * 无原文模式的输出上限（模型要写正文，最贵的路径）。
         *
         * 实测：50 块 × 80 字 ≈ 4780，× 140 字 ≈ 6280，× 200 字 ≈ 7780。
         * 取 9000，覆盖最长的正文规格。
         */
        const val DEFAULT_TOPIC_MAX_TOKENS = 9000


        /** 建议的单次输入上限；更长先分段。 */
        const val MAX_CONTENT_CHARS = 4000

        /**
         * 最大块数。**全局唯一的定义**，提示词、请求参数、解析截断都用它。
         *
         * 注意：50 块 × 140 字正文 ≈ 6300 输出 token，因此 [DEFAULT_TOPIC_MAX_TOKENS]
         * 必须同步放大，否则生成会在中途被截断 —— 表现为「块数还是不够」。
         */
        const val MAX_BLOCKS = 50

        /**
         * system prompt，与 `kg/prompts/extract_outline.md` 第 3 节逐字一致。
         *
         * **只有一个提示词，不分模式**：是否给了原文只体现在「原文」这一条要求上。
         * 有原文 → 按原文切分、给区间（正文由程序从原文截取，可溯源）；
         * 没原文 → 按学习顺序规划并撰写正文（未核实）。
         *
         * 刻意**不含任何领域词**：示例用抽象占位符。
         */
        val SYSTEM_PROMPT: String = """
            你是一名指导教师，负责为零基础学生规划主题的知识脉络。
            只输出合法 JSON，不解释、不使用 Markdown、不加任何评论、不输出代码块标记。

            【输入】
            你会收到一个 JSON 对象：
            - topic：学习主题
            - notes：可选，补充说明
            - source：可选，原文。有值时按下面【原文要求】执行
            - max_blocks：可选，最大块数，默认 50

            【任务】
            把主题拆成按学习顺序排列的内容块，确保零基础学生能逐步学会。
            1. 按该主题公认的学习顺序组织，先修内容在前，不要为凑数量硬编。
            2. 每块只讲清一个要点，可以细分，但块数不超过 max_blocks。
            3. 每块正文放在 body 字段，80–200 个中文字符。
            4. 正文中首次出现术语时用一句话解释；不假设学生已掌握前置知识。
            5. 不确定的地方在正文中标注"（此点存疑）"，不要编造具体数字、时间、人名或法规条文。
            6. title 用编号表示层级，一级写 "1 xxx"，二级写 "1.1 xxx"，与你的结构一致；
               不超过 20 个字符。
            7. summary 不超过 40 个字符，概括该块核心内容或学习目标，不要只复述 title。
            8. 不写"正确""错误""成立""不成立""很好""很差"等判断性内容。
            9. 不输出分类标签、领域名、关键词列表。
            10. title 与 summary 用 source 或 topic 的语言书写。

            【原文要求】
            如果输入里给了 source：
            - 必须**只切分 source 里已有的内容**，不得改写、不得补充原文没有的内容。
            - 每块额外输出 bodyStart / bodyEnd，表示该块在 source 中的字符区间
              （从 0 开始，bodyEnd 指向最后一个字符之后，不含；相邻块不得重叠）。
            - 有 source 时用 bodyStart / bodyEnd 代替 body。
            没有给 source 时，正常撰写 body，不要输出 bodyStart / bodyEnd。

            【前置关系】
            判断这一块**依赖哪些其它块**——即「不先学那块，这一块就学不懂或跟不上」。
            每块可选输出 requires 数组，每项：
            - on：被依赖块的 title，必须与 blocks 里某个 title 完全一致
            - kind：required（不先学就学不懂）或 recommended（有帮助，但不学也能跟上）
            - why：一句话说明依赖点，不超过 20 字

            约束：
            - 每块最多 3 条 requires；required 不要超过所有 requires 的一半。
            - 只能引用**排在本块之前**的块（先修在前），不能形成环。
            - 只是"话题相关"或"编号相邻"不算前置，不要输出。
            - 没有合理前置就不输出 requires 字段。

            【输出格式】
            无 source：
            {"blocks":[{"title":"...","summary":"...","body":"...",
              "requires":[{"on":"...","kind":"required","why":"..."}]}]}

            有 source：
            {"blocks":[{"title":"...","summary":"...","bodyStart":0,"bodyEnd":120,
              "requires":[{"on":"...","kind":"recommended","why":"..."}]}]}

            缺少 topic 且缺少 source 时，输出 {"blocks":[]}。
        """.trimIndent()

        /**
         * user prompt：**统一格式**，一个 JSON 对象。
         *
         * 不管有没有原文都用同一种输入，是否带 `source` 由调用方决定 ——
         * 这样提示词里只需要一条「原文要求」，不用写两套。
         *
         * @param topic 学习主题
         * @param notes 补充说明，可空
         * @param source 原文，可空
         * @param maxBlocks 最大块数
         */
        internal fun userPrompt(
            topic: String,
            notes: String = "",
            source: String = "",
            maxBlocks: Int = MAX_BLOCKS
        ): String = buildString {
            append("{\n")
            append("  \"topic\": ").append(jsonString(topic.trim())).append(",\n")
            if (notes.isNotBlank()) {
                append("  \"notes\": ").append(jsonString(notes.trim())).append(",\n")
            }
            if (source.isNotBlank()) {
                // 原文字数写进 notes 之外的独立说明，帮助模型算准字符区间
                append("  \"source\": ").append(jsonString(source.trim())).append(",\n")
                append("  \"source_length\": ").append(source.trim().length).append(",\n")
            }
            append("  \"max_blocks\": ").append(maxBlocks.coerceIn(1, MAX_BLOCKS)).append('\n')
            append("}")
        }

        /** 最小 JSON 字符串转义：只处理必须要转的字符。 */
        private fun jsonString(raw: String): String = buildString {
            append('"')
            for (ch in raw) {
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
                }
            }
            append('"')
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


    /**
     * 解析模型返回的 `blocks`。
     *
     * @param maxBlocks 最多保留多少块。**必须由调用方传入**，不要在解析器里写死 ——
     *   这里曾经有一个独立的 `private const val MAX_BLOCKS = 12`，
     *   与 `CloudOutlineExtractor.MAX_BLOCKS` 是两个不同的常量；
     *   改外面那个、里面这个不变，结果「改了上限却毫无效果」。
     */
    fun parse(
        raw: String,
        maxBlocks: Int = CloudOutlineExtractor.MAX_BLOCKS
    ): Result {
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
        objects.take(maxBlocks).forEachIndexed { i, obj ->
            val title = stringField(obj, "title")?.trim().orEmpty()
            if (title.isEmpty()) {
                warnings += "#${i + 1} 缺少 title，已丢弃"
                return@forEachIndexed
            }
            val start = intField(obj, "bodyStart")
            val end = intField(obj, "bodyEnd")
            val body = stringField(obj, "body")?.trim().orEmpty()

            // 双模式：有区间 → 结构化模式；无区间但有 body → 生成式模式
            val requires = parseRequires(obj)
            when {
                start != null && end != null -> blocks += OutlineBuilder.RawBlock(
                    title = title,
                    summary = stringField(obj, "summary")?.trim().orEmpty(),
                    bodyStart = start,
                    bodyEnd = end,
                    requires = requires
                )

                body.isNotEmpty() -> blocks += OutlineBuilder.RawBlock(
                    title = title,
                    summary = stringField(obj, "summary")?.trim().orEmpty(),
                    bodyStart = 0,
                    bodyEnd = 0,
                    body = body,
                    requires = requires
                )

                else -> {
                    warnings += "#${i + 1}「$title」既无区间也无正文，已丢弃"
                    return@forEachIndexed
                }
            }
        }
        if (objects.size > maxBlocks) {
            warnings += "块数超过上限 $maxBlocks，已截断"
        }
        return Result(blocks, warnings)
    }

    // --- 宽松 JSON 读取 ------------------------------------------------------

    /**
     * 解析一个块里的 `requires` 数组。
     *
     * 容错策略与其它字段一致：
     * - 缺 `on` 或 `on` 为空 → 丢弃该条
     * - `kind` 不认识 → 按 `recommended`（更保守，不会把建议当硬约束）
     * - `why` 缺省为空
     */
    private fun parseRequires(obj: String): List<OutlineBuilder.RawRequire> {
        val raw = valueStartOf(obj, "requires") ?: return emptyList()
        if (obj.getOrNull(raw) != '[') return emptyList()
        val end = matchBracket(obj, raw) ?: return emptyList()
        val body = obj.substring(raw + 1, end)

        return splitObjects(body).mapNotNull { item ->
            val on = stringField(item, "on")?.trim().orEmpty()
            if (on.isEmpty()) return@mapNotNull null
            OutlineBuilder.RawRequire(
                on = on,
                kind = PrerequisiteKind.fromWire(stringField(item, "kind")?.trim()),
                why = stringField(item, "why")?.trim().orEmpty()
            )
        }
    }

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
