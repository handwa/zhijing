package com.example.zhijing.kg.depth

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用**真实命题**验证契约与求值器。
 *
 * 这组测试回答一个决定性问题：现有 35 条命题能不能写成可执行契约？
 * 如果连最典型的形状类命题都写不出来，那「模型生成契约」这条路就不成立。
 *
 * 命题取自 `graph.json`，一字不改。
 */
class RealContractTest {

    /**
     * 命题：clm.arch.ffn.linear-in-dim.4a3e
     * 「Linear 层要求输入张量的最后一维等于权重的输入维度，否则矩阵乘无法进行。」
     *
     * 条件：x.shape[-1] == W.shape[1]
     *
     * 形式化：Linear 做的是 `x @ W.T`。
     * x = [N, in_features]，W = [out_features, in_features]。
     * 转置后内维是 in_features，两端天然相等 —— 所以「相等」不是自动满足的，
     * 而是**由共享同一个符号**表达的。干预 in_features 会同时改两边，
     * 矩阵乘依然成立。
     *
     * 因此这条命题真正的可拨点不是「改 in_features」，而是
     * **「让输入的最后一维不再等于权重的输入维」** —— 也就是把两者拆成两个符号。
     * 这正是「前导维自由、最后一维不自由」的分界。
     */
    private fun linearInDim() = ShapeContract(
        symbols = mapOf(
            "N" to 8,           // batch
            "in_features" to 4,
            "out_features" to 6,
        ),
        op = TensorOp.MatMul(
            lhs = listOf("N", "in_features"),
            rhs = listOf("out_features", "in_features"),
            rhsTransposed = true,
        ),
        constraints = listOf(
            Constraint.DimEquals(
                left = DimExpr.Sym("in_features"),
                right = DimExpr.Sym("in_features"),
                describe = "x.shape[-1] == W.shape[1]",
            )
        ),
    )

    @Test
    fun `原状态下形状约束成立`() {
        val r = ContractEvaluator.validate(linearInDim())
        assertTrue("契约应自洽且成立，实际 $r", r is ContractEvaluator.Result.Holds)
    }

    @Test
    fun `改 batch 不影响成立`() {
        // 前导维自由 —— 这是命题明说的
        val r = ContractEvaluator.evaluate(
            linearInDim(),
            overrides = mapOf("N" to 160)
        )
        assertTrue("改 batch 后仍应成立，实际 $r", r is ContractEvaluator.Result.Holds)
    }

    /**
     * 这条是关键：把输入维度与权重维度**拆开**，就能模拟「不相等」。
     *
     * 用户把输入最后一维从 4 改成 5，而权重仍是 4 —— 矩阵乘内维不等。
     */
    @Test
    fun `输入最后一维与权重不等时 矩阵乘失败`() {
        // 用两个符号分别代表两端，制造「不等」
        val broken = ShapeContract(
            symbols = mapOf(
                "N" to 8,
                "in_x" to 5,      // 输入最后一维 = 5
                "in_w" to 4,      // 权重最后一维 = 4
                "out_features" to 6,
            ),
            op = TensorOp.MatMul(
                lhs = listOf("N", "in_x"),
                rhs = listOf("out_features", "in_w"),
                rhsTransposed = true,
            ),
            constraints = listOf(
                Constraint.DimEquals(
                    DimExpr.Sym("in_x"), DimExpr.Sym("in_w"),
                    "输入最后一维必须等于权重最后一维"
                )
            ),
        )
        val r = ContractEvaluator.evaluate(broken)
        assertTrue("内维不等应判为失败，实际 $r", r is ContractEvaluator.Result.Fails)
        val reason = (r as ContractEvaluator.Result.Fails).reason
        assertTrue("失败原因应讲清内外维：$reason", reason.contains("5") && reason.contains("4"))
    }

    /**
     * 命题：clm.arch.ffn.relu-shape.6092
     * 「ReLU 逐元素作用，输出形状与输入完全一致。」
     */
    private fun reluShape() = ShapeContract(
        symbols = mapOf("N" to 8, "d" to 16),
        op = TensorOp.ElementWise(input = listOf("N", "d")),
        constraints = listOf(
            Constraint.DimEquals(
                DimExpr.Sym("N"), DimExpr.Sym("N"),
                "输出首维与输入一致"
            )
        ),
    )

