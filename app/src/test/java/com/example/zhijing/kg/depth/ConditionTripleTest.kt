package com.example.zhijing.kg.depth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三元组的完整链路测试：**真实命题 → 契约 → 干预 → 求值 → 后果 → 误判解释**。
 *
 * 这组测试回答「最小验证能不能跑通」这个问题。
 */
class ConditionTripleTest {

    /**
     * 真实命题：`clm.arch.ffn.linear-in-dim.4a3e`
     * 「Linear 层要求输入张量的最后一维等于权重的输入维度，否则矩阵乘无法进行。」
     *
     * 条件：`x.shape[-1] == W.shape[1]`
     *
     * ## 为什么把两端拆成 in_x / in_w
     *
     * 若两端共享 `in_features` 一个符号，改它同时改两边，内维永远相等 ——
     * 那这条条件就成了恒等式，拨不动。
     *
     * 真实的失败场景是「输入换了、权重没换」：
     * 上游模型改了输出维度，下游 Linear 层的权重没跟着改。
     * 所以契约用两个符号，干预只改其中一个。
     */
    private val linearContract = ShapeContract(
        symbols = mapOf("N" to 8, "in_x" to 4, "in_w" to 4, "out_features" to 6),
        op = TensorOp.MatMul(
            lhs = listOf("N", "in_x"),
            rhs = listOf("out_features", "in_w"),
            rhsTransposed = true,
        ),
        constraints = listOf(
            Constraint.DimEquals(
                DimExpr.Sym("in_x"),
                DimExpr.Sym("in_w"),
                "输入最后一维必须等于权重最后一维",
            )
        ),
    )

    private fun linearTriple() = ConditionTriple(
        id = "tri.linear-in-dim.1",
        claimId = "clm.arch.ffn.linear-in-dim.4a3e",
        cardId = "crd.s1-1-linear",
        statement = "输入张量的最后一维必须等于权重的输入维度",
        kind = ConditionKind.SHAPE,
        mechanism = "矩阵乘按内维归约：把 x 的最后一维与 W 的最后一维配对求和。" +
            "内维不等时，配不上对的元素无处可去，求和本身无法定义 —— " +
            "所以这不是「结果不准」，是「运算不存在」。",
        computable = true,
        interventions = listOf(
            Intervention(
                id = "tri.linear-in-dim.1.i1",
                action = "上游改了输出维度：输入最后一维从 4 变成 5",
                overrides = mapOf("in_x" to 5),
                contract = linearContract,
                outcome = Outcome.FAILS,
                consequence = "矩阵乘内维不等，运算未定义",
                source = TruthSource.COMPUTED,
                misjudgements = listOf(
                    Misjudgement(
                        predicted = Outcome.HOLDS,
                        why = "把「前导维自由」推广成了「所有维都自由」——" +
                            "以为 batch、序列长度能随便改，最后一维也就能随便改。",
                        correction = "自由的是前导维（N、序列长度），它们不参与归约。" +
                            "最后一维恰恰不自由：它是矩阵乘的内维，决定能不能求和。",
                    ),
                    Misjudgement(
                        predicted = Outcome.BECOMES_OTHER,
                        why = "以为框架会自动对齐维度、退化成别的运算。",
                        correction = "自动广播只作用于可广播的位置。" +
                            "内维不等不是广播能救的，会直接抛错。",
                    ),
                ),
            ),
            Intervention(
                id = "tri.linear-in-dim.1.i2",
                action = "改 batch：从 8 变成 160",
                overrides = mapOf("N" to 160),
                contract = linearContract,
                outcome = Outcome.HOLDS,
                consequence = "仍然成立 —— batch 是前导维，不参与内维归约",
                source = TruthSource.COMPUTED,
                misjudgements = listOf(
                    Misjudgement(
                        predicted = Outcome.FAILS,
                        why = "把「维度必须匹配」理解成了「所有维度都要固定」。",
                        correction = "被约束的只有参与归约的那一维。" +
                            "其余维度只要两端一致就能算，batch 变大不影响内维配对。",
                    ),
                ),
            ),
        ),
    )

    /* ---------------------------------------------- 三元组门槛 */

    @Test
    fun `合格的三元组通过入库校验`() {
        val t = linearTriple()
        assertEquals("不应有问题：${t.problems()}", emptyList<String>(), t.problems())
        assertTrue(t.isValid)
    }

    @Test
    fun `缺机制被拒`() {
        val t = linearTriple().copy(mechanism = "")
        assertFalse(t.isValid)
        assertTrue(t.problems().any { it.contains("缺机制") })
    }

    @Test
    fun `没有反事实被拒`() {
        val t = linearTriple().copy(interventions = emptyList())
        assertFalse(t.isValid)
        assertTrue(t.problems().any { it.contains("装饰品") })
    }

    @Test
    fun `没有误判库被拒`() {
        val t = linearTriple()
        val broken = t.copy(
            interventions = t.interventions.map { it.copy(misjudgements = emptyList()) }
        )
        assertFalse("答错了无法解释，不该入库", broken.isValid)
        assertTrue(broken.problems().any { it.contains("误判库") })
    }

