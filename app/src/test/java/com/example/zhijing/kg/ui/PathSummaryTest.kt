package com.example.zhijing.kg.ui

import com.example.zhijing.kg.ConditionPathStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 路径摘要的文案口径。
 *
 * 这里守的是「摘要只在真的发生变化时出现」——写错的后果是界面上出现
 * 「你从「仍然成立」走到「仍然成立」」这种凑字数的话。
 */
class PathSummaryTest {

    @Test
    fun `有原来猜的用原来猜的做起点`() {
        val summary = PathSummary.of(
            listOf(
                step(predicted = "不再成立", previous = "仍然成立"),
            )
        )

        assertEquals("你从「仍然成立」走到「不再成立」", summary)
    }

    @Test
    fun `多步取首步起点与末步终点`() {
        val summary = PathSummary.of(
            listOf(
                step(predicted = "不再成立", previous = "仍然成立"),
                step(predicted = "运算未定义", previous = "不再成立"),
            )
        )

        assertEquals("你从「仍然成立」走到「运算未定义」", summary)
    }

    @Test
    fun `没有原来猜的就用当前猜测做起点`() {
        val summary = PathSummary.of(
            listOf(
                step(predicted = "仍然成立", previous = null),
                step(predicted = "运算未定义", previous = "仍然成立"),
            )
        )

        assertEquals("你从「仍然成立」走到「运算未定义」", summary)
    }

    @Test
    fun `首末相同不显示`() {
        // 只有一条、且没改过：说「你从 X 走到 X」等于废话
        assertNull(PathSummary.of(listOf(step(predicted = "仍然成立", previous = null))))
    }

    @Test
    fun `空路径不显示`() {
        assertNull(PathSummary.of(emptyList()))
    }

    @Test
    fun `空白内容当作没有`() {
        assertNull(PathSummary.of(listOf(step(predicted = "  ", previous = null))))
    }

    @Test
    fun `首尾空白会被裁掉`() {
        val summary = PathSummary.of(
            listOf(step(predicted = " 运算未定义 ", previous = "仍然成立 "))
        )

        assertEquals("你从「仍然成立」走到「运算未定义」", summary)
    }

    /* ------------------------------------------------------------ 工具 */

    private fun step(predicted: String, previous: String?) = ConditionPathStep(
        interventionId = "inv.test",
        action = "把输入最后一维 4 改成 5",
        statement = "内维必须相等",
        predicted = predicted,
        previousPredicted = previous,
        actual = "运算未定义",
        matched = previous != null,
        attempts = 1,
        wrongAttempts = if (previous != null) 1 else 0,
    )
}
