package com.example.zhijing.kg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 广度抽取的**通用性**与交叉链接测试。
 *
 * 最重要的一条：**提示词里不能出现任何领域词**。
 * 一旦出现（写「前馈网络」「注意力」之类），模型就会把这些词当成
 * 「要抽的内容」，换领域就失效 —— 这正是通用提示词与领域提示词的分界。
 */
class CloudOutlineExtractorTest {

    /** 已知领域词：任何一个出现在提示词里，都说明它不是通用提示词。 */
    private val DOMAIN_TERMS = listOf(
        "前馈", "神经", "网络", "张量", "线性层", "激活", "注意力", "编码器", "解码器",
        "梯度", "权重", "卷积", "池化", "归一化", "损失", "优化器",
        "Linear", "ReLU", "Softmax", "BatchNorm", "LayerNorm", "Transformer",
        "logits", "batch", "token", "embedding"
    )

    @Test
    fun `提示词不含任何领域词`() {
        val prompt = CloudOutlineExtractor.SYSTEM_PROMPT
        DOMAIN_TERMS.forEach { term ->
            assertFalse(
                "通用提示词里出现了领域词「$term」，会把模型引向特定领域：\n$prompt",
                prompt.contains(term, ignoreCase = true)
            )
        }
    }

    @Test
    fun `提示词要求不输出判断`() {
        val prompt = CloudOutlineExtractor.SYSTEM_PROMPT
        // 提示词里提到这些词是**禁止**它们，而不是要求输出
        assertTrue(prompt.contains("判断性内容"))
        assertTrue(prompt.contains("bodyStart"))
        assertTrue(prompt.contains("bodyEnd"))
    }

    @Test
    fun `user prompt 带上原文字数`() {
        val text = "这是一段原文。"
        val p = CloudOutlineExtractor.userPrompt(topic = "测试主题", source = text)
        assertTrue(p.contains("\"source_length\": ${text.length}"))
        assertTrue(p.contains(text))
    }

    @Test
    fun `解析标准输出`() {
        val raw = """{"blocks":[{"title":"甲","summary":"讲甲","bodyStart":0,"bodyEnd":10}]}"""
        val r = OutlineOutputParser.parse(raw)
        assertEquals(1, r.blocks.size)
        assertEquals("甲", r.blocks.single().title)
        assertEquals(0, r.blocks.single().bodyStart)
        assertEquals(10, r.blocks.single().bodyEnd)
    }

    @Test
    fun `剥除代码围栏`() {
        val raw = "好的：\n```json\n{\"blocks\":[{\"title\":\"甲\",\"summary\":\"s\",\"bodyStart\":0,\"bodyEnd\":5}]}\n```\n"
        val r = OutlineOutputParser.parse(raw)
        assertEquals(1, r.blocks.size)
        assertTrue(r.warnings.any { it.contains("围栏") })
    }

    @Test
    fun `缺少区间的块被丢弃`() {
        val raw = """{"blocks":[{"title":"甲","summary":"s"},{"title":"乙","bodyStart":0,"bodyEnd":5}]}"""
        val r = OutlineOutputParser.parse(raw)
        assertEquals(1, r.blocks.size)
        assertEquals("乙", r.blocks.single().title)
    }

    @Test
    fun `超过上限被截断`() {
        // 显式传一个小的上限，避免测试与全局 MAX_BLOCKS 的数值耦死
        val items = (1..20).joinToString(",") { i ->
            """{"title":"块$i","summary":"s","bodyStart":${i - 1},"bodyEnd":$i}"""
        }
        val r = OutlineOutputParser.parse("""{"blocks":[$items]}""", maxBlocks = 5)
        assertEquals(5, r.blocks.size)
        assertTrue(r.warnings.any { it.contains("上限") })
    }

    @Test
    fun `完全非法时返回空`() {
        val r = OutlineOutputParser.parse("我无法完成这个任务。")
        assertTrue(r.blocks.isEmpty())
        assertTrue(r.warnings.isNotEmpty())
    }

    @Test
    fun `空输出返回空`() {
        val r = OutlineOutputParser.parse("")
        assertTrue(r.blocks.isEmpty())
        assertTrue(r.warnings.any { it.contains("为空") })
    }

    @Test
    fun `交叉链接按共享词建立`() {
        val cards = mapOf(
            "a" to "张量形状决定矩阵乘能否进行，形状不匹配会报错。",
            "b" to "矩阵乘要求张量形状对齐，形状不对就无法相乘。",
            "c" to "今天天气很好，适合出门散步。"
        )
        val links = CrossLinker.compute(cards)

        // a 与 b 共享「张量」「形状」等词，应连边
        assertTrue(links["a"]!!.any { it.targetCardId == "b" })
        // a 与 c 没有共享词，不连边
        assertTrue(links["a"]!!.none { it.targetCardId == "c" })
    }

    @Test
    fun `交叉链接封顶五条`() {
        val cards = (1..12).associate { "c$it" to "张量形状决定矩阵乘能否进行" }
        val links = CrossLinker.compute(cards)
        links.forEach { (_, list) ->
            assertTrue("每卡最多 5 条", list.size <= CrossLinker.MAX_LINKS)
        }
    }

    @Test
    fun `链接按权重降序`() {
        val cards = mapOf(
            "a" to "张量形状匹配矩阵乘对齐",
            "b" to "张量形状匹配矩阵乘对齐",
            "c" to "张量形状无关的一句话完全不同的内容"
        )
        val links = CrossLinker.compute(cards)["a"]!!
        val weights = links.map { it.weight }
        assertEquals(weights.sortedDescending(), weights)
    }

    @Test
    fun `空正文不连边`() {
        val links = CrossLinker.compute(mapOf("a" to "", "b" to "有内容的一句中文"))
        assertTrue(links["a"]!!.isEmpty())
    }

    @Test
    fun `分词过滤单字与停用词`() {
        val tokens = CrossLinker.tokenize("这是一个张量形状的问题")
        assertFalse(tokens.contains("这是"))
        assertTrue(tokens.contains("张量"))
    }
}
