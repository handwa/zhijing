package com.example.zhijing.kg

import com.example.zhijing.kg.ui.TreeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结构图布局的基础测试。
 *
 * ## 现在的模型
 *
 * 结构图**只画大纲节点**，卡片不再单独成元素（卡与节点 1:1、标题 100% 相同）。
 * 每个节点携带它挂的卡片 id，点开在底部弹窗看内容。
 *
 * 因此旧测试里「卡片在节点右侧」「卡片与节点同列」这类断言全部失效，
 * 改为断言节点间的几何关系。
 */
class TreeLayoutTest {

    private fun outline(
        id: String,
        path: String,
        title: String,
        level: Int,
        order: Int,
        children: List<GraphOutlineNode> = emptyList()
    ) = GraphOutlineNode(
        id = id,
        title = title,
        level = level,
        parentId = null,
        path = path,
        order = order,
        children = children,
        cardIds = emptyList()
    )

    private fun card(id: String, title: String, outlinePath: String) = GraphCard(
        id = id,
        title = title,
        outlinePath = outlinePath,
        summary = "摘要",
        body = "正文",
        claims = emptyList(),
    )

    /** 两个主题（各一章一卡），用于验证分区。 */
    private fun sampleGraph(): MergedGraph = MergedGraph(
        schemaVersion = "1.0",
        generatedAt = "",
        outline = listOf(
            outline("out.a", "a", "第一章", 1, 0),
            outline("out.b", "b", "第二章", 1, 1),
        ),
        cards = listOf(
            card("crd.a1", "卡片A", "a"),
            card("crd.b1", "卡片B", "b"),
        ),
        claims = emptyMap(),
        edges = emptyMap(),
    )

    /* ------------------------------------------------------ 主题分区 */

    @Test
    fun `每个主题一个分区`() {
        val r = TreeLayout.layout(sampleGraph())
        assertEquals(2, r.groups.size)
        assertEquals(listOf("第一章", "第二章"), r.groups.map { it.title })
    }

    @Test
    fun `分区纵向排列且不重叠`() {
        val r = TreeLayout.layout(sampleGraph())
        val sorted = r.groups.sortedBy { it.y }
        assertTrue(
            "第二个分区应在第一个下方",
            sorted[1].y >= sorted[0].y + sorted[0].height
        )
    }

    @Test
    fun `分区标记出厂与生成`() {
        val g = sampleGraph()
        val generated = outline("out.gen", "gen.t1", "生成主题", 1, 2)
        val r = TreeLayout.layout(g.copy(outline = g.outline + generated))
        assertTrue(r.groups.first { it.title == "第一章" }.isBundled)
        assertFalse("gen. 前缀应标为生成", r.groups.first { it.title == "生成主题" }.isBundled)
    }

    /* ------------------------------------------------------ 节点几何 */

