package com.example.zhijing.kg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.zhijing.kg.GraphCard
import com.example.zhijing.kg.GraphOutlineNode
import com.example.zhijing.kg.MergedGraph
import com.example.zhijing.kg.depth.ConditionProbeSession
import com.example.zhijing.ui.theme.ZjRadius
import com.example.zhijing.ui.theme.ZjSpace
import com.zhijing.data.model.ProbeStatus

/**
 * 图谱主页（广度视图）。
 *
 * 结构对应设计文档：
 * ```
 * 顶栏：标题 · 四态芯片
 * 大纲：横向 chips（手机宽度放不下侧栏，见设计讨论）
 * 卡片流：单列
 * 下钻：覆盖层，关闭回到原位置
 * ```
 *
 * 三条不能违反的原则：
 * 1. **大纲只组织不判断**：点 chip 是**筛选**，不是下钻；只有卡片右下角能下钻；
 * 2. **角标由卡内命题状态聚合派生**，不单独存储；
 * 3. **深度不是并列入口**：这里没有任何进判断面板的全局按钮。
 */
@Composable
fun GraphScreen(
    graph: MergedGraph?,
    loading: Boolean,
    onJudge: (claimId: String, correct: Boolean, misconception: String?) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onGenerateNew: () -> Unit = {},
) {
    var selectedOutline by remember { mutableStateOf<String?>(null) }
    var descendCardId by remember { mutableStateOf<String?>(null) }
    var claimIndex by remember { mutableIntStateOf(0) }
    // 结构图里被点中的节点路径（null = 未选中）。
    // 选中后：与它相连的连线高亮、其余淡出，并弹出底部详情。
    var selectedNodePath by remember { mutableStateOf<String?>(null) }
    // 视图形态：卡片流（默认，好读）或结构图（看关系）。
    // 刻意做成**同一个页面的两种呈现**，而不是两个一级 tab ——
    // 结构图与广度视图功能重叠（都是"看关系找位置"），并列会让导航变乱。
    var viewMode by remember { mutableStateOf(GraphViewMode.CARDS) }

    if (loading || graph == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // 大纲筛选：选中一级章节时，其下所有卡片都显示
    val visibleCards = if (selectedOutline == null) {
        graph.cards
    } else {
        val node = findNode(graph.outline, selectedOutline!!)
        val paths = collectPaths(node)
        graph.cards.filter { it.claims.isNotEmpty() || it.outlinePath in paths }
    }

    Column(modifier.fillMaxSize()) {
        // ---- 顶栏：标题 + 四态芯片 ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ZjSpace.pageHorizontal, vertical = ZjSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "认知庭院",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            // 视图切换：树 / 图
            TextButton(onClick = {
                viewMode = if (viewMode == GraphViewMode.CARDS) GraphViewMode.TREE else GraphViewMode.CARDS
            }) {
                Text(if (viewMode == GraphViewMode.CARDS) "结构图" else "卡片流")
            }
            // 生成新图谱：图谱页内的直接入口，不必绕到设置
            IconButton(onClick = onGenerateNew) {
                Icon(Icons.Default.Add, contentDescription = "生成新图谱")
            }
            // 设置：与 TopAppBar 的齿轮功能相同，但位置在本页内，保证够得着
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "设置")
            }
            StatusSummaryChips(graph)
        }

        // 结构图：与卡片流互斥呈现
        if (viewMode == GraphViewMode.TREE) {
            GraphTreeView(
                graph = graph,
                // 点节点 → 选中它（高亮相关连线）+ 底部弹窗展示内容
                onNodeClick = { path ->
                    selectedNodePath = if (selectedNodePath == path) null else path
                },
                selectedNode = selectedNodePath,
                modifier = Modifier.weight(1f),
            )
        } else {
            // ---- 卡片流：大纲 chips + 卡片列表 ----
            GraphCardList(
                graph = graph,
                selectedOutline = selectedOutline,
                onSelectOutline = { selectedOutline = it },
                onDescend = { cardId ->
                    descendCardId = cardId
                    claimIndex = 0
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    // ---- 节点详情：底部弹窗 ----
    // 结构图里节点是唯一入口，卡片内容（摘要/正文/命题）在这里展开。
    val selectedPath = selectedNodePath
    if (viewMode == GraphViewMode.TREE && selectedPath != null) {
        val nodeCards = graph.cards.filter { it.outlinePath == selectedPath }
        NodeDetailSheet(
            title = findNodeByPath(graph.outline, selectedPath)?.title
                ?: nodeCards.firstOrNull()?.title
                ?: selectedPath,
            cards = nodeCards,
            onDismiss = { selectedNodePath = null },
            onDescend = { cardId ->
                selectedNodePath = null
                descendCardId = cardId
                claimIndex = 0
            },
        )
    }

    // ---- 深度下钻：覆盖层（两种视图共用，不是并列入口）----
    DescendDialog(
        graph = graph,
        cardId = descendCardId,
        claimIndex = claimIndex,
        onIndexChange = { claimIndex = it },
        onClose = { descendCardId = null },
        onJudge = onJudge,
        onProbeAnswer = { answer ->
            // 阶段 3 接入 kg_condition_probe；先落日志，避免作答被静默丢弃
            android.util.Log.i(
                "GraphScreen",
                "probe ${answer.question.interventionId}: " +
                    "predicted=${answer.predicted} actual=${answer.actual.outcome} " +
                    "matched=${answer.matched}",
            )
        },
    )
}

/**
 * 卡片流视图：大纲 chips + 卡片列表。
 *
 * 从 [GraphScreen] 里抽出来，是因为原实现用 `return` 在 Column 内提前退出，
 * 那会**跳过后续的下钻 Dialog**，导致结构图模式下点卡片没反应。
 */
@Composable
private fun GraphCardList(
    graph: MergedGraph,
    selectedOutline: String?,
    onSelectOutline: (String?) -> Unit,
    onDescend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 大纲筛选：选中一级章节时，其下所有卡片都显示
    val visibleCards = if (selectedOutline == null) {
        graph.cards
    } else {
        val node = findNode(graph.outline, selectedOutline)
        val paths = collectPaths(node)
        graph.cards.filter { it.claims.isNotEmpty() || it.outlinePath in paths }
    }

    Column(modifier.fillMaxSize()) {
        // ---- 大纲：横向 chips ----
        LazyRow(
            contentPadding = PaddingValues(horizontal = ZjSpace.pageHorizontal),
            horizontalArrangement = Arrangement.spacedBy(ZjSpace.sm),
        ) {
            item {
                AssistChip(
                    onClick = { onSelectOutline(null) },
                    label = { Text("全部") },
                    colors = if (selectedOutline == null) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    } else AssistChipDefaults.assistChipColors(),
                )
            }
            items(graph.outline) { node ->
                val selected = selectedOutline == node.id
                AssistChip(
                    onClick = { onSelectOutline(if (selected) null else node.id) },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            OutlineStatusDot(graph, node)
                            Text(node.title.take(14))
                        }
                    },
                    colors = if (selected) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    } else AssistChipDefaults.assistChipColors(),
                )
            }
        }

        Spacer(Modifier.height(ZjSpace.sm))

        // ---- 卡片流 ----
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                horizontal = ZjSpace.pageHorizontal,
                vertical = ZjSpace.sm
            ),
            verticalArrangement = Arrangement.spacedBy(ZjSpace.md),
        ) {
            items(visibleCards, key = { it.id }) { card ->
                GraphCardView(
                    card = card,
                    breadcrumb = outlinePathOf(graph, card),
                    onDescend = { onDescend(card.id) },
                    onOpenCrossLink = { /* 卡片流内暂不跳转 */ },
                )
            }
            item { Spacer(Modifier.height(ZjSpace.xl)) }
        }
    }
}


