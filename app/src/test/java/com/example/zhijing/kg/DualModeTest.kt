package com.example.zhijing.kg

import com.example.zhijing.kg.ui.looksLikeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 双模式（有原文 / 无原文）的判定与构建测试。
 *
 * 核心不变量：
 * 1. 长文或成段内容 → 走**结构化**（正文来自原文，可溯源）；
 * 2. 短说明 → 走**生成式**（正文来自模型，**未核实**）；
 * 3. 两种模式产出的大纲结构同构，渲染层不区分。
 */
class DualModeTest {

    /* -------------------------------------------------- 模式判定 */

    @Test
    fun `长文本判定为原文`() {
        val long = "这是一段很长的内容。".repeat(30)
        assertTrue(long.length > 200)
        assertTrue(looksLikeSource(long))
    }

    @Test
    fun `多句短文本判定为原文`() {
        // 3 个句号 → 成段内容
        assertTrue(looksLikeSource("第一句。第二句。第三句。"))
    }

    @Test
    fun `一句话判定为说明`() {
        assertFalse(looksLikeSource("只讲行列式和矩阵"))
    }

    @Test
    fun `空输入判定为说明`() {
        assertFalse(looksLikeSource(""))
        assertFalse(looksLikeSource("   "))
    }

    @Test
    fun `一句较长的说明仍判定为说明`() {
        // 120 字，但没有句子分隔 → 说明
        val note = "我想了解" + "行列式".repeat(60)
        assertTrue(note.length < 200)
        assertFalse(looksLikeSource(note))
    }

    /* ------------------------------------------ 无原文模式的构建 */

    @Test
    fun `无原文模式用模型给的正文`() {
        val blocks = listOf(
            OutlineBuilder.RawBlock(
                title = "第一部分",
                summary = "概括一",
                bodyStart = 0,
                bodyEnd = 0,
                body = "这是模型写的正文一。"
            ),
            OutlineBuilder.RawBlock(
                title = "第二部分",
                summary = "概括二",
                bodyStart = 0,
                bodyEnd = 0,
                body = "这是模型写的正文二。"
            )
        )
        val r = OutlineBuilder.buildFromBodies(blocks)

        assertEquals(2, r.cards.size)
        assertEquals("这是模型写的正文一。", r.cards[0].body)
        assertEquals("这是模型写的正文二。", r.cards[1].body)
    }

    @Test
    fun `无原文模式仍按标题编号推层级`() {
        val blocks = listOf(
            OutlineBuilder.RawBlock("1 章", "s", 0, 0, body = "正文"),
            OutlineBuilder.RawBlock("1.1 节", "s", 0, 0, body = "正文"),
            OutlineBuilder.RawBlock("2 章", "s", 0, 0, body = "正文")
        )
        val r = OutlineBuilder.buildFromBodies(blocks)

        assertEquals(3, r.outline.size)
        assertEquals(1, r.outline[0].level)
        assertEquals(2, r.outline[1].level)
        assertEquals(1, r.outline[2].level)
        // 层级推断逻辑与有原文模式完全一致
        assertEquals(r.outline[0].path, r.outline[1].parentPath)
    }

    @Test
    fun `无正文的块被丢弃`() {
        val blocks = listOf(
            OutlineBuilder.RawBlock("有正文", "s", 0, 0, body = "正文"),
            OutlineBuilder.RawBlock("没正文", "s", 0, 0, body = "")
        )
        val r = OutlineBuilder.buildFromBodies(blocks)
        assertEquals(1, r.cards.size)
        assertEquals("有正文", r.cards.single().title)
        assertTrue(r.warnings.any { it.contains("缺少正文") })
    }

    @Test
    fun `无原文模式空输入返回空结果`() {
        val r = OutlineBuilder.buildFromBodies(emptyList())
        assertTrue(r.cards.isEmpty())
        assertTrue(r.outline.isEmpty())
    }