    @Test
    fun `列内节点不重叠`() {
        val r = TreeLayout.layout(sampleGraph())
        val chapters = r.nodes.filter { it.title.startsWith("第") }.sortedBy { it.y }
        chapters.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "节点不能重叠：${a.title}(y=${a.y}) 与 ${b.title}(y=${b.y})",
                b.y >= a.y + a.height
            )
        }
    }

    @Test
    fun `布局是确定性的`() {
        val a = TreeLayout.layout(sampleGraph())
        val b = TreeLayout.layout(sampleGraph())
        assertEquals(
            a.nodes.map { "${it.nodePath}@${it.x},${it.y}" },
            b.nodes.map { "${it.nodePath}@${it.x},${it.y}" }
        )
        assertEquals(a.lines.size, b.lines.size)
    }

    @Test
    fun `空图谱返回空布局`() {
        val r = TreeLayout.layout(
            MergedGraph("1.0", "", emptyList(), emptyList(), emptyMap(), emptyMap())
        )
        assertTrue(r.isEmpty)
        assertEquals(0f, r.contentWidth)
        assertTrue(r.groups.isEmpty())
    }

    /* ------------------------------------------------------ 卡片关联 */

    @Test
    fun `节点携带它的卡片 id`() {
        val r = TreeLayout.layout(sampleGraph())
        assertEquals(listOf("crd.a1"), r.nodes.first { it.nodePath == "a" }.cardIds)
        assertEquals(listOf("crd.b1"), r.nodes.first { it.nodePath == "b" }.cardIds)
    }

    @Test
    fun `没有卡片的节点不可下钻`() {
        val g = sampleGraph()
        val emptyNode = outline("out.empty", "empty", "空组织节点", 1, 9)
        val r = TreeLayout.layout(g.copy(outline = g.outline + emptyNode))

        val withCard = r.nodes.first { it.nodePath == "a" }
        val noCard = r.nodes.first { it.nodePath == "empty" }
        assertTrue("有卡片→可点开", withCard.descendable)
        assertFalse("无卡片→不可点开", noCard.descendable)
        assertTrue(noCard.cardIds.isEmpty())
    }

    /* ------------------------------------------------------ 连线 */

    @Test
    fun `层级连线一律向右`() {
        val r = TreeLayout.layout(sampleGraph())
        val hierarchy = r.lines.filter { it.style == TreeLayout.Line.Style.HIERARCHY }
        // sampleGraph 两章都是根，没有父子关系 → 没有层级线
        // 这里只断言「若有，则一律向右」，父子情形由 RealTreeIndentTest 覆盖
        hierarchy.forEach { line ->
            assertTrue("层级线必须向右", line.toX > line.fromX)
        }
    }

    @Test
    fun `前置依赖映射到节点级`() {
        // 两张卡分属不同节点，A 是 B 的必须前置
        val g = MergedGraph(
            schemaVersion = "1.0",
            generatedAt = "",
            outline = listOf(
                outline("out.a", "a", "第一章", 1, 0),
                outline("out.b", "b", "第二章", 1, 1),
            ),
            cards = listOf(
                card("crd.a1", "卡片A", "a").copy(
                    prerequisites = listOf(
                        PrerequisiteLink(
                            from = "crd.a1",
                            to = "crd.b1",
                            kind = PrerequisiteKind.REQUIRED,
                            reason = "测试",
                        )
                    )
                ),
                card("crd.b1", "卡片B", "b"),
            ),
            claims = emptyMap(),
            edges = emptyMap(),
        )
        val r = TreeLayout.layout(g)
        val prereq = r.lines.filter { it.style == TreeLayout.Line.Style.PREREQUISITE_REQUIRED }
        assertEquals("应有一条必须前置线", 1, prereq.size)
        assertEquals("a", prereq.single().fromNode)
        assertEquals("b", prereq.single().toNode)
    }

    @Test
    fun `同一节点内两张卡的前置退化为自环被丢弃`() {
        // 两张卡都挂在 node a 下 → 映射到节点级是自环，应被丢掉
        val g = MergedGraph(
            schemaVersion = "1.0",
            generatedAt = "",
            outline = listOf(outline("out.a", "a", "第一章", 1, 0)),
            cards = listOf(
                card("crd.a1", "卡片A1", "a").copy(
                    prerequisites = listOf(
                        PrerequisiteLink("crd.a1", "crd.a2", PrerequisiteKind.REQUIRED, "内部依赖")
                    )
                ),
                card("crd.a2", "卡片A2", "a"),
            ),
            claims = emptyMap(),
            edges = emptyMap(),
        )
        val r = TreeLayout.layout(g)
        val prereq = r.lines.filter {
            it.style == TreeLayout.Line.Style.PREREQUISITE_REQUIRED ||
                it.style == TreeLayout.Line.Style.PREREQUISITE_RECOMMENDED
        }
        assertTrue("自环应被丢弃", prereq.isEmpty())
    }

    @Test
    fun `连线记录了端点节点 供选中高亮使用`() {
        val g = MergedGraph(
            schemaVersion = "1.0",
            generatedAt = "",
            outline = listOf(
                outline("out.a", "a", "第一章", 1, 0, children = listOf(outline("out.a1", "a1", "1.1 节", 2, 0))),
            ),
            cards = listOf(card("crd.a1", "卡", "a1")),
            claims = emptyMap(),
            edges = emptyMap(),
        )
        val r = TreeLayout.layout(g)
        val hierarchy = r.lines.filter { it.style == TreeLayout.Line.Style.HIERARCHY }
        assertTrue(hierarchy.isNotEmpty())
        hierarchy.forEach { line ->
            assertEquals("a", line.fromNode)
            assertEquals("a1", line.toNode)
        }
    }
}
