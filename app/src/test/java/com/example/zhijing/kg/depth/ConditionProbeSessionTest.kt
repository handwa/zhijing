package com.example.zhijing.kg.depth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「预测 → 揭示」状态机测试。
 *
 * 这里守住三条产品约束：
 * 1. 判定来自本地求值器，不来自任何模型；
 * 2. 没答不能推进（不能跳过）；
 * 3. 答错时要能匹配到对应误解，答对时不要硬塞解释。
 */
class ConditionProbeSessionTest {

    /* -------------------------------------------------------------- 构造 */

    private fun matMulContract() = ShapeContract(
        symbols = mapOf("N" to 8, "in_x" to 4, "in_w" to 4, "out_w" to 6),
        op = TensorOp.MatMul(
            lhs = listOf("N", "in_x"),
            rhs = listOf("out_w", "in_w"),
            rhsTransposed = true,
        ),
        constraints = listOf(
            Constraint.DimEquals(DimExpr.Sym("in_x"), DimExpr.Sym("in_w"), "内维相等")
        ),
    )

    private fun intervention(
        id: String,
        overrides: Map<String, Int> = emptyMap(),
        misjudgements: List<Misjudgement> = listOf(
            Misjudgement(Outcome.FAILS, "默认误判", "默认纠正")
        ),
    ) = Intervention(
        id = id,
        action = "拨动 $id",
        overrides = overrides,
        contract = matMulContract(),
        outcome = Outcome.HOLDS,
        consequence = "后果 $id",
        source = TruthSource.COMPUTED,
        misjudgements = misjudgements,
    )

    private fun triple(
        id: String = "tri.a",
        claimId: String = "clm.a",
        cardId: String = "crd.a",
        mechanism: String = "矩阵乘要沿内维求和",
        interventions: List<Intervention> = listOf(intervention("inv.1")),
    ) = ConditionTriple(
        id = id,
        claimId = claimId,
        cardId = cardId,
        statement = "输入最后一维等于权重输入维",
        kind = ConditionKind.SHAPE,
        mechanism = mechanism,
        interventions = interventions,
        computable = true,
    )

    /* -------------------------------------------------------------- 门槛 */

    @Test
    fun forCardIgnoresTriplesFailingThreshold() {
        val invalid = triple(id = "tri.bad", mechanism = "")

        val session = ConditionProbeSession.forCard(listOf(invalid), "crd.a")

        assertFalse("缺机制的三元组不该出题", session.hasQuestions)
        assertTrue(session.isFinished)
        assertNull(session.current)
    }

    @Test
    fun forCardFiltersByCard() {
        val other = triple(id = "tri.other", cardId = "crd.other")

        val session = ConditionProbeSession.forCard(listOf(other), "crd.a")

        assertFalse(session.hasQuestions)
    }

    @Test
    fun questionsAreOrderedDeterministically() {
        val b = triple(id = "tri.b", interventions = listOf(intervention("inv.1")))
        val a = triple(
            id = "tri.a",
            interventions = listOf(intervention("inv.2"), intervention("inv.1")),
        )

        val session = ConditionProbeSession.forCard(listOf(b, a), "crd.a")

        assertEquals("tri.a", session.current!!.tripleId)
        assertEquals("inv.1", session.current!!.interventionId)
        assertEquals(3, session.total)
    }

    /* -------------------------------------------------------------- 判定 */

    @Test
    fun answerUsesLocalEvaluatorNotPrediction() {
        val session = ConditionProbeSession.forCard(
            listOf(triple(interventions = listOf(intervention("inv.1", overrides = mapOf("in_x" to 5))))),
            "crd.a",
        )

        val answer = session.answer(Outcome.HOLDS)

        assertEquals("求值器算出内维不等", Outcome.FAILS, answer.actual.outcome)
        assertFalse("用户猜成立，但实际不成立", answer.matched)
        assertEquals(TruthSource.COMPUTED, answer.actual.source)
    }

