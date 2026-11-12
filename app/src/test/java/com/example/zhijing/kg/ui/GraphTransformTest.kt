package com.example.zhijing.kg.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 视图变换的纯函数测试。
 *
 * 这里只断言几何关系（内容是否还可见、锚点是否不动），不涉及 Compose 运行时。
 */
class GraphTransformTest {

    /** 常见组合：1080×1920 像素、density 3 → 视口 360×640 dp。 */
    private val viewportPx = Size(1080f, 1920f)
    private val density = 3f
    private val viewportDp = 360f

    /* ------------------------------------------------------------ 平移钳制 */

    @Test
    fun offsetWithinRangeIsKept() {
        val content = Size(600f, 400f)

        val clamped = GraphTransform.clampOffset(
            offset = Offset(0f, 0f),
            contentDp = content,
            viewportPx = viewportPx,
            density = density,
            renderScale = 1f,
        )

        assertEquals(0f, clamped.x, 0.001f)
        assertEquals(0f, clamped.y, 0.001f)
    }

    @Test
    fun draggingFarRightCannotPushContentOffScreen() {
        val content = Size(600f, 400f)

        val clamped = GraphTransform.clampOffset(
            offset = Offset(10_000f, 0f),
            contentDp = content,
            viewportPx = viewportPx,
            density = density,
            renderScale = 1f,
        )

        val margin = viewportDp * GraphTransform.OVERSCROLL_RATIO
        assertEquals(margin, clamped.x, 0.001f)
        // 内容左边缘仍在视口内（右移了 margin），没有整块拖走
        assertEquals(true, clamped.x < viewportDp)
    }

    @Test
    fun draggingFarLeftCannotPushContentOffScreen() {
        val content = Size(600f, 400f)

        val clamped = GraphTransform.clampOffset(
            offset = Offset(-10_000f, 0f),
            contentDp = content,
            viewportPx = viewportPx,
            density = density,
            renderScale = 1f,
        )

        val expected = viewportDp - content.width - viewportDp * GraphTransform.OVERSCROLL_RATIO
        assertEquals(expected, clamped.x, 0.001f)
        // 内容右边缘仍在视口内
        assertEquals(true, clamped.x + content.width > 0f)
    }

    @Test
    fun smallContentStaysInsideViewport() {
        val content = Size(100f, 100f)

        val clampedRight = GraphTransform.clampOffset(
            offset = Offset(10_000f, 10_000f),
            contentDp = content,
            viewportPx = viewportPx,
            density = density,
            renderScale = 1f,
        )
        val clampedLeft = GraphTransform.clampOffset(
            offset = Offset(-10_000f, -10_000f),
            contentDp = content,
            viewportPx = viewportPx,
            density = density,
            renderScale = 1f,
        )

        // 右/下拖到头：内容右/下边缘最多越界 margin
        assertEquals(viewportDp - content.width + viewportDp * GraphTransform.OVERSCROLL_RATIO, clampedRight.x, 0.001f)
        assertEquals(-viewportDp * GraphTransform.OVERSCROLL_RATIO, clampedLeft.x, 0.001f)
    }

    @Test
    fun clampRespectsRenderScale() {
        val content = Size(600f, 400f)

        // 放大 2 倍后内容宽 1200dp，远超视口：左边界随内容变宽而左移
        val clamped = GraphTransform.clampOffset(
            offset = Offset(-10_000f, 0f),
            contentDp = content,
            viewportPx = viewportPx,
            density = density,
            renderScale = 2f,
        )

        val expected = viewportDp - content.width * 2f - viewportDp * GraphTransform.OVERSCROLL_RATIO
        assertEquals(expected, clamped.x, 0.001f)
    }

    @Test
    fun invalidViewportOrDensityLeavesOffsetUntouched() {
        val offset = Offset(12f, 34f)

        assertEquals(
            offset,
            GraphTransform.clampOffset(offset, Size(100f, 100f), Size.Zero, density, 1f)
        )
        assertEquals(
            offset,
            GraphTransform.clampOffset(offset, Size(100f, 100f), viewportPx, 0f, 1f)
        )
    }

