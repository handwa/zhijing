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

    /**
     * 重新下钻时不再重问已经拨过的干预点。
     *
     * 不跳过的话，一张有 5 个干预点的卡每次进来都要先重答 5 道，才轮得到判断题面板 ——
     * 用户会认为「我明明答过了，怎么还是没反应」。
     */
    @Test
    fun forCardSkipsAlreadyProbedInterventions() {
        val triples = listOf(
            triple("tri.a", interventions = listOf(intervention("inv.1"), intervention("inv.2")))
        )

        assertEquals(2, ConditionProbeSession.forCard(triples, "crd.a").total)

        val resumed = ConditionProbeSession.forCard(triples, "crd.a", setOf("inv.1"))
        assertEquals(1, resumed.total)
        // 只剩没拨过的那道，题号从 1 重新排
        assertEquals("inv.2", resumed.current?.interventionId)
        assertEquals(1, resumed.current?.ordinal)

        // 全拨过：直接 Done，UI 会显示判断题面板
        val done = ConditionProbeSession.forCard(triples, "crd.a", setOf("inv.1", "inv.2"))
        assertEquals(0, done.total)
        assertEquals(ConditionProbeSession.Step.Done, done.step)
    }

    /**
     * 「重走这道」：从路径里点某个干预点，直接停在它上面，不必把前面的题再答一遍。
     */
    @Test
    fun forCardStartsAtRequestedIntervention() {
        val triples = listOf(
            triple(
                "tri.a",
                interventions = listOf(
                    intervention("inv.1"),
                    intervention("inv.2"),
                    intervention("inv.3"),
                )
            )
        )

        val resumed = ConditionProbeSession.forCard(triples, "crd.a", startAt = "inv.3")
        assertEquals("inv.3", resumed.current?.interventionId)
        // 题号按真实位置算，不会变成「重走后的第 1 题」
        assertEquals(3, resumed.current?.ordinal)
    }

    @Test
    fun forCardFallsBackWhenStartAtMissing() {
        // 三元组被下架后旧路径仍然存在：找不到起点就从第一题开始，不抛异常
        val triples = listOf(triple("tri.a", interventions = listOf(intervention("inv.1"))))
        val session = ConditionProbeSession.forCard(triples, "crd.a", startAt = "inv.gone")

        assertEquals("inv.1", session.current?.interventionId)
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

    /* ------------------------------------------------ 待答状态（防闪退） */

    @Test
    fun hasPendingIsTrueBeforeAnswering() {
        val session = ConditionProbeSession.forCard(listOf(triple()), "crd.a")

        assertTrue(session.hasPending)
    }

    @Test
    fun hasPendingTurnsFalseOnceAnswered() {
        val session = ConditionProbeSession.forCard(listOf(triple()), "crd.a")
        session.answer(Outcome.HOLDS)

        assertFalse("已作答的题不应再被当作待答", session.hasPending)
    }

    @Test
    fun hasPendingIsFalseWhenFinished() {
        val session = ConditionProbeSession.forCard(listOf(triple()), "crd.a")
        session.answer(Outcome.HOLDS)
        session.advance()

        assertFalse(session.hasPending)
        assertTrue(session.isFinished)
    }

    /**
     * 这条用例锁定「UI 必须先查 [ConditionProbeSession.hasPending]」这一约束：
     * 走完所有题后再调 [ConditionProbeSession.answer] 会抛异常。
     *
     * 之所以保留抛异常而不是静默返回，是因为它代表调用方确实算错了状态；
     * UI 侧的兜底（`if (probe.hasPending)`）负责在正常交互中永不触发它。
     */
    @Test
    fun answerAfterFinishThrows() {
        val session = ConditionProbeSession.forCard(listOf(triple()), "crd.a")
        session.answer(Outcome.HOLDS)
        session.advance()

        assertThrows(IllegalStateException::class.java) {
            session.answer(Outcome.FAILS)
        }
    }

    /**
     * 模拟「快速连点同一个选项」：第二次点击落在尚未重组的旧按钮上。
     *
     * UI 的兜底逻辑就是先读 hasPending 再作答，所以第二次点击会被丢弃，
     * 不会把会话推进两次，也不会闪退。
     */
    @Test
    fun rapidDoubleTapIsIgnoredByPendingGuard() {
        val session = ConditionProbeSession.forCard(
            listOf(triple(interventions = listOf(intervention("inv.1"), intervention("inv.2")))),
            "crd.a",
        )

        // 第一次点击
        assertTrue(session.hasPending)
        session.answer(Outcome.HOLDS)
        session.advance()

        // 第二次点击（旧按钮）：兜底判定为 false，直接丢弃
        assertEquals("inv.2", session.current!!.interventionId)
        assertTrue("下一题仍然可以作答", session.hasPending)
        assertEquals(1, session.answeredCount)
    }

    /* ------------------------------------------- 题号与界面状态（单一真相） */

    /**
     * 题号必须由位置决定，不能由「已答计数」推算。
     *
     * 曾经出现过 `3/2`：答完第 2 题但尚未推进时，已答计数是 2，
     * 界面却拿它当题号再 +1。现在题号随 [ConditionProbeSession.Question] 一起给出。
     */
    @Test
    fun questionOrdinalComesFromPositionNotAnsweredCount() {
        val session = ConditionProbeSession.forCard(
            listOf(triple(interventions = listOf(intervention("inv.1"), intervention("inv.2")))),
            "crd.a",
        )

        assertEquals(1, session.current!!.ordinal)
        assertEquals(2, session.current!!.total)

        session.answer(Outcome.HOLDS)
        session.advance()

        assertEquals(2, session.current!!.ordinal)
        assertEquals(2, session.current!!.total)
    }

    @Test
    fun stepIsRevealingRightAfterAnswer() {
        val session = ConditionProbeSession.forCard(listOf(triple()), "crd.a")

        assertTrue(session.step is ConditionProbeSession.Step.Asking)

        session.answer(Outcome.HOLDS)

        val step = session.step
        assertTrue("答完应立即进入揭示，而不是继续显示题目", step is ConditionProbeSession.Step.Revealing)
        assertEquals(Outcome.HOLDS, (step as ConditionProbeSession.Step.Revealing).answer.predicted)
    }

    @Test
    fun stepIsDoneAfterAdvancingPastLastQuestion() {
        val session = ConditionProbeSession.forCard(listOf(triple()), "crd.a")

        session.answer(Outcome.HOLDS)
        session.advance()

        assertEquals(ConditionProbeSession.Step.Done, session.step)
        assertNull("已结束时不该再有可答的题", session.current)
    }

    @Test
    fun hasNextReflectsRemainingQuestions() {
        val session = ConditionProbeSession.forCard(
            listOf(triple(interventions = listOf(intervention("inv.1"), intervention("inv.2")))),
            "crd.a",
        )

        assertTrue("还有第 2 题", session.hasNext)
        session.answer(Outcome.HOLDS)
        session.advance()
        assertFalse("已是最后一题", session.hasNext)
    }
}
