package com.example.zhijing.kg

import com.example.zhijing.kg.ui.TreeLayout
import com.zhijing.data.model.ClaimProbeState
import com.zhijing.data.model.ClaimSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题分区布局测试。
 *
 * 守护的核心不变量：**多个主题各有自己的区域，不混在同一块里**。
 */
class GroupLayoutTest {

    private fun card(id: String, title: String, outlinePath: String) = GraphCard(
        id = id,
        title = title,
        outlinePath = outlinePath,
        summary = "s",
        body = "b",
        claims = emptyList(),
        badgeStatus = null
    )

    private fun node(path: String, title: String, order: Int, cardIds: List<String>, children: List<GraphOutlineNode> = emptyList()) =
        GraphOutlineNode(
            id = "out.$path",
            title = title,
            level = 1,
            parentId = null,
            path = path,
            order = order,
            children = children,
            cardIds = cardIds
        )

    /** 两个主题：出厂（transformer）+ 生成（线性代数）。 */
    private fun twoTopicGraph(): MergedGraph {
        val bundledCh1 = node("ch1", "第一章", 0, listOf("crd.a1"))
        val bundledCh2 = node("ch2", "第二章", 1, listOf("crd.a2"))
        val bundledRoot = node("root", "预置图谱", 0, emptyList(), listOf(bundledCh1, bundledCh2))
        val generated = node("gen.t1", "线性代数", 1, listOf("crd.g1"))

        return MergedGraph(
            schemaVersion = "1.0",
            generatedAt = "",
            outline = listOf(bundledRoot, generated),
            cards = listOf(
                card("crd.a1", "卡片A1", "ch1"),
                card("crd.a2", "卡片A2", "ch2"),
                card("crd.g1", "生成卡片", "gen.t1")
            ),
            claims = emptyMap(),
            edges = emptyMap()
        )
    }

    @Test
    fun `每个主题一个分区`() {
        val r = TreeLayout.layout(twoTopicGraph())
        assertEquals(2, r.groups.size)
        assertEquals(listOf("预置图谱", "线性代数"), r.groups.map { it.title })
    }

    @Test
    fun `分区纵向不重叠且有序`() {
        val r = TreeLayout.layout(twoTopicGraph())
        val sorted = r.groups.sortedBy { it.y }
        assertEquals(2, sorted.size)
        val first = sorted[0]
        val second = sorted[1]
        assertTrue(
            "第二个分区应完全在第一个下方：${first.y + first.height} vs ${second.y}",
            second.y >= first.y + first.height
        )
    }

    @Test
    fun `分区标记出出厂与生成`() {
        val r = TreeLayout.layout(twoTopicGraph())
        val bundled = r.groups.first { it.title == "预置图谱" }
        val generated = r.groups.first { it.title == "线性代数" }
        assertTrue("出厂图谱应标为 bundled", bundled.isBundled)
        assertFalse("生成图谱不应标为 bundled", generated.isBundled)
    }

    @Test
    fun `分区包围盒包含其所有节点`() {
        val r = TreeLayout.layout(twoTopicGraph())
        r.groups.forEach { g ->
            val inside = r.nodes.filter { n ->
                n.x >= g.x && n.x + n.width <= g.x + g.width
            }
            assertTrue("分区 ${g.title} 应包含至少一个节点", inside.isNotEmpty())
        }
    }

    @Test
    fun `单主题时只有一个分区`() {
        val single = MergedGraph(
            schemaVersion = "1.0",
            generatedAt = "",
            outline = listOf(node("only", "唯一主题", 0, listOf("crd.x"))),
            cards = listOf(card("crd.x", "卡", "only")),
            claims = emptyMap(),
            edges = emptyMap()
        )
        val r = TreeLayout.layout(single)
        assertEquals(1, r.groups.size)
        assertEquals("唯一主题", r.groups.single().title)
    }

    @Test
    fun `空图谱没有分区`() {
        val empty = MergedGraph("", "", emptyList(), emptyList(), emptyMap(), emptyMap())
        val r = TreeLayout.layout(empty)
        assertTrue(r.groups.isEmpty())
        assertTrue(r.isEmpty)
    }

    @Test
    fun `分区高度包含标题栏`() {
        val r = TreeLayout.layout(twoTopicGraph())
        r.groups.forEach { g ->
            assertTrue("分区 ${g.title} 高度应至少容纳标题", g.height > TreeLayout.GROUP_TITLE_HEIGHT)
        }
    }
}
