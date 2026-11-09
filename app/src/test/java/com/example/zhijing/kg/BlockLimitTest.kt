package com.example.zhijing.kg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 块数上限的配置测试。
 *
 * ## 背景：一个曾经真实发生的 bug
 *
 * 代码里曾同时存在两个常量：
 * - `CloudOutlineExtractor.MAX_BLOCKS = 12` —— 用于请求参数
 * - `OutlineOutputParser` 内部的 `private const val MAX_BLOCKS = 12` —— 用于解析截断
 *
 * 改外面那个、里面这个不变，结果**改了上限却毫无效果**，而且不会报错。
 * 这组测试锁住「解析器必须按调用方给的上限截断」，防止再分裂。
 */
class BlockLimitTest {

    private fun blockJson(title: String, body: String = "正文") =
        """{"title":"$title","summary":"概括","body":"$body"}"""

    private fun blocksJson(n: Int): String =
        (1..n).joinToString(",", prefix = """{"blocks":[""", postfix = "]}") {
            blockJson("$it 第 $it 块")
        }

    @Test
    fun `解析器按显式传入的上限截断`() {
        val raw = blocksJson(20)
        val r = OutlineOutputParser.parse(raw, maxBlocks = 5)
        assertEquals("传入 5 就只保留 5 块", 5, r.blocks.size)
        assertTrue("应给出截断警告", r.warnings.any { it.contains("超过上限") })
    }

    @Test
    fun `解析器默认上限与全局常量一致`() {
        // 不传 maxBlocks 时用的就是 CloudOutlineExtractor.MAX_BLOCKS
        val raw = blocksJson(CloudOutlineExtractor.MAX_BLOCKS + 10)
        val r = OutlineOutputParser.parse(raw)
        assertEquals(CloudOutlineExtractor.MAX_BLOCKS, r.blocks.size)
    }

    @Test
    fun `上限之内不截断也不警告`() {
        val r = OutlineOutputParser.parse(blocksJson(3), maxBlocks = 10)
        assertEquals(3, r.blocks.size)
        assertTrue("不应有截断警告", r.warnings.none { it.contains("超过上限") })
    }

    @Test
    fun `全局上限已放到 50`() {
        assertEquals(50, CloudOutlineExtractor.MAX_BLOCKS)
    }

    /* ------------------------------------------------ 请求参数一致性 */

    @Test
    fun `请求里的 max_blocks 跟随全局上限`() {
        val p = CloudOutlineExtractor.userPrompt(topic = "测试主题")
        assertTrue(
            "user prompt 里的 max_blocks 应是 ${CloudOutlineExtractor.MAX_BLOCKS}，实际：$p",
            p.contains("\"max_blocks\": ${CloudOutlineExtractor.MAX_BLOCKS}")
        )
    }

    @Test
    fun `max_blocks 不会被放大到超过全局上限`() {
        val p = CloudOutlineExtractor.userPrompt(topic = "x", maxBlocks = 999)
        assertTrue(p.contains("\"max_blocks\": ${CloudOutlineExtractor.MAX_BLOCKS}"))
    }

    /* ---------------------------------------------------- 输出预算 */

    @Test
    fun `输出上限覆盖 50 块的最长正文`() {
        // 实测：50 块 × 200 字正文 ≈ 7780 token
        // 若上限低于这个值，生成会被硬截断 —— 现象同样是「块数不够」
        assertTrue(
            "无原文模式上限应 >= 8000，实际 ${CloudOutlineExtractor.DEFAULT_TOPIC_MAX_TOKENS}",
            CloudOutlineExtractor.DEFAULT_TOPIC_MAX_TOKENS >= 8000
        )
        // 有原文模式不写正文，50 块约 3500 token
        assertTrue(
            "有原文模式上限应 >= 4000，实际 ${CloudOutlineExtractor.DEFAULT_MAX_TOKENS}",
            CloudOutlineExtractor.DEFAULT_MAX_TOKENS >= 4000
        )
    }
}
