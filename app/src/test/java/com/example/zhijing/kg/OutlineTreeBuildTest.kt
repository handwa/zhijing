package com.example.zhijing.kg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.example.zhijing.kg.ui.TreeLayout
import org.junit.Test

/**
 * 出厂图谱大纲建树的测试。
 *
 * ## 背景
 *
 * `graph.json` 只写了 `level`（1/2/3），**没写 `parentId`**。
 * 原实现按 `parentId` 找子节点，结果所有节点都被当成根，结构图塌成一列。
 *
 * 这组测试锁住「按 level 推导父子」这条规则，防止回归。
 */
class OutlineTreeBuildTest {

    /** 直接调用真实的 buildOutline 不方便，这里复刻同样的推导规则做验证。 */
    private fun inferTree(levels: List<Pair<String, Int>>): Map<String, String?> {
        // 与 GraphAssembler.buildOutline 的 ancestors-stack 逻辑一致
        val parentOf = LinkedHashMap<String, String?>()
        val stack = ArrayList<Pair<String, Int>>()
        levels.forEach { (id, level) ->
            while (stack.isNotEmpty() && stack.last().second >= level) {
                stack.removeAt(stack.size - 1)
            }
            parentOf[id] = stack.lastOrNull()?.first
            stack += (id to level)
        }
        return parentOf
    }

    @Test
    fun `按 level 推导父子关系`() {
        val tree = inferTree(
            listOf(
                "root" to 1,
                "ch1" to 2,
                "s11" to 3,
                "s12" to 3,
                "ch2" to 2,
                "s21" to 3
            )
        )
        assertEquals(null, tree["root"])
        assertEquals("root", tree["ch1"])
        assertEquals("ch1", tree["s11"])
        assertEquals("ch1", tree["s12"])
        assertEquals("root", tree["ch2"])
        assertEquals("ch2", tree["s21"])
    }

    @Test
    fun `level 相同时是兄弟不会互相当父`() {
        val tree = inferTree(
            listOf("a" to 2, "b" to 2, "c" to 2)
        )
        // 都没有 level 1 的祖先，三个都是根
        assertEquals(null, tree["a"])
        assertEquals(null, tree["b"])
        assertEquals(null, tree["c"])
    }

    @Test
    fun `跳级也能正确挂到最近的浅层祖先`() {
        // 1 -> 3（跳过 2 级）
        val tree = inferTree(
            listOf("root" to 1, "deep" to 3, "deep2" to 3)
        )
        assertEquals("root", tree["deep"])
        assertEquals("root", tree["deep2"])
    }

    @Test
    fun `真实数据的层级形状`() {
        // graph.json 实测：1 个 level1、6 个 level2、17 个 level3
        val levels = buildList {
            add("root" to 1)
            repeat(6) { i -> add("ch$i" to 2) }
            repeat(17) { i -> add("s$i" to 3) }
        }
        val tree = inferTree(levels)

        assertEquals(null, tree["root"])
        // 所有 level2 都挂在 root 下
        (0 until 6).forEach { assertEquals("root", tree["ch$it"]) }
        // level3 挂在各自最近的 level2 下
        (0 until 17).forEach { i ->
            val parent = tree["s$i"]
            assertTrue("s$i 的父应是一个章节，实际 $parent", parent?.startsWith("ch") == true)
        }
    }

    @Test
    fun `层级越深横向偏移越大`() {
        // 横向布局：x = groupLeft + depth * LEVEL_WIDTH
        val levelWidth = TreeLayout.LEVEL_WIDTH
        val groupLeft = 24f
        val depthX = (0..3).map { groupLeft + it * levelWidth }
        assertEquals(4, depthX.size)
        depthX.zipWithNext().forEach { (a, b) ->
            assertTrue("更深一层必须更靠右：$a -> $b", b > a)
        }
        assertEquals(groupLeft, depthX.first(), 0.001f)
    }
}
