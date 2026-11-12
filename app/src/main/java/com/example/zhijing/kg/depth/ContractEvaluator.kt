package com.example.zhijing.kg.depth

/**
 * 契约求值器 —— 深度图谱的**真值裁判**。
 *
 * ## 职责
 *
 * 1. **验证契约自洽**：模型生成的契约可能是错的（引用了不存在的符号、
 *    约束写法与算子不匹配）。这里是入库闸门，不自洽就拒绝。
 * 2. **计算干预后果**：用户拨动条件 → 改符号绑定 → 重新求值 →
 *    得出「仍成立 / 弱化 / 变成另一命题 / 不成立」。
 *
 * ## 为什么它是唯一裁判
 *
 * 大模型不能当裁判：同一个条件拨两次可能给不同答案，用户学到的是模型的偏好。
 * 求值器是确定性的 —— 同样的契约 + 同样的干预，永远同一个结果。
 *
 * ## 局限（必须诚实）
 *
 * 它只能判**形状与维度**层面的约束。数值性质（如「梯度为 0」）与语义性质
 * （如「含义变了」）判不了，那些走人工预设干预。
 */
object ContractEvaluator {

    /** 求值结果。 */
    sealed class Result {
        /** 命题成立：所有约束都满足，算子形状也合法。 */
        data class Holds(val note: String = "") : Result()

        /**
         * 不成立：某个约束被违反，或算子本身无法执行（如矩阵乘内维不等）。
         *
         * [reason] 必须能直接讲给用户听 —— 它是「撞错」时最关键的一句话。
         */
        data class Fails(val reason: String) : Result()

        /** 契约本身有问题（入库应拒绝）。 */
        data class Invalid(val reason: String) : Result()
    }

    /**
     * 在当前符号绑定下求值。
     *
     * @param contract 契约
     * @param overrides 干预：把某些符号改掉。空 = 未拨动，检查命题是否成立。
     */
    fun evaluate(
        contract: ShapeContract,
        overrides: Map<String, Int> = emptyMap()
    ): Result {
        // 1) 符号表：契约自带的 + 干预覆盖的
        val env = HashMap(contract.symbols)
        overrides.forEach { (k, v) ->
            if (!env.containsKey(k)) {
                return Result.Invalid("干预引用了不存在的符号：$k")
            }
            if (v <= 0) {
                return Result.Invalid("符号 $k 被干预成非正值：$v")
            }
            env[k] = v
        }

        // 2) 算子自身的合法性（形状层面）
        val opCheck = checkOpShape(contract.op, env)
        if (opCheck != null) return Result.Fails(opCheck)

        // 3) 逐条检查约束
        contract.constraints.forEach { c ->
            when (val r = checkConstraint(c, env)) {
                is ConstraintCheck.Violated -> return Result.Fails(r.reason)
                is ConstraintCheck.Invalid -> return Result.Invalid(r.reason)
                ConstraintCheck.Satisfied -> Unit
            }
        }

        return Result.Holds()
    }

    /* ------------------------------------------------------ 算子形状 */

    /**
     * 检查算子在当前符号绑定下能否执行。
     *
     * @return 失败原因；null 表示可以执行。
     */
    private fun checkOpShape(op: TensorOp, env: Map<String, Int>): String? {
        return when (op) {
            is TensorOp.MatMul -> {
                val lhs = op.lhs.map { dimValue(it, env) }
                val rhs = op.rhs.map { dimValue(it, env) }
                when {
                    lhs.any { it == null } || rhs.any { it == null } ->
                        "未定义的维度：${op.allDims}"

                    lhs.size < 2 || rhs.size < 2 ->
                        "矩阵乘要求至少二维"

                    else -> {
                        // lhs @ rhs.T：内维是 lhs.last 与 rhs.last
                        val innerL = lhs.last()
                        val innerR = rhs.last()
                        if (innerL != innerR) {
                            // 这句话会直接展示给用户，要具体
                            "矩阵乘的内维不等：输入最后一维是 $innerL，" +
                                "权重最后一维是 $innerR —— 无法求和，运算未定义"
                        } else {
                            null
                        }
                    }
                }
            }

            is TensorOp.Softmax -> {
                val rank = op.input.size
                val axis = normalizeAxis(op.axis, rank)
                if (axis !in 0 until rank) {
                    "softmax 的轴越界：axis=${op.axis}, rank=$rank"
                } else null
            }

            is TensorOp.Reduce -> {
                val rank = op.input.size
                val axis = normalizeAxis(op.axis, rank)
                if (axis !in 0 until rank) {
                    "归约的轴越界：axis=${op.axis}, rank=$rank"
                } else null
            }

            is TensorOp.SoftmaxSum -> {
                val rank = op.input.size
                if (rank == 0) {
                    "softmax 的输入没有维度"
                } else {
                    val softmaxAxis = normalizeAxis(op.softmaxAxis, rank)
                    val sumAxis = normalizeAxis(op.sumAxis, rank)
                    when {
                        softmaxAxis !in 0 until rank ->
                            "softmax 的轴越界：axis=${op.softmaxAxis}, rank=$rank"

                        sumAxis !in 0 until rank ->
                            "求和的轴越界：axis=${op.sumAxis}, rank=$rank"

                        // 只有沿归一化轴求和才恒等于 1；沿其他轴求和是「若干概率相加」，
                        // 结果在 0..类别数之间，不能断言等于 1。
                        sumAxis != softmaxAxis ->
                            "沿轴 $sumAxis 求和不是 1：softmax 只在轴 $softmaxAxis 上归一化，" +
                                "沿其他轴求和得到的是多个概率之和"

                        else -> null
                    }
                }
            }

            is TensorOp.ElementWise -> null
        }
    }