    @Test
    fun correctPredictionHasNoMisconception() {
        val session = ConditionProbeSession.forCard(listOf(triple()), "crd.a")

        val answer = session.answer(Outcome.HOLDS)

        assertTrue(answer.matched)
        assertNull("答对时不该硬塞误解解释", answer.misconception)
    }

    @Test
    fun wrongPredictionSurfacesMatchingMisconception() {
        val session = ConditionProbeSession.forCard(
            listOf(
                triple(
                    interventions = listOf(
                        intervention(
                            id = "inv.1",
                            overrides = mapOf("in_x" to 5),
                            misjudgements = listOf(
                                Misjudgement(Outcome.HOLDS, "以为只是变宽", "内维必须相等")
                            ),
                        )
                    )
                )
            ),
            "crd.a",
        )

        val answer = session.answer(Outcome.HOLDS)

        assertFalse(answer.matched)
        assertNotNull("答错时要指出具体误解", answer.misconception)
        assertEquals("以为只是变宽", answer.misconception!!.why)
        assertEquals("内维必须相等", answer.misconception!!.correction)
    }

    /* ---------------------------------------------------------- 不能跳过 */

    @Test
    fun cannotAdvanceBeforeAnswering() {
        val session = ConditionProbeSession.forCard(
            listOf(
                triple(
                    interventions = listOf(
                        intervention("inv.1"),
                        intervention("inv.2"),
                    )
                )
            ),
            "crd.a",
        )

        val moved = session.advance()

        assertFalse("未作答不能推进", moved)
        assertEquals("inv.1", session.current!!.interventionId)
        assertEquals(0, session.answeredCount)
    }

    @Test
    fun advanceMovesToNextAfterAnswering() {
        val session = ConditionProbeSession.forCard(
            listOf(
                triple(
                    interventions = listOf(
                        intervention("inv.1"),
                        intervention("inv.2"),
                    )
                )
            ),
            "crd.a",
        )

        session.answer(Outcome.HOLDS)
        val moved = session.advance()

        assertTrue(moved)
        assertEquals("inv.2", session.current!!.interventionId)
    }

    @Test
    fun finishesAfterLastAnswer() {
        val session = ConditionProbeSession.forCard(listOf(triple()), "crd.a")

        session.answer(Outcome.HOLDS)
        assertFalse("刚答完还没推进", session.isFinished)

        val moved = session.advance()

        assertFalse("没有下一题", moved)
        assertTrue(session.isFinished)
        assertNull(session.current)
    }

    @Test
    fun duplicateAnswerIsRejected() {
        val session = ConditionProbeSession.forCard(listOf(triple()), "crd.a")
        session.answer(Outcome.HOLDS)

        assertThrows(IllegalStateException::class.java) {
            session.answer(Outcome.FAILS)
        }
    }

    /* ------------------------------------------------------------ 选项集 */

    @Test
    fun unknownIsNotAnOption() {
        val session = ConditionProbeSession.forCard(listOf(triple()), "crd.a")

        val options = session.current!!.options

        assertFalse("UNKNOWN 是契约异常，不该让用户猜", options.contains(Outcome.UNKNOWN))
        assertEquals(4, options.size)

        assertThrows(IllegalArgumentException::class.java) {
            session.answer(Outcome.UNKNOWN)
        }
    }

    @Test
    fun answersAreRecordedInOrder() {
        val session = ConditionProbeSession.forCard(
            listOf(
                triple(
                    interventions = listOf(
                        intervention("inv.1"),
                        intervention("inv.2", overrides = mapOf("in_x" to 5)),
                    )
                )
            ),
            "crd.a",
        )

        session.answer(Outcome.HOLDS)
        session.advance()
        session.answer(Outcome.HOLDS)

        assertEquals(2, session.answers.size)
        assertTrue(session.answers[0].matched)
        assertFalse("第二个干预点内维不等", session.answers[1].matched)
    }
}
