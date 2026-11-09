package com.example.zhijing.kg

import com.example.zhijing.kg.ui.stripCodeFences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown 标记清理的测试。
 *
 * 结构图的节点详情弹窗和卡片流都直接把原文当纯文本渲染，
 * 因此必须把 Markdown 标记**去掉**，而不是让 ` ``` `、`**`、`` ` `` 露给用户。
 * 这不是美观问题 —— 用户看到一堆符号会以为内容坏了。
 */
class MarkdownStripTest {

    @Test
    fun `去掉代码围栏`() {
        val raw = """
            一个最小编码块可以写成下面这条链：

            ```text
            Linear → ReLU → Softmax
            ```

            每一步都能接不上。
        """.trimIndent()
        val out = stripCodeFences(raw)
        assertFalse("不应残留围栏", out.contains("```"))
        assertTrue("应保留内容", out.contains("Linear → ReLU → Softmax"))
        assertTrue("应保留正文", out.contains("每一步都能接不上。"))
    }

    @Test
    fun `去掉行内反引号`() {
        val out = stripCodeFences("输出是 `(N, out_features)` 的形状")
        assertEquals("输出是 (N, out_features) 的形状", out)
    }

    @Test
    fun `去掉粗体标记`() {
        val out = stripCodeFences("**Linear** 层做的是仿射变换")
        assertEquals("Linear 层做的是仿射变换", out)
        assertFalse(out.contains("**"))
    }

    @Test
    fun `去掉标题井号`() {
        val out = stripCodeFences("#### 小结\n内容是这些")
        assertFalse("不应残留井号", out.contains("#"))
        assertTrue(out.contains("小结"))
    }

    @Test
    fun `纯文本原样返回`() {
        val raw = "这是一段没有标记的正文，包含数字 3.14 与符号 → 和 =。"
        assertEquals(raw, stripCodeFences(raw))
    }

    @Test
    fun `空字符串安全`() {
        assertEquals("", stripCodeFences(""))
        assertEquals("   ", stripCodeFences("   "))
    }

    @Test
    fun `不误删正常的星号`() {
        // 数学里的乘号不该被吃掉
        val out = stripCodeFences("2 * 3 = 6")
        assertTrue("乘号应保留：$out", out.contains("*"))
    }

    @Test
    fun `真实卡片正文清理后无标记残留`() {
        val raw = "**Linear** 层做的是仿射变换：`y = x @ W.T + b`。输入 `x` 的形状通常是 `(N, in_features)`。"
        val out = stripCodeFences(raw)
        assertFalse("不应残留反引号", out.contains("`"))
        assertFalse("不应残留粗体标记", out.contains("**"))
        assertTrue(out.contains("Linear 层做的是仿射变换"))
        assertTrue(out.contains("y = x @ W.T + b"))
    }
}
