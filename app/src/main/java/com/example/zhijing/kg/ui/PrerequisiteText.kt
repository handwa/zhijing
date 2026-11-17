package com.example.zhijing.kg.ui

import com.example.zhijing.kg.PrerequisiteLink
import com.example.zhijing.kg.PrerequisiteSource

/**
 * 一条前置依赖的可读解释。
 *
 * 数据早就有（`PrerequisiteLink.reason` / `source` 从 assets 与生成图谱一路解析下来），
 * 缺的只是出口：节点详情里从未显示过「为什么要先学这张卡」。
 */
internal data class PrerequisiteLine(
    /** 前置卡的 id（点击定位、去重用）。 */
    val cardId: String,
    /** 前置卡的标题；卡片被删时回退成 id 尾段，不留空。 */
    val title: String,
    /** 「必须前置」/「推荐前置」。 */
    val kindLabel: String,
    /** 为什么存在这条前置；模型没给就是 null，不伪造。 */
    val reason: String?,
    /** 这条前置的可信度。 */
    val sourceLabel: String,
)

/**
 * 前置解释的组装：标题解析 + 来源标签 + 缺失回退。
 *
 * 抽成纯函数是为了能直接断言文案口径 —— 来源标签写错，用户会把模型推断当课程事实。
 */
internal object PrerequisiteText {

    /** 来源标签。刻意用中性词，不写「示例数据」：这批是逐条写的事实判断。 */
    fun sourceLabel(source: PrerequisiteSource): String = when (source) {
        PrerequisiteSource.AUTHORED_HUMAN -> "人工书写"
        PrerequisiteSource.AUTHORED_MODEL -> "未经核实"
    }

    /**
     * @param cardTitles cardId → 标题；随图谱缓存一次，别在渲染里现算
     */
    fun of(link: PrerequisiteLink, cardTitles: Map<String, String>): PrerequisiteLine {
        val title = cardTitles[link.from]?.takeIf { it.isNotBlank() }
            ?: link.from.substringAfter("crd.", link.from).ifBlank { link.from }

        return PrerequisiteLine(
            cardId = link.from,
            title = title,
            kindLabel = link.kind.label,
            reason = link.reason.trim().takeIf { it.isNotEmpty() },
            sourceLabel = sourceLabel(link.source),
        )
    }
}
