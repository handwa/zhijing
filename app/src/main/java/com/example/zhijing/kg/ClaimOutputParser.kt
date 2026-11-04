package com.example.zhijing.kg

/**
 * 把大模型返回的文本解析成 [ExtractedClaim]。
 *
 * 实现 `kg/prompts/output_contract.md` 第 5 节的容错链：
 *
 * ```
 * 原始文本
 *   ├─ 1. 直接解析
 *   ├─ 2. 取第一个 { 到最后一个 } 再解析
 *   ├─ 3. 去掉 ```json 围栏后重试
 *   └─ 4. 用正则救出 claims 数组，逐条尝试补全必需字段；救不出的条目丢弃并记 warning
 * ```
 *
 * 第 4 步的补全规则（与契约一致）：
 * - 缺 `expected` → 按 `false`，并打警告（宁可判错也不静默当成立）；
 * - 缺 `conditions` → 补「未标注条件」，并打警告（这类条目需要人工补，不能当高质量数据）；
 * - 缺 `statement` → **整条丢弃**（没有断言就无从判断）。
 *
 * 为什么不用 `org.json` 解析：它在纯 JVM 单测里是空桩，无法被测试覆盖；
 * 而解析逻辑恰恰是这条链路最需要回归测试的部分。这里的 JSON 读取器只做「宽松取值」，
 * 不追求通用性 —— 模型输出本来就经常不合法，能取出多少算多少比严格报错更有用。
 */
object ClaimOutputParser {

    /** 解析结果：候选命题 + 整体层面的问题。 */
    data class Result(
        val claims: List<ExtractedClaim>,
        val warnings: List<String> = emptyList()
    ) {
        val isEmpty: Boolean get() = claims.isEmpty()
    }

    private const val MAX_CLAIMS = 8

    /**
     * 解析模型输出。
     *
     * @param raw 模型返回的完整文本（可能带 ```json 围栏、可能夹带解释文字）
     */
    fun parse(raw: String): Result {
        val warnings = ArrayList<String>()
        val text = raw.trim()
        if (text.isEmpty()) {
            return Result(emptyList(), listOf("模型返回为空"))
        }

        val payload = unwrapFence(text, warnings)
        val claimsArray = locateClaimsArray(payload, warnings)

        if (claimsArray == null) {
            warnings += "未在返回文本中找到 claims 数组"
            return Result(emptyList(), warnings)
        }
        if (claimsArray.isEmpty()) {
            // 模型按契约合法地表示「没有合格命题」，这不是错误
            return Result(emptyList(), warnings)
        }

        val claims = ArrayList<ExtractedClaim>()
        claimsArray.take(MAX_CLAIMS).forEachIndexed { index, obj ->
            val parsed = parseOne(obj, index, warnings)
            if (parsed != null) claims += parsed
        }
        return Result(claims, warnings)
    }

    // --- 第 3 步：去掉 Markdown 围栏 -----------------------------------------

    private fun unwrapFence(text: String, warnings: MutableList<String>): String {
        if (!text.contains("```")) return text
        // 优先取 ```json / ```JSON 围栏内部；没有标注语言时取第一个围栏
        val fenced = Regex("```(?:json|JSON)?\\s*\\n([\\s\\S]*?)\\n?```")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (fenced.isNullOrEmpty()) {
            warnings += "检测到 Markdown 围栏但内容为空"
            return text
        }
        warnings += "已剥除 Markdown 代码围栏"
        return fenced
    }

    // --- 第 2 步：定位 claims 数组 -------------------------------------------

    /**
     * 找到 `claims` 数组里的每个顶层对象。
     *
     * 返回 `null` 表示没找到数组；返回空列表表示数组存在但为空（合法的「无命题」）。
     */
    private fun locateClaimsArray(payload: String, warnings: MutableList<String>): List<String>? {
        // 模型常在 JSON 前后夹带解释文字，先定位真正的 JSON 起点
        val json = trimToJsonSpan(payload)
        val keyIdx = findKey(json, "claims") ?: return null
        // 从 key 之后找冒号，再跳过空白，应看到 '['
        var cursor = keyIdx + "\"claims\"".length
        while (cursor < json.length && json[cursor] != ':') cursor++
        cursor++
        cursor = skipWs(json, cursor)
        if (json.getOrNull(cursor) != '[') {
            warnings += "claims 的值不是数组"
            return null
        }
        val end = matchBracket(json, cursor)
        if (end == null) {
            warnings += "claims 数组未闭合，按截断处理"
        }
        // 未闭合时把 body 开到末尾：splitTopLevelObjects 会自行丢弃不完整的尾巴，
        // 这样前面已经完整的条目仍能被救出来（否则整段白丢）。
        val bodyStart = cursor + 1
        val bodyEnd = end ?: json.length
        val body = json.substring(bodyStart, bodyEnd.coerceAtLeast(bodyStart))
        return splitTopLevelObjects(body)
    }

