package com.example.zhijing.kg.ui

import com.example.zhijing.kg.PrerequisiteKind
import com.example.zhijing.kg.PrerequisiteLink
import com.example.zhijing.kg.PrerequisiteSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 前置解释的文案口径。
 *
 * 写错来源标签的后果不是崩溃，而是**把模型推断说成课程事实** —— 只能靠断言钉住。
 */
class PrerequisiteTextTest {

    @Test
    fun `人工书写的前置标成人工书写`() {
        val line = PrerequisiteText.of(
            link(source = PrerequisiteSource.AUTHORED_HUMAN, reason = "Softmax 的输入是线性层的 logits"),
            cardTitles = mapOf("crd.s1-1-linear" to "1.1 Linear 层"),
        )

        assertEquals("1.1 Linear 层", line.title)
        assertEquals("人工书写", line.sourceLabel)
        assertEquals("Softmax 的输入是线性层的 logits", line.reason)
        assertEquals("必须前置", line.kindLabel)
    }

    @Test
    fun `模型推断的前置必须标未经核实`() {
        val line = PrerequisiteText.of(
            link(source = PrerequisiteSource.AUTHORED_MODEL),
            cardTitles = emptyMap(),
        )

        assertEquals("未经核实", line.sourceLabel)
        // 没给原因就留空，不编一句「两者相关」糊过去
        assertNull(line.reason)
    }

    @Test
    fun `推荐前置用推荐文案`() {
        val line = PrerequisiteText.of(
            link(kind = PrerequisiteKind.RECOMMENDED),
            cardTitles = mapOf("crd.a" to "A"),
        )

        assertEquals("推荐前置", line.kindLabel)
    }

    @Test
    fun `标题缺失时回退成卡片 id 尾段`() {
        val line = PrerequisiteText.of(link(), cardTitles = emptyMap())

        // 卡片被删（例如生成主题重建）时不能让界面出现空白
        assertEquals("s1-1-linear", line.title)
    }

    @Test
    fun `标题是空白串时同样回退`() {
        val line = PrerequisiteText.of(link(), cardTitles = mapOf("crd.s1-1-linear" to "  "))

        assertEquals("s1-1-linear", line.title)
    }

    /* ------------------------------------------------------------ 工具 */

    private fun link(
        kind: PrerequisiteKind = PrerequisiteKind.REQUIRED,
        reason: String = "",
        source: PrerequisiteSource = PrerequisiteSource.AUTHORED_MODEL,
    ) = PrerequisiteLink(
        from = "crd.s1-1-linear",
        to = "crd.s1-3-softmax",
        kind = kind,
        reason = reason,
        source = source,
    )
}
