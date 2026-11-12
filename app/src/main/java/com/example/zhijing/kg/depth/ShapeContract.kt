package com.example.zhijing.kg.depth

/**
 * 条件的形式化契约。
 *
 * ## 为什么需要形式化
 *
 * 深度图谱的原子是「条件—机制—反事实」。用户拨动一条条件后，
 * 系统必须**立刻**说出「命题现在会怎样」——不能调用大模型，
 * 否则答案不可复现、有延迟、而且用户学到的是模型的偏好而非物理事实。
 *
 * 因此每条可拨动的条件都要配一份**可执行的契约**：
 * 运行时本地求值，结果是真值，不是推测。
 *
 * ## 为什么用符号而不是具体数值
 *
 * 一条契约要能回答任意干预。若把 `in_features = 4` 写死，
 * 那「改成 5」就得另写一条契约。改用符号：
 *
 * ```
 * lhs: [N, in_features]
 * rhs: [out_features, in_features]   (转置后)
 * requires: lhs.last == rhs.last
 * ```
 *
 * 于是「把 in_features 改成 5」只需把符号绑定到 5 再求值，
 * 一条契约覆盖整族干预。
 */
data class ShapeContract(
    /** 契约版本，便于将来演进。 */
    val version: Int = 1,
    /** 符号表：变量名 → 当前值。干预就是改这里的绑定。 */
    val symbols: Map<String, Int>,
    /** 要做的运算。求值它的形状，并检查 constraints。 */
    val op: TensorOp,
    /** 命题成立所依赖的等式/不等式。全部满足才算「成立」。 */
    val constraints: List<Constraint> = emptyList(),
)

/** 参与运算的张量算子。只收录本主题真正用到的那几种。 */
sealed class TensorOp {
    /** 该算子涉及的所有维度名（用于收集符号）。 */
    abstract val allDims: List<String>

    /**
     * 矩阵乘：`lhs @ rhs.T`（Linear 层的形式）。
     *
     * 要求 `lhs.last == rhs.last` —— 因为 rhs 会被转置，
     * 内维是 rhs 的**最后一维**。
     */
    data class MatMul(
        val lhs: List<String>,
        val rhs: List<String>,
        /** rhs 是否转置（Linear 层总是 true）。 */
        val rhsTransposed: Boolean = true,
    ) : TensorOp() {
        override val allDims: List<String> get() = lhs + rhs
    }

    /** 逐元素运算（ReLU / 激活）：输出形状与输入完全一致。 */
    data class ElementWise(
        val input: List<String>,
    ) : TensorOp() {
        override val allDims: List<String> get() = input
    }

    /** Softmax：沿某一轴归一化，要求该轴上的元素求和为 1。 */
    data class Softmax(
        val input: List<String>,
        /** 沿第几轴（0-based，负数表示从末尾数）。 */
        val axis: Int,
    ) : TensorOp() {
        override val allDims: List<String> get() = input
    }

    /** 归约（sum / mean）：沿某轴把维度消掉。 */
    data class Reduce(
        val input: List<String>,
        val axis: Int,
        val kind: String = "sum",
    ) : TensorOp() {
        override val allDims: List<String> get() = input
    }

    /**
     * softmax 之后沿某轴求和。
     *
     * 用来表达「沿类别维求和等于 1」这类统计条件。它本质是**轴是否对齐**的结构事实，
     * 不需要浮点运算，因此仍由 [ContractEvaluator] 确定性判定，不引入浮点引擎。
     *
     * 典型用法：
     * - 沿 softmax 的归一化轴求和 → 成立（和为 1）；
     * - 沿 batch 等其他轴求和 → 不成立（每个位置各是一个概率，逐个加起来不是 1）。
     */
    data class SoftmaxSum(
        val input: List<String>,
        /** softmax 归一化的轴（0-based，负数从末尾数）。 */
        val softmaxAxis: Int,
        /** 求和的轴。 */
        val sumAxis: Int,
    ) : TensorOp() {
        override val allDims: List<String> get() = input
    }
}

/**
 * 命题成立所需的约束。
 *
 * 只有这几类 —— 再多就无法自动判定了。刻意保持窄：
 * **能判的写进来，不能判的走人工预设干预**（见 [Intervention]）。
 */
sealed class Constraint {
    abstract val describe: String

    /** `a == b`，两边都是维度表达式。 */
    data class DimEquals(
        val left: DimExpr,
        val right: DimExpr,
        override val describe: String,
    ) : Constraint()

    /** `a != b`。用于「改了某个符号后不再相等」这类判定。 */
    data class DimNotEquals(
        val left: DimExpr,
        val right: DimExpr,
        override val describe: String,
    ) : Constraint()

    /** `a <= b`（或 >=，用 [inclusive] 控制）。用于范围类条件。 */
    data class DimCompare(
        val left: DimExpr,
        val right: DimExpr,
        /** true = `<=`，false = `<`。 */
        val lessOrEqual: Boolean = true,
        override val describe: String,
    ) : Constraint()

    /** 某个符号必须为正数（如 in_features >= 1）。 */
    data class Positive(
        val symbol: String,
        override val describe: String,
    ) : Constraint()
}

/**
 * 一个维度表达式。
 *
 * 只支持「符号」与「符号 + 常数」两种形态 —— 足以表达
 * `last == in_features`、`N == batch` 这类真实条件，
 * 又不至于变成一个通用算术引擎。
 */
sealed class DimExpr {
    /** 一个符号，或一个常量。`offset` 用于 `in_features + 1` 这种干预。 */
    data class Sym(val name: String, val offset: Int = 0) : DimExpr()
    data class Const(val value: Int) : DimExpr()

    companion object {
        fun sym(name: String, offset: Int = 0) = if (name.all { it.isDigit() }) {
            Const(name.toInt() + offset)
        } else {
            Sym(name, offset)
        }
    }
}

/** 沿轴归一化：负数从末尾数。 */
internal fun normalizeAxis(axis: Int, rank: Int): Int =
    if (axis < 0) rank + axis else axis
