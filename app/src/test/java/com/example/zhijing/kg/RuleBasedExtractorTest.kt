package com.example.zhijing.kg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地规则抽取测试。
 *
 * 移植自 `kg/src/extract_rules.py` 的判断题表路径，因此用例刻意对照 Python 侧的行为：
 * 表头识别、真值判定、边界列提取、非表格不产出。
 */
class RuleBasedExtractorTest {

    private val markdown = """
        ## 2 层与层之间的五种依赖

        一些自由文本，规则抽取不看。

        | 判断句 | 是否成立 | 边界 |
        |---|---|---|
        | ReLU 是逐元素操作，不改变输入形状 | 成立 | 逐元素 |
        | BatchNorm 在 batch=1 时稳定 | 不成立 | batch=1 时方差为 0 |
        | 看不懂的答案列 | 也许 | 未标注 |
    """.trimIndent()

    @Test
    fun `识别判断题表并抽出命题`() {
        val r = RuleBasedExtractor.extractTableClaims(markdown, "arch.dep")
        assertEquals(2, r.claims.size)
        assertEquals("ReLU 是逐元素操作，不改变输入形状", r.claims[0].statement)
        assertTrue(r.claims[0].expected)
        assertEquals("BatchNorm 在 batch=1 时稳定", r.claims[1].statement)
        assertFalse(r.claims[1].expected)
    }

    @Test
    fun `边界列被写进条件`() {
        val r = RuleBasedExtractor.extractTableClaims(markdown, "arch.dep")
        assertTrue(r.claims[0].conditions.single().contains("逐元素"))
        assertTrue(r.claims[1].conditions.single().contains("方差为 0"))
    }

    @Test
    fun `无法判定的行被跳过并告警`() {
        val r = RuleBasedExtractor.extractTableClaims(markdown, "arch.dep")
        assertTrue(r.warnings.any { it.contains("跳过") })
    }

    @Test
    fun `自由文本不产出命题`() {
        val r = RuleBasedExtractor.extractTableClaims("ReLU 是激活函数。Softmax 做归一化。", "x")
        assertTrue(r.claims.isEmpty())
        // 这是规则路径的固有边界：它只认显式结构，不猜自由文本
        assertTrue(r.warnings.any { it.contains("未找到判断题表") })
    }

    @Test
    fun `没有真值列的表格不当作判断题表`() {
        val md = """
            | 名称 | 说明 |
            |---|---|
            | ReLU | 激活函数 |
        """.trimIndent()
        val r = RuleBasedExtractor.extractTableClaims(md, "x")
        assertTrue(r.claims.isEmpty())
    }

    @Test
    fun `真值判定函数与Python侧一致`() {
        assertEquals(false, RuleBasedExtractor.truthOf("不成立"))
        assertEquals(false, RuleBasedExtractor.truthOf("错"))
        assertEquals(false, RuleBasedExtractor.truthOf("否"))
        assertEquals(true, RuleBasedExtractor.truthOf("成立"))
        assertEquals(true, RuleBasedExtractor.truthOf("对"))
        assertEquals(true, RuleBasedExtractor.truthOf("是"))
        assertNull(RuleBasedExtractor.truthOf("也许"))
        assertNull(RuleBasedExtractor.truthOf(""))
    }

    @Test
    fun `领域词识别覆盖词表`() {
        val entities = RuleBasedExtractor.extractEntities(
            "Linear 层之后接 ReLU，再经过 Softmax，最后用 CrossEntropy。"
        )
        assertTrue("Linear" in entities)
        assertTrue("ReLU" in entities)
        assertTrue("Softmax" in entities)
        assertTrue("CrossEntropy" in entities)
    }

    @Test
    fun `领域词识别别名`() {
        val entities = RuleBasedExtractor.extractEntities("用残差和批归一化提升稳定性。")
        // 「残差」是 残差连接 的别名；「批归一化」是 BatchNorm 的别名
        assertTrue("残差连接" in entities)
        assertTrue("BatchNorm" in entities)
    }

    @Test
    fun `空文本不产出`() {
        val r = RuleBasedExtractor.extractTableClaims("", "x")
        assertTrue(r.claims.isEmpty())
    }
}
