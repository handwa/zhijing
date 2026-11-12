package com.example.zhijing.kg.ui

import com.example.zhijing.kg.GraphOutlineNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节点搜索的纯逻辑测试。
 *
 * 关注三件事：大小写不敏感、能区分同名节点（面包屑）、结果有条数上限。
 */
class GraphSearchTest {

    private fun node(
        path: String,
        title: String,
        order: Int = 0,
        children: List<GraphOutlineNode> = emptyList(),
    ) = GraphOutlineNode(
        id = path,
        title = title,
        level = path.count { it == '-' } + 1,
        parentId = null,
        path = path,
        order = order,
        children = children,
    )

    private val outline = listOf(
        node(
            path = "s1",
            title = "1 前馈网络：从张量到概率",
            order = 1,
            children = listOf(
                node("s1-1-linear", "1.1 Linear 层", order = 1),
                node("s1-3-softmax", "1.3 Softmax", order = 3),
            ),
        ),
        node(
            path = "s4",
            title = "4 注意力机制",
            order = 4,
            children = listOf(
                node("s4-1", "4.1 缩放点积注意力", order = 1),
            ),
        ),
    )

    @Test
    fun blankQueryReturnsNothing() {
        assertTrue(GraphSearch.search(outline, "").isEmpty())
        assertTrue(GraphSearch.search(outline, "   ").isEmpty())
    }

    @Test
    fun englishMatchIsCaseInsensitive() {
        val hits = GraphSearch.search(outline, "softmax")

        assertEquals(1, hits.size)
        assertEquals("s1-3-softmax", hits.single().path)
    }

    @Test
    fun chineseSubstringMatches() {
        val hits = GraphSearch.search(outline, "注意")

        // 「4 注意力机制」与「4.1 缩放点积注意力」都应命中
        assertEquals(2, hits.size)
        assertEquals(listOf("s4", "s4-1"), hits.map { it.path })
    }

    @Test
    fun breadcrumbShowsParentTitle() {
        val hit = GraphSearch.search(outline, "Softmax").single()

        assertEquals("1 前馈网络：从张量到概率", hit.breadcrumb)
    }

    @Test
    fun resultsFollowPreOrderTraversal() {
        val hits = GraphSearch.search(outline, "1")

        // 先序：父节点先于子节点，兄弟按 order
        assertEquals(listOf("s1", "s1-1-linear", "s1-3-softmax", "s4-1"), hits.map { it.path })
    }

    @Test
    fun decimalPointIsMatchedLiterally() {
        val hits = GraphSearch.search(outline, "1.")

        // 「4.1」里的「1」后面是空格，不是「.」，所以只命中 1.1 与 1.3
        assertEquals(listOf("s1-1-linear", "s1-3-softmax"), hits.map { it.path })
    }

    @Test
    fun limitCapsResultCount() {
        val hits = GraphSearch.search(outline, "1", limit = 1)

        assertEquals(1, hits.size)
    }

    @Test
    fun unmatchedQueryReturnsEmpty() {
        assertTrue(GraphSearch.search(outline, "不存在的标题").isEmpty())
    }
}
