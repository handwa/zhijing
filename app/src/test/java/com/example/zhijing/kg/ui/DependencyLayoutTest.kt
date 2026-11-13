package com.example.zhijing.kg.ui

import com.example.zhijing.kg.GraphCard
import com.example.zhijing.kg.GraphOutlineNode
import com.example.zhijing.kg.MergedGraph
import com.example.zhijing.kg.PrerequisiteKind
import com.example.zhijing.kg.PrerequisiteLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「只看依赖」布局测试。
 *
 * 两个模式共用同一套布局代码，唯一差别是**兄弟节点的排序依据**：
 * 层级视图按大纲顺序（`order`），依赖视图按前置关系的拓扑序。
 * 这里用「大纲顺序与依赖顺序相反」的构造来区分两种模式，否则两种模式结果相同、测不出问题。
 */
class DependencyLayoutTest {

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

    /** 章节 s1 下有两个兄弟：a（order 0）与 b（order 1），但依赖要求 b 先学。 */
    private fun graphWithReversedDependency() = MergedGraph(
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
            card("crd.b", "b", listOf(PrerequisiteLink("crd.b", "crd.a", PrerequisiteKind.REQUIRED))),
        ),
        claims = emptyMap(),
        edges = emptyMap(),
    )

    private fun yOf(layout: TreeLayout.Result, path: String): Float =
        layout.nodes.single { it.nodePath == path }.y

    @Test
    fun ranksFollowPrerequisiteDirection() {
        val ranks = TreeLayout.dependencyRanks(graphWithReversedDependency())

        // 前置关系写的是 b → a（先学 b），所以 a 的层级应为 1
        assertEquals("实际分级：$ranks", 1, ranks["a"])
    }

    @Test
    fun hierarchyModeFollowsOutlineOrder() {
        val layout = TreeLayout.layout(graphWithReversedDependency())

        // 大纲顺序：a 在 b 之前
        assertTrue(yOf(layout, "a") < yOf(layout, "b"))
    }

    @Test
    fun dependencyModeFollowsPrerequisiteOrder() {
        val layout = TreeLayout.layout(
            graphWithReversedDependency(),
            TreeLayout.LayoutMode.DEPENDENCY,
        )

        // 依赖要求 b 先学，所以 b 应排到 a 上面 —— 与大纲顺序相反
        assertTrue(
            "依赖视图应把 b 排在 a 之前：b=${yOf(layout, "b")} a=${yOf(layout, "a")}",
            yOf(layout, "b") < yOf(layout, "a"),
        )
    }

    @Test
    fun dependencyModeWithoutPrerequisitesKeepsOutlineOrder() {
        val graph = MergedGraph(
            schemaVersion = "",
            generatedAt = "",
            outline = listOf(
                node(
                    "out.s1", "s1", order = 0,
                    children = listOf(node("out.a", "a", 0), node("out.b", "b", 1)),
                ),
            ),
            cards = listOf(card("crd.a", "a"), card("crd.b", "b")),
            claims = emptyMap(),
            edges = emptyMap(),
        )

        val layout = TreeLayout.layout(graph, TreeLayout.LayoutMode.DEPENDENCY)

        assertTrue("没有前置关系时应回落到大纲顺序", yOf(layout, "a") < yOf(layout, "b"))
    }

    @Test
    fun dependencyModeSurvivesCycles() {
        val graph = MergedGraph(
            schemaVersion = "",
            generatedAt = "",
            outline = listOf(
                node(
                    "out.s1", "s1", order = 0,
                    children = listOf(node("out.a", "a", 0), node("out.b", "b", 1)),
                ),
            ),
            cards = listOf(
                card("crd.a", "a", listOf(PrerequisiteLink("crd.b", "crd.a", PrerequisiteKind.REQUIRED))),
                card("crd.b", "b", listOf(PrerequisiteLink("crd.a", "crd.b", PrerequisiteKind.REQUIRED))),
            ),
            claims = emptyMap(),
            edges = emptyMap(),
        )

        val layout = TreeLayout.layout(graph, TreeLayout.LayoutMode.DEPENDENCY)

        // 一个章节 + 两个子节点；成环不能让节点丢失行号，也不能抛栈溢出
        assertEquals(3, layout.nodes.size)
        assertTrue(layout.nodes.all { it.y.isFinite() })
    }
}
