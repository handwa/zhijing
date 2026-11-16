package com.example.zhijing.kg

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zhijing.data.ZhiJingData
import com.zhijing.data.model.ClaimReviewStatus
import com.zhijing.data.model.ClaimSource
import com.zhijing.data.model.GeneratedCard
import com.zhijing.data.model.GeneratedClaim
import com.zhijing.data.model.GeneratedGraph
import com.zhijing.data.model.GeneratedOutlineNode
import com.zhijing.data.model.GeneratedPrerequisite
import com.zhijing.data.model.ProbeStatus
import com.zhijing.data.model.RuntimeClaim
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 图谱合并加载器测试。
 *
 * 直接读真实的 `app/src/main/assets/kg/graph.json`（Robolectric 会把 assets 挂进来），
 * 验证「出厂图谱 + Room 运行数据」能合成一份 UI 可用的完整图谱。
 *
 * 守护的关键不变量：
 * 1. 出厂图谱能被完整解析（24 大纲 / 22 卡片 / 36 命题 / 16 边）；
 * 2. 状态按命题 ID 派生到命题与卡片角标上；
 * 3. **未确认的云端候选不进入图谱**；
 * 4. 已确认的云端命题能挂在对应卡片下，且标记为可删除（bundled=false）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GraphAssemblerTest {

    private lateinit var data: ZhiJingData
    private lateinit var assembler: GraphAssembler

    /**
     * 随包图谱的命题数，与 `graph.json` 的 `meta.counts.claim` 对齐。
     *
     * 之所以抽成常量：这一份图谱是「语料 + 补充命题」的出版数据，数量会随内容生产变化，
     * 散落十几个字面量会让每次补内容都变成找数字。含 1 条 5 章「判断句有边界」的补充命题。
     */
    private val bundledClaims = 36

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 用真实单例：ZhiJingData 的构造器是 internal，跨模块测试不可见。
        // Robolectric 把数据库落在隔离的测试沙箱里，不污染开发机上的真实数据。
        data = ZhiJingData.get(context)
        // Robolectric 在同一个类里共享数据库文件，生成图谱也要清掉，
        // 否则「大纲树按父子关系重建」这类假设只有出厂图谱的用例会被泄漏数据破坏
        runBlocking {
            data.kgClaims.deleteAll(); data.kgStates.deleteAll()
            // 条件级记录同样会跨用例泄漏：「错过几次」是累加的，
            // 漏清会让后一个用例读到前一个用例的作答次数。
            data.kgConditionProbes.deleteAll()
            data.kgGenerated.listGraphs().forEach { data.kgGenerated.deleteGraph(it.topicId) }
        }
        assembler = GraphAssembler(context, data)
        // Robolectric 单测默认不挂载 app/src/main/assets，直接把真实文件内容注入
        assembler.seedBundled(loadRealGraph())
    }

    @After
    fun tearDown() {
        runBlocking {
            data.kgClaims.deleteAll(); data.kgStates.deleteAll()
            data.kgConditionProbes.deleteAll()
            data.kgGenerated.listGraphs().forEach { data.kgGenerated.deleteGraph(it.topicId) }
        }
    }

    /**
     * 读取真实的出厂图谱。
     *
     * 从仓库里的 `app/src/main/assets/kg/graph.json` 读，确保测的是**真数据**而不是
     * 手搓的假样本 —— 假样本会让「字段名写错」这类问题漏过去。
     */
    private fun loadRealGraph(): ParsedGraph {
        val candidates = listOf(
            File("src/main/assets/kg/graph.json"),
            File("app/src/main/assets/kg/graph.json")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("找不到 assets/kg/graph.json，当前目录=${File(".").absolutePath}")
        return ParsedGraph.fromJson(file.readText(Charsets.UTF_8), file.path)
    }

    @Test
    fun `出厂图谱能完整解析`() = runBlocking {
        val graph = assembler.bundledGraph()
        assertEquals("1.0", graph.schemaVersion)
        assertEquals(24, graph.outline.size)
        assertEquals(22, graph.cards.size)
        assertEquals(bundledClaims, graph.claims.size)
        assertEquals(16, graph.edges.size)
    }

    @Test
    fun `合并后命题全部标记为出厂且不可删除`() = runBlocking {
        val merged = assembler.load(includeRuntimeClaims = false)
        assertEquals(bundledClaims, merged.claims.size)
        assertTrue(merged.claims.values.all { it.bundled })
        assertEquals(0, merged.runtimeClaimCount)
    }

    @Test
    fun `状态按命题ID派生到命题与卡片角标`() = runBlocking {
        val claimId = "clm.arch.ffn.relu-shape.6092"
        data.kgStates.recordJudgement(claimId, correct = false, misconception = "以为改形状")

        val merged = assembler.load(includeRuntimeClaims = false)
        val claim = merged.claims.getValue(claimId)
        assertEquals(ProbeStatus.CONFUSED, claim.probe.probeStatus)
        assertEquals(listOf("以为改形状"), claim.probe.misconceptions)

        // 该命题归属的卡片角标应为「易混淆」
        val card = merged.cards.first { it.id == claim.cardId }
        assertEquals(ProbeStatus.CONFUSED, card.badgeStatus)
        assertTrue(card.descendable)
        assertEquals(listOf("以为改形状"), card.misconceptionNotes)
    }

    @Test
    fun `未探测命题的卡片角标为未探测`() = runBlocking {
        val merged = assembler.load(includeRuntimeClaims = false)
        val card = merged.cards.first { it.claims.isNotEmpty() }
        // 没有任何状态记录时，卡内命题都是 unprobed
        assertEquals(ProbeStatus.UNPROBED, card.badgeStatus)
    }

    @Test
    fun `没有命题的卡片不可下钻`() = runBlocking {
        val merged = assembler.load(includeRuntimeClaims = false)
        val empty = merged.cards.filter { it.claims.isEmpty() }
        assertTrue(empty.isNotEmpty())
        assertTrue(empty.all { !it.descendable && it.badgeStatus == null })
    }

    @Test
    fun `状态汇总按四态统计`() = runBlocking {
        data.kgStates.recordJudgement("clm.arch.ffn.relu-shape.6092", correct = true, misconception = null)
        data.kgStates.recordJudgement("clm.arch.ffn.softmax-sum.a505", correct = false, misconception = "误")

        val merged = assembler.load(includeRuntimeClaims = false)
        val summary = merged.statusSummary
        assertEquals(1, summary[ProbeStatus.VERIFIED])
        assertEquals(1, summary[ProbeStatus.CONFUSED])
        // 其余仍是未探测（一条判对、一条判错）
        assertEquals(bundledClaims - 2, summary[ProbeStatus.UNPROBED])
    }

    @Test
    fun `未确认的云端候选不进入图谱`() = runBlocking {
        // runtimeClaim 默认就是 PENDING，无需额外参数
        data.kgClaims.addCandidate(runtimeClaim("clm.arch.cloud.test.1a2b", cardId = "crd.s1-1-linear"))
        val merged = assembler.load(includeRuntimeClaims = true)
        assertEquals(bundledClaims, merged.claims.size)
        assertFalse(merged.claims.containsKey("clm.arch.cloud.test.1a2b"))
    }

    @Test
    fun `已确认的云端命题挂到对应卡片`() = runBlocking {
        val id = "clm.arch.cloud.test.1a2b"
        data.kgClaims.addCandidate(runtimeClaim(id, cardId = "crd.s1-1-linear"))
        data.kgClaims.review(id, ClaimReviewStatus.CONFIRMED)

        val merged = assembler.load(includeRuntimeClaims = true)
        assertEquals(bundledClaims + 1, merged.claims.size)
        val added = merged.claims.getValue(id)
        assertFalse(added.bundled)
        assertEquals(ClaimSource.CLOUD, added.source)
        assertEquals("crd.s1-1-linear", added.cardId)

        val card = merged.cards.first { it.id == "crd.s1-1-linear" }
        assertTrue(card.claims.any { it.id == id })
        assertEquals(1, merged.runtimeClaimCount)
    }

    @Test
    fun `离线模式不合并云端命题`() = runBlocking {
        val id = "clm.arch.cloud.test.1a2b"
        data.kgClaims.addCandidate(runtimeClaim(id, cardId = "crd.s1-1-linear"))
        data.kgClaims.review(id, ClaimReviewStatus.CONFIRMED)

        // includeRuntimeClaims = false 时与只读 assets 完全一致
        val merged = assembler.load(includeRuntimeClaims = false)
        assertEquals(bundledClaims, merged.claims.size)
        assertFalse(merged.claims.containsKey(id))
    }

    @Test
    fun `大纲树按父子关系重建`() = runBlocking {
        val merged = assembler.load(includeRuntimeClaims = false)
        val roots = merged.outline
        assertEquals(1, roots.size)
        val root = roots.single()
        assertEquals(1, root.level)
        assertTrue(root.children.isNotEmpty())
        // 子节点的 parentId 指向父节点
        root.children.forEach { assertEquals(root.id, it.parentId) }
    }

    @Test
    fun `已合并到出厂命题的云端命题不重复出现`() = runBlocking {
        // 云端生成的派生 ID 被合并到一条出厂命题
        val bundledId = "clm.arch.ffn.linear-param-batch.2fe7"
        val derived = "clm.arch.cloud.dup.9f9f"
        data.kgClaims.linkMerge(
            com.zhijing.data.model.ClaimMerge(
                sourceClaimId = derived,
                targetClaimId = bundledId,
                similarity = 0.95f,
                basis = "vector",
                createdTime = 1
            )
        )
        data.kgClaims.addCandidate(runtimeClaim(derived, cardId = "crd.s1-1-linear"))
        data.kgClaims.review(derived, ClaimReviewStatus.CONFIRMED)

        val merged = assembler.load(includeRuntimeClaims = true)
        // 图谱里不应因为这条派生命题多出一条
        assertEquals(bundledClaims, merged.claims.size)
    }

    @Test
    fun `深度优先排序把易混淆的放前面`() = runBlocking {
        val card = "crd.s1-2-relu"
        val bundled = assembler.load(includeRuntimeClaims = false)
        val claimIds = bundled.cards.first { it.id == card }.claims.map { it.id }
        // 该卡下至少有两条命题
        assertTrue(claimIds.size >= 2)

        // 把最后一条标成易混淆
        data.kgStates.recordJudgement(claimIds.last(), correct = false, misconception = "误")
        val merged = assembler.load(includeRuntimeClaims = false)
        val ordered = merged.claimsByPriority(card)
        assertEquals(ProbeStatus.CONFUSED, ordered.first().probe.probeStatus)
    }

    @Test
    fun `观察流能收到状态变化`() = runBlocking {
        data.kgStates.recordJudgement("clm.arch.ffn.relu-shape.6092", correct = false, misconception = "误")
        val merged = assembler.observe(includeRuntimeClaims = false).first()
        assertEquals(ProbeStatus.CONFUSED, merged.claims.getValue("clm.arch.ffn.relu-shape.6092").probe.probeStatus)
    }

    /* --------------------------------------------------- 候选确认闭环 */

    @Test
    fun `待确认队列只包含pending候选`() = runBlocking {
        val pendingId = "clm.arch.cloud.pending.1a2b"
        val confirmedId = "clm.arch.cloud.confirmed.c3d4"
        data.kgClaims.addCandidate(runtimeClaim(pendingId, cardId = "crd.s1-1-linear"))
        data.kgClaims.addCandidate(runtimeClaim(confirmedId, cardId = "crd.s1-1-linear"))
        data.kgClaims.review(confirmedId, ClaimReviewStatus.CONFIRMED)

        assertEquals(listOf(pendingId), assembler.pendingCandidates().map { it.claimId })
    }

    @Test
    fun `确认后候选从待确认队列消失并进入图谱`() = runBlocking {
        val id = "clm.arch.cloud.flow.5e6f"
        data.kgClaims.addCandidate(runtimeClaim(id, cardId = "crd.s1-2-relu"))

        // 确认前：在待确认队列里，但不在图谱里
        assertEquals(1, assembler.pendingCandidates().size)
        assertEquals(bundledClaims, assembler.load(includeRuntimeClaims = true).claims.size)

        // 确认后：离开队列，进入图谱
        data.kgClaims.review(id, ClaimReviewStatus.CONFIRMED, now = 1_700_000_000_001L)
        assertTrue(assembler.pendingCandidates().isEmpty())

        val merged = assembler.load(includeRuntimeClaims = true)
        assertEquals(bundledClaims + 1, merged.claims.size)
        assertFalse(merged.claims.getValue(id).bundled)
        assertTrue(merged.cards.first { it.id == "crd.s1-2-relu" }.claims.any { it.id == id })
    }

    @Test
    fun `否决后离开队列且不进入图谱`() = runBlocking {
        val id = "clm.arch.cloud.reject.7a8b"
        data.kgClaims.addCandidate(runtimeClaim(id, cardId = "crd.s1-1-linear"))
        data.kgClaims.review(id, ClaimReviewStatus.REJECTED, now = 1L)

        assertTrue(assembler.pendingCandidates().isEmpty())
        assertFalse(assembler.load(includeRuntimeClaims = true).claims.containsKey(id))
    }

    @Test
    fun `确认的命题在离线模式下仍不出现`() = runBlocking {
        val id = "clm.arch.cloud.offline.9c0d"
        data.kgClaims.addCandidate(runtimeClaim(id, cardId = "crd.s1-1-linear"))
        data.kgClaims.review(id, ClaimReviewStatus.CONFIRMED)

        // 离线模式不查询增量命题表：连已确认的也不混进来
        assertEquals(bundledClaims, assembler.load(includeRuntimeClaims = false).claims.size)
        // 云端增强模式才可见
        assertEquals(bundledClaims + 1, assembler.load(includeRuntimeClaims = true).claims.size)
    }

    @Test
    fun `卡片标题在出厂图谱里能查到`() = runBlocking {
        assertEquals("1.1 Linear 层", assembler.cardTitleOf("crd.s1-1-linear"))
        // 云端新建的卡片不在出厂图谱里，返回 null 让 UI 退化显示路径
        assertNull(assembler.cardTitleOf("crd.content.42"))
    }

    /* --------------------------------------------------- 条件级回写 */

    @Test
    fun `没有条件级记录时卡片不显示事实`() = runBlocking {
        val merged = assembler.load(includeRuntimeClaims = false)
        val card = merged.cards.first { it.id == "crd.s1-1-linear" }

        // 一次都没拨过条件时，卡片保持干净：不要出现「错过 0 次」这种噪音
        assertTrue(card.conditionFacts.isEmpty())
        assertTrue(merged.claims.getValue("clm.arch.ffn.linear-in-dim.4a3e").conditionPath.isEmpty())
    }

    @Test
    fun `答错后卡片显示错过几次与哪条机制`() = runBlocking {
        data.kgConditionProbes.record(
            tripleId = "tri.linear-in-dim.1",
            interventionId = "inv.linear-in-x.4-to-5",
            claimId = "clm.arch.ffn.linear-in-dim.4a3e",
            predictedOption = "HOLDS",
            correctOption = "FAILS",
            misconception = "以为维度不等只是让结果变宽",
            now = 1_000L
        )

        val card = assembler.load(includeRuntimeClaims = false)
            .cards.first { it.id == "crd.s1-1-linear" }
        val fact = card.conditionFacts.single()

        assertEquals(1, fact.wrongCount)
        assertTrue("事实要能说清拨的是哪一根轴：${fact.action}", fact.action.contains("最后一维"))
        assertTrue("事实要带上条件：${fact.statement}", fact.statement.contains("最后一维必须等于"))
        assertEquals("以为维度不等只是让结果变宽", fact.misconception)
    }

    @Test
    fun `答对之后卡片仍记得错过过`() = runBlocking {
        data.kgConditionProbes.record(
            tripleId = "tri.linear-in-dim.1",
            interventionId = "inv.linear-in-x.4-to-5",
            claimId = "clm.arch.ffn.linear-in-dim.4a3e",
            predictedOption = "HOLDS",
            correctOption = "FAILS",
            now = 1_000L
        )
        data.kgConditionProbes.record(
            tripleId = "tri.linear-in-dim.1",
            interventionId = "inv.linear-in-x.4-to-5",
            claimId = "clm.arch.ffn.linear-in-dim.4a3e",
            predictedOption = "FAILS",
            correctOption = "FAILS",
            now = 2_000L
        )

        val card = assembler.load(includeRuntimeClaims = false)
            .cards.first { it.id == "crd.s1-1-linear" }

        // 「在这里错过几次」是历史事实，答对不清零
        assertEquals(1, card.conditionFacts.single().wrongCount)
    }

    @Test
    fun `理解路径给出原来猜什么现在改成了什么`() = runBlocking {
        data.kgConditionProbes.record(
            tripleId = "tri.linear-in-dim.1",
            interventionId = "inv.linear-in-x.4-to-5",
            claimId = "clm.arch.ffn.linear-in-dim.4a3e",
            predictedOption = "WEAKENED",
            correctOption = "FAILS",
            now = 1_000L
        )
        data.kgConditionProbes.record(
            tripleId = "tri.linear-in-dim.1",
            interventionId = "inv.linear-in-x.4-to-5",
            claimId = "clm.arch.ffn.linear-in-dim.4a3e",
            predictedOption = "FAILS",
            correctOption = "FAILS",
            now = 2_000L
        )

        val step = assembler.load(includeRuntimeClaims = false)
            .claims.getValue("clm.arch.ffn.linear-in-dim.4a3e")
            .conditionPath.single()

        assertEquals("只是适用范围变窄", step.previousPredicted)
        assertEquals("不再成立", step.predicted)
        assertEquals("不再成立", step.actual)
        assertEquals(2, step.attempts)
        assertTrue("错过之后改对了，才叫「改念」", step.revised)
        assertTrue(step.action.contains("最后一维"))
    }

    @Test
    fun `理解路径只包含有记录的干预点`() = runBlocking {
        data.kgConditionProbes.record(
            tripleId = "tri.linear-in-dim.1",
            interventionId = "inv.linear-batch.8-to-160",
            claimId = "clm.arch.ffn.linear-in-dim.4a3e",
            predictedOption = "HOLDS",
            correctOption = "HOLDS",
            now = 1_000L
        )

        val path = assembler.load(includeRuntimeClaims = false)
            .claims.getValue("clm.arch.ffn.linear-in-dim.4a3e")
            .conditionPath

        assertEquals("inv.linear-batch.8-to-160", path.single().interventionId)
        assertFalse(path.single().revised)
    }

    /* --------------------------------------------------- 生成图谱合并 */

    @Test
    fun `生成图谱进入合并结果且结构图有层级连线`() = runBlocking {
        val topicId = "ut-gen-1"
        data.kgGenerated.save(
            meta = GeneratedGraph(
                topicId = topicId,
                title = "测试主题",
                createdTime = 1L,
                providerId = "deepseek",
                blockCount = 2,
                verified = false
            ),
            outline = listOf(
                GeneratedOutlineNode(topicId, path = "ch1", parentPath = null, title = "第一章", level = 1, order = 0),
                GeneratedOutlineNode(topicId, path = "ch1.sec1", parentPath = "ch1", title = "1.1 节", level = 2, order = 1),
                GeneratedOutlineNode(topicId, path = "ch2", parentPath = null, title = "第二章", level = 1, order = 2)
            ),
            cards = listOf(
                GeneratedCard(topicId, cardId = "crd.gen.$topicId.0", outlinePath = "ch1.sec1", title = "块A", summary = "", body = "正文A", order = 0),
                GeneratedCard(topicId, cardId = "crd.gen.$topicId.1", outlinePath = "ch2", title = "块B", summary = "", body = "正文B", order = 1)
            )
        )

        val merged = assembler.load(includeRuntimeClaims = false)
        val genRoot = merged.outline.first { it.path == "gen.$topicId" }
        assertEquals(2, genRoot.children.size)
        val ch1 = genRoot.children.first { it.path == "ch1" }
        assertEquals(1, ch1.children.size)
        assertEquals(listOf("crd.gen.$topicId.0"), ch1.children.single().cardIds)

        // 结构图：生成子树内部要有层级连线（根→ch1、ch1→ch1.sec1、根→ch2）
        val layout = com.example.zhijing.kg.ui.TreeLayout.layout(merged)
        val genPaths = setOf("gen.$topicId", "ch1", "ch1.sec1", "ch2")
        val hierarchyLines = layout.lines.filter {
            it.style == com.example.zhijing.kg.ui.TreeLayout.Line.Style.HIERARCHY &&
                it.fromNode in genPaths && it.toNode in genPaths
        }
        assertEquals(3, hierarchyLines.size)
    }

    @Test
    fun `生成图谱的命题挂到卡片并打开下钻`() = runBlocking {
        val topicId = "ut-gen-claims"
        val cardId = "crd.gen.$topicId.0"
        val claimId = "clm.gen.test.1a2b"

        data.kgGenerated.save(
            meta = GeneratedGraph(
                topicId = topicId,
                title = "带命题的主题",
                createdTime = 1L,
                providerId = "deepseek",
                blockCount = 1,
                verified = false
            ),
            outline = listOf(
                GeneratedOutlineNode(topicId, path = "ch1", parentPath = null, title = "第一章", level = 1, order = 0)
            ),
            cards = listOf(
                GeneratedCard(topicId, cardId = cardId, outlinePath = "ch1", title = "块A", summary = "", body = "正文A", order = 0)
            ),
            claims = listOf(
                GeneratedClaim(
                    topicId = topicId,
                    claimId = claimId,
                    cardId = cardId,
                    outlinePath = "ch1",
                    statement = "矩阵加法满足交换律",
                    expected = true
                )
            )
        )

        val merged = assembler.load(includeRuntimeClaims = false)
        val card = merged.cards.first { it.id == cardId }
        val claim = card.claims.single()

        assertTrue("有命题就该能下钻", card.descendable)
        assertEquals("矩阵加法满足交换律", claim.statement)
        assertFalse("生成命题不属于出厂", claim.bundled)
        // 没有原文依据：UI 靠这个字段标「未经核实」
        assertFalse(claim.verified)
        assertEquals(ClaimSource.CLOUD, claim.source)
        // 必须进全图索引，否则状态汇总与 ID 解析看不到它
        assertSame(claim, merged.claims.getValue(claimId))
    }

    @Test
    fun `出厂卡片答完一轮即点亮并解锁下游`() = runBlocking {
        // 覆盖用户实测说「怎么都不行」的三张卡：1.1、1.4 交叉熵、4.1 缩放点积注意力。
        // 每张都是两条命题，全部判对才算点亮（80% 阈值对 2 条命题等于「都要练」）。
        val cases = listOf(
            "crd.s1-1-linear" to listOf(
                "clm.arch.ffn.linear-in-dim.4a3e",
                "clm.arch.ffn.linear-param-batch.2fe7"
            ),
            "crd.s1-4" to listOf(
                "clm.arch.ffn.ce-logits.5a91",
                "clm.arch.ffn.ce-double-softmax.eed8"
            ),
            "crd.s4-1" to listOf(
                "clm.arch.attn.scale.2883",
                "clm.arch.attn.no-scale.1418"
            )
        )
        cases.forEach { (_, claimIds) ->
            claimIds.forEach { id ->
                data.kgStates.recordJudgement(id, correct = true, misconception = null)
            }
        }

        val merged = assembler.load(includeRuntimeClaims = false)
        val skill = SkillProgress.of(merged)

        cases.forEach { (cardId, claimIds) ->
            assertEquals("$cardId 的命题应该都已练过", claimIds.size, skill.cards.getValue(cardId).practicedCount)
            assertEquals("$cardId 答完一轮就该点亮", SkillProgress.State.LIT, skill.stateOf(cardId))
        }
        // 下游从「未解锁」变「现在能学」
        assertEquals(SkillProgress.State.READY, skill.stateOf("crd.s1-2-relu"))
    }

    @Test
    fun `生成图谱的命题作答后点亮并解锁下游`() = runBlocking {
        val topicId = "ut-gen-ready"
        val cardA = "crd.gen.$topicId.0"
        val cardB = "crd.gen.$topicId.1"
        val claimId = "clm.gen.test.ready"

        data.kgGenerated.save(
            meta = GeneratedGraph(
                topicId = topicId,
                title = "点亮测试",
                createdTime = 1L,
                providerId = "deepseek",
                blockCount = 2,
                verified = false
            ),
            outline = listOf(
                GeneratedOutlineNode(topicId, path = "ch1", parentPath = null, title = "第一章", level = 1, order = 0)
            ),
            cards = listOf(
                GeneratedCard(topicId, cardA, "ch1", "块A", "", "正文A", 0),
                GeneratedCard(topicId, cardB, "ch1", "块B", "", "正文B", 1)
            ),
            prerequisites = listOf(
                GeneratedPrerequisite(topicId, fromCardId = cardA, toCardId = cardB, kind = "required")
            ),
            claims = listOf(
                GeneratedClaim(
                    topicId = topicId,
                    claimId = claimId,
                    cardId = cardA,
                    outlinePath = "ch1",
                    statement = "矩阵加法满足交换律",
                    expected = true
                ),
                // B 也要有自己的命题：没有命题的卡在判定里等同「无需学习」，
                // 那样 B 会直接算点亮，测不出「解锁」这一步
                GeneratedClaim(
                    topicId = topicId,
                    claimId = "clm.gen.test.downstream",
                    cardId = cardB,
                    outlinePath = "ch1",
                    statement = "矩阵乘法不满足交换律",
                    expected = true
                )
            )
        )

        // 答对生成命题的唯一一条
        data.kgStates.recordJudgement(claimId, correct = true, misconception = null)

        val merged = assembler.load(includeRuntimeClaims = false)
        val skill = SkillProgress.of(merged)

        // 根因回归：load() 只按出厂命题 id 查状态时，生成命题永远停留在 UNPROBED
        assertEquals(SkillProgress.State.LIT, skill.stateOf(cardA))
        assertEquals(SkillProgress.State.READY, skill.stateOf(cardB))
    }

    @Test
    fun `parentPath断链时按level重建层级不丢子树`() = runBlocking {
        val topicId = "ut-gen-2"
        data.kgGenerated.save(
            meta = GeneratedGraph(
                topicId = topicId,
                title = "断链主题",
                createdTime = 1L,
                providerId = "deepseek",
                blockCount = 1,
                verified = false
            ),
            outline = listOf(
                GeneratedOutlineNode(topicId, path = "ch1", parentPath = null, title = "第一章", level = 1, order = 0),
                // parentPath 指向不存在的节点：旧实现会把该子树静默丢掉
                GeneratedOutlineNode(topicId, path = "ch1.sec1", parentPath = "ghost", title = "1.1 节", level = 2, order = 1)
            ),
            cards = listOf(
                GeneratedCard(topicId, cardId = "crd.gen.$topicId.0", outlinePath = "ch1.sec1", title = "块A", summary = "", body = "正文A", order = 0)
            )
        )

        val merged = assembler.load(includeRuntimeClaims = false)
        val genRoot = merged.outline.first { it.path == "gen.$topicId" }
        assertEquals(1, genRoot.children.size)
        val ch1 = genRoot.children.single()
        assertEquals("ch1", ch1.path)
        // level=2 < 断链也拦不住：按祖先栈重建后仍挂在 ch1 下
        assertEquals(1, ch1.children.size)
        assertEquals("ch1.sec1", ch1.children.single().path)
        assertEquals("crd.gen.$topicId.0", ch1.children.single().cardIds.single())
    }

    @Test
    fun `观察流也包含生成图谱`() = runBlocking {
        val topicId = "ut-gen-3"
        data.kgGenerated.save(
            meta = GeneratedGraph(topicId = topicId, title = "观察流主题", createdTime = 1L, blockCount = 1, verified = false),
            outline = listOf(
                GeneratedOutlineNode(topicId, path = "only", parentPath = null, title = "唯一块", level = 1, order = 0)
            ),
            cards = listOf(
                GeneratedCard(topicId, cardId = "crd.gen.$topicId.0", outlinePath = "only", title = "块", summary = "", body = "正文", order = 0)
            )
        )

        val merged = assembler.observe(includeRuntimeClaims = false).first()
        assertTrue(merged.outline.any { it.path == "gen.$topicId" })
        assertTrue(merged.cards.any { it.id == "crd.gen.$topicId.0" })
    }

    private fun runtimeClaim(
        id: String,
        cardId: String
    ) = RuntimeClaim(
        claimId = id,
        statement = "云端生成的测试命题",
        expected = true,
        conditions = listOf("条件"),
        explanation = "解释",
        misconception = null,
        evidence = "证据",
        cardId = cardId,
        outlinePath = "s1-1-linear",
        source = ClaimSource.CLOUD,
        reviewStatus = ClaimReviewStatus.PENDING,
        providerId = "deepseek",
        createdTime = 1_700_000_000_000,
        reviewedTime = null
    )
}
