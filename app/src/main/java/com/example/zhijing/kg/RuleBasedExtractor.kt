package com.example.zhijing.kg

/**
 * 本地规则抽取器 —— 对应 `kg/src/extract_rules.py` 的「判断题表」与「词表实体」两条路径。
 *
 * **不调用任何模型**：纯确定性规则，可复现、可 diff、零幻觉。
 * 这正好是它和云端抽取的分工：
 *
 * | | 规则抽取 | 云端抽取 |
 * |---|---|---|
 * | 产出 | 结构严格、格式可预期 | 表达丰富、但可能不合法 |
 * | 覆盖 | 只认「判断题表」这种显式结构 | 任意自然语言 |
 * | 可复现 | 是 | 否（温度 0.1 也有波动） |
 * | 成本 | 0 | 按 token 计费 |
 *
 * 为什么端上也要一份而不是调 Python：Android 上跑不了 Python 脚本，
 * 而「图谱测试」需要能**就地切换**两条生成路径做对比，不能依赖外部进程。
 *
 * 移植时保持的三条一致：
 * 1. 表头识别关键字（`VERDICT_HEADERS` / `TRUTH_HEADERS`）逐字相同；
 * 2. `_truth_of` 的判定前缀逐字相同；
 * 3. 命题 ID 用 `ClaimIds.claimId`，与 Python 的 `claim_id` 同源。
 */
object RuleBasedExtractor {

    /** 表头里出现这些词，才认为它是「判断题表」（与 Python 侧一致）。 */
    private val VERDICT_HEADERS = listOf("判断句", "说法", "命题")

    /** 真值列的表头关键字。 */
    private val TRUTH_HEADERS = listOf("是否成立", "成立吗", "对错")

    /** 边界列的表头关键字。 */
    private val BOUNDARY_HEADERS = listOf("边界", "条件")

    /** 判定一个 cell 表示「成立 / 不成立」；无法判定返回 null。 */
    fun truthOf(cell: String): Boolean? {
        val c = cell.trim()
        return when {
            c.startsWith("不成立") || c.startsWith("错") || c.startsWith("否") -> false
            c.startsWith("成立") || c.startsWith("对") || c.startsWith("是") -> true
            else -> null
        }
    }

