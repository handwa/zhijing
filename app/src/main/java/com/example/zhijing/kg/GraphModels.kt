package com.example.zhijing.kg

import com.zhijing.data.model.ClaimProbeState
import com.zhijing.data.model.ClaimSource
import com.zhijing.data.model.ProbeStatus

/**
 * 广度视图的一张卡片（渲染用，已与运行时数据合并）。
 *
 * 与 assets 里的 `card` 相比多出：
 * - [claims]：已合并出厂命题与已确认的云端增量命题；
 * - [probe]：从 Room 派生的状态（广度侧不存储状态，只派生）。
 */
data class GraphCard(
    val id: String,
    val title: String,
    val outlinePath: String,
    val summary: String,
    val body: String,
    val chain: List<String> = emptyList(),
    val crossLinks: List<CrossLink> = emptyList(),
    val claims: List<GraphClaim> = emptyList(),
    /**
     * 卡片角标。一张卡可能挂多条命题，取「最需要用户注意」的那一个状态：
     * 易混淆 > 未探测 > 已验证 > 已掌握。
     *
     * 这样用户扫一眼大纲就知道：哪张卡里还有易错没解除、哪张卡还没碰过。
     */
    val badgeStatus: ProbeStatus? = null
) {
    /** 是否可下钻：只有挂了命题的叶子卡才有下钻入口。 */
    val descendable: Boolean get() = claims.isNotEmpty()

    /** 易错点注记（回写价值最高的部分）。 */
    val misconceptionNotes: List<String>
        get() = claims.flatMap { it.probe.misconceptions }.distinct()
}

/** 交叉链接。 */
data class CrossLink(
    val target: String,
    val label: String,
    val weight: Double,
    val reason: String
)

/**
 * 深度视图的一条命题（渲染用，已与运行时数据合并）。
 *
 * [source] 区分出厂命题与云端生成：UI 需要给云端生成的命题打标，
 * 并允许用户删除（出厂命题不允许删）。
 */
data class GraphClaim(
    val id: String,
    val statement: String,
    val expected: Boolean,
    val conditions: List<String>,
    val evidence: String,
    val cardId: String,
    val outlinePath: String,
    val question: String,
    val explanation: String,
    val errorKinds: List<ExtractedErrorKind>,
    val misconception: String,
    val edgeIds: List<String>,
    val probe: ClaimProbeState,
    val source: ClaimSource,
    /** 是否来自 assets（出厂命题）。`false` 表示运行时增量，可被用户删除。 */
    val bundled: Boolean
)

/** 一条边（供深度视图展示「依赖与影响」）。 */
data class GraphEdge(
    val id: String,
    val from: String,
    val to: String,
    val type: String,
    val kind: String,
    val premise: String,
    val boundary: String,
    val counterexample: String
)

/** 大纲节点。 */
data class GraphOutlineNode(
    val id: String,
    val title: String,
    val level: Int,
    val parentId: String?,
    val path: String,
    val order: Int,
    val children: List<GraphOutlineNode> = emptyList(),
    val cardIds: List<String> = emptyList()
)

/**
 * 合并后的完整图谱：广度 + 深度一次给全。
 *
 * 这是 UI 唯一需要的数据入口 —— 页面只认这个对象，不认 assets 也不认 Room。
 */
data class MergedGraph(
    val schemaVersion: String,
    val generatedAt: String,
    val outline: List<GraphOutlineNode>,
    val cards: List<GraphCard>,
    val claims: Map<String, GraphClaim>,
    val edges: Map<String, GraphEdge>
) {
    /** 状态汇总：按四态计数，仅统计图谱里实际存在的命题。 */
    val statusSummary: Map<ProbeStatus, Int>
        get() {
            val out = LinkedHashMap<ProbeStatus, Int>()
            ProbeStatus.entries.forEach { out[it] = 0 }
            claims.values.forEach { out[it.probe.probeStatus] = (out[it.probe.probeStatus] ?: 0) + 1 }
            return out
        }

    /** 某张卡下需要优先重读的命题（易混淆优先）。 */
    fun claimsByPriority(cardId: String): List<GraphClaim> =
        claims.values
            .filter { it.cardId == cardId }
            .sortedWith(CLAIM_PRIORITY)

    /** 云端生成、可被用户删除的命题数。 */
    val runtimeClaimCount: Int get() = claims.values.count { !it.bundled }

    companion object {
        /** 易错优先：与 `kg/web/app.js` 的下钻排序一致。 */
        val CLAIM_PRIORITY: Comparator<GraphClaim> = compareBy<GraphClaim> {
            when (it.probe.probeStatus) {
                ProbeStatus.CONFUSED -> 0
                ProbeStatus.UNPROBED -> 1
                ProbeStatus.VERIFIED -> 2
                ProbeStatus.MASTERED -> 3
            }
        }.thenByDescending { it.probe.attempts }

        /** 卡片角标优先级：易混淆 > 未探测 > 已验证 > 已掌握。 */
        private val BADGE_PRIORITY = listOf(
            ProbeStatus.CONFUSED,
            ProbeStatus.UNPROBED,
            ProbeStatus.VERIFIED,
            ProbeStatus.MASTERED
        )

        internal fun badgeOf(statuses: List<ProbeStatus>): ProbeStatus? {
            if (statuses.isEmpty()) return null
            return BADGE_PRIORITY.firstOrNull { it in statuses }
        }
    }
}
