package com.example.zhijing.kg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zhijing.data.model.ProbeStatus
import com.example.zhijing.ui.theme.LocalStatusColors

/**
 * 四种探测状态的视觉映射。
 *
 * 设计文档的硬约束：**状态用「墨点 + 文字」表示，不靠色块堆信息**，
 * 且四态色不复用 Material 的 error/warning ——「易混淆」不是报错，
 * 是「已探测但易错未解除」，颜色要醒目但不能像错误提示。
 *
 * 颜色的唯一来源是 `LocalStatusColors`（浅色/深色分别调过对比度），
 * 这里只做状态 → 颜色的查表，不硬编码任何色值。
 */
object StatusVisual {

    /** 状态中文名。 */
    fun label(status: ProbeStatus): String = when (status) {
        ProbeStatus.UNPROBED -> "未探测"
        ProbeStatus.VERIFIED -> "已验证"
        ProbeStatus.MASTERED -> "已掌握"
        ProbeStatus.CONFUSED -> "易混淆"
    }

    /** 状态对应的墨点颜色。 */
    @Composable
    fun color(status: ProbeStatus): Color {
        val c = LocalStatusColors.current
        return when (status) {
            ProbeStatus.UNPROBED -> c.unprobed
            ProbeStatus.VERIFIED -> c.verified
            ProbeStatus.MASTERED -> c.mastered
            ProbeStatus.CONFUSED -> c.confused
        }
    }

    /** 卡片角标优先级：易混淆 > 未探测 > 已验证 > 已掌握。 */
    val BADGE_PRIORITY = listOf(
        ProbeStatus.CONFUSED,
        ProbeStatus.UNPROBED,
        ProbeStatus.VERIFIED,
        ProbeStatus.MASTERED
    )

    /** 一组状态聚合成一个角标（取最需要注意的那个）。 */
    fun aggregate(statuses: List<ProbeStatus>): ProbeStatus? =
        if (statuses.isEmpty()) null
        else BADGE_PRIORITY.firstOrNull { it in statuses }
}

/**
 * 一个状态角标：墨点 + 文字。
 *
 * @param compact 紧凑模式只显示墨点与短文字，用于卡片右上角
 */
@Composable
fun StatusBadge(
    status: ProbeStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val color = StatusVisual.color(status)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(if (compact) 6.dp else 7.dp)
                .background(color, CircleShape)
        )
        Text(
            text = StatusVisual.label(status),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/** 只有墨点、没有文字的极简标记（大纲树的行内状态点）。 */
@Composable
fun StatusDot(status: ProbeStatus, size: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(
        Modifier
            .size(size)
            .background(StatusVisual.color(status), CircleShape)
    )
}

/** 图例：四态横排，说明颜色含义。 */
@Composable
fun StatusLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProbeStatus.entries.forEach { status ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                StatusDot(status)
                Text(
                    StatusVisual.label(status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
