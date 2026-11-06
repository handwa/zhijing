package com.example.zhijing.kg.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zhijing.kg.MergedGraph
import com.example.zhijing.ui.theme.ZjSpace
import kotlin.math.min

/**
 * 结构图：语义布局的节点连线视图。
 *
 * | 元素 | 样式 |
 * |---|---|
 * | 章节标题 | 主色胶囊（对应「绿色胶囊 = 大纲节点」的设计） |
 * | 卡片 | 白底圆角矩形，右上角圆标显示命题数 |
 * | 层级连线 | 实线 |
 * | 交叉链接 | **虚线** —— 横向跳转的唯一视觉线索 |
 *
 * 交互：双指缩放（0.35–2.4×）、单指拖动平移、点击卡片跳回广度视图。
 *
 * 为什么用 [Canvas] 而不是堆 Compose 组件：
 * 24 个节点 + 上百条线，用组件堆既难画线也难做整体变换；Canvas 一次绘制、
 * 一个 `graphicsLayer` 式的变换即可。但**卡片文字仍由 Compose 测量**（[TextMeasurer]），
 * 保证字号随系统缩放设置变化。
 */
@Composable
fun GraphTreeView(
    graph: MergedGraph,
    onCardClick: (cardId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = remember(graph) { TreeLayout.layout(graph) }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    // 视图变换：
    // - scale 是**相对初始适配比例**的倍率（1.0 = 恰好放下全部内容）
    // - offset 是平移量，单位 dp 概念值
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // 初始适配比例：把内容缩放到能看全。由容器尺寸算出来，见 fitScale。
    var fitScale by remember { mutableFloatStateOf(1f) }
    var viewportPx by remember { mutableStateOf(Size.Zero) }

    val colors = MaterialTheme.colorScheme
    val statusColors = com.example.zhijing.ui.theme.LocalStatusColors.current

    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                // 手势必须挂在 Canvas 上，否则拿不到触点
                .pointerInput(layout) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        scale = next
                        // pan 单位是像素，除以 density 换成 dp 概念值
                        offset = Offset(
                            x = offset.x + pan.x / density.density,
                            y = offset.y + pan.y / density.density,
                        )
                    }
                }
        ) {
            // 容器尺寸变化时重算"恰好放下全部内容"的比例
            if (viewportPx != size) {
                viewportPx = size
                fitScale = computeFitScale(layout, size, density.density)
            }

            // Canvas 的 DrawScope 坐标单位是**像素**，而 TreeLayout 产出的是 dp 概念值。
            //
            // 统一做法：在变换里再加一次 `scale(density)`，把「dp 坐标」映射到像素。
            // 这样内部所有绘制都直接使用 dp 原值，不必每处手乘 density ——
            // 之前就是漏乘，导致 118dp 的卡片被当成 118px，全部挤在一起。
            val pxPerDp = fitScale * scale * density.density
            withTransform({
                // 平移量是 dp，乘 density 换成像素
                translate(offset.x * density.density, offset.y * density.density)
                // 内容缩放 + dp→像素，合并成一次
                scale(fitScale * scale * density.density, fitScale * scale * density.density, Offset.Zero)
            }) {
                drawLines(layout, colors.outline, colors.outlineVariant, colors.primary, pxPerDp)
                drawNodes(layout, measurer, colors, density.density)
            }
        }

        // 右上角控制
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(ZjSpace.md),
            color = colors.surface.copy(alpha = 0.92f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            tonalElevation = 2.dp,
        ) {
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = { scale = (scale * 1.25f).coerceAtMost(MAX_SCALE) }) {
                    Text("＋")
                }
                TextButton(onClick = { scale = (scale / 1.25f).coerceAtLeast(MIN_SCALE) }) {
                    Text("－")
                }
                TextButton(onClick = {
                    scale = 1f
                    offset = Offset.Zero
                }) { Text("复位") }
            }
        }

        // 左下角图例
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(ZjSpace.md),
            color = colors.surface.copy(alpha = 0.92f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            tonalElevation = 2.dp,
        ) {
            androidx.compose.foundation.layout.Column(Modifier.padding(ZjSpace.sm)) {
                Text(
                    "━ 必须前置　┅ 推荐前置",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
                Text(
                    "·· 关联　│ 层级",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    "双指缩放 · 拖动平移",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** 缩放范围：与 `kg/web` 的 0.35–2.4× 保持一致。 */
private const val MIN_SCALE = 0.35f
private const val MAX_SCALE = 2.4f

/**
 * 计算「恰好放下全部内容」的缩放比。
 *
 * 内容宽高是 dp 概念值，视口是像素，所以两边先统一到像素再比。
 * 上限 1.0：内容比屏幕小时不放大（放大反而露馅，字号会糊）。
 */
private fun computeFitScale(
    layout: TreeLayout.Result,
    viewport: Size,
    density: Float
): Float {
    if (layout.isEmpty || viewport.width <= 0f || viewport.height <= 0f) return 1f
    val contentW = layout.contentWidth * density
    val contentH = layout.contentHeight * density
    if (contentW <= 0f || contentH <= 0f) return 1f
    val fit = minOf(viewport.width / contentW, viewport.height / contentH)
    return fit.coerceIn(0.2f, 1f)
}

/**
 * 画连线。
 *
 * 视觉契约：
 *
 * | 样式 | 线型 | 颜色 | 粗细 |
 * |---|---|---|---|
 * | 章节层级 | 实线 | 描边色（最淡） | 1.5 |
 * | **必须前置** | **实线** | 主色（醒目） | 2.0 |
 * | **推荐前置** | **虚线** | 主色半透明 | 1.2 |
 * | 交叉链接 | 细虚线 | 描边色（最淡） | 1.0 |
 *
 * 坐标直接用 dp 原值，由外层变换统一处理。
 * 线宽需要**反比于缩放**，否则放大时线会跟着变粗 —— 除以 [pxPerDp] 抵消。
 */
private fun DrawScope.drawLines(
    layout: TreeLayout.Result,
    hierarchyColor: Color,
    crossColor: Color,
    primaryColor: Color,
    pxPerDp: Float,
) {
    val unit = 1f / pxPerDp.coerceAtLeast(0.01f)
    // 虚线间距按屏幕像素固定，因此同乘 unit
    val dashFine = PathEffect.dashPathEffect(floatArrayOf(4f * unit, 4f * unit), 0f)
    val dashWide = PathEffect.dashPathEffect(floatArrayOf(8f * unit, 5f * unit), 0f)

    layout.lines.forEach { line ->
        val start = Offset(line.fromX, line.fromY)
        val end = Offset(line.toX, line.toY)
        when (line.style) {
            TreeLayout.Line.Style.HIERARCHY ->
                drawLine(hierarchyColor, start, end, strokeWidth = 1.5f * unit)

            TreeLayout.Line.Style.PREREQUISITE_REQUIRED ->
                drawLine(primaryColor, start, end, strokeWidth = 2f * unit)

            TreeLayout.Line.Style.PREREQUISITE_RECOMMENDED ->
                drawLine(
                    primaryColor.copy(alpha = 0.65f),
                    start,
                    end,
                    strokeWidth = 1.2f * unit,
                    pathEffect = dashWide
                )

            TreeLayout.Line.Style.CROSS ->
                drawLine(
                    crossColor.copy(alpha = 0.6f),
                    start,
                    end,
                    strokeWidth = 1f * unit,
                    pathEffect = dashFine
                )
        }
    }
}

/**
 * 画节点。
 *
 * **坐标直接用 dp 原值**：外层的 `withTransform` 已合并做了「内容缩放 + dp→像素」，
 * 这里若再乘 density 就会重复放大（这正是卡片重叠的根因）。
 *
 * 文字的特殊处理：`TextMeasurer` 产出的是**像素**尺寸，而这里的坐标系是 dp。
 * 因此文字不参与 outer 的 density 变换 —— 单独用 `withTransform { scale(1/density) }`
 * 把坐标系临时退回 1:1，让像素尺寸正确落位。
 */
private fun DrawScope.drawNodes(
    layout: TreeLayout.Result,
    measurer: TextMeasurer,
    colors: androidx.compose.material3.ColorScheme,
    density: Float,
) {
    layout.nodes.forEach { node ->
        val topLeft = Offset(node.x, node.y)
        val size = Size(node.width, node.height)

        if (node.isSection) {
            drawRoundRect(
                color = colors.primary.copy(alpha = 0.14f),
                topLeft = topLeft,
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(node.height / 2)
            )
            drawRoundRect(
                color = colors.primary.copy(alpha = 0.5f),
                topLeft = topLeft,
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(node.height / 2),
                style = Stroke(width = 1f)
            )
        } else {
            drawRoundRect(
                color = colors.surface,
                topLeft = topLeft,
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f)
            )
            drawRoundRect(
                color = if (node.descendable) colors.primary.copy(alpha = 0.55f) else colors.outline,
                topLeft = topLeft,
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f),
                style = Stroke(width = if (node.descendable) 1.6f else 1f)
            )
        }

        // 文本：宽度约束用像素（= dp × density）
        val textLayout = measurer.measure(
            text = node.title,
            style = TextStyle(
                color = if (node.isSection) colors.primary else colors.onSurface,
                fontSize = if (node.isSection) 12.sp else 11.sp,
            ),
            maxLines = 2,
            constraints = androidx.compose.ui.unit.Constraints(
                maxWidth = ((node.width - 12f) * density).toInt().coerceAtLeast(1)
            ),
        )
        // 像素尺寸换回 dp 以定位
        val textW = textLayout.size.width / density
        val textH = textLayout.size.height / density
        val textX = node.x + 6f
        val textY = node.y + (node.height - textH) / 2f
        // 临时抵消 density：让文字以其像素尺寸直接落位
        withTransform({ scale(1f / density, 1f / density, Offset.Zero) }) {
            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(textX * density, textY * density)
            )
        }

        // 右上角命题数圆标
        if (node.claimCount > 0) {
            val r = 9f
            val cx = node.x + node.width - 2f
            val cy = node.y + 2f
            drawCircle(color = colors.primary, radius = r, center = Offset(cx, cy))
            val badge = measurer.measure(
                text = node.claimCount.toString(),
                style = TextStyle(color = colors.onPrimary, fontSize = 9.sp),
            )
            withTransform({ scale(1f / density, 1f / density, Offset.Zero) }) {
                drawText(
                    textLayoutResult = badge,
                    topLeft = Offset(
                        (cx - (badge.size.width / density) / 2f) * density,
                        (cy - (badge.size.height / density) / 2f) * density
                    )
                )
            }
        }
    }
}
