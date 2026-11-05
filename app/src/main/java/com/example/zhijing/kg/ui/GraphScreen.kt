package com.example.zhijing.kg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
) {
    var selectedOutline by remember { mutableStateOf<String?>(null) }
    var descendCardId by remember { mutableStateOf<String?>(null) }
    var claimIndex by remember { mutableIntStateOf(0) }

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
            StatusSummaryChips(graph)
        }

        // ---- 大纲：横向 chips ----
        LazyRow(
            contentPadding = PaddingValues(horizontal = ZjSpace.pageHorizontal),
            horizontalArrangement = Arrangement.spacedBy(ZjSpace.sm),
        ) {
            item {
                AssistChip(
                    onClick = { selectedOutline = null },
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
                    onClick = { selectedOutline = if (selected) null else node.id },
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
                    onDescend = {
                        descendCardId = card.id
                        claimIndex = 0
                    },
                    onOpenCrossLink = { target ->
                        visibleCards.firstOrNull { it.id == target }?.let {
                            descendCardId = null
                        }
                    },
                )
            }
            item { Spacer(Modifier.height(ZjSpace.xl)) }
        }
    }

    // ---- 深度下钻：覆盖层（不是并列入口，没有独立路由）----
    val card = descendCardId?.let { id -> graph.cards.firstOrNull { it.id == id } }
    if (card != null && card.claims.isNotEmpty()) {
        val ordered = graph.claimsByPriority(card.id)
        val current = ordered.getOrNull(claimIndex.coerceIn(0, ordered.lastIndex))

        Dialog(
            onDismissRequest = { descendCardId = null },
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
                    if (current != null) {
                        item {
                            ClaimJudgementView(
                                claim = current,
                                index = claimIndex,
                                total = ordered.size,
                                onAnswered = { correct ->
                                    onJudge(current.id, correct, current.misconception.takeIf { it.isNotBlank() })
                                },
                                onNext = { if (claimIndex + 1 < ordered.size) claimIndex++ },
                                onClose = { descendCardId = null },
                            )
                        }
                    }
                }
            }
        }
    }
}

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
