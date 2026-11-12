package com.example.zhijing.kg.depth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `SoftmaxSum` 契约的判定测试。
 *
 * 核心不变量：只有**沿 softmax 的归一化轴**求和才恒等于 1；
 * 沿其他轴求和是「若干概率相加」，不能断言等于 1。
 * 这条判定必须确定性，不依赖模型。
 */
class SoftmaxSumTest {

    private fun contract(
        softmaxAxis: Int,
        sumAxis: Int,
        input: List<String> = listOf("N", "C"),
    ) = ShapeContract(
        symbols = mapOf("N" to 8, "C" to 10),
        op = TensorOp.SoftmaxSum(
            input = input,
            softmaxAxis = softmaxAxis,
            sumAxis = sumAxis,
        ),
    )

    /* -------------------------------------------------------------- 成立 */

    @Test
    fun sumAlongSoftmaxAxisHolds() {
        val result = ContractEvaluator.evaluate(contract(softmaxAxis = 1, sumAxis = 1))

        assertTrue("沿归一化轴求和应成立，实际：$result", result is ContractEvaluator.Result.Holds)
    }

    @Test
    fun negativeAxisIsNormalizedFromEnd() {
        // -1 与 1 在 rank=2 下是同一根轴
        val result = ContractEvaluator.evaluate(contract(softmaxAxis = -1, sumAxis = 1))

        assertTrue("负轴应归一化后比较，实际：$result", result is ContractEvaluator.Result.Holds)
    }

    /* -------------------------------------------------------------- 不成立 */

    @Test
    fun sumAlongBatchAxisFails() {
        val result = ContractEvaluator.evaluate(contract(softmaxAxis = 1, sumAxis = 0))

        assertTrue("沿 batch 轴求和不应成立，实际：$result", result is ContractEvaluator.Result.Fails)
        val reason = (result as ContractEvaluator.Result.Fails).reason
        assertTrue("失败原因要能直接讲给用户听，实际：$reason", reason.contains("不是 1"))
        assertTrue("原因里要指出实际的轴，实际：$reason", reason.contains("轴 1"))
    }

    @Test
    fun negativeAxisAlignmentMismatchFails() {
        val result = ContractEvaluator.evaluate(contract(softmaxAxis = -1, sumAxis = 0))

        assertTrue("负轴归一化后不同轴仍应失败，实际：$result", result is ContractEvaluator.Result.Fails)
    }

    @Test
    fun axisOutOfRangeFails() {
        val result = ContractEvaluator.evaluate(contract(softmaxAxis = 1, sumAxis = 5))

        assertTrue("轴越界应失败，实际：$result", result is ContractEvaluator.Result.Fails)
        assertTrue(
            (result as ContractEvaluator.Result.Fails).reason.contains("越界")
        )
    }

    @Test
    fun emptyInputFails() {
        val result = ContractEvaluator.evaluate(contract(softmaxAxis = 0, sumAxis = 0, input = emptyList()))

        assertTrue("没有维度应失败，实际：$result", result is ContractEvaluator.Result.Fails)
    }

    /* ----------------------------------------------------------- 入库闸门 */

    @Test
    fun validateAcceptsSelfConsistentContract() {
        val result = ContractEvaluator.validate(contract(softmaxAxis = 1, sumAxis = 1))

        assertTrue("自洽契约应通过入库闸门，实际：$result", result is ContractEvaluator.Result.Holds)
    }

    @Test
    fun validateRejectsContractThatNeverHolds() {
        val result = ContractEvaluator.validate(contract(softmaxAxis = 1, sumAxis = 0))

        assertTrue("原状态就不成立的契约应被拒绝，实际：$result", result is ContractEvaluator.Result.Invalid)
        assertTrue(
            (result as ContractEvaluator.Result.Invalid).reason.contains("原状态下命题就不成立")
        )
    }

    @Test
    fun validateRejectsMissingSymbolBinding() {
        val unbound = ShapeContract(
            symbols = mapOf("N" to 8), // 缺 C
            op = TensorOp.SoftmaxSum(input = listOf("N", "C"), softmaxAxis = 1, sumAxis = 1),
        )

        val result = ContractEvaluator.validate(unbound)

        assertTrue("缺符号绑定应被拒绝，实际：$result", result is ContractEvaluator.Result.Invalid)
        assertTrue((result as ContractEvaluator.Result.Invalid).reason.contains("缺少符号绑定"))
    }

    /* ------------------------------------------------------- 与形状契约一致 */

    @Test
    fun softmaxAndSoftmaxSumAgreeOnAxisLegality() {
        // 同一组轴：Softmax 合法时，SoftmaxSum 同轴求和必定成立
        val softmax = TensorOp.Softmax(input = listOf("N", "C"), axis = 1)
        val softmaxSum = TensorOp.SoftmaxSum(input = listOf("N", "C"), softmaxAxis = 1, sumAxis = 1)

        val a = ContractEvaluator.evaluate(ShapeContract(symbols = mapOf("N" to 8, "C" to 10), op = softmax))
        val b = ContractEvaluator.evaluate(ShapeContract(symbols = mapOf("N" to 8, "C" to 10), op = softmaxSum))

        assertEquals(a::class, b::class)
    }
}
