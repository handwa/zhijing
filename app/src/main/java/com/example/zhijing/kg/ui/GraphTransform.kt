package com.example.zhijing.kg.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * 结构图的视图变换数学。
 *
 * ## 为什么单独抽出来
 *
 * 缩放锚点与平移边界都是纯几何，放在 Composable 里既难测又容易写错单位。
 * 抽成不依赖 Compose 运行时（只用 `Offset` / `Size` 这两个纯 Kotlin 值类）的纯函数后，
 * 可以直接用普通 JUnit 断言公式，不必上 Compose 测试框架。
 *
 * ## 坐标约定（全链路只有这一套）
 *
 * - `TreeLayout` 产出的是 **dp 概念值**；
 * - `offset` 也是 dp 概念值（屏幕坐标除以 density）；
 * - 内容在屏幕上的实际尺寸 = `内容dp × fitScale × scale`；
 * - 绘制时才统一乘 `density` 变成像素。
 *
 * 不要在调用方再引入第二套单位 —— roadmap 记录过「Canvas 坐标单位混乱导致卡片全挤在一起」的坑。
 */
internal object GraphTransform {

    /**
     * 允许内容越出视口的最大比例（相对视口尺寸）。
     *
     * 留一点余量是为了手感：完全钉死会让用户在边缘拖起来像撞墙；
     * 但 30% 已经足以保证「怎么拖都还剩一部分可见」。
     */
    const val OVERSCROLL_RATIO = 0.3f

    /**
     * 把平移量钳制到合法范围，保证内容不会被整个拖出屏幕。
     *
     * @param offset 当前平移（dp 概念值）
     * @param contentDp 布局产出的内容尺寸（dp）
     * @param viewportPx 可视区域尺寸（像素）
     * @param density 屏幕密度（px per dp）
     * @param renderScale 内容 dp → 屏幕 dp 的倍率，即 `fitScale * scale`
     * @param overscrollRatio 允许的越界比例
     */
    fun clampOffset(
        offset: Offset,
        contentDp: Size,
        viewportPx: Size,
        density: Float,
        renderScale: Float,
        overscrollRatio: Float = OVERSCROLL_RATIO,
    ): Offset {
        if (density <= 0f || viewportPx.width <= 0f || viewportPx.height <= 0f) return offset

        val viewportDp = Size(viewportPx.width / density, viewportPx.height / density)
        val contentOnScreen = Size(
            width = contentDp.width * renderScale,
            height = contentDp.height * renderScale,
        )

        return Offset(
            x = clampAxis(offset.x, contentOnScreen.width, viewportDp.width, overscrollRatio),
            y = clampAxis(offset.y, contentOnScreen.height, viewportDp.height, overscrollRatio),
        )
    }

    /**
     * 以手指位置为锚点缩放。
     *
     * 目标：缩放前后，手指底下的那个内容点保持不动。
     *
     * 由 `屏幕位置 = 内容点 × k + offset`（k 与 scale 成正比）可得：
     *
     * ```
     * newOffset = centroid - (centroid - oldOffset) × (newScale / oldScale)
     * ```
     *
     * @param centroidPx 手势中心（像素）
     * @return 新的平移量（dp 概念值）
     */
    fun zoomAround(
        offset: Offset,
        oldScale: Float,
        newScale: Float,
        centroidPx: Offset,
        density: Float,
    ): Offset {
        if (density <= 0f || oldScale <= 0f || newScale <= 0f) return offset

        val centroidDp = Offset(centroidPx.x / density, centroidPx.y / density)
        val ratio = newScale / oldScale
        return Offset(
            x = centroidDp.x - (centroidDp.x - offset.x) * ratio,
            y = centroidDp.y - (centroidDp.y - offset.y) * ratio,
        )
    }

    /**
     * 单轴钳制。
     *
     * 两种情况分开写，因为可行区间的含义不同：
     * - 内容比视口**大**：允许两边各越界 [ratio] 个视口，其余钉住（拖到头就停）；
     * - 内容比视口**小**：整块留在视口内，同样保留 [ratio] 的越界余量，避免「一拖就飞走」。
     *
     * 若统一用「min/max 互换」的写法，小内容会被强行顶到离边 `margin` 处，一进页面就偏移，
     * 反而不自然。
     */
    private fun clampAxis(offset: Float, content: Float, viewport: Float, ratio: Float): Float {
        if (content <= 0f || viewport <= 0f) return offset

        val margin = viewport * ratio
        return if (content >= viewport) {
            offset.coerceIn(viewport - content - margin, margin)
        } else {
            offset.coerceIn(-margin, viewport - content + margin)
        }
    }
}