    /**
     * 把「JSON 前后夹带解释文字」的文本裁成纯 JSON 片段。
     *
     * 取第一个 `{` 到最后一个 `}` —— 模型偶尔在 JSON 之后再加一句，
     * 但那句里极少含花括号，因此这个切法比正向扫描更稳。
     * 找不到花括号时原样返回，交给后续步骤报错。
     */
    private fun trimToJsonSpan(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return text
        return text.substring(start, end + 1)
    }

    /** 把一个数组体拆成顶层对象字符串。不完整的尾巴会被丢弃。 */
    private fun splitTopLevelObjects(body: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < body.length) {
            i = skipWs(body, i)
            if (i >= body.length) break
            if (body[i] == ',') {
                i++
                continue
            }
            if (body[i] != '{') {
                // 数组里出现非对象元素（字符串/数字），跳到下一个逗号
                while (i < body.length && body[i] != ',') i++
                continue
            }
            // 这里必须只数花括号：数组体里嵌套的 `[...]` 与 `{...}` 交错出现，
            // 若把方括号也计入层级，`]}]}` 这种收尾会被切错（曾经踩过）。
            val end = matchBraceOnly(body, i)
            if (end == null) break // 截断：后面都不完整，直接放弃
            out += body.substring(i, end + 1)
            i = end + 1
        }
        return out
    }

    /** 从 `{` 起找配对 `}`；与 [matchBracket] 共用同一套栈式匹配。 */
    private fun matchBraceOnly(s: String, start: Int): Int? = matchBracket(s, start)

    // --- 第 4 步：逐条解析 + 补全 --------------------------------------------

    private fun parseOne(obj: String, index: Int, warnings: MutableList<String>): ExtractedClaim? {
        val local = ArrayList<String>()
        val statement = stringField(obj, "statement")?.trim().orEmpty()
        if (statement.isEmpty()) {
            warnings += "#${index + 1} 缺少 statement，已丢弃"
            return null
        }

        val expectedRaw = rawField(obj, "expected")
        val expected = when (expectedRaw?.trim()?.lowercase()) {
            "true" -> true
            "false" -> false
            null, "" -> {
                local += "缺少 expected，按 false 处理"
                false
            }

            else -> {
                local += "expected 取值不可识别（$expectedRaw），按 false 处理"
                false
            }
        }

        val conditions = stringArrayField(obj, "conditions")
            .filter { it.isNotBlank() }
            .ifEmpty {
                local += "缺少 conditions，已补默认条件（需人工补全）"
                listOf("未标注条件")
            }

        val evidence = stringField(obj, "evidence").orEmpty().trim()
        if (evidence.isEmpty()) local += "缺少 evidence，无法溯源"

        val errorKinds = parseErrorKinds(obj, local)
        val edges = parseEdges(obj, local)

        if (local.isNotEmpty()) {
            warnings += "#${index + 1} ${local.joinToString("；")}"
        }

        return ExtractedClaim(
            statement = statement,
            expected = expected,
            conditions = conditions,
            question = stringField(obj, "question")?.trim()?.ifEmpty { null },
            explanation = stringField(obj, "explanation")?.trim()?.ifEmpty { null },
            misconception = stringField(obj, "misconception")?.trim()?.ifEmpty { null },
            evidence = evidence,
            errorKinds = errorKinds,
            edges = edges,
            warnings = local
        )
    }

    private fun parseErrorKinds(obj: String, local: MutableList<String>): List<ExtractedErrorKind> {
        val raw = arrayBodyField(obj, "errorKinds") ?: return emptyList()
        return splitTopLevelObjects(raw).mapNotNull { item ->
            val kind = stringField(item, "kind")?.trim()?.lowercase().orEmpty()
            val why = stringField(item, "why")?.trim().orEmpty()
            when {
                kind.isEmpty() -> {
                    local += "errorKinds 有一项缺少 kind，已跳过"
                    null
                }

                kind !in ExtractedErrorKind.ALLOWED -> {
                    local += "errorKinds.kind=$kind 不在五类内，已跳过"
                    null
                }

                why.isEmpty() -> {
                    local += "errorKinds.$kind 缺少 why，已跳过"
                    null
                }

                else -> ExtractedErrorKind(kind, why)
            }
        }
    }

    private fun parseEdges(obj: String, local: MutableList<String>): List<ExtractedEdge> {
        val raw = arrayBodyField(obj, "edges") ?: return emptyList()
        return splitTopLevelObjects(raw).mapNotNull { item ->
            val type = stringField(item, "type")?.trim()?.lowercase().orEmpty()
            val kind = stringField(item, "kind")?.trim()?.lowercase().orEmpty()
            val premise = stringField(item, "premise")?.trim().orEmpty()
            val boundary = stringField(item, "boundary")?.trim().orEmpty()
            // 契约：写不出边界的边不要 —— 缺 premise / boundary 的边直接丢，不静默补
            when {
                type !in ExtractedEdge.ALLOWED_TYPES -> {
                    local += "边 type=$type 不在六类内，已丢弃"
                    null
                }

                kind !in ExtractedErrorKind.ALLOWED -> {
                    local += "边 kind=$kind 不在五类内，已丢弃"
                    null
                }

                premise.isEmpty() || boundary.isEmpty() -> {
                    local += "边缺少 premise 或 boundary（前提/边界），已丢弃"
                    null
                }

                else -> ExtractedEdge(
                    to = stringField(item, "to")?.trim().orEmpty(),
                    type = type,
                    kind = kind,
                    premise = premise,
                    boundary = boundary,
                    counterexample = stringField(item, "counterexample")?.trim()?.ifEmpty { null },
                    weight = doubleField(item, "weight")?.coerceIn(0.0, 1.0) ?: 0.5
                )
            }
        }
    }

    // --- 宽松 JSON 读取 ------------------------------------------------------

    /** 取字符串字段；值不是字符串时返回 null。 */
    internal fun stringField(obj: String, key: String): String? {
        val valueStart = valueStartOf(obj, key) ?: return null
        if (obj.getOrNull(valueStart) != '"') return null
        return readString(obj, valueStart)
    }

    /** 取原始值（字符串、布尔、数字），不做类型判断。 */
    internal fun rawField(obj: String, key: String): String? {
        val valueStart = valueStartOf(obj, key) ?: return null
        if (valueStart >= obj.length) return null
        return if (obj[valueStart] == '"') readString(obj, valueStart) else readScalar(obj, valueStart)
    }

    /** 取数组字段的**内容**（不含外层方括号）。 */
    internal fun arrayBodyField(obj: String, key: String): String? {
        val valueStart = valueStartOf(obj, key) ?: return null
        if (obj.getOrNull(valueStart) != '[') return null
        val end = matchBracket(obj, valueStart) ?: return obj.substring(valueStart + 1)
        return obj.substring(valueStart + 1, end)
    }

    /**
     * `"key"` 之后跳过冒号与空白，返回值起点下标。
     *
     * 冒号必须**紧跟 key**（中间只允许空白），不能一路往后扫：
     * 否则 `"edges"` 后面若紧跟的是别处的冒号，就会取到错误的值起点。
     */
    private fun valueStartOf(obj: String, key: String): Int? {
        val idx = findKey(obj, key) ?: return null
        // idx 指向 key 的左引号；跳过 "key"
        var i = idx + key.length + 2
        i = skipWs(obj, i)
        if (obj.getOrNull(i) != ':') return null
        return skipWs(obj, i + 1)
    }

    /** 取字符串数组；单个字符串也容忍为单元素列表。 */
    private fun stringArrayField(obj: String, key: String): List<String> {
        val valueStart = valueStartOf(obj, key) ?: return emptyList()
        val i = valueStart
        return when (obj.getOrNull(i)) {
            '[' -> {
                val end = matchBracket(obj, i) ?: (obj.length - 1)
                val body = obj.substring(i + 1, end.coerceAtLeast(i + 1))
                val out = ArrayList<String>()
                var cursor = 0
                while (cursor < body.length) {
                    cursor = skipWs(body, cursor)
                    if (cursor >= body.length) break
                    if (body[cursor] == '"') {
                        val s = readString(body, cursor)
                        if (s != null) out += s
                        cursor += stringLength(body, cursor)
                    } else {
                        // 数组里混入非字符串（对象/数字），跳过
                        while (cursor < body.length && body[cursor] != ',') cursor++
                    }
                    if (cursor < body.length && body[cursor] == ',') cursor++
                }
                out
            }

            '"' -> listOfNotNull(readString(obj, i))
            else -> emptyList()
        }
    }

    private fun doubleField(obj: String, key: String): Double? =
        rawField(obj, key)?.trim()?.toDoubleOrNull()

    /**
     * 找到 key 作为**对象键**出现的位置（返回 key 左引号的下标）。
     *
     * 判定条件有两条，缺一不可：
     *
     * 1. 字符串内容等于 `key`；
     * 2. **其后紧跟冒号**（中间允许空白）—— 这一条把「值」与「键」区分开，
     *    否则 `{"a":"edges"}` 里的值 `"edges"` 会被误认成键。
     *
     * 不限制层级：调用方拿到的往往是「整个 JSON」或「某个对象」，
     * 目标 key 可能位于最外层，也可能位于数组元素的某一层（如 `claims[0].edges`）。
     * 取**第一次**作为键出现的位置即可 —— JSON 对象里同一层不会重复 key，
     * 而跨层同名时外层的语义正是调用方要的。
     */
    private fun findKey(obj: String, key: String): Int? {
        var i = 0
        while (i < obj.length) {
            when (obj[i]) {
                '"' -> {
                    val end = endOfString(obj, i) ?: return null
                    if (obj.substring(i + 1, end) == key) {
                        val after = skipWs(obj, end + 1)
                        if (obj.getOrNull(after) == ':') return i
                    }
                    i = end + 1
                    continue
                }
            }
            i++
        }
        return null
    }

    private fun readString(s: String, start: Int): String? {
        var i = start + 1
        val sb = StringBuilder()
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' -> {
                    val nxt = s.getOrNull(i + 1) ?: return sb.toString()
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
                            val hex = if (i + 5 < s.length) s.substring(i + 2, i + 6) else null
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
        return sb.toString()
    }

    private fun stringLength(s: String, start: Int): Int {
        var i = start + 1
        while (i < s.length) {
            when (s[i]) {
                '\\' -> i += 2
                '"' -> return i - start + 1
                else -> i++
            }
        }
        return s.length - start
    }

    /** 读裸标量（true / false / 数字 / null），到逗号或右括号为止。 */
    private fun readScalar(s: String, start: Int): String {
        var i = start
        while (i < s.length && s[i] != ',' && s[i] != '}' && s[i] != ']' && !s[i].isWhitespace()) i++
        return s.substring(start, i)
    }

    private fun matchBrace(s: String, start: Int): Int? = matchBracket(s, start)

    /**
     * 从 `[` 或 `{` 起找配对括号。
     *
     * 用**显式栈**而不是两个计数器：`[{"a":[1]}]` 里方括号与花括号交错，
     * 计数器版本在「双方同时归零」的边界上容易判断错（曾经踩过）。
     * 栈版本规定：遇到开括号压栈，遇到闭括号必须与栈顶同类才出栈，
     * 栈空即为当前括号的配对位置 —— 任何不匹配都视为格式错误。
     */
    private fun matchBracket(s: String, start: Int): Int? {
        val stack = ArrayDeque<Char>()
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (c == '"') {
                i = (endOfString(s, i) ?: return null) + 1
                continue
            }
            when (c) {
                '[', '{' -> stack.addLast(c)
                ']', '}' -> {
                    val expect = if (c == ']') '[' else '{'
                    if (stack.isEmpty() || stack.removeLast() != expect) return null
                    if (stack.isEmpty()) return i
                }
            }
            i++
        }
        return null
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

    private fun String.getOrNull(index: Int): Char? = if (index in indices) this[index] else null
}
