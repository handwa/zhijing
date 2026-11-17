package com.example.zhijing.kg.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zhijing.kg.MergedGraph
import com.example.zhijing.kg.PrerequisiteSource
import com.example.zhijing.kg.SkillProgress
import com.example.zhijing.ui.theme.ZjSpace
import kotlin.math.min
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
     * 点击某个节点。参数是被点节点的路径；**点在空白处传 null**。
     *
     * 为什么需要 null：选中态下与选中无关的层级线会被整体隐藏（见 [drawLines]），
     * 之前没有任何取消选中的交互 —— 点空白没反应、点别的节点只是换一个选中，
     * 一旦点过节点，层级连线看起来就「永久消失」了。点空白处是最自然的退出方式。
     *
     * 结构图不再单独画卡片（卡片与节点 1:1、标题 100% 重复），
     * 因此**节点是唯一入口**：点它 → 底部弹窗给出该节点的卡片内容。
     */
    onNodeClick: (nodePath: String?) -> Unit,
    /** 当前选中的节点路径；选中后与它相连的线高亮、其余淡出。 */
    selectedNode: String?,
    /**
     * 卡片三态（已点亮 / 可学 / 未解锁）。
     *
     * 由宿主传入而不是在这里算：统计行上的「可学 N」「✓ x/y」用同一份结果，
     * 两处各算一次迟早会出现数字对不上。
     */
    skill: SkillProgress.Result? = null,
    /** 搜索/切换视图时把某个节点移到视口中央；null 表示不主动移动视图。 */
    focusRequest: GraphFocusRequest? = null,
    /** 聚焦完成（或目标不在当前布局里）后回调，宿主应把请求清空。 */
    onFocusConsumed: () -> Unit = {},
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

    // 宿主没传就自己算一份（预览/单测里方便）
    val skillProgress = skill ?: remember(graph) { SkillProgress.of(graph) }

    // 点亮动效：只对「刚变成已点亮」的节点做一次扩散描边。
    // 状态在 LaunchedEffect 里推进，绘制期只读 —— DrawScope 里写状态会触发无限重组。
    val nodeStates = remember(layout, skillProgress) {
        SkillAnimation.nodeStates(layout, skillProgress)
    }
    val litProgress = remember { mutableStateMapOf<String, Float>() }
    val previousStates = remember { HashMap<String, SkillProgress.State>() }
    LaunchedEffect(nodeStates) {
        // 首次进入只记录基线，不闪：那不是「刚点亮」，是「刚打开」
        if (previousStates.isEmpty()) {
            previousStates.putAll(nodeStates)
            return@LaunchedEffect
        }
        val newly = SkillAnimation.newlyLit(previousStates, nodeStates)
            .take(SkillAnimation.MAX_CONCURRENT)
        previousStates.clear()
        previousStates.putAll(nodeStates)
        if (newly.isEmpty()) return@LaunchedEffect

        coroutineScope {
            newly.forEach { path ->
                launch {
                    animate(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = LIT_ANIMATION_MS),
                    ) { value, _ ->
                        litProgress[path] = value
                    }
                    litProgress.remove(path)
                }
            }
        }
    }

    // 读屏：Canvas 本身没有节点语义，这里至少给出整体状态与当前选中项，
    // 让 TalkBack 用户知道"图里有什么、我在哪"。
    val treeDescription = remember(skillProgress, selectedNode) {
        buildString {
            append("结构图：共 ")
            append(skillProgress.total)
            append(" 张卡片，已点亮 ")
            append(skillProgress.lit)
            append("，现在能学 ")
            append(skillProgress.ready)
            append("，未解锁 ")
            append(skillProgress.locked)
            if (selectedNode != null) {
                append("。已选中 ")
                append(selectedNode)
            }
        }
    }

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
        if (node == null) {
            // 目标不在当前布局里（例如依赖视图过滤掉了孤立节点）。
            // 必须消费掉请求，否则每次视口变化都会重试一遍，且用户得不到任何反馈。
            onFocusConsumed()
            return@LaunchedEffect
        }

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
        // 消费掉请求：否则键盘弹出、旋转、分屏等任何视口变化都会把视图
        // 强行拉回「上次搜索的节点」，覆盖用户刚才手动拖到的位置。
        onFocusConsumed()
    }

    val colors = MaterialTheme.colorScheme
    val statusColors = com.example.zhijing.ui.theme.LocalStatusColors.current

    // 点选命中测试用的快照：手势回调里读不到最新的 state，必须用 rememberUpdatedState
    val currentScale = rememberUpdatedState(scale)
    val currentOffset = rememberUpdatedState(offset)
    val currentFit = rememberUpdatedState(fitScale)

    // 选中某张卡片时，与它有依赖/关联的卡片一起高亮（其余压暗）。
    // 依赖 `layout` 而不是重建图：切换视图会重算布局，这个集合也必须跟着重算。
    val relatedNodes = remember(layout, selectedNode) {
        EdgePresentation.relatedNodes(layout.lines, selectedNode)
    }

    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = treeDescription }
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
                            // 命中 = 选中该节点；空白 = 取消选中（恢复全部层级线）
                            onNodeClick(hit?.nodePath)
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
                    // 层级线用 outline 而不是 outlineVariant：后者在浅色主题下
                    // 与背景几乎同色，用户反馈「看不到层级连线」即由此而来
                    hierarchyColor = colors.outline,
                    crossColor = colors.outline,
                    density = density.density,
                    pxPerDp = pxPerDp,
                    selectedNode = selectedNode,
                    // 依赖视图里层级线是噪音：两种关系混在一起正是要解决的问题
                    hideHierarchy = dependencyOnly,
                )
                drawNodes(
                    layout = layout,
                    measurer = measurer,
                    colors = colors,
                    density = density.density,
                    selectedNode = selectedNode,
                    relatedNodes = relatedNodes,
                    skill = skillProgress,
                    litProgress = litProgress,
                )
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
        //
        // 前置关系的来源必须写出来：系统不提供课程资料，模型推断出来的「前置」
        // 不是核实过的课程事实。出厂图谱那批是人工审定的，所以正常情况下不显示这句。
        val hasInferredPrereq = remember(graph) {
            graph.cards.any { card ->
                card.prerequisites.any { it.source == PrerequisiteSource.AUTHORED_MODEL }
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = ZjSpace.md, bottom = ZjSpace.md),
            color = colors.surface.copy(alpha = 0.86f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            tonalElevation = 1.dp,
        ) {
            // 没有选中时不画依赖线，图例也不该列出三种线让人找不到 ——
            // 先告诉用户"点一下就有了"，选中之后再解释线型。
            val legend = when {
                selectedNode != null -> "▬ 必须前置（暗红）　┅ 推荐前置（暖橙）　▬ 后续（蓝）　跨章节已淡化"
                // 依赖视图本身就在展示依赖，图例不能还写"点一下才有"
                dependencyOnly -> "▬ 必须前置（暗红）　┅ 推荐前置（暖橙）"
                else -> "点击卡片查看前置与后续关系"
            }
            Text(
                text = if (hasInferredPrereq) {
                    legend + "\n部分前置由 AI 推断，未经核实"
                } else {
                    legend
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * 依赖线统一粗细（dp）。
 *
 * 三种关系靠颜色与虚实区分，不靠粗细：粗细不一致时，读者会以为"更粗=更重要"，
 * 而实际上推荐前置与后续只是关系类型不同。
 */
private const val DEPENDENCY_LINE_WIDTH = 3f

/** 点亮扩散动画时长：够被看见，又不至于让人等。 */
private const val LIT_ANIMATION_MS = 320

/** 扩散描边的最大外扩距离（dp）。 */
private const val LIT_RING_EXPAND = 6f

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
 * ## 显示规则（用户定的，别"优化"）
 *
 * - **未选中任何节点**：只画层级线。前置 / 推荐 / 关联一条都不画 ——
 *   20 个节点的图同时压三类斜线，谁也读不出来。
 * - **选中一张卡片**：只画与它相连的线，并按方向换称谓与配色：
 *   指向选中卡的是「前置」，从选中卡出发的是「后续」。
 *
 * ## 视觉契约
 *
 * | 身份 | 线型 | 颜色 | 粗细 | 箭头 |
 * |---|---|---|---|---|
 * | 章节层级 | 正交折线 | 描边色（相关 1.0 / 无关 0.35） | 1.6 | 无 |
 * | 必须前置 | 实线 | `#D63031` 暗红 | 3.0 | 有 |
 * | 推荐前置 | 虚线 | `#F39C12` 暖橙 | 2.0 | **无** |
 * | 后续线 | 实线 | `#0984E3` 柔和蓝 | 3.0 | 有 |
 * | 交叉关联 | 细虚线 | 描边色（最淡） | 1.0 | 无 |
 *
 * 用固定色值而不是主题色：这三条是**语义编码**，不是装饰。暗红 / 暖橙 / 柔和蓝
 * 在浅色与深色主题下都能互相区分，而主题色会随品牌色漂移、和层级线撞色。
 *
 * 走向见 [linePath]：同列走二次贝塞尔（不是直线），跨列的必须前置复用层级折线。
 *
 * 坐标直接用 dp 原值，由外层变换统一处理。
 * 线宽需要**反比于缩放**，否则放大时线会跟着变粗 —— 除以 [pxPerDp] 抵消。
 */
private fun DrawScope.drawLines(
    layout: TreeLayout.Result,
    hierarchyColor: Color,
    crossColor: Color,
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
        val role = EdgePresentation.roleOf(
            style = line.style,
            fromNode = line.fromNode,
            toNode = line.toNode,
            selected = selectedNode,
            // 依赖视图里层级线被藏起来了，未选中时再藏依赖线就只剩节点
            alwaysShowDependencies = hideHierarchy,
        ) ?: return@forEach

        // 依赖视图把层级线整体隐藏（它在这个视图里是噪音）
        if (role == EdgePresentation.Role.HIERARCHY && hideHierarchy) return@forEach

        // 层级线是骨架：选中时把与选中无关的**压暗**而不是删掉。
        // 删掉会让用户以为选错了节点 —— 图突然小了一半。
        val related = selectedNode != null &&
            (line.fromNode == selectedNode || line.toNode == selectedNode)
        val hierarchyAlpha = if (selectedNode == null || related) 1f else 0.35f

        // 跨章节依赖：只降透明度，不改线宽 —— 粗细统一，靠颜色与透明度区分权重
        val crossAlpha = if (line.crossChapter) 0.5f else 1f

        val path = linePath(line)
        when (role) {
            EdgePresentation.Role.HIERARCHY -> drawPath(
                path = path,
                color = hierarchyColor.copy(alpha = hierarchyColor.alpha * hierarchyAlpha),
                style = Stroke(width = 1.6f * unit * (if (related) 1.7f else 1f)),
            )

            EdgePresentation.Role.REQUIRED -> {
                drawPath(
                    path = path,
                    color = GraphLineColors.required.copy(alpha = crossAlpha),
                    style = Stroke(width = DEPENDENCY_LINE_WIDTH * unit),
                )
                drawArrowHead(
                    color = GraphLineColors.required,
                    from = arrowAnchor(line),
                    to = Offset(line.toX, line.toY),
                    // 16px（约 5dp @3x）。`unit` 把屏幕像素换算回内容坐标，
                    // 所以放大缩小箭头的**视觉大小不变**。
                    size = 16f * unit,
                )
            }

            // 推荐前置**不给箭头**：箭头是"这条依赖成立"的断言，建议性的关系不该有方向感。
            EdgePresentation.Role.RECOMMENDED -> drawPath(
                path = path,
                color = GraphLineColors.recommended.copy(alpha = crossAlpha),
                style = Stroke(width = DEPENDENCY_LINE_WIDTH * unit, pathEffect = dashWide),
            )

            EdgePresentation.Role.SUCCESSOR -> {
                drawPath(
                    path = path,
                    color = GraphLineColors.successor.copy(alpha = crossAlpha),
                    style = Stroke(width = DEPENDENCY_LINE_WIDTH * unit),
                )
                drawArrowHead(
                    color = GraphLineColors.successor,
                    from = arrowAnchor(line),
                    to = Offset(line.toX, line.toY),
                    size = 16f * unit,
                )
            }

            // 交叉关联：极淡细虚线，它只是"有关"，不是依赖
            EdgePresentation.Role.CROSS -> drawPath(
                path = path,
                color = crossColor.copy(alpha = 0.4f),
                style = Stroke(width = 1f * unit, pathEffect = dashFine),
            )
        }
    }
}

/**
 * 卡片三态的配色。
 *
 * 绿=已点亮、蓝=现在能学、灰=未解锁：与依赖线的暗红/暖橙/蓝刻意分开一档饱和度，
 * 免得用户把"节点边框的颜色"读成"线的颜色"。未解锁再用虚线框做第二重编码。
 */
internal object SkillVisual {
    val lit = Color(0xFF27AE60)
    val ready = Color(0xFF0984E3)
    val locked = Color(0xFF9AA0A6)

    fun color(state: SkillProgress.State): Color = when (state) {
        SkillProgress.State.LIT -> lit
        SkillProgress.State.READY -> ready
        SkillProgress.State.LOCKED -> locked
    }
}

/**
 * 依赖线的语义配色。
 *
 * 固定色值而不是主题色：这三条线是**语义编码**。主题色会随品牌色漂移，
 * 一旦和层级线撞色，"必须先学"这条信息就没了。
 */
internal object GraphLineColors {
    /** 必须前置：`#D63031` 暗红。 */
    val required = Color(0xFFD63031)

    /** 推荐前置：`#F39C12` 暖橙。 */
    val recommended = Color(0xFFF39C12)

    /** 后续线：`#0984E3` 柔和蓝。 */
    val successor = Color(0xFF0984E3)
}

/**
 * 按 [TreeLayout.Line.Route] 把一条线转成 Path。
 *
 * 三种走法各有理由：
 * - [TreeLayout.Line.Route.ELBOW]：层级线与「跨列必须前置」共用。
 *   树里已经有这条正交路径，再画一条斜线等于同一关系画两遍。
 * - [TreeLayout.Line.Route.CURVE]：同列依赖。直线会像表格分隔线，
 *   弧线才像"从前一个连到后一个"；控制点由布局算好（避开节点框）。
 * - [TreeLayout.Line.Route.STRAIGHT]：交叉关联，保持最轻的斜线。
 */
private fun linePath(line: TreeLayout.Line): Path {
    val path = Path()
    when (line.route) {
        TreeLayout.Line.Route.ELBOW -> {
            val midX = line.fromX + (line.toX - line.fromX) * 0.5f
            path.moveTo(line.fromX, line.fromY)
            path.lineTo(midX, line.fromY)
            path.lineTo(midX, line.toY)
            path.lineTo(line.toX, line.toY)
        }

        TreeLayout.Line.Route.CURVE -> {
            path.moveTo(line.fromX, line.fromY)
            path.quadraticBezierTo(line.ctrlX, line.ctrlY, line.toX, line.toY)
        }

        // 列外车道：出列 → 沿槽下行 → 回车。中间节点不再压住线。
        TreeLayout.Line.Route.LANE -> {
            val laneX = line.ctrlX
            path.moveTo(line.fromX, line.fromY)
            path.lineTo(laneX, line.fromY)
            path.lineTo(laneX, line.toY)
            path.lineTo(line.toX, line.toY)
        }

        TreeLayout.Line.Route.STRAIGHT -> {
            path.moveTo(line.fromX, line.fromY)
            path.lineTo(line.toX, line.toY)
        }
    }
    return path
}

/**
 * 箭头朝向的参考点。
 *
 * 曲线取末端切线（控制点 → 终点），折线取最后一段，直线取起点；
 * 否则曲线上的箭头会按首尾连线摆，方向明显偏掉。
 */
private fun arrowAnchor(line: TreeLayout.Line): Offset = when (line.route) {
    TreeLayout.Line.Route.CURVE -> Offset(line.ctrlX, line.ctrlY)

    // 车道最后一段是水平回车，箭头按水平方向摆
    TreeLayout.Line.Route.LANE -> Offset(line.ctrlX, line.toY)
    TreeLayout.Line.Route.ELBOW -> Offset(
        line.fromX + (line.toX - line.fromX) * 0.5f,
        line.toY,
    )

    TreeLayout.Line.Route.STRAIGHT -> Offset(line.fromX, line.fromY)
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
    /** 与选中节点有依赖/关联关系的节点：一起高亮，其余压暗。 */
    relatedNodes: Set<String> = emptySet(),
    /** 卡片三态：节点边框与角标据此上色，图标做第二重编码（缩放后颜色不可靠）。 */
    skill: SkillProgress.Result,
    /** 「刚点亮」的节点 → 动画进度 0..1（0 表示没有动画）。 */
    litProgress: Map<String, Float> = emptyMap(),
) {
    // 未解锁的虚线框：颜色之外的第二重编码，色弱用户也能分辨
    val lockDash = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)

    layout.nodes.forEach { node ->
        val topLeft = Offset(node.x, node.y)
        val size = Size(node.width, node.height)

        val isSelected = node.nodePath == selectedNode
        val isRelated = node.nodePath in relatedNodes
        // 选中时只保留「自己 + 相关卡片」的存在感，其余淡化
        val dimmed = selectedNode != null && !isSelected && !isRelated

        // 有内容（可点开）的节点用实心胶囊；组织性节点用虚化描边。
        // 一眼能看出「哪里点得进去」。
        val hasContent = node.descendable
        val fillAlpha = if (hasContent) 0.14f else 0.05f

        val nodeState = skill.stateOfCards(node.cardIds)
        val stateColor = nodeState?.let { SkillVisual.color(it) }
        val icon = when (nodeState) {
            SkillProgress.State.LIT -> "✓"
            SkillProgress.State.READY -> "＋"
            SkillProgress.State.LOCKED -> "□"
            null -> null
        }

        drawRoundRect(
            color = when {
                isSelected -> colors.primary.copy(alpha = 0.28f)
                // 相关卡片给一层很浅的底：既区别于背景，又不抢选中卡片的焦点
                isRelated -> colors.primary.copy(alpha = 0.12f)
                // 有状态的卡片用状态色打底，未选中时整张图就是"三态地图"
                stateColor != null -> stateColor.copy(alpha = if (dimmed) 0.04f else 0.10f)
                else -> colors.primary.copy(alpha = if (dimmed) fillAlpha * 0.5f else fillAlpha)
            },
            topLeft = topLeft,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(node.height / 2)
        )

        val borderColor = when {
            isSelected -> colors.primary
            isRelated -> colors.primary.copy(alpha = 0.85f)
            stateColor != null -> stateColor.copy(alpha = if (dimmed) 0.3f else 0.9f)
            hasContent -> colors.primary.copy(alpha = if (dimmed) 0.25f else 0.55f)
            else -> colors.outline.copy(alpha = if (dimmed) 0.25f else 0.7f)
        }
        val borderWidth = when {
            isSelected -> 2.2f
            isRelated -> 1.8f
            nodeState != null -> 1.6f
            hasContent -> 1.2f
            else -> 1f
        }
        drawRoundRect(
            color = borderColor,
            topLeft = topLeft,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(node.height / 2),
            style = if (nodeState == SkillProgress.State.LOCKED && !isSelected) {
                Stroke(width = borderWidth, pathEffect = lockDash)
            } else {
                Stroke(width = borderWidth)
            }
        )

        // 点亮扩散：一圈描边外扩并淡出。只在绘制期读动画进度，不在这里写状态
        litProgress[node.nodePath]?.let { progress ->
            if (progress > 0f) {
                val expand = LIT_RING_EXPAND * progress
                drawRoundRect(
                    color = SkillVisual.lit.copy(alpha = (1f - progress) * 0.9f),
                    topLeft = Offset(node.x - expand, node.y - expand),
                    size = Size(node.width + expand * 2, node.height + expand * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        (node.height + expand * 2) / 2f
                    ),
                    style = Stroke(width = 2.5f),
                )
            }
        }

        // 文本：宽度约束用像素（= dp × density）。
        // 有状态图标时右侧让出 18dp，否则图标会压在标题最后一个字上。
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
                maxWidth = ((node.width - (if (icon != null) 30f else 12f)) * density)
                    .toInt().coerceAtLeast(1)
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

        // 三态图标：节点缩小后颜色先失效，图标是最后的兜底编码
        if (icon != null && stateColor != null) {
            val iconLayout = measurer.measure(
                text = icon,
                style = TextStyle(
                    color = stateColor.copy(alpha = if (dimmed) 0.35f else 1f),
                    fontSize = 11.sp,
                ),
            )
            val iconW = iconLayout.size.width / density
            val iconH = iconLayout.size.height / density
            withTransform({ scale(1f / density, 1f / density, Offset.Zero) }) {
                drawText(
                    textLayoutResult = iconLayout,
                    topLeft = Offset(
                        (node.x + node.width - iconW - 6f) * density,
                        (node.y + (node.height - iconH) / 2f) * density,
                    ),
                )
            }
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
