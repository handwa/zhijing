package com.example.zhijing.kg.ui

import com.example.zhijing.kg.GraphCard
import com.example.zhijing.kg.GraphOutlineNode
import com.example.zhijing.kg.MergedGraph
import com.example.zhijing.kg.PrerequisiteKind
import com.example.zhijing.kg.PrerequisiteLink
import com.example.zhijing.kg.ui.EdgePresentation.Role
import com.example.zhijing.kg.ui.TreeLayout.Line
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结构图「选中一张卡片后该显示哪些线」的交互契约。
 *
 * 这些规则写错了不会有编译错误，只会让图变糊：所以必须能用普通 JUnit 断言，
 * 而不是靠截图看。
 */
class EdgePresentationTest {

    /* --------------------------------------------------- 显示规则 */

    @Test
    fun `未选中时只显示层级线`() {
        assertEquals(Role.HIERARCHY, role(Line.Style.HIERARCHY, "a", "b", null))

        // 前置、推荐、关联一条都不画 —— 否则 20 个节点的图会同时压三类线
        assertNull(role(Line.Style.PREREQUISITE_REQUIRED, "a", "b", null))
        assertNull(role(Line.Style.PREREQUISITE_RECOMMENDED, "a", "b", null))
        assertNull(role(Line.Style.CROSS, "a", "b", null))
    }

    @Test
    fun `选中目标端时是前置`() {
        // to 端被选中：这条线指向"我"，所以是前置
        assertEquals(Role.REQUIRED, role(Line.Style.PREREQUISITE_REQUIRED, "a", "b", "b"))
        assertEquals(Role.RECOMMENDED, role(Line.Style.PREREQUISITE_RECOMMENDED, "a", "b", "b"))
    }

    @Test
    fun `选中出发端时是后续`() {
        // from 端被选中：这条线从"我"出发，所以是后续 —— 同一条边，称谓随选中对象换
        assertEquals(Role.SUCCESSOR, role(Line.Style.PREREQUISITE_REQUIRED, "a", "b", "a"))
        assertEquals(Role.SUCCESSOR, role(Line.Style.PREREQUISITE_RECOMMENDED, "a", "b", "a"))
    }

    @Test
    fun `与选中节点无关的依赖线不显示`() {
        assertNull(role(Line.Style.PREREQUISITE_REQUIRED, "a", "b", "c"))
        assertNull(role(Line.Style.CROSS, "a", "b", "c"))
    }

    @Test
    fun `只看依赖视图未选中时仍显示依赖线`() {
        // 依赖视图把层级线藏起来了，若依赖线也藏，画布上只剩孤零零的节点
        assertEquals(
            Role.REQUIRED,
            EdgePresentation.roleOf(Line.Style.PREREQUISITE_REQUIRED, "a", "b", null, true),
        )
        assertEquals(
            Role.RECOMMENDED,
            EdgePresentation.roleOf(Line.Style.PREREQUISITE_RECOMMENDED, "a", "b", null, true),
        )
        // 交叉关联不属于依赖，仍然只在选中时出现
        assertNull(EdgePresentation.roleOf(Line.Style.CROSS, "a", "b", null, true))
    }

    @Test
    fun `相关节点只算依赖邻居不算父子`() {
        val lines = listOf(
            line(Line.Style.HIERARCHY, "s1", "a"),
            line(Line.Style.HIERARCHY, "s1", "b"),
            line(Line.Style.PREREQUISITE_REQUIRED, "a", "b"),
            line(Line.Style.PREREQUISITE_RECOMMENDED, "b", "c"),
        )

        // 选中 a：只高亮 b（依赖邻居），不把父章节 s1 拉进来
        assertEquals(setOf("b"), EdgePresentation.relatedNodes(lines, "a"))
        assertEquals(setOf("a", "c"), EdgePresentation.relatedNodes(lines, "b"))
        assertTrue(EdgePresentation.relatedNodes(lines, null).isEmpty())
    }

    /* --------------------------------------------------- 走向 */

