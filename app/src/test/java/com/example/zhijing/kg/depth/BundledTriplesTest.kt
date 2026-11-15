package com.example.zhijing.kg.depth

import com.example.zhijing.kg.ParsedGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 校验随包的 `kg/graph.json`。
 *
 * 单测里手写 JSON 只能验证解析器逻辑，验证不了**仓库里那份文件**：
 * 少一个逗号会让 `GraphAssembler` 整体降级为空图谱，而这类错误在纯逻辑测试里发现不了。
 *
 * 这里直接读源文件而不是 `context.assets`：`:app` 的单元测试没有开启
 * `includeAndroidResources`，走 Robolectric 的 AssetManager 会拿不到 assets。
 * 读源文件同样能守住「文件本身是合法 JSON 且 triples 完整」这一目标。
 *
 * 数量断言（6 条三元组 / 24 个干预点）是**内容生产的验收口径**：
 * 改动数据时同步改这里，避免「悄悄少了两条」这种事没人发现。
 */
class BundledTriplesTest {

    private fun graphFile(): File {
        // Gradle 单测的工作目录通常是模块目录；兼容从仓库根目录跑的情况
        val candidates = listOf(
            File("src/main/assets/kg/graph.json"),
            File("app/src/main/assets/kg/graph.json"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("找不到 kg/graph.json，候选路径：${candidates.joinToString { it.absolutePath }}")
    }

    private fun loadGraph(): ParsedGraph {
        val text = graphFile().readText(Charsets.UTF_8)
        return ParsedGraph.fromJson(text, "kg/graph.json")
    }

    @Test
    fun bundledGraphStillParsesWithTriples() {
        val graph = loadGraph()

        // 加了 triples 之后，原有结构不能被带崩
        assertTrue("outline 不应为空", graph.outline.isNotEmpty())
        assertTrue("cards 不应为空", graph.cards.isNotEmpty())
        assertTrue("claims 不应为空", graph.claims.isNotEmpty())
        assertTrue("prerequisites 不应为空", graph.prerequisites.isNotEmpty())
    }

    @Test
    fun bundledTriplesCoverSixClaimsAndTwentyFourInterventions() {
        val triples = loadGraph().triples

        assertEquals("三元组条数", 6, triples.size)
        assertEquals("干预点总数", 24, triples.sumOf { it.interventions.size })
        assertEquals("覆盖的命题数", 6, triples.map { it.claimId }.distinct().size)
    }

    @Test
    fun everyTriplePassesEntryGateAndPointsToRealNodes() {
        val graph = loadGraph()
        val claimIds = graph.claims.map { it.id }.toSet()
        val cardIds = graph.cards.map { it.id }.toSet()

        graph.triples.forEach { triple ->
            assertTrue(
                "三元组 ${triple.id} 未通过入库门槛：${triple.problems()}",
                triple.isValid,
            )
            assertTrue("三元组 ${triple.id} 指向不存在的命题 ${triple.claimId}", triple.claimId in claimIds)
            assertTrue("三元组 ${triple.id} 指向不存在的卡片 ${triple.cardId}", triple.cardId in cardIds)
        }
    }

    /**
     * 契约的两种表达方式，验收口径不同：
     *
     * - **改符号**（`overrides` 非空）：契约描述的是**基线**，干预是把符号改成新值。
     *   这种契约必须通过 `ContractEvaluator.validate()` —— 基线不成立说明契约写错了。
     * - **改结构**（`overrides` 为空，典型是 softmax 的轴）：契约本身就是干预后的形态，
     *   没有可拨的符号。`validate()` 要求「未拨动时成立」，对它不适用；
     *   能守的是「求值器能给出确定结论」，即不是 `Invalid`（契约写坏）。
     */
    @Test
    fun computableTriplesHaveValidContractsAndPresetTriplesHaveOutcomes() {
        loadGraph().triples.forEach { triple ->
            if (triple.computable) {
                triple.interventions.forEach { intervention ->
                    val contract = intervention.contract
                        ?: error("可计算三元组 ${triple.id} 的干预 ${intervention.id} 没带契约")

                    if (intervention.overrides.isNotEmpty()) {
                        val result = ContractEvaluator.validate(contract)
                        assertTrue(
                            "契约不自洽（${intervention.id}）：$result",
                            result is ContractEvaluator.Result.Holds,
                        )
                    } else {
                        assertTrue(
                            "结构型契约 ${intervention.id} 的符号表不允许为空",
                            contract.symbols.isNotEmpty(),
                        )
                        assertTrue(
                            "结构型干预 ${intervention.id} 的契约求值不成立（契约写坏了）",
                            ContractEvaluator.evaluate(contract) !is ContractEvaluator.Result.Invalid,
                        )
                    }

                    assertTrue(
                        "干预 ${intervention.id} 求不出确定后果，等于不可判",
                        intervention.resolveOutcome().outcome != Outcome.UNKNOWN,
                    )
                }
            } else {
                triple.interventions.forEach { intervention ->
                    assertEquals(
                        "预置答案的干预 ${intervention.id} 不该带契约",
                        null,
                        intervention.contract,
                    )
                    assertTrue(
                        "预置答案必须是合法选项（${intervention.id}）",
                        intervention.outcome in Outcome.CHOICES,
                    )
                }
            }
        }
    }

    @Test
    fun everyInterventionHasAMisjudgementThatExplainsTheMechanism() {
        loadGraph().triples.flatMap { it.interventions }.forEach { intervention ->
            assertTrue(
                "干预 ${intervention.id} 没有误判库，答错时无法解释",
                intervention.misjudgements.isNotEmpty(),
            )
            intervention.misjudgements.forEach { misjudgement ->
                assertTrue(
                    "干预 ${intervention.id} 的误判 predicted=${misjudgement.predicted} 不是合法选项",
                    misjudgement.predicted in Outcome.CHOICES,
                )
                assertTrue(
                    "干预 ${intervention.id} 的纠正没回到机制上",
                    misjudgement.correction.length >= 10,
                )
            }
        }
    }

    @Test
    fun linearTripleIsComputableInBothDirections() {
        val triple = loadGraph().triples.single { it.claimId == "clm.arch.ffn.linear-in-dim.4a3e" }
        val outcomes = triple.interventions
            .sortedBy { it.id }
            .map { it.resolveOutcome().outcome }

        // inv.linear-batch...（仍成立）在前，inv.linear-in-x...（不成立）在后
        assertEquals(listOf(Outcome.HOLDS, Outcome.FAILS), outcomes)
    }

    @Test
    fun softmaxTripleFailsOnlyOnWrongAxis() {
        val triple = loadGraph().triples.single { it.claimId == "clm.arch.ffn.softmax-sum.a505" }

        val classAxis = triple.interventions.single { it.id.contains("class-axis") }
        val batchAxis = triple.interventions.single { it.id.contains("batch-axis") }

        assertEquals(Outcome.HOLDS, classAxis.resolveOutcome().outcome)
        assertEquals(Outcome.FAILS, batchAxis.resolveOutcome().outcome)
        assertTrue(batchAxis.resolveOutcome().detail.contains("不是 1"))
    }

    @Test
    fun residualTripleHoldsOnlyWhenBothBranchesMatch() {
        val triple = loadGraph().triples.single { it.claimId == "clm.arch.train.residual-shape.b560" }

        val widenedTogether = triple.interventions.single { it.id.contains("both-widened") }
        val widenedOnly = triple.interventions.single { it.id.contains("main-widened-only") }

        assertEquals(Outcome.HOLDS, widenedTogether.resolveOutcome().outcome)
        assertEquals(Outcome.FAILS, widenedOnly.resolveOutcome().outcome)
        // 失败原因必须能直接讲给用户听：指出是哪两个数对不上
        assertTrue(widenedOnly.resolveOutcome().detail.contains("768"))
    }

    @Test
    fun presetTriplesTeachWithWeakenedAndBecomeOther() {
        val preset = loadGraph().triples.filter { !it.computable }
        assertTrue("至少要有两条预置答案的三元组", preset.size >= 2)

        val outcomes = preset.flatMap { t -> t.interventions.map { it.resolveOutcome().outcome } }.toSet()
        // 预置答案的题库必须用满四选一：只会出「成立/不成立」等于把题型做成判断题
        assertTrue("预置题库缺少 WEAKENED", Outcome.WEAKENED in outcomes)
        assertTrue("预置题库缺少 BECOMES_OTHER", Outcome.BECOMES_OTHER in outcomes)
        assertTrue("预置题库缺少 HOLDS", Outcome.HOLDS in outcomes)
        assertTrue("预置题库缺少 FAILS", Outcome.FAILS in outcomes)
    }

    @Test
    fun s5SectionCardOwnsAClaimAfterContentFill() {
        val graph = loadGraph()

        // crd.s5「常见误区速查」是章节卡，原本 claimIds 为空，下钻入口是灰的；
        // 补上命题后它才和 s5-1 一样能点进去。
        val s5Claims = graph.claims.filter { it.cardId == "crd.s5" }
        assertTrue("crd.s5 没有主归属命题", s5Claims.isNotEmpty())
        assertTrue(
            "crd.s5 的命题必须落在同一条大纲路径上",
            s5Claims.all { it.outlinePath == "s5" },
        )
    }
}