    @Test
    fun `逐元素运算成立`() {
        assertTrue(
            ContractEvaluator.validate(reluShape()) is ContractEvaluator.Result.Holds
        )
    }

    @Test
    fun `逐元素运算没有可拨动的形状条件`() {
        // 逐元素运算的形状条件是恒等式，拨不动 —— 这类条件不该给可拨点。
        // 命题的可拨点应该落在数值上（比如负区间梯度为 0），那需要另一类契约。
        // 这个测试锁定「形状契约无法覆盖全部条件」这个事实。
        val r = ContractEvaluator.evaluate(reluShape(), overrides = mapOf("d" to 32))
        assertTrue("改维度后仍成立（形状跟随输入）", r is ContractEvaluator.Result.Holds)
    }

    /**
     * 命题：clm.arch.ffn.softmax-sum.a505
     * 「Softmax 的输出沿类别维求和为 1。」
     *
     * 形式化：`sum(softmax(x, axis=-1), axis=-1)` 的形状应消掉最后一维。
     * 若沿错轴归约，得到的不是「每行和为 1」。
     */
    @Test
    fun `沿最后一轴归约消掉类别维`() {
        val c = ShapeContract(
            symbols = mapOf("N" to 8, "C" to 10),
            op = TensorOp.Reduce(
                input = listOf("N", "C"),
                axis = -1,
                kind = "sum",
            ),
            constraints = listOf(
                Constraint.Positive("C", "类别维必须为正"),
            ),
        )
        assertTrue(
            "契约应成立",
            ContractEvaluator.validate(c) is ContractEvaluator.Result.Holds
        )
    }

    @Test
    fun `沿错轴归约轴越界时失败`() {
        val c = ShapeContract(
            symbols = mapOf("N" to 8, "C" to 10),
            op = TensorOp.Reduce(input = listOf("N", "C"), axis = 5, kind = "sum"),
        )
        val r = ContractEvaluator.evaluate(c)
        assertTrue("轴越界应判失败，实际 $r", r is ContractEvaluator.Result.Fails)
    }

    /* ------------------------------------------- 入库闸门 */

    @Test
    fun `缺符号绑定的契约被拒绝`() {
        val bad = ShapeContract(
            symbols = mapOf("N" to 8),   // 缺 in_features
            op = TensorOp.MatMul(
                lhs = listOf("N", "in_features"),
                rhs = listOf("out_features", "in_features"),
            ),
        )
        val r = ContractEvaluator.validate(bad)
        assertTrue("应判为非法，实际 $r", r is ContractEvaluator.Result.Invalid)
        assertTrue(
            (r as ContractEvaluator.Result.Invalid).reason.contains("in_features")
        )
    }

    @Test
    fun `原状态就不成立的契约被拒绝`() {
        // 模型写错契约的典型形态：内维一开始就不等
        val bad = ShapeContract(
            symbols = mapOf("N" to 8, "in_x" to 5, "in_w" to 4, "out_features" to 6),
            op = TensorOp.MatMul(
                lhs = listOf("N", "in_x"),
                rhs = listOf("out_features", "in_w"),
            ),
            constraints = listOf(
                Constraint.DimEquals(DimExpr.Sym("in_x"), DimExpr.Sym("in_w"), "内维相等")
            ),
        )
        val r = ContractEvaluator.validate(bad)
        assertTrue("应判为非法（与命题不符），实际 $r", r is ContractEvaluator.Result.Invalid)
    }

    @Test
    fun `干预引用不存在的符号被拒绝`() {
        val r = ContractEvaluator.evaluate(
            linearInDim(),
            overrides = mapOf("not_a_symbol" to 3)
        )
        assertTrue("应判非法，实际 $r", r is ContractEvaluator.Result.Invalid)
    }

    @Test
    fun `把符号干预成 0 被拒绝`() {
        val r = ContractEvaluator.evaluate(
            linearInDim(),
            overrides = mapOf("in_features" to 0)
        )
        assertTrue("形状不能为 0，实际 $r", r is ContractEvaluator.Result.Invalid)
    }
}