    /* --------------------------------------------------------- 以手指缩放 */

    @Test
    fun zoomKeepsPointUnderCentroidFixed() {
        val offset = Offset(10f, 20f)
        val oldScale = 1f
        val newScale = 2f
        val centroidPx = Offset(300f, 400f)
        val density2x = 2f // 取 2 便于手算：(300,400)px → (150,200)dp

        val zoomed = GraphTransform.zoomAround(offset, oldScale, newScale, centroidPx, density2x)

        // 公式核对：newOffset = centroid - (centroid - oldOffset) * (new/old)
        val centroidDp = Offset(centroidPx.x / density2x, centroidPx.y / density2x)
        assertEquals(centroidDp.x - (centroidDp.x - offset.x) * 2f, zoomed.x, 0.001f)
        assertEquals(centroidDp.y - (centroidDp.y - offset.y) * 2f, zoomed.y, 0.001f)

        // 锚点性质：手指底下那个内容点缩放后仍在手指底下
        val contentX = (centroidDp.x - offset.x) / oldScale
        val contentY = (centroidDp.y - offset.y) / oldScale
        assertEquals(centroidDp.x, contentX * newScale + zoomed.x, 0.001f)
        assertEquals(centroidDp.y, contentY * newScale + zoomed.y, 0.001f)
    }

    @Test
    fun zoomWithSameScaleKeepsOffset() {
        val offset = Offset(-30f, 45f)

        val zoomed = GraphTransform.zoomAround(offset, 1.5f, 1.5f, Offset(100f, 100f), density)

        assertEquals(offset.x, zoomed.x, 0.001f)
        assertEquals(offset.y, zoomed.y, 0.001f)
    }

    @Test
    fun zoomWithInvalidInputKeepsOffset() {
        val offset = Offset(5f, 6f)

        assertEquals(offset, GraphTransform.zoomAround(offset, 0f, 2f, Offset(1f, 1f), density))
        assertEquals(offset, GraphTransform.zoomAround(offset, 1f, 2f, Offset(1f, 1f), 0f))
    }

    /* ------------------------------------------------------- 搜索定位 */

    @Test
    fun focusOffsetCentersTarget() {
        val content = Size(600f, 400f)

        val focused = GraphTransform.focusOffset(
            centerDp = Offset(300f, 200f),
            contentDp = content,
            viewportPx = viewportPx,
            density = density,
            renderScale = 1f,
        )

        // 视口 360×640 dp：x = 180 - 300 = -120，y = 320 - 200 = 120，都在合法范围内
        assertEquals(-120f, focused.x, 0.001f)
        assertEquals(120f, focused.y, 0.001f)

        // 校验：目标点确实落在视口中心
        assertEquals(180f, 300f * 1f + focused.x, 0.001f)
        assertEquals(320f, 200f * 1f + focused.y, 0.001f)
    }

    @Test
    fun focusOffsetIsClampedForNodesNearEdge() {
        val content = Size(600f, 400f)

        val focused = GraphTransform.focusOffset(
            centerDp = Offset(10f, 10f),
            contentDp = content,
            viewportPx = viewportPx,
            density = density,
            renderScale = 1f,
        )

        // 直接把左边缘节点居中会把内容推空，钳制到 30% 越界余量
        val margin = viewportDp * GraphTransform.OVERSCROLL_RATIO
        assertEquals(margin, focused.x, 0.001f)
    }

    @Test
    fun focusOffsetWithInvalidViewportIsZero() {
        assertEquals(
            Offset.Zero,
            GraphTransform.focusOffset(Offset(10f, 10f), Size(100f, 100f), Size.Zero, density, 1f),
        )
        assertEquals(
            Offset.Zero,
            GraphTransform.focusOffset(Offset(10f, 10f), Size(100f, 100f), viewportPx, 0f, 1f),
        )
    }
}