    @Test
    fun `标为可计算却没契约被拒`() {
        val t = linearTriple()
        val broken = t.copy(
            interventions = t.interventions.map { it.copy(contract = null) }
        )
        assertFalse(broken.isValid)
        assertTrue(broken.problems().any { it.contains("没带契约") })
    }

    /* ---------------------------------------------- 求值链路 */

    @Test
    fun `改输入维度后判为不成立 且原因具体`() {
        val inv = linearTriple().interventions.first { it.id.endsWith("i1") }
        val r = inv.resolveOutcome()

        assertEquals(Outcome.FAILS, r.outcome)
        assertEquals(TruthSource.COMPUTED, r.source)
        assertFalse("算出来的结果不该标未经核实", r.needsCaveat)
        assertTrue("原因应点出 5 和 4：${r.detail}", r.detail.contains("5") && r.detail.contains("4"))
    }

    @Test
    fun `改 batch 后判为仍成立`() {
        val inv = linearTriple().interventions.first { it.id.endsWith("i2") }
        val r = inv.resolveOutcome()
        assertEquals(Outcome.HOLDS, r.outcome)
        assertEquals(TruthSource.COMPUTED, r.source)
    }

    @Test
    fun `预置答案与计算结果一致`() {
        linearTriple().interventions.forEach { inv ->
            assertTrue(
                "干预 ${inv.id} 的预置 outcome(${inv.outcome}) 与计算不一致",
                inv.isConsistent()
            )
        }
    }

    @Test
    fun `预置答案与计算冲突时能被检出`() {
        // 故意写错：把「改 batch」标成 FAILS
        val wrong = linearTriple().interventions.first { it.id.endsWith("i2") }
            .copy(outcome = Outcome.FAILS)
        assertFalse("质检应发现冲突", wrong.isConsistent())
        // 但实际判定仍以计算为准
        assertEquals(Outcome.HOLDS, wrong.resolveOutcome().outcome)
    }

    /* ---------------------------------------------- 误判解释 */

    @Test
    fun `预测仍成立时能匹配到对应误判`() {
        val inv = linearTriple().interventions.first { it.id.endsWith("i1") }
        val m = inv.misjudgements.firstOrNull { it.predicted == Outcome.HOLDS }
        assertTrue("应有针对 HOLDS 误判的解释", m != null)
        assertTrue("解释应指向机制：${m!!.correction}", m.correction.contains("内维"))
    }

    @Test
    fun `误判解释不是空话`() {
        linearTriple().interventions.forEach { inv ->
            inv.misjudgements.forEach { m ->
                assertTrue("why 不能太短：${m.why}", m.why.length > 10)
                assertTrue("correction 不能太短：${m.correction}", m.correction.length > 10)
            }
        }
    }

    /* ---------------------------------------------- 不可计算路径 */

    @Test
    fun `语义条件走预置答案并标注来源`() {
        val sem = ConditionTriple(
            id = "tri.semantic.1",
            claimId = "clm.arch.ffn.softmax-sum.a505",
            cardId = "crd.s1-3-softmax",
            statement = "softmax 的输入必须是未归一化的 logits",
            kind = ConditionKind.SEMANTIC,
            mechanism = "softmax 内部会做 exp 再归一化。若输入已是概率（0-1），" +
                "先 exp 会压缩差异、再归一化会重新拉开 —— 得到的是「概率的概率」，" +
                "语义已经不是原来的那一层。",
            computable = false,
            interventions = listOf(
                Intervention(
                    id = "tri.semantic.1.i1",
                    action = "把输入换成已经 softmax 过的概率",
                    outcome = Outcome.BECOMES_OTHER,
                    consequence = "输出仍是合法概率分布，但不再是「对 logits 的归一化」，" +
                        "而是对概率分布的再平滑 —— 命题描述的对象变了。",
                    source = TruthSource.AUTHORED_HUMAN,
                    misjudgements = listOf(
                        Misjudgement(
                            predicted = Outcome.FAILS,
                            why = "以为「输入不对」就等于「算不出来」。",
                            correction = "它算得出来，而且结果也是合法的概率。" +
                                "错的是语义：你在对已经归一化的东西再归一化一次。",
                        ),
                    ),
                ),
            ),
        )
        assertTrue("人工预设的三元组也应合格", sem.isValid)
        val r = sem.interventions.single().resolveOutcome()
        assertEquals(Outcome.BECOMES_OTHER, r.outcome)
        assertEquals(TruthSource.AUTHORED_HUMAN, r.source)
        assertFalse("人工审定过，不需要标注未经核实", r.needsCaveat)
    }

    @Test
    fun `模型生成未经核实的答案必须标注`() {
        val inv = Intervention(
            id = "x",
            action = "改点什么",
            outcome = Outcome.WEAKENED,
            consequence = "范围变窄",
            source = TruthSource.AUTHORED_MODEL,
            misjudgements = listOf(Misjudgement(Outcome.HOLDS, "以为没事", "其实有事")),
        )
        assertTrue("模型生成的结论必须提示未经核实", inv.resolveOutcome().needsCaveat)
    }
}