/**
 * 深度下钻的覆盖层。
 *
 * 提成独立组件的原因：卡片流与结构图两种呈现**都要能下钻**，
 * 而深度面板本身只有一份实现 —— 避免两种视图各写一遍判断面板。
 */
@Composable
private fun DescendDialog(
    graph: MergedGraph,
    cardId: String?,
    claimIndex: Int,
    onIndexChange: (Int) -> Unit,
    onClose: () -> Unit,
    onJudge: (claimId: String, correct: Boolean, misconception: String?) -> Unit,
    onProbeAnswer: (ConditionProbeSession.Answer) -> Unit,
) {
    val card = cardId?.let { id -> graph.cards.firstOrNull { it.id == id } }
    if (card == null || card.claims.isEmpty()) return

    // 判断题的顺序必须在弹窗打开期间**冻结**。
    //
    // 原因：作答会回写探测状态（`onJudge` → `recordJudgement` → 重新 load 图谱），
    // 而 `claimsByPriority` 按状态排序（CONFUSED → UNPROBED → VERIFIED → MASTERED）。
    // 答对一条后它从「未探测」变成「已验证」，排序位置后移，当前题会在下一次重组时
    // 被换成另一条命题，而序号仍是 1/2 —— 表现就是「点了成立/不成立没看到答案，直接跳题」。
    //
    // 这里只冻结**顺序**（按 id 快照），状态文案仍从最新图谱取，所以顶栏的四态标签会实时更新。
    val orderedIds = remember(card.id) { graph.claimsByPriority(card.id).map { it.id } }
    val ordered = orderedIds.mapNotNull { graph.claims[it] }
    val current = ordered.getOrNull(claimIndex.coerceIn(0, ordered.lastIndex.coerceAtLeast(0)))

    // 深度 probe：三元组存在时先走「预测→揭示」，走完再回到原来的判断题面板。
    // 没有 triples 的卡片行为完全不变（probe.hasQuestions == false）。
    val probe = remember(card.id, graph.triples) {
        ConditionProbeSession.forCard(graph.triples, card.id).also { session ->
            // 出题数为 0 是排查「为什么没出题」的第一手信息（旧包 / 卡片 id 对不上 / 数据没过门槛）
            android.util.Log.i(
                "GraphScreen",
                "descend card=${card.id} triples=${graph.triples.size} probes=${session.total}",
            )
        }
    }
    // UI 只镜像 session 的 step，不再自己维护「是否已揭示」。
    // 两份状态并行是「题号 3/2」「选项点不动」的根源：会话已经答完/推进，
    // 而 UI 还拿着一份旧的布尔值在渲染题目。
    var step by remember(card.id, probe) { mutableStateOf(probe.step) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            LazyColumn(Modifier.padding(bottom = ZjSpace.lg)) {
                item {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = ZjSpace.lg,
                            vertical = ZjSpace.md
                        ),
                    )
                }
                when (val probeStep = step) {
                    is ConditionProbeSession.Step.Asking -> item(
                        // 按干预点 id 作为 item key：换题时强制重新组合，
                        // 旧的按钮不会被复用，也就不会把点击派发到上一题的回调上。
                        key = "probe-${probeStep.question.interventionId}",
                    ) {
                        ProbeQuestionView(
                            question = probeStep.question,
                            onChoose = { choice ->
                                // 以 session 的实时状态为准：快速连点时第二次点击会打在这里，
                                // 此时本题已答完，直接丢弃而不是把界面打崩。
                                if (probe.hasPending) {
                                    val answer = probe.answer(choice)
                                    onProbeAnswer(answer)
                                    step = probe.step
                                } else {
                                    android.util.Log.w(
                                        "GraphScreen",
                                        "ignore stale probe tap on ${probeStep.question.interventionId}",
                                    )
                                }
                            },
                        )
                    }

                    is ConditionProbeSession.Step.Revealing -> item(
                        key = "reveal-${probeStep.answer.question.interventionId}",
                    ) {
                        ProbeRevealView(
                            answer = probeStep.answer,
                            hasNext = probe.hasNext,
                            onContinue = {
                                // advance() 在未作答时返回 false，这里已经答过，必然推进
                                probe.advance()
                                step = probe.step
                            },
                        )
                    }

                    ConditionProbeSession.Step.Done -> if (current != null) {
                        item(key = "claim-${current.id}") {
                            ClaimJudgementView(
                                claim = current,
                                index = claimIndex,
                                total = ordered.size,
                                onAnswered = { correct ->
                                    onJudge(current.id, correct, current.misconception.takeIf { it.isNotBlank() })
                                },
                                onNext = { if (claimIndex + 1 < ordered.size) onIndexChange(claimIndex + 1) },
                                onClose = onClose,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 图谱页的两种呈现。 */
private enum class GraphViewMode { CARDS, TREE }

/**
 * 顶栏四态芯片。
 *
 * 刻意**不做进度条**：`confidence` 做成进度条会让用户误以为「刷满就掌握」。
 * 这里只给四态计数，点击不筛选（筛选交给大纲 chips），纯作状态总览。
 */
@Composable
private fun StatusSummaryChips(graph: MergedGraph) {
    val summary = graph.statusSummary
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ProbeStatus.CONFUSED, ProbeStatus.UNPROBED).forEach { status ->
            val count = summary[status] ?: 0
            if (count > 0) {
                Text(
                    text = "${StatusVisual.label(status)} $count",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusVisual.color(status),
                )
            }
        }
    }
}

/**
 * 大纲 chip 上的状态点：聚合该节点下（含子节点）所有卡片的最需要注意状态。
 *
 * 这是「大纲只组织不判断」与「用户得知道哪里还没碰过」同时成立的唯一办法：
 * 点它只是筛选，但状态点让全貌可见。
 */
@Composable
private fun OutlineStatusDot(graph: MergedGraph, node: GraphOutlineNode) {
    val paths = collectPaths(node)
    val statuses = graph.cards
        .filter { it.outlinePath in paths }
        .mapNotNull { it.badgeStatus }
    val aggregated = StatusVisual.aggregate(statuses)
    if (aggregated != null) {
        StatusDot(aggregated, size = 6.dp)
    } else {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                .padding(0.dp)
        ) {}
    }
}

// --- 树工具 ---------------------------------------------------------------

private fun findNode(nodes: List<GraphOutlineNode>, id: String): GraphOutlineNode? {
    nodes.forEach { node ->
        if (node.id == id) return node
        findNode(node.children, id)?.let { return it }
    }
    return null
}

/** 收集一个节点及其所有子孙的 outlinePath。 */
private fun collectPaths(node: GraphOutlineNode?): Set<String> {
    if (node == null) return emptySet()
    val out = LinkedHashSet<String>()
    out += node.path
    node.children.forEach { out += collectPaths(it) }
    // 卡片挂的是叶子节点的 path，节点自身 path 也要算上
    out += node.cardIds.mapNotNull { it.substringAfter("crd.", "") }
    return out
}

/** 卡片的面包屑：沿大纲找到它所属节点的父链标题。 */
private fun outlinePathOf(graph: MergedGraph, card: GraphCard): String {
    fun find(nodes: List<GraphOutlineNode>, trail: List<String>): List<String>? {
        nodes.forEach { node ->
            val next = trail + node.title
            if (node.cardIds.contains(card.id) || node.path == card.outlinePath) return next
            find(node.children, next)?.let { return it }
        }
        return null
    }
    return find(graph.outline, emptyList())?.joinToString(" · ") ?: card.outlinePath
}

/**
 * 节点详情底部弹窗。
 *
 * ## 为什么是弹窗而不是常驻面板
 *
 * 结构图要守「先看骨架」：一屏内尽量多的节点可见，才看得出整体形状。
 * 常驻面板会永久吃掉 1/3 屏高，骨架就看不全了。
 * 弹窗是**按需展开**——点开看内容，关掉回归骨架。
 *
 * ## 内容分工
 *
 * 结构图上只显示节点标题（骨架）；这里给全部内容：
 * 卡片标题、摘要、正文，以及「可下钻的命题数」。
 * 这与卡片流视图看到的信息一致，但不需要在图上画卡片。
 */
@Composable
private fun NodeDetailSheet(
    title: String,
    cards: List<GraphCard>,
    onDismiss: () -> Unit,
    onDescend: (cardId: String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
    // 半透明遮罩：点击外部关闭
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(onClick = onDismiss, indication = null, interactionSource = remember { MutableInteractionSource() })
    )

    // 底部卡片
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .heightIn(max = 520.dp),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column(Modifier.padding(bottom = ZjSpace.md)) {
            // 拖拽条 + 标题
            Box(
                Modifier
                    .padding(top = ZjSpace.sm, bottom = ZjSpace.xs)
                    .width(36.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = ZjSpace.lg, vertical = ZjSpace.sm),
            )

            if (cards.isEmpty()) {
                Text(
                    "这一节只用来组织结构，没有具体内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ZjSpace.lg, vertical = ZjSpace.md),
                )
            } else {
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(cards, key = { it.id }) { card ->
                        Column(
                            Modifier.padding(
                                horizontal = ZjSpace.lg,
                                vertical = ZjSpace.sm
                            )
                        ) {
                            // 正文优先：`body` 通常已包含 `summary` 的内容
                            // （实测 summary 是 body 的一个子串），
                            // 两处都显示会让同一段话出现两遍。
                            // 没有 body 时才退回 summary。
                            val content = remember(card.id) {
                                if (card.body.isNotBlank()) stripCodeFences(card.body)
                                else stripCodeFences(card.summary)
                            }
                            Text(
                                content,
                                style = MaterialTheme.typography.bodyMedium,
                            )

                            // 命题数 + 下钻入口
                            if (card.claims.isNotEmpty()) {
                                Spacer(Modifier.height(ZjSpace.sm))
                                Surface(
                                    onClick = { onDescend(card.id) },
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        "下钻 → ${card.claims.size} 条命题",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(
                                            horizontal = ZjSpace.md,
                                            vertical = 6.dp
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(ZjSpace.md)) }
                }
            }
        }
    }
    }
}
/** 按大纲节点的 `path` 查找节点（[findNode] 按 id 查找，path 与 id 不同）。 */
private fun findNodeByPath(nodes: List<GraphOutlineNode>, path: String): GraphOutlineNode? {
    nodes.forEach { node ->
        if (node.path == path) return node
        findNodeByPath(node.children, path)?.let { return it }
    }
    return null
}
/**
 * 把 Markdown 代码围栏和行内标记去掉，只留内容。
 *
 * 原文里的 ```text ... ``` 是给渲染器看的标记，直接丢给 Text 会原样显示成一堆反引号，
 * 看起来像排版错误。同理 `**加粗**` 与 `行内代码` 的反引号也不该露出来。
 *
 * 结构图是纯文本视图，不解析 Markdown —— 但至少要把它**去掉**，
 * 而不是把标记当成正文显示给用户。
 */
internal fun stripCodeFences(raw: String): String {
    if (raw.isBlank()) return raw
    var out = raw
    if (out.contains("```")) {
        out = out.lineSequence()
            .filterNot { it.trim().startsWith("```") }
            .joinToString("\n")
    }
    // 行内代码：`code` → code
    out = out.replace(Regex("`([^`\n]+)`"), "$1")
    // 粗体 / 斜体标记：**text** → text
    out = out.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
    out = out.replace(Regex("(?<!\\*)\\*([^*\n]+)\\*(?!\\*)"), "$1")
    // 残留的标题井号（正文里偶有 #### ）
    out = out.replace(Regex("(?m)^#{1,6}\\s*"), "")
    return out.trim()
}