    @Test
    fun `同列相邻前置走弧线而不是直线`() {
        val layout = TreeLayout.layout(siblingGraph(PrerequisiteKind.REQUIRED))
        val dep = layout.lines.single { it.style == Line.Style.PREREQUISITE_REQUIRED }

        assertEquals(Line.Route.CURVE, dep.route)
        // 控制点偏离两端中点 = 确实鼓出去了；否则和直线没区别
        val midX = (dep.fromX + dep.toX) / 2f
        assertTrue("控制点应偏离中线：${dep.ctrlX} vs $midX", abs(dep.ctrlX - midX) > 1f)
    }

    @Test
    fun `跨列必须前置沿用层级折线`() {
        val layout = TreeLayout.layout(crossColumnGraph(PrerequisiteKind.REQUIRED))
        val dep = layout.lines.single { it.style == Line.Style.PREREQUISITE_REQUIRED }

        // 树里已经有父子这条正交路径，必须前置复用它，不再画一条斜线
        assertEquals(Line.Route.ELBOW, dep.route)
    }

    @Test
    fun `跨列推荐前置走自己的弧线`() {
        val layout = TreeLayout.layout(crossColumnGraph(PrerequisiteKind.RECOMMENDED))
        val dep = layout.lines.single { it.style == Line.Style.PREREQUISITE_RECOMMENDED }

        // "建议"不该看起来像"结构"：虚线不共用层级线通道
        assertEquals(Line.Route.CURVE, dep.route)
    }

    @Test
    fun `曲线控制点落在两端连线的侧向`() {
        // 竖直向下的线，正数 bulge 向左侧鼓
        val (cx, _) = TreeLayout.curveControl(0f, 0f, 0f, 100f, 10f)
        assertTrue("应当向负 x 方向鼓：$cx", cx < 0f)

        // 水平向右的线，正数 bulge 向下鼓
        val (_, cy) = TreeLayout.curveControl(0f, 0f, 100f, 0f, 10f)
        assertTrue("应当向正 y 方向鼓：$cy", cy > 0f)
    }

    /* --------------------------------------------------- 工具 */

    private fun role(style: Line.Style, from: String, to: String, selected: String?): Role? =
        EdgePresentation.roleOf(style, from, to, selected)

    private fun line(style: Line.Style, from: String, to: String) = Line(
        fromX = 0f,
        fromY = 0f,
        toX = 10f,
        toY = 10f,
        style = style,
        fromNode = from,
        toNode = to,
    )

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

    /** 一个章节下两个兄弟 a / b，card.a → card.b 的依赖落成同列上下两节点。 */
    private fun siblingGraph(kind: PrerequisiteKind) = MergedGraph(
        schemaVersion = "",
        generatedAt = "",
        outline = listOf(
            node(
                id = "out.s1",
                path = "s1",
                order = 0,
                children = listOf(
                    node("out.a", "a", order = 0),
                    node("out.b", "b", order = 1),
                ),
            ),
        ),
        cards = listOf(
            card("crd.a", "a"),
            card("crd.b", "b", listOf(PrerequisiteLink("crd.a", "crd.b", kind))),
        ),
        claims = emptyMap(),
        edges = emptyMap(),
    )

    /** 章节 ch（挂着 card.ch）→ 叶子 l（挂着 card.l），两端跨列。 */
    private fun crossColumnGraph(kind: PrerequisiteKind) = MergedGraph(
        schemaVersion = "",
        generatedAt = "",
        outline = listOf(
            node(
                id = "out.s1",
                path = "s1",
                order = 0,
                children = listOf(
                    node(
                        id = "out.ch",
                        path = "ch",
                        order = 0,
                        children = listOf(node("out.l", "l", order = 0)),
                    ),
                ),
            ),
        ),
        cards = listOf(
            card("crd.ch", "ch"),
            card("crd.l", "l", listOf(PrerequisiteLink("crd.ch", "crd.l", kind))),
        ),
        claims = emptyMap(),
        edges = emptyMap(),
    )
}
