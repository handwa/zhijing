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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.zhijing.kg.GraphCard
import com.example.zhijing.kg.GraphOutlineNode
import com.example.zhijing.kg.MergedGraph
import com.example.zhijing.kg.SkillProgress
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
/**
 * `internal` 而不是 `public`：`onProbeAnswer` 的参数类型 [ConditionProbeSession.Answer]
 * 是深度侧的内部类型。要对外暴露就得再包一层 DTO，而图谱页本来就只在 :app 内使用。
 */
@Composable
internal fun GraphScreen(
    graph: MergedGraph?,
    loading: Boolean,
    onJudge: (claimId: String, correct: Boolean, misconception: String?) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 清空某张卡的作答记录（回到全部未探测）。
     *
     * 状态表没有 UI 入口时，一条陈旧的状态（比如早就改对过的易错）会把卡片永久卡住，
     * 用户只能卸载重装。这里给一个明确的出口。
     */
    onResetCard: (cardId: String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onGenerateNew: () -> Unit = {},
    /**
     * 一次「预测 → 揭示」的作答。
     *
     * 只写**条件级**记录（`kg_condition_probe`），不写命题级状态：
     * 命题级的收敛由下面的判断题面板负责，同一道题记两次会让置信度提前到顶。
     */
    onProbeAnswer: (ConditionProbeSession.Answer) -> Unit = {},
) {
    var selectedOutline by remember { mutableStateOf<String?>(null) }
    var descendCardId by remember { mutableStateOf<String?>(null) }
    var claimIndex by remember { mutableIntStateOf(0) }
    // 结构图里被点中的节点路径（null = 未选中）。
    // 未选中时只画层级线；选中后只画与它相连的前置/后续线，相关卡片一起高亮。
    var selectedNodePath by remember { mutableStateOf<String?>(null) }
    // 视图形态：卡片流（默认，好读）或结构图（看关系）。
    // 刻意做成**同一个页面的两种呈现**，而不是两个一级 tab ——
    // 结构图与广度视图功能重叠（都是"看关系找位置"），并列会让导航变乱。
    var viewMode by remember { mutableStateOf(GraphViewMode.CARDS) }

    // 节点搜索：24 个节点靠眼睛还行，生成图谱到 100+ 就找不到了
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // 卡片流里「最近看过的那张卡」：切到结构图时优先定位到它所属的节点。
    // 注意章节 chip 存的是节点 id（out.s1），这里存的是卡片 id（crd.s1-1-linear），
    // 两者都能映射到结构图使用的 path，但映射方式不同。
    var selectedCardId by remember { mutableStateOf<String?>(null) }
    // 聚焦请求：搜索命中或从卡片流切到结构图时，把目标节点移到视口中央。
    // token 递增，保证「同一个节点连搜两次」也能重新定位。
    var focusRequest by remember { mutableStateOf<GraphFocusRequest?>(null) }
    var focusToken by remember { mutableLongStateOf(0L) }
    // 「现在能学」与三态分布：两段式（摘要 chip → 底部面板），不做常驻侧栏
    var showReadySheet by remember { mutableStateOf(false) }
    var showProgressSheet by remember { mutableStateOf(false) }
    // 理解路径「重走这道」的目标干预点；null = 正常从第一题开始
    var replayIntervention by remember { mutableStateOf<String?>(null) }

    if (loading || graph == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // 卡片三态：纯派生，不存表（见 SkillProgress 的说明）。
    // 统计行与结构图共用这一份结果，避免两处各算一次后数字对不上。
    val skill = remember(graph) { SkillProgress.of(graph) }

    // 大纲筛选在 GraphCardList 内部完成（它才是真正渲染卡片的地方）。
    // 这里原来有一份同样的 visibleCards，筛选修好后它就没人用了；
    // 留着会让读代码的人以为筛选仍带 `claims.isNotEmpty()` 那个失效条件，故删除。

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
                if (viewMode == GraphViewMode.CARDS) {
                    viewMode = GraphViewMode.TREE
                    // 定位优先级：刚点过的那张卡片 > 当前筛选的章节。
                    //
                    // 这里必须做 id → path 的转换：章节 chip 存的是节点 id（`out.s1`），
                    // 而结构图的聚焦按 `node.path`（`s1`）匹配，直接拿 id 会静默失效 ——
                    // 这正是「切过去跟以前没区别」的原因。
                    val target = GraphFocus.resolvePath(
                        cardOutlinePath = selectedCardId
                            ?.let { cardId -> graph.cards.firstOrNull { it.id == cardId }?.outlinePath },
                        outline = graph.outline,
                        selectedOutlineId = selectedOutline,
                    )
                    target?.let { path ->
                        selectedNodePath = path
                        focusRequest = GraphFocusRequest(path, ++focusToken)
                    }
                } else {
                    viewMode = GraphViewMode.CARDS
                }
            }) {
                Text(if (viewMode == GraphViewMode.CARDS) "结构图" else "卡片流")
            }
            // 节点搜索：命中后切到结构图并定位
            IconButton(onClick = { showSearch = true }) {
                Icon(Icons.Default.Search, contentDescription = "搜索节点")
            }
            // 生成新图谱：图谱页内的直接入口，不必绕到设置
            IconButton(onClick = onGenerateNew) {
                Icon(Icons.Default.Add, contentDescription = "生成新图谱")
            }
            // 设置：与 TopAppBar 的齿轮功能相同，但位置在本页内，保证够得着
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "设置")
            }
            StatusSummaryChips(
                graph = graph,
                skill = skill,
                // 窄屏（<400dp）只保留两个可执行入口：四态计数在进度面板里也有，
                // 但"能学几张、亮了几张"是唯一需要常驻的信息。
                compact = LocalConfiguration.current.screenWidthDp < 400,
                onOpenReady = { showReadySheet = true },
                onOpenProgress = { showProgressSheet = true },
            )
        }

        // 结构图：与卡片流互斥呈现
        if (viewMode == GraphViewMode.TREE) {
            GraphTreeView(
                graph = graph,
                // 点节点 → 选中它（高亮相关连线）+ 底部弹窗展示内容
                onNodeClick = { path ->
                    // 点空白处（null）取消选中；再点同一节点也取消
                    selectedNodePath = if (path == null || selectedNodePath == path) null else path
                },
                selectedNode = selectedNodePath,
                skill = skill,
                focusRequest = focusRequest,
                onFocusConsumed = { focusRequest = null },
                modifier = Modifier.weight(1f),
            )
        } else {
            // ---- 卡片流：大纲 chips + 卡片列表 ----
            GraphCardList(
                graph = graph,
                selectedOutline = selectedOutline,
                onSelectOutline = { selectedOutline = it },
                selectedCardId = selectedCardId,
                onSelectCard = { selectedCardId = it },
                onDescend = { cardId ->
                    // 下钻也算「看过这张卡」：关掉弹窗后切结构图，应定位到它
                    selectedCardId = cardId
                    descendCardId = cardId
                    claimIndex = 0
                    replayIntervention = null
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
            skill = skill,
            onResetCard = onResetCard,
            onDismiss = { selectedNodePath = null },
            onDescend = { cardId ->
                selectedNodePath = null
                descendCardId = cardId
                claimIndex = 0
                replayIntervention = null
            },
        )
    }

    // ---- 现在能学：底部面板（与卡片详情同一套两段式）----
    if (showReadySheet) {
        ReadySheet(
            entries = skill.readyCards.mapNotNull { progress ->
                graph.cards.firstOrNull { it.id == progress.cardId }?.let { progress to it }
            },
            onDismiss = { showReadySheet = false },
            onPick = { card ->
                showReadySheet = false
                // 与搜索命中同一条定位链路：切结构图 + 设为选中 + 居中
                viewMode = GraphViewMode.TREE
                selectedNodePath = card.outlinePath
                focusRequest = GraphFocusRequest(card.outlinePath, ++focusToken)
            },
        )
    }

    // ---- 三态分布：同一面板形态，纯计数 ----
    if (showProgressSheet) {
        ProgressSheet(graph = graph, skill = skill, onDismiss = { showProgressSheet = false })
    }

    // ---- 深度下钻：覆盖层（两种视图共用，不是并列入口）----
    DescendDialog(
        graph = graph,
        cardId = descendCardId,
        claimIndex = claimIndex,
        onIndexChange = { claimIndex = it },
        onClose = {
            descendCardId = null
            // 重走状态必须一起清掉，否则下次进来会莫名从第 3 题开始
            replayIntervention = null
        },
        onJudge = onJudge,
        onProbeAnswer = onProbeAnswer,
        probeStartAt = replayIntervention,
        onReplayStep = { replayIntervention = it },
    )

    // ---- 节点搜索 ----
    if (showSearch) {
        GraphSearchDialog(
            nodes = graph.outline,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onPick = { hit ->
                selectedNodePath = hit.path
                viewMode = GraphViewMode.TREE
                focusRequest = GraphFocusRequest(hit.path, ++focusToken)
                showSearch = false
            },
            onDismiss = { showSearch = false },
        )
    }
}