    @Test
    fun `无原文模式重复标题生成不同路径`() {
        val blocks = listOf(
            OutlineBuilder.RawBlock("同名", "s", 0, 0, body = "a"),
            OutlineBuilder.RawBlock("同名", "s", 0, 0, body = "b")
        )
        val r = OutlineBuilder.buildFromBodies(blocks)
        assertEquals(2, r.outline.map { it.path }.distinct().size)
    }

    /* ------------------------------------------ 解析器支持两种块 */

    @Test
    fun `解析器识别有区间的块`() {
        val raw = """{"blocks":[{"title":"甲","summary":"s","bodyStart":0,"bodyEnd":10}]}"""
        val r = OutlineOutputParser.parse(raw)
        assertEquals(1, r.blocks.size)
        assertEquals(10, r.blocks.single().bodyEnd)
        assertTrue(r.blocks.single().body.isEmpty())
    }

    @Test
    fun `解析器识别只有正文的块`() {
        val raw = """{"blocks":[{"title":"甲","summary":"s","body":"模型写的正文"}]}"""
        val r = OutlineOutputParser.parse(raw)
        assertEquals(1, r.blocks.size)
        assertEquals("模型写的正文", r.blocks.single().body)
    }

    @Test
    fun `既无区间也无正文的块被丢弃`() {
        val raw = """{"blocks":[{"title":"甲","summary":"s"}]}"""
        val r = OutlineOutputParser.parse(raw)
        assertTrue(r.blocks.isEmpty())
        assertTrue(r.warnings.any { it.contains("既无区间也无正文") })
    }

    /* ----------------------------------------- 提示词覆盖两种模式 */

    @Test
    fun `提示词包含原文要求而非两套模式`() {
        val p = CloudOutlineExtractor.SYSTEM_PROMPT
        // 统一提示词：只有【原文要求】一条，不再有「情况 A / 情况 B」两段
        assertTrue(p.contains("【原文要求】"))
        assertFalse(p.contains("情况 A"))
        assertFalse(p.contains("情况 B"))
        // 两种输出格式都写明了
        assertTrue(p.contains("bodyStart"))
        assertTrue(p.contains("\"body\""))
    }

    @Test
    fun `用户输入是 JSON 对象`() {
        val p = CloudOutlineExtractor.userPrompt(
            topic = "线性代数",
            notes = "给初学者",
            source = "",
            maxBlocks = 8
        )
        assertTrue(p.contains("\"topic\": \"线性代数\""))
        assertTrue(p.contains("\"notes\": \"给初学者\""))
        assertTrue(p.contains("\"max_blocks\": 8"))
        // 没有 source 时不应出现该字段
        assertFalse(p.contains("\"source\""))
    }

    @Test
    fun `有原文时输入包含 source 与长度`() {
        val src = "这是一段原文。"
        val p = CloudOutlineExtractor.userPrompt(
            topic = "线性代数",
            source = src
        )
        assertTrue(p.contains("\"source\": \"$src\""))
        assertTrue(p.contains("\"source_length\": ${src.length}"))
    }

    @Test
    fun `max_blocks 被钳制在合法范围`() {
        val tooMany = CloudOutlineExtractor.userPrompt(topic = "x", maxBlocks = 999)
        assertTrue(tooMany.contains("\"max_blocks\": ${CloudOutlineExtractor.MAX_BLOCKS}"))
        val tooFew = CloudOutlineExtractor.userPrompt(topic = "x", maxBlocks = 0)
        assertTrue(tooFew.contains("\"max_blocks\": 1"))
    }

    @Test
    fun `原文里的引号被转义`() {
        val p = CloudOutlineExtractor.userPrompt(topic = "x", source = "他说\"你好\"")
        // 转义后应是 \" 而不是裸引号导致的非法 JSON
        assertTrue(p.contains("\\\"你好\\\""))
    }
}
