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
    fun dependencyEdgesFoldCardsToNodes() {
        val edges = TreeLayout.dependencyEdges(graphWithReversedDependency())

        // 卡片级 crd.b → crd.a 上卷成节点级 b → a
        assertEquals(setOf("b" to "a"), edges)
    }

    /**
     * 回归：**没有任何前置关系的节点不允许被顶到最前**。
     *
     * 旧实现按「全局最长路径深度」排序，孤立节点深度为 0，会插到有前置的节点前面 ——
     * `2.3 统计量依赖` 就是这么跑到 `2.1 形状依赖` 前面的，生成图谱里同样会发生。
     */
    @Test
    fun isolatedSiblingIsNotPromoted() {
        val graph = MergedGraph(
            schemaVersion = "",
            generatedAt = "",
            outline = listOf(
                node(
                    "out.s1", "s1", order = 0,
                    children = listOf(
                        node("out.a", "a", 0),
                        node("out.b", "b", 1),
                        node("out.c", "c", 2),
                    ),
                ),
            ),
            // 只有 a → b 一条边；c 没有任何依赖
            cards = listOf(
                card("crd.a", "a"),
                card("crd.b", "b", listOf(PrerequisiteLink("crd.a", "crd.b", PrerequisiteKind.REQUIRED))),
                card("crd.c", "c"),
            ),
            claims = emptyMap(),
            edges = emptyMap(),
        )

        val layout = TreeLayout.layout(graph, TreeLayout.LayoutMode.DEPENDENCY)

        assertTrue(
            "c 不该被提到 b 前面：a=${yOf(layout, "a")} b=${yOf(layout, "b")} c=${yOf(layout, "c")}",
            yOf(layout, "a") < yOf(layout, "b") && yOf(layout, "b") < yOf(layout, "c"),
        )
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
