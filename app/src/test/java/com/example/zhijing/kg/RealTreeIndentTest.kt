package com.example.zhijing.kg

import com.example.zhijing.kg.ui.TreeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用**真实的出厂图谱数据形状**验证树布局。
 *
 * ## 结构图现在的形态
 *
 * 只画大纲节点（卡片已删除 —— 卡与节点 1:1 且标题 100% 重复）。
 * 层级沿 x 展开，同父的兄弟沿 y 分布，父节点垂直居中对齐子节点。
 *
 * 这组测试锁住三件事：
 * 1. 层级越深越靠右
 * 2. 兄弟同列、同 x
 * 3. 每个节点都带了它的卡片 id（底部弹窗据此取内容）
 */
class RealTreeIndentTest {

    private fun outline(
        id: String,
        path: String,
        title: String,
        level: Int,
        order: Int,
        parentId: String? = null,
        children: List<GraphOutlineNode> = emptyList()
    ) = GraphOutlineNode(
        id = id,
        title = title,
        level = level,
        parentId = parentId,
        path = path,
        order = order,
        children = children,
        cardIds = emptyList()
    )

    /** 测试只关心布局坐标，命题内容不参与，因此不构造 GraphClaim。 */
    private fun card(id: String, title: String, path: String) = GraphCard(
        id = id,
        title = title,
        outlinePath = path,
        summary = "摘要：$title",
        body = "正文：$title",
        claims = emptyList(),
    )


    /** 复刻 graph.json 的形状：1 root、2 章、每章 2 节，每节 1 张卡。 */
    private fun graph(): MergedGraph {
        val s11 = outline("out.s1-1", "s1-1", "1.1 Linear", 3, 0, "out.s1")
        val s12 = outline("out.s1-2", "s1-2", "1.2 ReLU", 3, 1, "out.s1")
        val s21 = outline("out.s2-1", "s2-1", "2.1 形状", 3, 0, "out.s2")
        val s22 = outline("out.s2-2", "s2-2", "2.2 取值", 3, 1, "out.s2")
        val s1 = outline("out.s1", "s1", "1 前馈网络", 2, 0, "out.root", listOf(s11, s12))
        val s2 = outline("out.s2", "s2", "2 层间依赖", 2, 1, "out.root", listOf(s21, s22))
        val root = outline("out.root", "root", "Transformer 架构", 1, 0, null, listOf(s1, s2))

        return MergedGraph(
            schemaVersion = "1.0",
            generatedAt = "",
            outline = listOf(root),
            cards = listOf(
                card("c.r", "根卡片", "root"),
                card("c.1", "章节1卡片", "s1"),
                card("c.11", "Linear卡片", "s1-1"),
                card("c.12", "ReLU卡片", "s1-2"),
                card("c.2", "章节2卡片", "s2"),
                card("c.21", "形状卡片", "s2-1"),
                card("c.22", "取值卡片", "s2-2"),
            ),
            claims = emptyMap(),
            edges = emptyMap(),
        )
    }

    private fun nodeOf(r: TreeLayout.Result, title: String): TreeLayout.Node =
        r.nodes.first { it.title == title }

    @Test
    fun `层级越深 x 越大`() {
        val r = TreeLayout.layout(graph())
        val rootX = nodeOf(r, "Transformer 架构").x
        val level2X = nodeOf(r, "1 前馈网络").x
        val level3X = nodeOf(r, "1.1 Linear").x

        assertTrue("level2 应在 root 右侧：$rootX vs $level2X", level2X > rootX)
        assertTrue("level3 应在 level2 右侧：$level2X vs $level3X", level3X > level2X)
    }

    @Test
    fun `每层横向位移等于 LEVEL_WIDTH`() {
        val r = TreeLayout.layout(graph())
        val rootX = nodeOf(r, "Transformer 架构").x
        val level2X = nodeOf(r, "1 前馈网络").x
        val level3X = nodeOf(r, "1.1 Linear").x
        val step = TreeLayout.LEVEL_WIDTH

        assertEquals(step, level2X - rootX, 0.01f)
        assertEquals(step, level3X - level2X, 0.01f)
    }

