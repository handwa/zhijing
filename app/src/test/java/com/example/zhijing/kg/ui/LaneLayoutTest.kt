package com.example.zhijing.kg.ui

import com.example.zhijing.kg.GraphCard
import com.example.zhijing.kg.GraphOutlineNode
import com.example.zhijing.kg.MergedGraph
import com.example.zhijing.kg.PrerequisiteKind
import com.example.zhijing.kg.PrerequisiteLink
import com.example.zhijing.kg.ui.TreeLayout.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长跳依赖的「列外车道」与跨章节标记。
 *
 * 两条都是纯几何：车道必须落在该列最右边界之外，否则线仍从中间卡片底下穿过，
 * 屏幕上只剩断断续续的线段 —— 那正是上一版被吐槽「看不清依赖」的形态。
 */
class LaneLayoutTest {

    @Test
    fun `同列长跳走车道且车道在列外`() {
        // 一个章节下 a / b / c 三个兄弟，依赖 a → c（中间隔着 b）
        val layout = TreeLayout.layout(graph(required("crd.a", "crd.c")))
        val dep = layout.lines.single { it.style == Line.Style.PREREQUISITE_REQUIRED }
        val source = layout.nodes.single { it.nodePath == dep.fromNode }

        val target = layout.nodes.single { it.nodePath == dep.toNode }

        assertEquals(Line.Route.LANE, dep.route)
        // 车道横坐标必须大于**同一列**所有节点的右边界
        val columnRight = layout.nodes
            .filter { it.x == source.x }
            .maxOf { it.x + it.width }
        assertTrue("车道 ${dep.ctrlX} 应在列右边界 $columnRight 之外", dep.ctrlX > columnRight)

        // 两端落在侧边中点：箭头指在节点右边缘上，不再横穿到节点中心
        assertEquals(source.x + source.width, dep.fromX)
        assertEquals(source.y + source.height / 2f, dep.fromY)
        assertEquals(target.x + target.width, dep.toX)
        assertEquals(target.y + target.height / 2f, dep.toY)
    }

    @Test
    fun `同列相邻依赖仍是弧线`() {
        // a → b 只隔一条行距：弧线在缝隙里绕一下就够，开车道反而绕远
        val layout = TreeLayout.layout(graph(required("crd.a", "crd.b")))
        val dep = layout.lines.single { it.style == Line.Style.PREREQUISITE_REQUIRED }

        assertEquals(Line.Route.CURVE, dep.route)
    }

    @Test
    fun `跨章节依赖被标出来`() {
        val layout = TreeLayout.layout(graph(required("crd.a", "crd.x"), twoChapters = true))
        val dep = layout.lines.single { it.style == Line.Style.PREREQUISITE_REQUIRED }

        assertTrue("跨章节依赖要能被绘制层淡化", dep.crossChapter)
    }

    @Test
    fun `同章节依赖不标跨章`() {
        val layout = TreeLayout.layout(graph(required("crd.a", "crd.c")))
        val dep = layout.lines.single { it.style == Line.Style.PREREQUISITE_REQUIRED }

        assertFalse(dep.crossChapter)
    }

    @Test
    fun `章节归属按一级节点继承`() {
        val layout = TreeLayout.layout(graph(prerequisite = null, twoChapters = true))

        // 主题根是主题自己；章节自己是自己；子节点继承所属章节
        assertEquals("root", layout.nodes.single { it.nodePath == "root" }.chapterPath)
        assertEquals("s1", layout.nodes.single { it.nodePath == "s1" }.chapterPath)
        assertEquals("s1", layout.nodes.single { it.nodePath == "a" }.chapterPath)
        assertEquals("s2", layout.nodes.single { it.nodePath == "s2" }.chapterPath)
        assertEquals("s2", layout.nodes.single { it.nodePath == "x" }.chapterPath)
    }

    /* ------------------------------------------------------------ 工具 */

    private fun required(from: String, to: String) =
        PrerequisiteLink(from, to, PrerequisiteKind.REQUIRED)

    private fun node(
        id: String,
        path: String,
        order: Int,
        children: List<GraphOutlineNode> = emptyList(),
    ) = GraphOutlineNode(
        id = id,
        title = path,
        level = 1,
        parentId = null,
        path = path,
        order = order,
        children = children,
    )

    private fun card(
        id: String,
        outlinePath: String,
        prerequisites: List<PrerequisiteLink> = emptyList(),
    ) = GraphCard(
        id = id,
        title = id,
        outlinePath = outlinePath,
        summary = "",
        body = "",
        prerequisites = prerequisites,
    )

    /**
     * 默认：一个章节 s1，下挂 a / b / c 三个节点；
     * [twoChapters] = true 时再加章节 s2 与它的节点 x，用来测跨章。
     */
    private fun graph(
        prerequisite: PrerequisiteLink?,
        twoChapters: Boolean = false,
    ): MergedGraph {
        val s1 = node(
            "out.s1", "s1", 0,
            children = listOf(
                node("out.a", "a", 0),
                node("out.b", "b", 1),
                node("out.c", "c", 2),
            )
        )
        val chapters = if (twoChapters) {
            listOf(s1, node("out.s2", "s2", 1, children = listOf(node("out.x", "x", 0))))
        } else {
            listOf(s1)
        }
        // 顶层是**主题**，章节是它的直接子节点：少了这层，s1 会被当成主题根，
        // a/b/c 各自变成「章节」，跨章判定就恒为 true 了。
        val outline = listOf(node("out.root", "root", 0, children = chapters))

        val cards = listOf("crd.a" to "a", "crd.b" to "b", "crd.c" to "c")
            .plus(if (twoChapters) listOf("crd.x" to "x") else emptyList())
            .map { (id, path) ->
                // 依赖挂在 to 端，与 GraphAssembler 一致
                val links = listOfNotNull(prerequisite).filter { it.to == id }
                card(id, path, links)
            }

        return MergedGraph(
            schemaVersion = "",
            generatedAt = "",
            outline = outline,
            cards = cards,
            claims = emptyMap(),
            edges = emptyMap(),
        )
    }
}
