package com.example.zhijing.kg.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
    /**
     * 点击某个节点。参数是被点节点的路径。
     *
     * 结构图不再单独画卡片（卡片与节点 1:1、标题 100% 重复），
     * 因此**节点是唯一入口**：点它 → 底部弹窗给出该节点的卡片内容。
     */
    onNodeClick: (nodePath: String) -> Unit,
    /** 当前选中的节点路径；选中后与它相连的线高亮、其余淡出。 */
    selectedNode: String?,
    /** 搜索/切换视图时把某个节点移到视口中央；null 表示不主动移动视图。 */
    focusRequest: GraphFocusRequest? = null,
    modifier: Modifier = Modifier,
) {
    // 「只看依赖」：隐藏层级线，并让同一父节点下按依赖顺序排列。
    // 回答的是「我该按什么顺序学」，而层级视图回答「这个主题由哪些部分组成」。
    var dependencyOnly by remember { mutableStateOf(false) }
    val layout = remember(graph, dependencyOnly) {
        TreeLayout.layout(
            graph = graph,
            mode = if (dependencyOnly) {
                TreeLayout.LayoutMode.DEPENDENCY
            } else {
                TreeLayout.LayoutMode.HIERARCHY
            },
        )
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    // 视图变换：
    // - scale 是**相对初始适配比例**的倍率（1.0 = 恰好放下全部内容）
    // - offset 是平移量，单位 dp 概念值
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // 初始适配比例：把内容缩放到能看全。
    // **不能在 draw scope 里更新它** —— 绘制时写状态会触发重组 → 再绘制 → 再写，
    // 形成无限循环导致卡死。改为用 onSizeChanged 记录尺寸，在副作用里算。
    var fitScale by remember { mutableFloatStateOf(1f) }
    var viewportPx by remember { mutableStateOf(Size.Zero) }

    // 尺寸变化时重算适配比例。放在 LaunchedEffect 里，与绘制解耦。
    LaunchedEffect(layout, viewportPx) {
        if (viewportPx.width > 0f && viewportPx.height > 0f) {
            fitScale = computeFitScale(layout, viewportPx, density.density)
        }
    }

    /**
     * 视图变换的统一收口。
     *
     * 任何改 `scale` / `offset` 的入口（手势、＋－按钮、搜索定位）都走这里，
     * 保证内容不会被整块拖出屏幕 —— 之前 `offset` 无约束，拖几下就能把图拖丢，
     * 只能点「复位」找回。
     */
    fun applyTransform(nextScale: Float, nextOffset: Offset) {
        scale = nextScale
        offset = GraphTransform.clampOffset(
            offset = nextOffset,
            contentDp = Size(layout.contentWidth, layout.contentHeight),
            viewportPx = viewportPx,
            density = density.density,
            renderScale = fitScale * nextScale,
        )
    }

    /**
     * 把搜索命中的节点移到视口中央。
     *
     * `fitScale` 与 `viewportPx` 也作为 key：首帧它们还是默认值，
     * 等尺寸算出来后会再跑一次，避免第一次定位用错比例。
     */
    LaunchedEffect(focusRequest, viewportPx, fitScale) {
        val request = focusRequest ?: return@LaunchedEffect
        if (viewportPx.width <= 0f || viewportPx.height <= 0f) return@LaunchedEffect

        val node = layout.nodes.firstOrNull { it.nodePath == request.nodePath }
            ?: return@LaunchedEffect

        applyTransform(
            nextScale = scale,
            nextOffset = GraphTransform.focusOffset(
                centerDp = Offset(node.x + node.width / 2f, node.y + node.height / 2f),
                contentDp = Size(layout.contentWidth, layout.contentHeight),
                viewportPx = viewportPx,
                density = density.density,
                renderScale = fitScale * scale,
            ),
        )
    }

    val colors = MaterialTheme.colorScheme
    val statusColors = com.example.zhijing.ui.theme.LocalStatusColors.current

    // 点选命中测试用的快照：手势回调里读不到最新的 state，必须用 rememberUpdatedState
    val currentScale = rememberUpdatedState(scale)
    val currentOffset = rememberUpdatedState(offset)
    val currentFit = rememberUpdatedState(fitScale)

    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                // 尺寸回调：只在这里写 viewportPx，绘制回调不再写状态
                .onSizeChanged { size ->
                    val next = Size(size.width.toFloat(), size.height.toFloat())
                    if (next != viewportPx) viewportPx = next
                }
                // 点选：必须挂在 transform 手势**之前**，否则 tap 会被 pan 消费掉。
                // detectTapGestures 只在「按下-抬起无位移」时触发，与拖动天然不冲突。
                .pointerInput(layout) {
                    detectTapGestures { tap ->
                        // 屏幕像素 → 内容 dp 坐标（逆变换）
                        val k = currentFit.value * currentScale.value
                        if (k > 0.01f) {
                            val dpX = (tap.x / density.density - currentOffset.value.x) / k
                            val dpY = (tap.y / density.density - currentOffset.value.y) / k
                            // 命中测试：取面积最小的命中节点（深层节点可能被父节点包住）
                            val hit = layout.nodes
                                .filter { dpX >= it.x && dpX <= it.x + it.width &&
                                    dpY >= it.y && dpY <= it.y + it.height }
                                .minByOrNull { it.width * it.height }
                            if (hit != null) onNodeClick(hit.nodePath)
                        }
                    }
                }
                // 手势必须挂在 Canvas 上，否则拿不到触点
                .pointerInput(layout) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        // 先以手指为锚点缩放（原来锚点是 Offset.Zero，在右上角缩放时内容会往左下跑），
                        // 再叠加平移；pan 单位是像素，除 density 换成 dp 概念值。
                        val zoomed = GraphTransform.zoomAround(
                            offset = offset,
                            oldScale = scale,
                            newScale = nextScale,
                            centroidPx = centroid,
                            density = density.density,
                        )
                        applyTransform(
                            nextScale = nextScale,
                            nextOffset = Offset(
                                x = zoomed.x + pan.x / density.density,
                                y = zoomed.y + pan.y / density.density,
                            ),
                        )
                    }
                }
        ) {
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
                // 分区底板先画，节点与连线压在它之上
                drawGroups(layout, measurer, colors, density.density)
                drawLines(
                    layout = layout,
                    hierarchyColor = colors.outlineVariant,
                    crossColor = colors.outline,
                    requiredColor = colors.tertiary,
                    recommendedColor = colors.tertiary.copy(alpha = 0.55f),
                    density = density.density,
                    pxPerDp = pxPerDp,
                    selectedNode = selectedNode,
                    // 依赖视图里层级线是噪音：两种关系混在一起正是要解决的问题
                    hideHierarchy = dependencyOnly,
                )
                drawNodes(layout, measurer, colors, density.density, selectedNode)
            }
        }

        // 缩放控制：放**右下**，避开顶栏的齿轮按钮与标题
        //
        // 曾经放在右上（TopEnd），结果与 TopAppBar 的齿轮图标区域重叠，
        // 导致齿轮点不开 —— Canvas 的手势层吃掉了点击。
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(ZjSpace.md),
            color = colors.surface.copy(alpha = 0.92f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            tonalElevation = 2.dp,
        ) {
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = {
                    dependencyOnly = !dependencyOnly
                    offset = Offset.Zero
                }) {
                    Text(if (dependencyOnly) "返回层级" else "只看依赖")
                }
                TextButton(onClick = {
                    applyTransform((scale * 1.25f).coerceAtMost(MAX_SCALE), offset)
                }) {
                    Text("＋")
                }
                TextButton(onClick = {
                    applyTransform((scale / 1.25f).coerceAtLeast(MIN_SCALE), offset)
                }) {
                    Text("－")
                }
                TextButton(onClick = {
                    scale = 1f
                    offset = Offset.Zero
                }) { Text("复位") }
            }
        }

        // 图例：压缩成**一行小字**放在底部中央，
        // 不再用三行面板 —— 那块面板会遮住近 1/4 屏的卡片。
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = ZjSpace.md, bottom = ZjSpace.md),
            color = colors.surface.copy(alpha = 0.86f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            tonalElevation = 1.dp,
        ) {
            Text(
                "▶ 必须前置   ▷ 推荐前置   ·· 关联",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** 缩放范围：与 `kg/web` 的 0.35–2.4× 保持一致。 */
private const val MIN_SCALE = 0.35f
private const val MAX_SCALE = 2.4f

/**
 * 初始适配比例的下限。
 *
 * 只按宽度适配后一般不会触发它；留作兜底，防止内容极宽时缩到看不见。
 * 与 [MIN_SCALE] 分开：前者是「初始适配」的下限，后者是「用户缩放」的下限。
 */
private const val MIN_FIT = 0.45f

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

    // **按高度适配**。
    //
    // 这是横向布局（层级沿 x 展开）该有的做法：宽度必然超出屏幕，
    // 强行按宽度塞进去会把比例压到 0.3 以下、字全糊掉，而用户本来就要横向拖动看图。
    // 纵向（同一层级的兄弟节点）才是需要「一屏看全」的方向 —— 那一列有多少个节点，
    // 决定这棵树有多高，所以按高度适配才能真正看清结构。
    val fitByHeight = viewport.height / contentH
    // 宽度也要有个下限：内容特别宽时，至少保证横向还能拖动而不是缩成一点
    val fitByWidthFloor = (viewport.width / contentW) * 1.6f
    return minOf(fitByHeight, 1f).coerceAtLeast(
        maxOf(MIN_FIT, fitByWidthFloor.coerceAtMost(1f))
    ).coerceIn(MIN_FIT, 1f)
}

/**
 * 画主题分区底板与标题。
 *
 * 出厂图谱与每个生成图谱各占一块，用**圆角底板 + 虚线边框**隔开 ——
 * 这是让用户分清「系统预置的」与「我生成的」唯一有效的视觉手段。
 * 两类用不同颜色：出厂图谱用描边色（中性），生成图谱用主色（醒目）。
 */
private fun DrawScope.drawGroups(
    layout: TreeLayout.Result,
    measurer: TextMeasurer,
    colors: androidx.compose.material3.ColorScheme,
    density: Float,
) {
    layout.groups.forEach { group ->
        val borderColor = if (group.isBundled) colors.outline else colors.primary

        drawRoundRect(
            color = borderColor.copy(alpha = 0.05f),
            topLeft = Offset(group.x, group.y),
            size = Size(group.width, group.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f)
        )
        drawRoundRect(
            color = borderColor.copy(alpha = 0.45f),
            topLeft = Offset(group.x, group.y),
            size = Size(group.width, group.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f),
            style = Stroke(
                width = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
            )
        )

        // 分区标题
        val title = measurer.measure(
            text = group.title,
            style = TextStyle(color = borderColor, fontSize = 13.sp),
            maxLines = 1,
            constraints = androidx.compose.ui.unit.Constraints(
                maxWidth = ((group.width - 24f) * density).toInt().coerceAtLeast(1)
            ),
        )
        val titleW = title.size.width / density
        val titleH = title.size.height / density
        withTransform({ scale(1f / density, 1f / density, Offset.Zero) }) {
            drawText(
                textLayoutResult = title,
                topLeft = Offset(
                    (group.x + 12f) * density,
                    (group.y + 8f) * density
                )
            )
        }
        // 标题下的细分隔线
        drawLine(
            color = borderColor.copy(alpha = 0.25f),
            start = Offset(group.x + 12f, group.y + 8f + titleH + 4f),
            end = Offset(group.x + group.width - 12f, group.y + 8f + titleH + 4f),
            strokeWidth = 1f
        )
        // 标题宽度参与布局计算，避免未使用告警
        if (titleW < 0f) Unit
    }
}

/**
 * 画连线。
 *
 * 视觉契约：
 *
 * | 样式 | 线型 | 颜色 | 粗细 | 箭头 |
 * |---|---|---|---|---|
 * | 章节层级 | 折线 | 描边色（最淡） | 1.2 | 无 |
 * | **必须前置** | **实线** | **赭黄（Ochre）** | **3.0** | **有** |
 * | **推荐前置** | **虚线** | 赭黄半透明 | 1.8 | **有** |
 * | 交叉链接 | 细虚线 | 描边色（最淡） | 1.0 | 无 |
 *
 * **前置线刻意不用主色（绿）**：主色与层级线同为绿灰系，在缩小的画布上分不出来，
 * 用户根本看不到「谁必须先学」。换成赭黄 + 加粗 + 箭头，才能一眼跳出来。
 *
 * 坐标直接用 dp 原值，由外层变换统一处理。
 * 线宽需要**反比于缩放**，否则放大时线会跟着变粗 —— 除以 [pxPerDp] 抵消。
 */
private fun DrawScope.drawLines(
    layout: TreeLayout.Result,
    hierarchyColor: Color,
    crossColor: Color,
    requiredColor: Color,
    recommendedColor: Color,
    density: Float,
    pxPerDp: Float,
    selectedNode: String?,
    /** 整体隐藏层级线（依赖视图用）。 */
    hideHierarchy: Boolean = false,
) {
    val unit = 1f / pxPerDp.coerceAtLeast(0.01f)
    // 虚线间距按屏幕像素固定，因此同乘 unit
    val dashFine = PathEffect.dashPathEffect(floatArrayOf(4f * unit, 4f * unit), 0f)
    val dashWide = PathEffect.dashPathEffect(floatArrayOf(8f * unit, 5f * unit), 0f)

    layout.lines.forEach { line ->
        // 关系高亮：选中某节点时，**与它相连的线**加粗提亮，其余淡出。
        //
        // 这是「点击节点查看关系」的核心 —— 22 个节点、30+ 条线同时画在屏幕上，
        // 用户无法靠肉眼追踪某个节点的依赖。选中后只让相关的线保留存在感，
        // 用户才能回答「这个点依赖谁、谁依赖它」。
        val related = selectedNode != null &&
            (line.fromNode == selectedNode || line.toNode == selectedNode)
        val unrelated = selectedNode != null && !related
        val alphaScale = if (unrelated) 0.18f else 1f
        val widthScale = if (related) 1.7f else 1f

        // 层级线在选中时进一步淡化：它是背景，不该盖过依赖关系
        val isHierarchy = line.style == TreeLayout.Line.Style.HIERARCHY
        // 依赖视图整体不要层级线；选中态下再额外隐藏与选中无关的层级线
        val skip = isHierarchy && (hideHierarchy || (selectedNode != null && !related))

        if (skip) return@forEach

        when (line.style) {
            // 层级线：横向布局下是「父右中 → 子左中」的水平折线。
            // 保持极淡：它是**背景结构**，不该和依赖关系抢注意力。
            TreeLayout.Line.Style.HIERARCHY -> {
                val c = hierarchyColor.copy(alpha = hierarchyColor.alpha * alphaScale)
                val midX = line.fromX + (line.toX - line.fromX) * 0.5f
                val w = 1.2f * unit * widthScale
                drawLine(c, Offset(line.fromX, line.fromY), Offset(midX, line.fromY), strokeWidth = w)
                drawLine(c, Offset(midX, line.fromY), Offset(midX, line.toY), strokeWidth = w)
                drawLine(c, Offset(midX, line.toY), Offset(line.toX, line.toY), strokeWidth = w)
            }

            // 必须前置：赭黄实线 + 箭头。这是全图最需要被看见的信息。
            TreeLayout.Line.Style.PREREQUISITE_REQUIRED -> {
                val c = requiredColor.copy(alpha = requiredColor.alpha * alphaScale)
                drawLine(
                    c,
                    Offset(line.fromX, line.fromY),
                    Offset(line.toX, line.toY),
                    strokeWidth = 3f * unit * widthScale
                )
                drawArrowHead(
                    color = c,
                    from = Offset(line.fromX, line.fromY),
                    to = Offset(line.toX, line.toY),
                    size = 7f * unit,
                )
            }

            // 推荐前置：赭黄虚线 + 箭头，比必须前置细一档
            TreeLayout.Line.Style.PREREQUISITE_RECOMMENDED -> {
                val c = recommendedColor.copy(alpha = recommendedColor.alpha * alphaScale)
                drawLine(
                    c,
                    Offset(line.fromX, line.fromY),
                    Offset(line.toX, line.toY),
                    strokeWidth = 1.8f * unit * widthScale,
                    pathEffect = dashWide
                )
                drawArrowHead(
                    color = c,
                    from = Offset(line.fromX, line.fromY),
                    to = Offset(line.toX, line.toY),
                    size = 6f * unit,
                )
            }

            // 交叉链接：保持斜线 + 极淡。
            // 它连接跨章节的节点，正交折线会与层级线混淆。
            TreeLayout.Line.Style.CROSS ->
                drawLine(
                    crossColor.copy(alpha = 0.4f * alphaScale),
                    Offset(line.fromX, line.fromY),
                    Offset(line.toX, line.toY),
                    strokeWidth = 1f * unit * widthScale,
                    pathEffect = dashFine
                )
        }
    }
}

/**
 * 在 [to] 处画一个箭头。
 *
 * 没有箭头的话，「A 是 B 的前置」和「B 是 A 的前置」在视觉上完全一样 ——
 * 方向信息丢了，用户会读反。
 */
private fun DrawScope.drawArrowHead(
    color: Color,
    from: Offset,
    to: Offset,
    size: Float,
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy)
    if (len < 0.01f) return
    val ux = dx / len
    val uy = dy / len
    // 箭头底部两点：沿反方向退 size，再向两侧各偏 size*0.5
    val baseX = to.x - ux * size
    val baseY = to.y - uy * size
    val perpX = -uy * size * 0.5f
    val perpY = ux * size * 0.5f
    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(baseX + perpX, baseY + perpY)
        lineTo(baseX - perpX, baseY - perpY)
        close()
    }
    drawPath(path, color)
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
    selectedNode: String?,
) {
    layout.nodes.forEach { node ->
        val topLeft = Offset(node.x, node.y)
        val size = Size(node.width, node.height)

        val isSelected = node.nodePath == selectedNode
        // 选中时其他节点淡化，让「这一个节点和它的邻居」跳出来
        val dimmed = selectedNode != null && !isSelected

        // 有内容（可点开）的节点用实心胶囊；组织性节点用虚化描边。
        // 一眼能看出「哪里点得进去」。
        val hasContent = node.descendable
        val fillAlpha = if (hasContent) 0.14f else 0.05f

        drawRoundRect(
            color = if (isSelected) {
                colors.primary.copy(alpha = 0.28f)
            } else {
                colors.primary.copy(alpha = if (dimmed) fillAlpha * 0.5f else fillAlpha)
            },
            topLeft = topLeft,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(node.height / 2)
        )
        drawRoundRect(
            color = when {
                isSelected -> colors.primary
                hasContent -> colors.primary.copy(alpha = if (dimmed) 0.25f else 0.55f)
                else -> colors.outline.copy(alpha = if (dimmed) 0.25f else 0.7f)
            },
            topLeft = topLeft,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(node.height / 2),
            style = Stroke(width = if (isSelected) 2.2f else if (hasContent) 1.2f else 1f)
        )

        // 文本：宽度约束用像素（= dp × density）
        val textLayout = measurer.measure(
            text = node.title,
            style = TextStyle(
                color = when {
                    isSelected -> colors.primary
                    hasContent -> colors.onSurface
                    else -> colors.onSurfaceVariant
                },
                fontSize = 12.sp,
            ),
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