    @Test
    fun `同级兄弟 x 相同`() {
        val r = TreeLayout.layout(graph())
        assertEquals(nodeOf(r, "1 前馈网络").x, nodeOf(r, "2 层间依赖").x, 0.01f)
        assertEquals(nodeOf(r, "1.1 Linear").x, nodeOf(r, "1.2 ReLU").x, 0.01f)
    }

    @Test
    fun `兄弟节点纵向不重叠`() {
        val r = TreeLayout.layout(graph())
        val a = nodeOf(r, "1.1 Linear")
        val b = nodeOf(r, "1.2 ReLU")
        assertTrue(
            "兄弟节点不能重叠：a.y=${a.y} a.h=${a.height} b.y=${b.y}",
            b.y >= a.y + a.height
        )
    }

    @Test
    fun `父节点垂直居中对齐子节点`() {
        val r = TreeLayout.layout(graph())
        val chapter = nodeOf(r, "1 前馈网络")
        val first = nodeOf(r, "1.1 Linear")
        val last = nodeOf(r, "1.2 ReLU")
        val expected = (first.y + last.y) / 2f
        assertEquals("父节点应居中于子节点", expected, chapter.y, 0.5f)
    }

    @Test
    fun `除根以外每个节点都有一条来自父节点的连线`() {
        val r = TreeLayout.layout(graph())
        val hierarchy = r.lines.filter { it.style == TreeLayout.Line.Style.HIERARCHY }
        // 7 个节点，除根外 6 个各有一条
        assertEquals(6, hierarchy.size)
    }

    @Test
    fun `层级连线一律向右`() {
        val r = TreeLayout.layout(graph())
        r.lines.filter { it.style == TreeLayout.Line.Style.HIERARCHY }.forEach { line ->
            assertTrue("层级线必须向右", line.toX > line.fromX)
        }
    }

    @Test
    fun `每个节点携带它挂的卡片`() {
        val r = TreeLayout.layout(graph())
        assertEquals(listOf("c.11"), nodeOf(r, "1.1 Linear").cardIds)
        assertEquals(listOf("c.1"), nodeOf(r, "1 前馈网络").cardIds)
        assertEquals(listOf("c.r"), nodeOf(r, "Transformer 架构").cardIds)
    }

    @Test
    fun `有卡片的节点可点击 没卡片的不可`() {
        val r = TreeLayout.layout(graph())
        // 所有节点在这个 fixture 里都有卡片
        r.nodes.forEach { assertTrue("${it.title} 应有卡片", it.descendable) }

        // 造一个没有卡片的组织性节点
        val g = graph()
        val orphan = GraphOutlineNode(
            id = "out.x", title = "空节点", level = 1, parentId = null,
            path = "x", order = 9, children = emptyList(), cardIds = emptyList(),
        )
        val r2 = TreeLayout.layout(g.copy(outline = g.outline + orphan))
        val empty = r2.nodes.first { it.title == "空节点" }
        assertTrue("无卡片的节点不可点击", !empty.descendable)
        assertTrue("无卡片的节点 cardIds 为空", empty.cardIds.isEmpty())
    }

    @Test
    fun `所有节点都在主题分区内`() {
        val r = TreeLayout.layout(graph())
        val g = r.groups.single()
        r.nodes.forEach { n ->
            assertTrue(
                "节点「${n.title}」超出分区",
                n.x >= g.x && n.x + n.width <= g.x + g.width + 1f
            )
        }
    }

    @Test
    fun `节点路径唯一`() {
        val r = TreeLayout.layout(graph())
        val paths = r.nodes.map { it.nodePath }
        assertEquals("节点路径应唯一", paths.size, paths.distinct().size)
        assertNotNull(paths.firstOrNull { it == "root" })
    }
}
