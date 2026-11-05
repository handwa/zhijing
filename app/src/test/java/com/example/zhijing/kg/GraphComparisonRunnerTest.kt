package com.example.zhijing.kg

import com.example.zhijing.cloud.CloudProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 生成质量对比测试的执行逻辑。
 *
 * 关键约束：**云端未配置时不是失败，而是「未执行」**。
 * 用户没配云服务是合法状态，不该在测试报告里显示成一次错误。
 */
class GraphComparisonRunnerTest {

    private val runner = GraphComparisonRunner()

    private val table = """
        | 判断句 | 是否成立 | 边界 |
        |---|---|---|
        | ReLU 不改变形状 | 成立 | 逐元素 |
        | Dropout 推理时仍随机置零 | 不成立 | 推理关闭 |
    """.trimIndent()

    @Test
    fun `未配置云端时云端路径标记为未执行`() = runBlocking {
        val r = runner.compare(text = table, path = "x", provider = null)

        assertTrue(r.rule.success)
        assertFalse(r.cloud.success)
        assertTrue(r.cloud.failure!!.contains("未配置"))
        // 规则路径不受影响
        assertEquals(2, r.rule.claimCount)
    }

    @Test
    fun `配置不完整时同样不发起请求`() = runBlocking {
        val broken = CloudProvider.DEEPSEEK.copy(apiKey = "")
        val r = runner.compare(text = table, path = "x", provider = broken)

        assertFalse(r.cloud.success)
        assertTrue(r.cloud.failure!!.contains("配置不完整"))
        assertEquals(0, r.cloud.claimCount)
    }

    @Test
    fun `规则路径能抽出可用命题`() = runBlocking {
        val r = runner.compare(text = table, path = "x", provider = null)
        assertEquals(2, r.rule.usableCount)
        assertEquals(1f, r.rule.usableRate, 0.001f)
        // 判断题表路径会补上语义依赖作为错点归因
        assertEquals(2, r.rule.withErrorKindCount)
    }

    @Test
    fun `自由文本下规则路径为空这是分工而非缺陷`() = runBlocking {
        val r = runner.compare(
            text = "ReLU 是逐元素激活，输出非负。Softmax 期望未归一化的 logits。",
            path = "x",
            provider = null
        )
        assertEquals(0, r.rule.claimCount)
        // 但领域词仍然被识别出来，说明规则路径「看得见概念、看不出断言」
        assertTrue(r.ruleEntities.contains("ReLU"))
        assertTrue(r.ruleEntities.contains("Softmax"))
    }

    @Test
    fun `重合度计算`() {
        // 两条路径都为空时重合度为 0，不抛除零
        val empty = GraphComparisonRunner.PathResult(
            path = GraphComparisonRunner.GenerationPath.LOCAL_RULES,
            success = true, claims = emptyList(), warnings = emptyList(), elapsedMs = 0
        )
        val r = GraphComparisonRunner.ComparisonResult(
            textPreview = "", textLength = 0, rule = empty, cloud = empty, ruleEntities = emptyList()
        )
        assertEquals(0f, r.overlap, 0.001f)
        assertTrue(r.summaryLine().contains("重合 0%"))
    }

    @Test
    fun `摘要行包含两条路径的命题数`() = runBlocking {
        val r = runner.compare(text = table, path = "x", provider = null)
        val line = r.summaryLine()
        assertTrue(line.contains("规则 2 条"))
        assertTrue(line.contains("云端 0 条"))
    }
}
