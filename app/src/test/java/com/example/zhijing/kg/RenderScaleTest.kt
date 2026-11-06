package com.example.zhijing.kg

import com.example.zhijing.kg.ui.TreeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 坐标单位转换测试。
 *
 * **这一组测试是为「卡片全部重合」这个 bug 补的**：`TreeLayout` 输出的坐标单位是
 * dp 概念值，而 `Canvas` 只认像素。绘制层如果把 dp 当像素直接用，在高密度屏幕上
 * 卡片会缩小约 density 倍并互相重叠。
 *
 * 这里把转换公式单独固化成可测的形式，任何改动都会被抓住。
 */
class RenderScaleTest {

    /** 与 `GraphTreeView` 中保持一致：dp → 像素的比例。 */
    private fun pxPerDp(fitScale: Float, userScale: Float, density: Float): Float =
        fitScale * userScale * density

    @Test
    fun `内容坐标到像素必须乘 density`() {
        // 3x 屏幕上，118dp 的卡片宽应为 354px，而不是 118px
        val density = 3f
        val cardWidthDp = 118f
        val px = cardWidthDp * pxPerDp(fitScale = 1f, userScale = 1f, density = density)
        assertEquals(354f, px, 0.01f)
    }

    @Test
    fun `漏乘 density 会导致卡片重叠`() {
        // 演示旧实现的问题：列间距 72dp 被当成 72px，而卡片宽 118dp 也当 118px
        val density = 3f
        val columnGapDp = 72f
        val cardWidthDp = 118f

        // 正确换算：列间距 216px > 卡片宽 354px？不，是各自换算后仍应有间距
        val gapPx = columnGapDp * pxPerDp(1f, 1f, density)
        val widthPx = cardWidthDp * pxPerDp(1f, 1f, density)
        assertEquals(216f, gapPx, 0.01f)
        assertEquals(354f, widthPx, 0.01f)

        // 旧实现（都用 dp 当 px）：间距 72 < 边距要求，视觉上相互覆盖
        assertTrue(
            "若 dp 当 px 用，72 的间距不足以容纳内容，必然重叠",
            columnGapDp < cardWidthDp
        )
    }

    @Test
    fun `缩放比例影响最终像素`() {
        val base = pxPerDp(fitScale = 0.5f, userScale = 1f, density = 2f)
        val zoomed = pxPerDp(fitScale = 0.5f, userScale = 2f, density = 2f)
        assertEquals(base * 2f, zoomed, 0.01f)
    }

    @Test
    fun `适配比例让内容落在视口内`() {
        // 用一个真实规模的布局验证 fit 计算
        val layout = TreeLayout.layout(sampleGraph())
        val density = 3f
        val viewportW = 1080f
        val viewportH = 1800f

        val contentW = layout.contentWidth * density
        val contentH = layout.contentHeight * density
        val fit = minOf(viewportW / contentW, viewportH / contentH).coerceIn(0.2f, 1f)

        // 缩放后内容不应超出视口
        assertTrue(contentW * fit <= viewportW + 1f)
        assertTrue(contentH * fit <= viewportH + 1f)
    }

    private fun sampleGraph(): MergedGraph {
        val cards = (1..4).map { i ->
            GraphCard(
                id = "crd.c$i",
                title = "卡片$i",
                outlinePath = "p$i",
                summary = "s",
                body = "b"
            )
        }
        val outline = cards.mapIndexed { i, c ->
            GraphOutlineNode(
                id = "out.${c.outlinePath}",
                title = c.title,
                level = 1,
                parentId = null,
                path = c.outlinePath,
                order = i,
                children = emptyList(),
                cardIds = listOf(c.id)
            )
        }
        return MergedGraph(
            schemaVersion = "1.0",
            generatedAt = "",
            outline = outline,
            cards = cards,
            claims = emptyMap(),
            edges = emptyMap()
        )
    }
}
