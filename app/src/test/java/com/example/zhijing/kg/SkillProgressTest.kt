package com.example.zhijing.kg

import com.zhijing.data.model.ClaimProbeState
import com.zhijing.data.model.ClaimSource
import com.zhijing.data.model.ProbeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 卡片三态（已点亮 / 现在能学 / 未解锁）的纯逻辑测试。
 *
 * 这几条规则一旦错了，用户看到的是「明明前置没学却显示能学」或者
 * 「学了一大半却永远锁着」—— 都不会崩，只会让人不再信这个界面。
 */
class SkillProgressTest {

    /* --------------------------------------------------- 点亮判定 */

    /**
     * 回归：一轮下钻里每条命题只判一次，收敛结果是 `VERIFIED` 而不是 `MASTERED`。
     *
     * 第一版按「80% 已掌握」点亮，实测的结果是「答完整张卡也不会亮，全图除 1.1 外全锁死」。
     * 点亮必须认 `VERIFIED`；`MASTERED` 留给之后的"稳固"标记。
     */
    @Test
    fun `答对一轮就点亮，不必连对两次`() {
        val result = SkillProgress.of(
            graph(listOf(cardWithStatuses("a", List(2) { ProbeStatus.VERIFIED })))
        )
        assertEquals(SkillProgress.State.LIT, result.stateOf("a"))
    }

    /**
     * 出厂图谱的卡片有「预测 → 揭示」：用户可能只拨条件、不做判断题。
     * 只认命题状态时，这类卡永远点不亮 —— 用户看到的是「我答了但没反应」。
     */
    @Test
    fun `只拨对条件不做判断题也算练过`() {
        val result = SkillProgress.of(
            graph(
                listOf(
                    cardWithStatuses(
                        "a",
                        listOf(ProbeStatus.UNPROBED),
                        steps = listOf(pathStep(matched = true))
                    )
                )
            )
        )
        assertEquals(SkillProgress.State.LIT, result.stateOf("a"))
    }

    @Test
    fun `条件拨错不算练过`() {
        val result = SkillProgress.of(
            graph(
                listOf(
                    cardWithStatuses(
                        "a",
                        listOf(ProbeStatus.UNPROBED),
                        steps = listOf(pathStep(matched = false))
                    )
                )
            )
        )
        assertEquals(SkillProgress.State.READY, result.stateOf("a"))
    }

    @Test
    fun `有易混淆的命题挡住点亮`() {
        val result = SkillProgress.of(
            graph(
                listOf(
                    cardWithStatuses(
                        "a",
                        listOf(ProbeStatus.VERIFIED, ProbeStatus.CONFUSED),
                    )
                )
            )
        )
        // 撞错过的必须先改对；这时点亮会误导用户"可以往下走"
        assertEquals(SkillProgress.State.READY, result.stateOf("a"))
    }

    @Test
    fun `探测比例达到阈值才算点亮`() {
        // 5 条命题、4 条已掌握 = 80%，刚好够
        val lit = SkillProgress.of(graph(listOf(card("a", claims = 5, mastered = 4))))
        assertEquals(SkillProgress.State.LIT, lit.stateOf("a"))

        // 5 条里 3 条 = 60%，不够
        val notLit = SkillProgress.of(graph(listOf(card("a", claims = 5, mastered = 3))))
        assertEquals(SkillProgress.State.READY, notLit.stateOf("a"))
    }

    @Test
    fun `没有命题的组织性卡片不挡路`() {
        // 章节导语没有可判定内容；若把它算成"未点亮"，后面所有卡都会被锁死
        val result = SkillProgress.of(
            graph(
                listOf(
                    card("intro"),
                    // b 自己有命题，才需要判断"能不能学"；没有命题的卡一律视为已点亮
                    card("b", claims = 1, prerequisites = listOf(required("intro", "b"))),
                )
            )
        )
        assertEquals(SkillProgress.State.LIT, result.stateOf("intro"))
        assertEquals(SkillProgress.State.READY, result.stateOf("b"))
    }

    @Test
    fun `组织性卡片不计入进度分母`() {
        val result = SkillProgress.of(
            graph(
                listOf(
                    card("intro"),
                    card("a", claims = 2, mastered = 2),
                )
            )
        )
        // 分母只数"有命题的卡"，否则 `✓ x/y` 里会混进永远绿着的导语卡
        assertEquals(1, result.total)
        assertEquals(1, result.lit)
    }

    /* --------------------------------------------------- 三态判定 */

    @Test
    fun `必须前置没点亮就锁住`() {
        val result = SkillProgress.of(
            graph(
                listOf(
                    card("a", claims = 2, mastered = 0),
                    card("b", claims = 1, prerequisites = listOf(required("a", "b"))),
                    card("c", claims = 1, prerequisites = listOf(required("b", "c"))),
                )
            )
        )
        // a 未点亮 → b 锁住；c 的前置 b 也没亮 → 同样锁住
        assertEquals(SkillProgress.State.READY, result.stateOf("a"))
        assertEquals(SkillProgress.State.LOCKED, result.stateOf("b"))
        assertEquals(listOf("b"), result.cards.getValue("c").missingPrerequisites)
        assertEquals(1, result.ready)
        assertEquals(2, result.locked)
    }

