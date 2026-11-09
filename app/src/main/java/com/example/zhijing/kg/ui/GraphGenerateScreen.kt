package com.example.zhijing.kg.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.zhijing.ui.theme.ZjRadius
import com.example.zhijing.ui.theme.ZjSpace

/**
 * 生成图谱页：输入一个主题或一段文本，由云端切块并落库。
 *
 * ## 为什么需要"原文"而不只是"主题名"
 *
 * 模型要做的是**切分已有内容**，不是凭空生成知识。只给一个主题名（如"线性代数"），
 * 它只能凭训练记忆编造，产物不可信。
 *
 * 因此这一页要求用户提供**一段文本**；主题名只是显示用的标签。
 * 这与 `cs-math-skill-tree` 的差别是本质的：它有 CS2023 作为骨架，你没有资料就只能靠模型。
 *
 * 如果用户只给主题名，UI 会提示补充内容 —— 而不是硬生成一份编造的图谱。
 */
@Composable
fun GraphGenerateScreen(
    topicName: String,
    onTopicNameChange: (String) -> Unit,
    content: String,
    onContentChange: (String) -> Unit,
    running: Boolean,
    hasCloud: Boolean,
    message: String?,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ZjSpace.pageHorizontal),
    ) {
        Text(
            "生成新图谱",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(ZjSpace.xs))
        Text(
            "给一个主题，AI 会规划它的结构并撰写内容。\n" +
                "如果贴上原文，AI 就只做切分，内容会有出处。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(ZjSpace.md))

        OutlinedTextField(
            value = topicName,
            onValueChange = onTopicNameChange,
            label = { Text("主题") },
            placeholder = { Text("例如：线性代数入门") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(ZjRadius.sm),
        )

        Spacer(Modifier.height(ZjSpace.sm))

        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            label = { Text("补充说明（可选）") },
            placeholder = {
                Text(
                    "想更具体？写一句说明，例如：\n" +
                        "「只讲行列式和矩阵，给初学的人看」\n\n" +
                        "或者直接贴一段原文（不超过 4000 字）——\n" +
                        "贴了原文，AI 就不会自己编内容，只做切分。"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 170.dp),
            shape = RoundedCornerShape(ZjRadius.sm),
        )

        Spacer(Modifier.height(ZjSpace.xs))
        if (content.isNotBlank()) {
            Text(
                if (looksLikeSource(content)) {
                    "检测到原文（${content.length} 字）→ 内容将有出处（可溯源）"
                } else {
                    "当作补充说明 → 内容由 AI 撰写（未核实）"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "未提供原文 → 内容由 AI 撰写，会标注「未核实」",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(ZjSpace.md))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (!hasCloud) "需要先在设置里配置云端大模型"
                else "生成后会出现在「知识图谱」页的主题列表里",
                style = MaterialTheme.typography.labelSmall,
                color = if (hasCloud) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = onGenerate,
                enabled = !running && hasCloud && topicName.isNotBlank(),
            ) {
                Text(if (running) "生成中…" else "生成")
            }
        }

        if (running) {
            Spacer(Modifier.height(ZjSpace.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }

        message?.let { msg ->
            Spacer(Modifier.height(ZjSpace.lg))
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (msg.startsWith("失败") || msg.startsWith("需要")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * 粗略判断用户贴的是「原文」还是「一句补充说明」。
 *
 * 判定标准刻意简单，因为**判错的代价不对称**：
 * - 把长文误判成说明 → 走生成式，模型会重写用户的内容（差）
 * - 把说明误判成原文 → 走结构化，模型只在短句里切块（也不理想，但不至于改写用户资料）
 *
 * 所以宁可偏向"当成原文"：只要超过 200 字**或**含多个句子分隔，就按原文处理。
 */
internal fun looksLikeSource(text: String): Boolean {
    val t = text.trim()
    if (t.length >= 200) return true
    // 句子/段落分隔出现 2 次以上，多半是成段的内容
    val separators = t.count { it == '。' || it == '\n' || it == '；' }
    return separators >= 2
}
