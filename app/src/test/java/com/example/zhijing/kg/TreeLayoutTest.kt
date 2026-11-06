package com.example.zhijing.kg

import com.example.zhijing.kg.ui.TreeLayout
import com.zhijing.data.model.ClaimProbeState
import com.zhijing.data.model.ClaimSource
import com.zhijing.data.model.ProbeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结构图布局测试。
 *
 * 守护「语义布局」的三条性质（它们正是力导向给不了的）：
 * 1. **位置稳定**：同样输入永远同样坐标；
 * 2. **一级章节一列**：第几张章节就在第几列，横向位置单调递增；
 * 3. **列内不重叠**：同一列里任意两个节点不重叠。
 */
class TreeLayoutTest {

    private fun claim(id: String, cardId: String) = GraphClaim(
        id = id,
        statement = "测试命题",
        expected = true,
        conditions = listOf("条件"),
        evidence = "",
        cardId = cardId,
        outlinePath = cardId,
        question = "成立吗",
        explanation = "",
        errorKinds = emptyList(),
        misconception = "",
        edgeIds = emptyList(),
        probe = ClaimProbeState(id),
        source = ClaimSource.BUNDLED,
        bundled = true
    )

    private fun card(id: String, title: String, outlinePath: String, claims: List<GraphClaim>) =
        GraphCard(
            id = id,
            title = title,
            outlinePath = outlinePath,
            summary = "摘要",
            body = "正文",
            claims = claims,
            badgeStatus = ProbeStatus.UNPROBED
        )

    /** 两个一级章节，第一章有 2 张卡，第二章有 1 张卡。 */
    private fun sampleGraph(): MergedGraph {
        // 卡片的 outlinePath 必须与对应大纲节点的 path 一致 —— 这是装配层的约定
        // （`GraphAssembler` 里 `outlinePath` 就是 `outline.path`）。
        val c1 = card("crd.a1", "A1", "a", listOf(claim("clm.1", "crd.a1")))
        val c2 = card("crd.a2", "A2", "a", emptyList())
        val c3 = card("crd.b1", "B1", "b", listOf(claim("clm.2", "crd.b1")))
        val outline = listOf(
            GraphOutlineNode("out.a", "第一章", 1, null, "a", 0, emptyList(), emptyList()),
            GraphOutlineNode("out.b", "第二章", 1, null, "b", 1, emptyList(), emptyList())
        )
        return MergedGraph(
            schemaVersion = "1.0",
            generatedAt = "",
            outline = outline,
            cards = listOf(c1, c2, c3),
            claims = mapOf(
                "clm.1" to c1.claims[0],
                "clm.2" to c3.claims[0]
            ),
            edges = emptyMap()
        )
    }

    @Test
    fun `一级章节一列且横向递增`() {
        val r = TreeLayout.layout(sampleGraph())
        val sections = r.nodes.filter { it.isSection }
        assertEquals(2, sections.size)
        // 两列的 x 必须不同，且按 order 递增
        assertTrue("第二章应在第一章右侧", sections[1].x > sections[0].x)

        // 第一章的所有节点（标题 + 两张卡）横向位置必须一致
        val firstSectionX = sections[0].x
        val colA = r.nodes.filter { it.cardId == "crd.a1" || it.cardId == "crd.a2" }
        assertEquals(2, colA.size)
        assertTrue("第一章的卡片应与章节标题同列", colA.all { it.x == firstSectionX })

        // 第二章的卡在右侧
        val b1 = r.nodes.first { it.cardId == "crd.b1" }
        assertTrue("第二章的卡应在右侧", b1.x > firstSectionX)
    }

    @Test
    fun `列内节点不重叠`() {
        val r = TreeLayout.layout(sampleGraph())
        val cards = r.nodes.filter { !it.isSection }
        // 按列分组后检查纵向不重叠
        cards.groupBy { it.x }.values.forEach { column ->
            val sorted = column.sortedBy { it.y }
            sorted.zipWithNext { upper, lower ->
                assertTrue(
                    "节点 ${upper.title} 与 ${lower.title} 在列内重叠",
                    lower.y >= upper.y + upper.height
                )
            }
        }
    }

    @Test
    fun `布局是确定性的`() {
        val a = TreeLayout.layout(sampleGraph())
        val b = TreeLayout.layout(sampleGraph())
        assertEquals(a.nodes.map { "${it.cardId}@${it.x},${it.y}" }, b.nodes.map { "${it.cardId}@${it.x},${it.y}" })
        assertEquals(a.lines.size, b.lines.size)
    }

    @Test
    fun `空图谱返回空布局`() {
        val empty = MergedGraph("", "", emptyList(), emptyList(), emptyMap(), emptyMap())
        val r = TreeLayout.layout(empty)
        assertTrue(r.isEmpty)
        assertEquals(0f, r.contentWidth)
    }

    @Test
    fun `命题数显示在节点上`() {
        val r = TreeLayout.layout(sampleGraph())
        val a1 = r.nodes.first { it.cardId == "crd.a1" }
        assertEquals(1, a1.claimCount)
        val a2 = r.nodes.first { it.cardId == "crd.a2" }
        assertEquals(0, a2.claimCount)
        // 有命题的卡可下钻
        assertTrue(a1.descendable)
        assertTrue(!a2.descendable)
    }

    @Test
    fun `层级连线连接章节标题与首张卡`() {
        val r = TreeLayout.layout(sampleGraph())
        val hierarchyLines = r.lines.filter { it.style == TreeLayout.Line.Style.HIERARCHY }
        // 每个章节一条
        assertEquals(2, hierarchyLines.size)
        hierarchyLines.forEach { line ->
            // 竖线：起止 x 相同
            assertEquals(line.fromX, line.toX, 0.001f)
            assertTrue(line.toY > line.fromY)
        }
    }

    @Test
    fun `内容尺寸覆盖所有节点`() {
        val r = TreeLayout.layout(sampleGraph())
        r.nodes.forEach { node ->
            assertTrue(
                "节点 ${node.title} 超出内容宽度",
                node.x + node.width <= r.contentWidth + 0.01f
            )
            assertTrue(
                "节点 ${node.title} 超出内容高度",
                node.y + node.height <= r.contentHeight + 0.01f
            )
        }
    }
}
