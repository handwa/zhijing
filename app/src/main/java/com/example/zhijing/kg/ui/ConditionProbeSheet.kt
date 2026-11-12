package com.example.zhijing.kg.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhijing.kg.depth.ConditionProbeSession
import com.example.zhijing.kg.depth.Outcome
import com.example.zhijing.ui.theme.ZjSpace

/**
 * 「预测 → 揭示」两屏。
 *
 * ## 为什么第一屏必须是题，而不是条件列表
 *
 * roadmap 的判据：没有预测，讲解就没有落点。用户先自己押一个后果，系统再揭示，
 * 「你为什么错」才有的放矢；直接列条件等于把答案先给了。
 *
 * ## 判定在哪
 *
 * 不在这里。[ConditionProbeSession.answer] 已经用本地求值器算完了真值，
 * 本文件只负责把它画出来 —— UI 不做任何对错判断。
 */

/** 第一屏：给一道题，必须选一个后果。 */
@Composable
internal fun ProbeQuestionView(
    question: ConditionProbeSession.Question,
    onChoose: (Outcome) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = ZjSpace.lg, vertical = ZjSpace.md)) {
        Text(
            // 题号来自 Question 自身（position + 1），不再由调用方推算 ——
            // 之前传的是「已答计数」，答完未推进时会显示成 3/2
            text = "先猜一下 · ${question.ordinal}/${question.total}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(ZjSpace.sm))
        Text(
            text = question.condition,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(ZjSpace.sm))
        Text(
            text = question.action,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(ZjSpace.md))
        Text(
            text = "这条命题会怎样？",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(ZjSpace.xs))

        // 四个选项对应 Outcome 的四态；UNKNOWN 不在其中（契约异常不该让用户猜）
        question.options.forEach { outcome ->
            TextButton(
                onClick = { onChoose(outcome) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = outcome.label,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/** 第二屏：揭示实际后果，并把机制讲在「你为什么错」上。 */
@Composable
internal fun ProbeRevealView(
    answer: ConditionProbeSession.Answer,
    hasNext: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = ZjSpace.lg, vertical = ZjSpace.md)) {
        Text(
            text = if (answer.matched) "猜对了" else "这次猜错了",
            style = MaterialTheme.typography.titleMedium,
            color = if (answer.matched) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Spacer(Modifier.height(ZjSpace.sm))
        Text(
            text = "你选了「${answer.predicted.label}」，实际是「${answer.actual.outcome.label}」。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(ZjSpace.sm))
        Text(
            text = answer.actual.detail,
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(ZjSpace.md))
        Text(
            text = "为什么",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(ZjSpace.xs))
        Text(
            text = answer.mechanism,
            style = MaterialTheme.typography.bodyMedium,
        )

        // 误解只在他真的选了那个错选项时才出现 —— 这是「讲在错处」的关键
        answer.misconception?.let { misconception ->
            Spacer(Modifier.height(ZjSpace.md))
            Text(
                text = "你可能是这么想的",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(ZjSpace.xs))
            Text(
                text = misconception.why,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(ZjSpace.xs))
            Text(
                text = misconception.correction,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (answer.actual.needsCaveat) {
            Spacer(Modifier.height(ZjSpace.md))
            Text(
                text = "这条后果由模型撰写，尚未人工核实。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(ZjSpace.md))
        TextButton(onClick = onContinue) {
            Text(if (hasNext) "再拨一个条件" else "看这道命题")
        }
    }
}
