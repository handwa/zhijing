package com.example.zhijing.kg.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zhijing.kg.GraphCard
import com.example.zhijing.kg.GraphClaim
import com.example.zhijing.kg.GraphEdge
import com.example.zhijing.ui.theme.ZjRadius
import com.example.zhijing.ui.theme.ZjSpace

/**
 * 广度视图的一张卡片。
 *
 * 信息密度的取舍（设计文档「卡片信息密度上限」）：
 * **默认只显示 4 项** —— 标题+角标、摘要、交叉链接、下钻按钮。
 * 正文折叠成「展开全文」，易错注记只在真有易错时出现。
 *
 * 理由：广度图谱第一目标是**可读**，八项信息堆满的卡片不可读。
 */
@Composable
fun GraphCardView(
    card: GraphCard,
    breadcrumb: String,
    onDescend: () -> Unit,
    onOpenCrossLink: (String) -> Unit,
    /** 是否被选中（切到结构图时会定位到这张卡所属的节点）。 */
    selected: Boolean = false,
    /** 点卡片主体：记为「最近看过的那张卡」。 */
    onSelect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expanded by remember(card.id) { mutableStateOf(false) }
    val badge = card.badgeStatus

    Card(
        modifier = modifier
            .fillMaxWidth()
            // 整张卡可点：这是「选中」而不是「下钻」，下钻仍走底部按钮
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(ZjRadius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row {
            // 左侧「叶脉书签」竖条：颜色即状态
            Box(
                Modifier
                    .width(3.dp)
                    .height(if (expanded) 220.dp else 96.dp)
                    .background(
                        badge?.let { StatusVisual.color(it) }
                            ?: MaterialTheme.colorScheme.outlineVariant
                    )
            )
            Column(Modifier.padding(ZjSpace.lg)) {
                // 标题行 + 角标
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    badge?.let { StatusBadge(it, compact = true) }
                }

                Spacer(Modifier.height(ZjSpace.xs))

                // 面包屑（大纲标题链）
                if (breadcrumb.isNotBlank()) {
                    Text(
                        text = breadcrumb,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 只显示一份正文。
                // 1) summary 往往是 body 的子串（生成时就复用了同一段话），两处都显示等于写两遍；
                // 2) 必须剥掉 Markdown 标记（`**`、反引号、代码围栏），否则卡片上会露出源码符号。
                val bodyText = remember(card.id, card.body, card.summary) {
                    stripCodeFences(card.body.ifBlank { card.summary })
                }
                if (bodyText.isNotBlank()) {
                    Spacer(Modifier.height(ZjSpace.sm))
                    Text(
                        text = bodyText,
                        style = MaterialTheme.typography.bodyMedium,
                        // 折叠时给三行预览，展开看全文 —— 比「折叠就只剩摘要」信息量更大
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 条件级事实：这条卡下设的干预点上，用户**实际**错过几次。
                // 刻意不用百分比：百分比不驱动行动，还会把「只答过一次」说成「80% 掌握」。
                val facts = card.conditionFacts
                if (facts.isNotEmpty()) {
                    val confused = StatusVisual.color(com.zhijing.data.model.ProbeStatus.CONFUSED)
                    Spacer(Modifier.height(ZjSpace.sm))
                    facts.take(2).forEach { fact ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = confused,
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.size(width = 3.dp, height = 14.dp),
                            ) {}
                            Spacer(Modifier.width(ZjSpace.sm))
                            Text(
                                text = "在「${fact.action}」上错过 ${fact.wrongCount} 次",
                                style = MaterialTheme.typography.labelSmall,
                                color = confused,
                            )
                        }
                        if (!fact.misconception.isNullOrBlank()) {
                            Text(
                                text = "　${fact.misconception}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (facts.size > 2) {
                        Text(
                            text = "　还有 ${facts.size - 2} 条条件曾经错过",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 易错点注记：只在有易错、且还没有更具体的条件事实时出现
                val notes = card.misconceptionNotes
                if (notes.isNotEmpty() && facts.isEmpty()) {
                    Spacer(Modifier.height(ZjSpace.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = StatusVisual.color(com.zhijing.data.model.ProbeStatus.CONFUSED),
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.size(width = 3.dp, height = 14.dp),
                        ) {}
                        Spacer(Modifier.width(ZjSpace.sm))
                        Text(
                            text = "易错：${notes.first()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusVisual.color(com.zhijing.data.model.ProbeStatus.CONFUSED),
                        )
                    }
                }

                // 交叉链接：封顶 5 条，按权重降序
                if (card.crossLinks.isNotEmpty()) {
                    Spacer(Modifier.height(ZjSpace.sm))
                    Text(
                        text = "关联：" + card.crossLinks.take(5).joinToString(" · ") { it.label },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // 底部动作
                Spacer(Modifier.height(ZjSpace.sm))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (card.body.isNotBlank()) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "收起" else "展开全文")
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    if (card.descendable) {
                        TextButton(onClick = onDescend) {
                            Text("下钻 ${card.claims.size} 条命题")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一张卡的深度面板内容：判断列表。
 *
 * 第一屏原则：**只有一句话命题 + 两个按钮**，条件与解释在作答后才展开。
 * 这不是风格问题，是产品原则 —— 用户没有参照物，多给一个信息他就不知道该干什么。
 */
@Composable
fun ClaimJudgementView(
    claim: GraphClaim,
    index: Int,
    total: Int,
    onAnswered: (correct: Boolean) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    /** 还有下一条命题。false 时底部只给「完成」——避免点了没反应的假按钮。 */
    hasNext: Boolean = true,
    /** 理解路径里点「重走这道」：回到那个干预点重答。 */
    onReplayStep: ((interventionId: String) -> Unit)? = null,
    /** 这条命题的深度边：给出成立前提、边界与反例。 */
    boundaryEdges: List<GraphEdge> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var answered by remember(claim.id) { mutableStateOf<Boolean?>(null) }

    Column(modifier.padding(ZjSpace.lg)) {
        // 顶栏：进度 + 关闭
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildString {
                    append("第 ${index + 1} / $total 条 · ")
                    append(StatusVisual.label(claim.probe.probeStatus))
                    // 生成图谱的命题没有原文依据：必须让用户知道自己在判一条模型写的断言
                    if (!claim.verified) append(" · 未经核实")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text("关闭") }
        }

        Spacer(Modifier.height(ZjSpace.lg))

        // 第一屏唯一文案：命题
        Text(
            text = claim.question,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(ZjSpace.md))
        Text(
            text = claim.statement,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
        )

        if (answered == null) {
            Spacer(Modifier.height(ZjSpace.md))
            Text(
                text = "先自己判断，条件与边界在作答之后才展开。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ZjSpace.xl))
            // 只有两个按钮
            Row(horizontalArrangement = Arrangement.spacedBy(ZjSpace.md)) {
                Surface(
                    onClick = {
                        answered = true
                        onAnswered(claim.expected)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("成立") }
                }
                Surface(
                    onClick = {
                        answered = false
                        onAnswered(!claim.expected)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("不成立") }
                }
            }
        } else {
            // 揭示屏：判定 → 为什么 → 条件 → 错点 → 误解
            Spacer(Modifier.height(ZjSpace.lg))
            RevealSection(
                claim = claim,
                judgedCorrect = answered!!,
                onReplayStep = onReplayStep,
                boundaryEdges = boundaryEdges,
            )
            Spacer(Modifier.height(ZjSpace.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(ZjSpace.md)) {
                if (hasNext) {
                    TextButton(onClick = onNext, modifier = Modifier.weight(1f)) {
                        Text("下一条")
                    }
                }
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (hasNext) "返回卡片" else "完成")
                }
            }
        }
    }
}

@Composable
private fun RevealSection(
    claim: GraphClaim,
    judgedCorrect: Boolean,
    onReplayStep: ((interventionId: String) -> Unit)? = null,
    boundaryEdges: List<GraphEdge> = emptyList(),
) {
    val verdictColor = if (judgedCorrect) {
        StatusVisual.color(com.zhijing.data.model.ProbeStatus.MASTERED)
    } else {
        StatusVisual.color(com.zhijing.data.model.ProbeStatus.CONFUSED)
    }

    // 判定条
    Text(
        text = if (judgedCorrect) {
            "✓ 判断正确 —— 这条命题的结论是「${if (claim.expected) "成立" else "不成立"}」"
        } else {
            "✗ 撞错了 —— 正确结论是「${if (claim.expected) "成立" else "不成立"}」"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = verdictColor,
        fontWeight = FontWeight.Medium,
    )

    if (claim.explanation.isNotBlank()) {
        Spacer(Modifier.height(ZjSpace.md))
        SectionTitle("为什么")
        Text(claim.explanation, style = MaterialTheme.typography.bodyMedium)
    }

    if (claim.conditions.isNotEmpty()) {
        Spacer(Modifier.height(ZjSpace.md))
        SectionTitle("成立条件")
        claim.conditions.forEach { cond ->
            Text(
                "· $cond",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // 错点归因：**最有价值的一屏**
    if (claim.errorKinds.isNotEmpty()) {
        Spacer(Modifier.height(ZjSpace.md))
        SectionTitle("错点落在哪条依赖上")
        claim.errorKinds.forEach { ek ->
            Row(
                Modifier.padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(ZjSpace.sm),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = "${kindLabel(ek.kind)}依赖",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    ek.why,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (claim.evidence.isNotBlank()) {
        Spacer(Modifier.height(ZjSpace.md))
        SectionTitle("原文依据")
        Text(
            claim.evidence,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // 成立边界与反例：来自深度边（含增补源补的边）。有边才显示，
    // 没边的命题不要伪造一段"因为没有原文所以…"的填充话。
    if (boundaryEdges.isNotEmpty()) {
        Spacer(Modifier.height(ZjSpace.md))
        SectionTitle("成立边界与反例")
        boundaryEdges.take(2).forEach { edge ->
            if (edge.premise.isNotBlank()) {
                Text("前提：${edge.premise}", style = MaterialTheme.typography.bodySmall)
            }
            if (edge.boundary.isNotBlank()) {
                Text(
                    text = "边界：${edge.boundary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (edge.counterexample.isNotBlank()) {
                Text(
                    text = "反例：${edge.counterexample}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusVisual.color(com.zhijing.data.model.ProbeStatus.CONFUSED),
                )
            }
        }
    }

    // 理解路径（第一版，单线）：提问 → 预测 → 揭示。
    // 重点是「你原来猜 X，实际是 Y」这个对比 —— 它比「答错了」多给一个可修正的模型。
    if (claim.conditionPath.isNotEmpty()) {
        Spacer(Modifier.height(ZjSpace.md))
        SectionTitle("你的理解路径")
        // 一句话主干：明细是逐条念的，读完记不住自己变了什么。
        // 没有变化（只答一次 / 首末相同）时返回 null，不渲染。
        PathSummary.of(claim.conditionPath)?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(ZjSpace.xs))
        }
        claim.conditionPath.take(3).forEach { step ->
            Text(
                text = "提问：${step.action}",
                style = MaterialTheme.typography.bodySmall,
            )
            val prediction = if (step.previousPredicted != null) {
                "你原来猜「${step.previousPredicted}」，现在猜「${step.predicted}」"
            } else {
                "你猜「${step.predicted}」"
            }
            Text(
                text = "$prediction，实际「${step.actual}」" + when {
                    step.revised -> " —— 已经改过来"
                    step.matched -> ""
                    else -> " —— 还没改过来"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (step.matched) {
                    StatusVisual.color(com.zhijing.data.model.ProbeStatus.MASTERED)
                } else {
                    StatusVisual.color(com.zhijing.data.model.ProbeStatus.CONFUSED)
                },
            )
            // 只放开「还没改过来」的步骤：已改对的重走会覆盖「原来猜 X」这条证据
            if (!step.matched && onReplayStep != null) {
                TextButton(
                    onClick = { onReplayStep(step.interventionId) },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("重走这道", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(ZjSpace.xs))
        }
        if (claim.conditionPath.size > 3) {
            Text(
                text = "…… 还有 ${claim.conditionPath.size - 3} 次拨动",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(ZjSpace.xs))
}

/** 五类依赖的中文名。 */
fun kindLabel(kind: String): String = when (kind) {
    "shape" -> "形状"
    "range" -> "值域"
    "statistics" -> "统计量"
    "gradient" -> "梯度"
    "semantic" -> "语义"
    else -> kind
}