    /**
     * 从 Markdown 文本里抽取判断题表的行。
     *
     * 只认结构明确的三列表：`| 判断句 | 是否成立 | 边界 |`。
     * 表格之外的自由文本，规则抽取**不猜** —— 猜出来的东西既不可靠也不可复现，
     * 那正是云端抽取该做的事。
     *
     * @param markdown 待抽取的全文
     * @param path 语义路径，决定命题 ID 的 slug
     * @return 抽到的命中行 + 跳过原因
     */
    fun extractTableClaims(markdown: String, path: String): Result {
        val claims = ArrayList<ExtractedClaim>()
        val warnings = ArrayList<String>()
        var skipped = 0

        val lines = markdown.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!line.trimStart().startsWith("|")) {
                i++
                continue
            }
            // 收集连续表格块
            val block = ArrayList<String>()
            while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                block += lines[i]
                i++
            }
            val parsed = parseTable(block) ?: continue
            val header = parsed.header
            if (VERDICT_HEADERS.none { h -> header.any { it.contains(h) } }) continue
            if (TRUTH_HEADERS.none { h -> header.any { it.contains(h) } }) continue

            val idxStmt = header.indexOfFirst { h -> VERDICT_HEADERS.any { h.contains(it) } }
            val idxTruth = header.indexOfFirst { h -> TRUTH_HEADERS.any { h.contains(it) } }
            val idxBound = header.indexOfFirst { h -> BOUNDARY_HEADERS.any { h.contains(it) } }
            if (idxStmt < 0 || idxTruth < 0) continue

            parsed.rows.forEach { row ->
                val statement = row.getOrNull(idxStmt)?.trim().orEmpty()
                val expected = truthOf(row.getOrNull(idxTruth).orEmpty())
                if (statement.isEmpty() || expected == null) {
                    skipped++
                    return@forEach
                }
                val boundary = if (idxBound >= 0) row.getOrNull(idxBound)?.trim().orEmpty() else ""
                claims += ExtractedClaim(
                    statement = statement,
                    expected = expected,
                    conditions = listOf(
                        if (boundary.isNotEmpty()) "边界：$boundary" else "边界：未在表格中标注"
                    ),
                    evidence = "判断题表：$statement —— ${row.getOrNull(idxTruth)?.trim().orEmpty()}",
                    errorKinds = listOf(
                        ExtractedErrorKind(
                            kind = "semantic",
                            why = "表格判断题未标注错点类型，按语义依赖处理"
                        )
                    )
                )
            }
        }

        if (skipped > 0) warnings += "判断题表跳过 $skipped 行（无法判定成立与否或缺少判断句）"
        if (claims.isEmpty() && warnings.isEmpty()) {
            warnings += "未找到判断题表：规则抽取只认「| 判断句 | 是否成立 | 边界 |」这类显式表格"
        }
        return Result(claims = claims, warnings = warnings)
    }

    /**
     * 抽取文本里出现的领域词（实体名）。
     *
     * 用于「本地路径能发现哪些概念」，供对比测试时展示覆盖率差异：
     * 规则抽取看得见的是词表里的词，云端看得见的是语义。
     */
    fun extractEntities(markdown: String): List<String> {
        val found = LinkedHashMap<String, String>() // canonical -> 原文命中写法
        TERM_LEXICON.forEach { (canonical, aliases) ->
            val hit = (listOf(canonical) + aliases).firstOrNull { term ->
                markdown.contains(term, ignoreCase = true)
            }
            if (hit != null) found[canonical] = hit
        }
        return found.keys.toList()
    }

    // --- Markdown 表格解析 ----------------------------------------------------

    private data class ParsedTable(val header: List<String>, val rows: List<List<String>>)

    /**
     * 解析一个 Markdown 表格块。
     *
     * @return 表头与数据行；不是合法表格（少于两行、没有分隔行）返回 null
     */
    private fun parseTable(block: List<String>): ParsedTable? {
        if (block.size < 2) return null
        val header = splitRow(block[0])
        // 第二行必须是分隔行（| --- | :---: |）
        if (!isSeparator(block[1])) return null
        val rows = block.drop(2)
            .filter { it.isNotBlank() }
            .map { splitRow(it) }
        return ParsedTable(header, rows)
    }

    private fun isSeparator(line: String): Boolean {
        val cells = splitRow(line)
        return cells.isNotEmpty() && cells.all { cell ->
            val c = cell.trim()
            c.isNotEmpty() && c.all { it == '-' || it == ':' }
        }
    }

    /** 按 `|` 切分一行，去掉首尾空单元（Markdown 表格以 `|` 开头结尾）。 */
    private fun splitRow(line: String): List<String> {
        val trimmed = line.trim()
        val body = if (trimmed.startsWith("|")) trimmed.substring(1) else trimmed
        val out = ArrayList<String>()
        val current = StringBuilder()
        var escaped = false
        for (ch in body) {
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                }

                ch == '\\' -> escaped = true
                ch == '|' -> {
                    out += current.toString()
                    current.setLength(0)
                }

                else -> current.append(ch)
            }
        }
        // 末尾的残留：如果行以 | 结尾，最后一段是空串，丢弃
        val tail = current.toString()
        if (tail.isNotBlank() || !body.trimEnd().endsWith("|")) out += tail
        return out
    }

    /** 一次规则抽取的结果。 */
    data class Result(
        val claims: List<ExtractedClaim>,
        val warnings: List<String> = emptyList()
    )

    /**
     * 领域词表，与 `kg/src/kg_model.py` 的 `TERM_LEXICON` 保持一致。
     *
     * 只保留词条与别名（kind 在端上不参与抽取，故省略）。
     */
    private val TERM_LEXICON: Map<String, List<String>> = linkedMapOf(
        "Linear" to listOf("线性层", "全连接层", "FC", "Dense"),
        "ReLU" to listOf("线性整流", "Rectified Linear Unit"),
        "GELU" to emptyList(),
        "Sigmoid" to emptyList(),
        "Softmax" to listOf("归一化指数函数"),
        "CrossEntropy" to listOf("交叉熵", "交叉熵损失", "CE"),
        "MSE" to listOf("均方误差"),
        "LayerNorm" to listOf("层归一化", "LN"),
        "BatchNorm" to listOf("批归一化", "BN"),
        "Dropout" to listOf("随机失活"),
        "残差连接" to listOf("残差", "捷径连接", "skip connection", "Residual"),
        "缩放点积注意力" to listOf("scaled dot-product attention", "点积注意力"),
        "多头注意力" to listOf("Multi-Head Attention", "MHA"),
        "自注意力" to listOf("Self-Attention"),
        "因果掩码" to listOf("causal mask", "下三角掩码"),
        "位置编码" to listOf("positional encoding", "位置嵌入"),
        "logits" to listOf("未归一化得分"),
        "batch" to listOf("批", "批大小", "batch size"),
        "张量形状" to listOf("shape", "形状"),
        "学习率" to listOf("learning rate", "lr"),
        "Adam" to listOf("AdamW"),
        "Softmax 饱和" to listOf("饱和"),
        "梯度消失" to emptyList(),
        "Pre-LN" to listOf("Pre-LayerNorm"),
        "Post-LN" to listOf("Post-LayerNorm")
    )
}