/**
 * 节点搜索弹窗：输入标题片段 → 点结果 → 切到结构图并定位。
 *
 * 只匹配标题，不匹配正文：搜索的用途是「定位到哪个节点」，
 * 正文匹配会把几十张卡片都带出来，反而找不到。
 */
@Composable
private fun GraphSearchDialog(
    nodes: List<GraphOutlineNode>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (GraphSearch.Hit) -> Unit,
    onDismiss: () -> Unit,
) {
    val hits = remember(query, nodes) { GraphSearch.search(nodes, query) }
    val inputFocus = remember { FocusRequester() }
    // 弹窗一出现就把焦点交给输入框：否则要先点一下输入框才会弹键盘，
    // 在「打开就为了搜」的场景里等于多一次无效操作。
    LaunchedEffect(Unit) { inputFocus.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(ZjRadius.md),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Column(Modifier.padding(ZjSpace.lg)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    label = { Text("搜索节点") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(inputFocus),
                )
                Spacer(Modifier.height(ZjSpace.sm))
                when {
                    query.isBlank() -> Text(
                        text = "输入标题片段，例如「Softmax」",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    hits.isEmpty() -> Text(
                        text = "没有匹配的节点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> Column(
                        Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        hits.forEach { hit ->
                            TextButton(
                                onClick = { onPick(hit) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = hit.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    if (hit.breadcrumb.isNotBlank()) {
                                        Text(
                                            text = hit.breadcrumb,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
    /** 当前选中的卡片（切到结构图时定位到它所属的节点）。 */
    selectedCardId: String?,
    onSelectCard: (String) -> Unit,
    onDescend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 大纲筛选：选中一级章节时，只显示其子树内的卡片。
    //
    // 之前是 `claims.isNotEmpty() || outlinePath in paths` —— 出厂卡片几乎都有命题，
    // 这个 OR 让它们在任何选中状态下都显示，筛选形同虚设（选中「高等数学」
    // 仍能看到 Transformer 的卡片）。该 OR 是单一主题时代的残留：当时唯一的
    // 分区根节点本来就包含全部卡片，条件不起作用也没人发现。
    val visibleCards = if (selectedOutline == null) {
        graph.cards
    } else {
        val node = findNode(graph.outline, selectedOutline)
        // 选中节点查不到（图谱重建后旧选中残留）时退回全部，避免空白页
        if (node == null) graph.cards
        else {
            val paths = collectPaths(node)
            graph.cards.filter { it.outlinePath in paths }
        }
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
                    selected = card.id == selectedCardId,
                    onSelect = { onSelectCard(card.id) },
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
    /** 理解路径「重走这道」的目标干预点；null 表示正常从头走。 */
    probeStartAt: String? = null,
    /** 点路径里某一步 → 回到那道干预题。 */
    onReplayStep: (interventionId: String) -> Unit = {},
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
    //
    // 已经拨过的干预点：**打开弹窗时**快照一次并跳过，直接进入判断题。
    // 必须快照而不是实时跟随图谱：作答会触发重新 load，`answeredInterventions` 变大，
    // 实时计算会让会话被重建——刚答完的那题的揭示屏会当场消失，直接跳到下一题。
    val skippedBeforeOpen = remember(card.id) {
        graph.claims.values.flatMap { it.conditionPath }.map { it.interventionId }.toSet()
    }
    // 「重走这道」时，目标那道必须**不被跳过**，否则会话一上来就是 Done
    val skipForSession = if (probeStartAt == null) {
        skippedBeforeOpen
    } else {
        skippedBeforeOpen - probeStartAt
    }
    val probe = remember(card.id, graph.triples, probeStartAt) {
        ConditionProbeSession.forCard(
            triples = graph.triples,
            cardId = card.id,
            skipInterventionIds = skipForSession,
            startAt = probeStartAt,
        ).also { session ->
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
                                hasNext = claimIndex + 1 < ordered.size,
                                onReplayStep = onReplayStep,
                                // 增补的深度边：按 from 反查，不依赖 claim.edgeIds（它来自 build 产物）
                                boundaryEdges = graph.edges.values.filter { it.from == current.id },
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
private fun StatusSummaryChips(
    graph: MergedGraph,
    skill: SkillProgress.Result,
    compact: Boolean,
    onOpenReady: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    val summary = graph.statusSummary
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) {
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
        // 「可学 N」：这是"我现在能做什么"的入口，比"还剩多少没探测"更可执行
        if (skill.ready > 0) {
            Text(
                text = "可学 ${skill.ready}",
                style = MaterialTheme.typography.labelSmall,
                color = SkillVisual.ready,
                modifier = Modifier.clickable(onClick = onOpenReady),
            )
        }
        // 进度只给"已经亮了几个"，不做百分比条（会让人以为刷满就掌握）
        Text(
            text = "✓ ${skill.lit}/${skill.total}",
            style = MaterialTheme.typography.labelSmall,
            color = SkillVisual.lit,
            modifier = Modifier.clickable(onClick = onOpenProgress),
        )
    }
}

/**
 * 「现在能学」底部面板。
 *
 * 排序不是按标题、也不是按章节，而是按**解锁数量**：同样都能学，先学解锁多的那张，
 * 后面的路才会变短。这是参考项目"后续 44"那个标记的唯一用途 —— 排序，不是评分。
 */
@Composable
private fun ReadySheet(
    entries: List<Pair<SkillProgress.CardProgress, GraphCard>>,
    onDismiss: () -> Unit,
    onPick: (GraphCard) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    onClick = onDismiss,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                )
        )
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
                SheetHandle()
                Text(
                    text = "现在能学 · ${entries.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = ZjSpace.pageHorizontal),
                )
                Spacer(Modifier.height(ZjSpace.xs))
                Text(
                    text = "前置都已点亮。按解锁数量排序，先学解锁多的。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ZjSpace.pageHorizontal),
                )
                Spacer(Modifier.height(ZjSpace.sm))
                if (entries.isEmpty()) {
                    Text(
                        text = "暂时没有可学的卡片 —— 先把已有的前置点亮。",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(ZjSpace.pageHorizontal),
                    )
                } else {
                    LazyColumn {
                        items(entries) { (progress, card) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(card) }
                                    .padding(
                                        horizontal = ZjSpace.pageHorizontal,
                                        vertical = ZjSpace.sm,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(card.title, style = MaterialTheme.typography.bodyMedium)
                                    if (card.summary.isNotBlank()) {
                                        Text(
                                            text = card.summary,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                if (progress.downstreamCount > 0) {
                                    Text(
                                        text = "后续 ${progress.downstreamCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SkillVisual.ready,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 三态分布明细。刻意只给计数，不给百分比。 */
@Composable
private fun ProgressSheet(
    graph: MergedGraph,
    skill: SkillProgress.Result,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    onClick = onDismiss,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                )
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(bottom = ZjSpace.md)) {
                SheetHandle()
                Text(
                    text = "三态分布",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = ZjSpace.pageHorizontal),
                )
                Spacer(Modifier.height(ZjSpace.sm))
                ProgressRow("已点亮", skill.lit, SkillVisual.lit)
                ProgressRow("现在能学", skill.ready, SkillVisual.ready)
                ProgressRow("未解锁", skill.locked, SkillVisual.locked)
                // 窄屏时顶栏不显示四态计数，这里补上 —— 信息不丢，只是不常驻
                val summary = graph.statusSummary
                ProbeStatus.CONFUSED.let {
                    ProgressRow(
                        StatusVisual.label(it),
                        summary[it] ?: 0,
                        StatusVisual.color(it),
                    )
                }
                ProbeStatus.UNPROBED.let {
                    ProgressRow(
                        StatusVisual.label(it),
                        summary[it] ?: 0,
                        StatusVisual.color(it),
                    )
                }
                Spacer(Modifier.height(ZjSpace.sm))
                Text(
                    // 门控只是展示：不阻止点击未解锁卡片看内容
                    text = "未解锁只是「前置还没点亮」，卡片内容仍然可以看。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ZjSpace.pageHorizontal),
                )
            }
        }
    }
}

@Composable
private fun ProgressRow(label: String, count: Int, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ZjSpace.pageHorizontal, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** 底部面板顶部的拖拽条：与卡片详情面板保持同一视觉。 */
@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .padding(top = ZjSpace.sm, bottom = ZjSpace.xs)
                .width(36.dp)
                .height(4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(2.dp),
                )
        )
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

/**
 * 卡片状态的一句话说明。
 *
 * 只画颜色不够：一张卡有 2 条命题时，答完 1 条不会点亮（阈值 80%），
 * 用户看到的是「我答了但没反应」。这里把「练了几条、还差几条、有几条易错」写出来。
 */
private fun cardStateLine(progress: SkillProgress.CardProgress): String {
    val base = when (progress.state) {
        SkillProgress.State.LIT -> "已点亮"
        SkillProgress.State.READY -> "现在能学"
        SkillProgress.State.LOCKED -> "未解锁"
    }
    return buildString {
        append(base)
        append(" · ")
        append("${progress.practicedCount}/${progress.claimCount} 条已练")
        if (progress.state == SkillProgress.State.LOCKED) {
            append("，还差 ${progress.missingPrerequisites.size} 条前置")
        }
        if (progress.confusedCount > 0) {
            append("，${progress.confusedCount} 条易错待纠正")
            progress.confusedStatement?.let { append("：「$it…」") }
        } else if (progress.state != SkillProgress.State.LIT &&
            progress.practicedCount < progress.claimCount
        ) {
            append("，还剩 ${progress.claimCount - progress.practicedCount} 条没练")
        }
    }
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
    skill: SkillProgress.Result,
    onResetCard: (cardId: String) -> Unit,
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

                            // 为什么这张卡还没点亮：写清「练了几条 / 还差几条 / 有几条易错」。
                            // 只画颜色的话，用户答了一条没亮，不知道是没答完还是规则坏了。
                            val progress = skill.cards[card.id]
                            if (progress != null && progress.learnable) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = cardStateLine(progress),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (progress.state) {
                                        SkillProgress.State.LIT -> SkillVisual.lit
                                        SkillProgress.State.READY -> SkillVisual.ready
                                        SkillProgress.State.LOCKED -> SkillVisual.locked
                                    },
                                )
                            }

                            // 卡在易错时的出口：否则一条陈旧状态会让这张卡永久灰着
                            if (progress != null && progress.confusedCount > 0) {
                                TextButton(
                                    onClick = { onResetCard(card.id) },
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        "清空这张卡的记录",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }

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