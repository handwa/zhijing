package com.example.zhijing.kg.depth

import com.example.zhijing.kg.*

/**
 * 出厂图谱里 `triples` 字段的解析。
 *
 * ## 为什么单独一个文件
 *
 * `GraphAssembler` 已经承担了 outline / cards / claims / edges 的解析，再塞进三元组与契约的
 * 递归结构会让它难以维护。这里只做「弱类型 JSON 树 → 强类型模型」，不做语义校验：
 * 语义门槛由 [ConditionTriple.isValid] 在消费侧把关，契约自洽性由
 * [ContractEvaluator.validate] 把关。
 *
 * ## 降级策略（硬要求）
 *
 * 字段缺失、类型错误、单条数据写坏，**一律跳过而不是抛异常**：
 * 旧版 `graph.json` 没有 `triples`，图谱页必须照常打开，只是没有可拨条件。
 */
internal object TripleParser {

    /** 解析顶层 `triples`；字段缺失或不是数组时返回空列表。 */
    fun parse(root: Map<String, Any?>): List<ConditionTriple> =
        root.list("triples").mapNotNull { parseTriple(it.asMap()) }

    /* ------------------------------------------------------------ 三元组 */

    private fun parseTriple(m: Map<String, Any?>): ConditionTriple? {
        val id = m.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val claimId = m.str("claimId")?.takeIf { it.isNotBlank() } ?: return null
        val interventions = m.list("interventions").mapNotNull { parseIntervention(it.asMap()) }

        return ConditionTriple(
            id = id,
            claimId = claimId,
            cardId = m.strOrEmpty("cardId"),
            statement = m.strOrEmpty("statement"),
            kind = ConditionKind.fromWire(m.str("kind")),
            mechanism = m.strOrEmpty("mechanism"),
            interventions = interventions,
            // 没写 computable 时按「有没有契约」推断，避免人工漏填导致能力被错判
            computable = m.bool("computable") ?: interventions.any { it.contract != null },
        )
    }

    private fun parseIntervention(m: Map<String, Any?>): Intervention? {
        val id = m.str("id")?.takeIf { it.isNotBlank() } ?: return null

        return Intervention(
            id = id,
            action = m.strOrEmpty("action"),
            overrides = m.obj("overrides").mapNotNull { (key, value) ->
                value.asInt()?.let { key to it }
            }.toMap(),
            contract = m.obj("contract").takeIf { it.isNotEmpty() }?.let(::parseContract),
            outcome = Outcome.fromWire(m.str("outcome")) ?: Outcome.HOLDS,
            consequence = m.strOrEmpty("consequence"),
            source = TruthSource.fromWire(m.str("source")),
            misjudgements = m.list("misjudgements").mapNotNull { parseMisjudgement(it.asMap()) },
        )
    }

    private fun parseMisjudgement(m: Map<String, Any?>): Misjudgement? {
        val predicted = Outcome.fromWire(m.str("predicted")) ?: return null
        return Misjudgement(
            predicted = predicted,
            why = m.strOrEmpty("why"),
            correction = m.strOrEmpty("correction"),
        )
    }

    /* -------------------------------------------------------------- 契约 */

    private fun parseContract(m: Map<String, Any?>): ShapeContract? {
        val symbols = m.obj("symbols").mapNotNull { (name, value) ->
            value.asInt()?.let { name to it }
        }.toMap()
        if (symbols.isEmpty()) return null

        val op = parseOp(m.obj("op")) ?: return null
        val constraints = m.list("constraints").mapNotNull { parseConstraint(it.asMap()) }
        return ShapeContract(symbols = symbols, op = op, constraints = constraints)
    }

    private fun parseOp(m: Map<String, Any?>): TensorOp? = when (m.str("kind")) {
        "matmul" -> {
            val lhs = m.strings("lhs")
            val rhs = m.strings("rhs")
            if (lhs.size < 2 || rhs.size < 2) null
            else TensorOp.MatMul(lhs = lhs, rhs = rhs, rhsTransposed = m.bool("rhsTransposed") ?: true)
        }

        "elementWise" -> m.strings("input").takeIf { it.isNotEmpty() }?.let { TensorOp.ElementWise(it) }

        "softmax" -> m.strings("input").takeIf { it.isNotEmpty() }
            ?.let { TensorOp.Softmax(input = it, axis = m.int("axis") ?: -1) }

        "reduce" -> m.strings("input").takeIf { it.isNotEmpty() }?.let {
            TensorOp.Reduce(
                input = it,
                axis = m.int("axis") ?: 0,
                kind = m.str("reduceKind") ?: "sum",
            )
        }

        "softmaxSum" -> m.strings("input").takeIf { it.isNotEmpty() }?.let {
            TensorOp.SoftmaxSum(
                input = it,
                softmaxAxis = m.int("softmaxAxis") ?: -1,
                sumAxis = m.int("sumAxis") ?: 0,
            )
        }

        else -> null
    }

    private fun parseConstraint(m: Map<String, Any?>): Constraint? {
        val describe = m.strOrEmpty("describe")
        return when (m.str("kind")) {
            "equals" -> Constraint.DimEquals(
                left = parseDimExpr(m.strOrEmpty("left")),
                right = parseDimExpr(m.strOrEmpty("right")),
                describe = describe.ifBlank { "两维必须相等" },
            )

            "notEquals" -> Constraint.DimNotEquals(
                left = parseDimExpr(m.strOrEmpty("left")),
                right = parseDimExpr(m.strOrEmpty("right")),
                describe = describe.ifBlank { "两维必须不同" },
            )

            "compare" -> Constraint.DimCompare(
                left = parseDimExpr(m.strOrEmpty("left")),
                right = parseDimExpr(m.strOrEmpty("right")),
                lessOrEqual = m.bool("lessOrEqual") ?: true,
                describe = describe.ifBlank { "两维需要满足大小关系" },
            )

            "positive" -> {
                val symbol = m.strOrEmpty("symbol")
                if (symbol.isBlank()) null
                else Constraint.Positive(
                    symbol = symbol,
                    describe = describe.ifBlank { "符号 $symbol 必须为正" },
                )
            }

            else -> null
        }
    }

    /** 维度表达式：纯数字 → 常量；`sym` / `sym+1` / `sym-2` → 带偏移的符号。 */
    private val DIM_EXPR = Regex("^([A-Za-z_][A-Za-z0-9_.]*)([+-]\\d+)?$")

    private fun parseDimExpr(raw: String): DimExpr {
        val text = raw.trim()
        text.toIntOrNull()?.let { return DimExpr.Const(it) }

        val match = DIM_EXPR.matchEntire(text) ?: return DimExpr.Const(0)
        val offset = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        return DimExpr.Sym(name = match.groupValues[1], offset = offset)
    }
}