    /* ------------------------------------------------------ 约束检查 */

    private sealed class ConstraintCheck {
        object Satisfied : ConstraintCheck()
        data class Violated(val reason: String) : ConstraintCheck()
        data class Invalid(val reason: String) : ConstraintCheck()
    }

    private fun checkConstraint(c: Constraint, env: Map<String, Int>): ConstraintCheck =
        when (c) {
            is Constraint.DimEquals -> {
                val l = evalExpr(c.left, env) ?: return ConstraintCheck.Invalid("无法求值：${c.describe}")
                val r = evalExpr(c.right, env) ?: return ConstraintCheck.Invalid("无法求值：${c.describe}")
                if (l == r) ConstraintCheck.Satisfied
                else ConstraintCheck.Violated("${c.describe} 不满足：左边是 $l，右边是 $r")
            }

            is Constraint.DimNotEquals -> {
                val l = evalExpr(c.left, env) ?: return ConstraintCheck.Invalid("无法求值：${c.describe}")
                val r = evalExpr(c.right, env) ?: return ConstraintCheck.Invalid("无法求值：${c.describe}")
                if (l != r) ConstraintCheck.Satisfied
                else ConstraintCheck.Violated("${c.describe} 不满足：两边都是 $l，本应不同")
            }

            is Constraint.DimCompare -> {
                val l = evalExpr(c.left, env) ?: return ConstraintCheck.Invalid("无法求值：${c.describe}")
                val r = evalExpr(c.right, env) ?: return ConstraintCheck.Invalid("无法求值：${c.describe}")
                val ok = if (c.lessOrEqual) l <= r else l < r
                if (ok) ConstraintCheck.Satisfied
                else ConstraintCheck.Violated(
                    "${c.describe} 不满足：$l ${if (c.lessOrEqual) ">" else ">="} $r"
                )
            }

            is Constraint.Positive -> {
                val v = env[c.symbol] ?: return ConstraintCheck.Invalid("未定义符号：${c.symbol}")
                if (v > 0) ConstraintCheck.Satisfied
                else ConstraintCheck.Violated("${c.describe} 不满足：${c.symbol} = $v，应为正数")
            }
        }

    /* ------------------------------------------------------ 求值工具 */

    /** 把一个维度名解析成数值；未定义返回 null。 */
    private fun dimValue(name: String, env: Map<String, Int>): Int? =
        name.toIntOrNull() ?: env[name]

    private fun evalExpr(expr: DimExpr, env: Map<String, Int>): Int? =
        when (expr) {
            is DimExpr.Const -> expr.value
            is DimExpr.Sym -> env[expr.name]?.plus(expr.offset)
        }

    /**
     * 入库闸门：契约是否自洽。
     *
     * 校验项：
     * - 所有引用的符号都已绑定
     * - 符号值都是正数（形状不可能是 0 或负）
     * - 算子形状合法
     * - 未拨动时命题**应当成立** —— 如果原契约都不成立，说明契约写错了
     *
     * 最后一条是关键：模型可能写出一个「原状态下就不成立」的契约，
     * 那它描述的不是这条命题。
     */
    fun validate(contract: ShapeContract): Result {
        val unbound = collectSymbols(contract).filterNot { contract.symbols.containsKey(it) }
        if (unbound.isNotEmpty()) {
            return Result.Invalid("契约缺少符号绑定：$unbound")
        }
        contract.symbols.forEach { (k, v) ->
            if (v <= 0) return Result.Invalid("符号 $k 的值为 $v，形状必须为正")
        }
        return when (val r = evaluate(contract)) {
            is Result.Holds -> r
            is Result.Fails -> Result.Invalid("原状态下命题就不成立，契约与命题不符：${r.reason}")
            is Result.Invalid -> r
        }
    }

    /** 收集契约里引用到的所有符号名。 */
    private fun collectSymbols(c: ShapeContract): Set<String> {
        val out = LinkedHashSet<String>()
        c.op.allDims.forEach { if (it.toIntOrNull() == null) out += it }
        fun fromExpr(e: DimExpr) {
            if (e is DimExpr.Sym) out += e.name
        }
        c.constraints.forEach { ct ->
            when (ct) {
                is Constraint.DimEquals -> { fromExpr(ct.left); fromExpr(ct.right) }
                is Constraint.DimNotEquals -> { fromExpr(ct.left); fromExpr(ct.right) }
                is Constraint.DimCompare -> { fromExpr(ct.left); fromExpr(ct.right) }
                is Constraint.Positive -> out += ct.symbol
            }
        }
        return out
    }
}
