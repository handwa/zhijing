package com.example.zhijing.kg.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.zhijing.kg.GraphComparisonRunner
import com.example.zhijing.ui.theme.ZjRadius
import com.example.zhijing.ui.theme.ZjSpace

/**
 * 图谱生成质量对比测试页。
 *
 * 同一段文本同时跑两条路径，并列呈现指标，回答三个问题：
 * **能抽出多少条、多少条可用、花了多久**。
 *
 * 刻意**不自动判定哪条路径更好**：判断优劣需要人工标注（哪条命题是真命题、
 * 边界写得准不准），程序只能给客观指标。把评判权留给人和实验，是这套测试的价值所在。
 */
@Composable
fun GraphTestScreen(
    text: String,
    onTextChange: (String) -> Unit,
    result: GraphComparisonRunner.ComparisonResult?,
    running: Boolean,
    hasCloud: Boolean,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ZjSpace.pageHorizontal),
    ) {
        Text(
            "图谱生成对比测试",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(ZjSpace.xs))
        Text(
            "同一段文本，分别用本地规则与云端大模型抽取命题，比较产出。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(ZjSpace.md))

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text("待抽取的文本") },
            placeholder = { Text("粘贴一段文本。规则抽取只认「| 判断句 | 是否成立 | 边界 |」这类表格。") },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(ZjRadius.sm),
        )

        Spacer(Modifier.height(ZjSpace.md))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!hasCloud) {
                Text(
                    "未配置云端：只跑本地规则",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "云端不可达时该路径会标记为失败，不影响本地结果",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onRun, enabled = !running && text.isNotBlank()) {
                Text(if (running) "测试中…" else "运行对比")
            }
        }

        if (running) {
            Spacer(Modifier.height(ZjSpace.lg))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        }

        result?.let { r ->
            Spacer(Modifier.height(ZjSpace.lg))
            HorizontalDivider()
            Spacer(Modifier.height(ZjSpace.md))

            Text(
                r.summaryLine(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(ZjSpace.xs))
            Text(
                "文本 ${r.textLength} 字",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(ZjSpace.md))

            // 两条路径并列
            Row(horizontalArrangement = Arrangement.spacedBy(ZjSpace.md)) {
                PathCard("本地规则", r.rule, Modifier.weight(1f))
                PathCard("云端大模型", r.cloud, Modifier.weight(1f))
            }

            // 规则路径的视野边界
            if (r.ruleEntities.isNotEmpty()) {
                Spacer(Modifier.height(ZjSpace.md))
                Text(
                    "规则路径识别到的领域词（${r.ruleEntities.size}）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    r.ruleEntities.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // 命题明细
            Spacer(Modifier.height(ZjSpace.md))
            Text(
                "本地规则抽出的命题",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (r.rule.claims.isEmpty()) {
                Text(
                    "（0 条）规则抽取在自由文本上不产出命题 —— 这正是它与云端路径的分工",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                r.rule.claims.forEach { c ->
                    Text(
                        "${if (c.expected) "✔" else "✘"} ${c.statement}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (r.cloud.claims.isNotEmpty()) {
                Spacer(Modifier.height(ZjSpace.md))
                Text(
                    "云端抽出的命题",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                r.cloud.claims.forEach { c ->
                    Text(
                        "${if (c.expected) "✔" else "✘"} ${c.statement}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            val allWarnings = r.rule.warnings + r.cloud.warnings
            if (allWarnings.isNotEmpty()) {
                Spacer(Modifier.height(ZjSpace.md))
                Text(
                    "解析与抽取警告",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                allWarnings.take(10).forEach { w ->
                    Text(
                        "· $w",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(ZjSpace.xxl))
        }
    }
}

@Composable
private fun PathCard(title: String, r: GraphComparisonRunner.PathResult, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(ZjRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
    ) {
        Column(Modifier.padding(ZjSpace.md)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(ZjSpace.xs))

            if (!r.success) {
                Text(
                    r.failure ?: "未执行",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }

            Metric("命题数", r.claimCount.toString())
            Metric("可用", "${r.usableCount}（${(r.usableRate * 100).toInt()}%）")
            Metric("带证据", r.withEvidenceCount.toString())
            Metric("带错点", r.withErrorKindCount.toString())
            Metric("带边", r.withEdgesCount.toString())
            Metric("耗时", "${r.elapsedMs} ms")
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}
