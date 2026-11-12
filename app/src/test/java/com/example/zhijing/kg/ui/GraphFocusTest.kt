package com.example.zhijing.kg.ui

import com.example.zhijing.kg.GraphOutlineNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「切到结构图时定位到哪」的解析测试。
 *
 * 重点是回归 id / path 混用：章节 chip 存 `out.s1`，结构图按 `s1` 匹配，
 * 混用时不会报错、只是静默不生效，所以必须用测试固定住「返回的一定是 path」。
 */
class GraphFocusTest {

    private fun node(
        id: String,
        path: String,
        title: String = path,
        children: List<GraphOutlineNode> = emptyList(),
    ) = GraphOutlineNode(
        id = id,
        title = title,
        level = 1,
        parentId = null,
        path = path,
        order = 0,
        children = children,
    )

    private val outline = listOf(
        node(
            id = "out.s1",
            path = "s1",
            title = "1 前馈网络：从张量到概率",
            children = listOf(node("out.s1-1", "s1-1-linear", "1.1 Linear 层")),
        ),
        node("out.s4", "s4", "4 注意力机制"),
    )

    @Test
    fun cardOutlinePathWins() {
        assertEquals(
            "s1-1-linear",
            GraphFocus.resolvePath("s1-1-linear", outline, "out.s4"),
        )
    }

    @Test
    fun chapterIdIsConvertedToPath() {
        // chip 给的是 id（out.s4），结构图要的是 path（s4）
        assertEquals("s4", GraphFocus.resolvePath(null, outline, "out.s4"))
    }

    @Test
    fun nestedChapterIdIsConvertedToPath() {
        assertEquals("s1-1-linear", GraphFocus.resolvePath(null, outline, "out.s1-1"))
    }

    @Test
    fun nothingSelectedMeansNoFocus() {
        assertNull(GraphFocus.resolvePath(null, outline, null))
    }

    @Test
    fun unknownChapterIdYieldsNoFocus() {
        assertNull(GraphFocus.resolvePath(null, outline, "out.not-exist"))
    }

    @Test
    fun blankCardPathFallsThroughToChapter() {
        assertEquals("s4", GraphFocus.resolvePath("   ", outline, "out.s4"))
    }
}