    @Test
    fun `推荐前置不参与门控`() {
        val result = SkillProgress.of(
            graph(
                listOf(
                    card("a", claims = 2, mastered = 0),
                    card(
                        "b",
                        claims = 1,
                        prerequisites = listOf(
                            PrerequisiteLink("a", "b", PrerequisiteKind.RECOMMENDED)
                        ),
                    ),
                )
            )
        )
        // a 还没点亮，但只是"推荐先学" —— b 仍然可以学
        assertEquals(SkillProgress.State.READY, result.stateOf("b"))
    }

    @Test
    fun `前置点亮后下游变成可学`() {
        val result = SkillProgress.of(
            graph(
                listOf(
                    card("a", claims = 2, mastered = 2),
                    card("b", claims = 1, prerequisites = listOf(required("a", "b"))),
                )
            )
        )
        assertEquals(SkillProgress.State.LIT, result.stateOf("a"))
        assertEquals(SkillProgress.State.READY, result.stateOf("b"))
        assertEquals(1, result.lit)
        assertEquals(1, result.ready)
    }

    /* --------------------------------------------------- 排序与聚合 */

    @Test
    fun `解锁数量按传递闭包计算并用于排序`() {
        val result = SkillProgress.of(
            graph(
                listOf(
                    card("a", claims = 1),
                    card("b", claims = 1, prerequisites = listOf(required("a", "b"))),
                    card("c", claims = 1, prerequisites = listOf(required("b", "c"))),
                    card("d", claims = 1, prerequisites = listOf(required("a", "d"))),
                )
            )
        )
        // a 解锁 b→c 与 d，共 3 张
        assertEquals(3, result.cards.getValue("a").downstreamCount)
        assertEquals(1, result.cards.getValue("b").downstreamCount)

        // "现在能学"里 a、b、d 都能学（组织性卡片已点亮），a 应该排最前
        val ready = result.readyCards.map { it.cardId }
        assertTrue("a 应排第一：$ready", ready.first() == "a")
    }

    @Test
    fun `节点状态取最保守的一张卡`() {
        val result = SkillProgress.of(
            graph(
                listOf(
                    card("lit", claims = 1, mastered = 1),
                    card("locker", claims = 1),
                    card("locked", claims = 1, prerequisites = listOf(required("locker", "locked"))),
                    card("ready", claims = 1),
                )
            )
        )
        // 同节点挂了 ready + locked：显示 LOCKED，点进去才不会撞墙
        assertEquals(
            SkillProgress.State.LOCKED,
            result.stateOfCards(listOf("ready", "locked")),
        )
        // 没挂卡片（纯组织性节点）不画状态
        assertEquals(null, result.stateOfCards(emptyList()))
    }

    /* --------------------------------------------------- 工具 */

    private fun required(from: String, to: String) =
        PrerequisiteLink(from, to, PrerequisiteKind.REQUIRED)

    private fun graph(cards: List<GraphCard>) = MergedGraph(
        schemaVersion = "",
        generatedAt = "",
        outline = emptyList(),
        cards = cards,
        claims = emptyMap(),
        edges = emptyMap(),
    )

    private fun card(
        id: String,
        claims: Int = 0,
        mastered: Int = 0,
        prerequisites: List<PrerequisiteLink> = emptyList(),
    ) = cardWithStatuses(
        id = id,
        statuses = List(claims) { index ->
            if (index < mastered) ProbeStatus.MASTERED else ProbeStatus.UNPROBED
        },
        prerequisites = prerequisites,
    )

    /** 直接指定每条命题的状态，用来测"探测一次算不算点亮"这类边界。 */
    private fun cardWithStatuses(
        id: String,
        statuses: List<ProbeStatus>,
        prerequisites: List<PrerequisiteLink> = emptyList(),
        steps: List<ConditionPathStep> = emptyList(),
    ) = GraphCard(
        id = id,
        title = id,
        outlinePath = id,
        summary = "",
        body = "",
        claims = statuses.mapIndexed { index, status ->
            claim(id = "$id.c$index", cardId = id, status = status, steps = steps)
        },
        prerequisites = prerequisites,
    )

    private fun pathStep(matched: Boolean) = ConditionPathStep(
        interventionId = "inv.test",
        action = "把输入最后一维 4 改成 5",
        statement = "最后一维要对齐",
        predicted = "仍然成立",
        actual = "不再成立",
        matched = matched,
        attempts = 1,
        wrongAttempts = if (matched) 0 else 1,
    )

    private fun claim(
        id: String,
        cardId: String,
        status: ProbeStatus,
        steps: List<ConditionPathStep> = emptyList(),
    ) = GraphClaim(
        id = id,
        statement = id,
        expected = true,
        conditions = emptyList(),
        evidence = "",
        cardId = cardId,
        outlinePath = cardId,
        question = "",
        explanation = "",
        errorKinds = emptyList(),
        misconception = "",
        edgeIds = emptyList(),
        probe = ClaimProbeState(claimId = id, probeStatus = status),
        source = ClaimSource.BUNDLED,
        bundled = true,
        conditionPath = steps,
    )
}
