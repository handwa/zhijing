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
    fun bundledTriplesAreTwoAndAllValid() {
        val triples = loadGraph().triples

        assertEquals(2, triples.size)
        triples.forEach { triple ->
            assertTrue(
                "三元组 ${triple.id} 未通过入库门槛：${triple.problems()}",
                triple.isValid,
            )
            assertTrue("每个干预点都要带契约才能本地判定", triple.interventions.all { it.contract != null })
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
}
