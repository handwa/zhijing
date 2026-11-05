package com.example.zhijing.kg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 广度构建的本地算法测试。
 *
 * 守护三条「模型不该碰、本地必须算对」的规则：
 * 1. **正文来自原文**（不是模型重写）；
 * 2. **层级从编号推**（不让模型猜父子）；
 * 3. **非法区间一律丢弃**（宁可少一块，不截出错字）。
 */
class OutlineBuilderTest {

    private val content = """
        1 第一部分

        这是第一部分的内容，讲了一件事。

        1.1 子部分

        这是子部分的内容，讲了另一件事。

        2 第二部分

        第二部分内容。
    """.trimIndent()

    private fun block(title: String, start: Int, end: Int, summary: String = "摘要") =
        OutlineBuilder.RawBlock(title, summary, start, end)

    @Test
    fun `正文从原文逐字截取`() {
        val start = content.indexOf("1 第一部分")
        val end = start + "1 第一部分".length
        val r = OutlineBuilder.build(content, listOf(block("1 第一部分", start, end)))

        assertEquals(1, r.cards.size)
        assertEquals("1 第一部分", r.cards.single().body)
        // 关键：正文就是原文，模型改不了内容
        assertTrue(content.contains(r.cards.single().body))
    }

    @Test
    fun `编号决定层级`() {
        val s1 = content.indexOf("1 第一部分")
        val s11 = content.indexOf("1.1 子部分")
        val s2 = content.indexOf("2 第二部分")
        val r = OutlineBuilder.build(
            content,
            listOf(
                block("1 第一部分", s1, s1 + 10),
                block("1.1 子部分", s11, s11 + 10),
                block("2 第二部分", s2, s2 + 10)
            )
        )

        assertEquals(3, r.outline.size)
        assertEquals(1, r.outline[0].level)
        assertEquals(2, r.outline[1].level)
        assertEquals(1, r.outline[2].level)
        // 1.1 的父节点是 1
        assertEquals(r.outline[0].path, r.outline[1].parentPath)
        // 2 没有父节点
        assertNull(r.outline[2].parentPath)
        // path 用点分层级
        assertTrue(r.outline[1].path.startsWith(r.outline[0].path + "."))
    }

    @Test
    fun `无编号时首块为根其余同级`() {
        val r = OutlineBuilder.build(
            content,
            listOf(block("甲", 0, 5), block("乙", 5, 10))
        )
        assertEquals(1, r.outline[0].level)
        assertEquals(1, r.outline[1].level)
        assertNull(r.outline[1].parentPath)
    }

    @Test
    fun `越界区间被丢弃`() {
        val r = OutlineBuilder.build(content, listOf(block("越界", 0, content.length + 100)))
        assertTrue(r.cards.isEmpty())
        assertTrue(r.warnings.any { it.contains("区间非法") })
    }

    @Test
    fun `倒置区间被丢弃`() {
        val r = OutlineBuilder.build(content, listOf(block("倒置", 50, 10)))
        assertTrue(r.cards.isEmpty())
    }

    @Test
    fun `重叠区间被丢弃`() {
        val r = OutlineBuilder.build(
            content,
            listOf(block("甲", 0, 20), block("乙", 10, 30))
        )
        assertEquals(1, r.cards.size)
        assertTrue(r.warnings.any { it.contains("重叠") })
    }

    @Test
    fun `缺少标题的块被丢弃`() {
        val r = OutlineBuilder.build(content, listOf(block("   ", 0, 10)))
        assertTrue(r.cards.isEmpty())
        assertTrue(r.warnings.any { it.contains("缺少 title") })
    }

    @Test
    fun `摘要为空时用标题兜底`() {
        val r = OutlineBuilder.build(content, listOf(block("甲", 0, 10, summary = "")))
        assertEquals("甲", r.cards.single().summary)
    }

    @Test
    fun `层级不超过上限`() {
        assertEquals(1, OutlineBuilder.numberDepth("1 章"))
        assertEquals(2, OutlineBuilder.numberDepth("1.2 节"))
        assertEquals(3, OutlineBuilder.numberDepth("1.2.3 小节"))
        assertEquals(4, OutlineBuilder.numberDepth("1.2.3.4 更深"))
        assertEquals(1, OutlineBuilder.numberDepth("一、第一章"))
        assertEquals(1, OutlineBuilder.numberDepth("（二）第二章"))
        assertNull(OutlineBuilder.numberDepth("没有编号的标题"))
    }

    @Test
    fun `中文标题也能生成非空路径`() {
        val slug = OutlineBuilder.slugifyTitle("前馈网络")
        assertTrue(slug.isNotBlank())
        // 不含点号（点号是路径分隔符）
        assertTrue(!slug.contains("."))
    }

    @Test
    fun `重复标题生成不同路径`() {
        val r = OutlineBuilder.build(
            content,
            listOf(block("同名", 0, 5), block("同名", 5, 10))
        )
        // 两个块标题相同，路径必须不同，否则会互相覆盖
        assertEquals(2, r.outline.map { it.path }.distinct().size)
    }
}
