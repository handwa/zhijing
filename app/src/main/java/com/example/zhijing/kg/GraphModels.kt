package com.example.zhijing.kg

import com.example.zhijing.kg.depth.ConditionTriple
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
/**
 * 卡片之间的一条前置关系。
 *
 * **与交叉链接（[CrossLink]）是两种东西**：
 *
 * | | 交叉链接 | 前置关系 |
 * |---|---|---|
 * | 含义 | 两张卡「有关」 | 学这张卡**之前**要先学那张 |
 * | 方向 | 无向 | 有向（`from` 必须先于 `to`） |
 * | 来源 | 词共现，本地算 | 模型推断 |
 * | 视觉 | 细虚线 | 必须 = 实线，推荐 = 虚线 |
 *
 * @param from 前置卡的 ID（先学这张）
 * @param to 依赖卡的 ID（后学这张）
 * @param kind 必须 / 推荐
 * @param reason 为什么存在这条前置（模型给的一句话，可为空）
 * @param source 这条前置是人工审定还是模型推断 —— 决定 UI 要不要标「未经核实」
 */
data class PrerequisiteLink(
    val from: String,
    val to: String,
    val kind: PrerequisiteKind,
    val reason: String = "",
    val source: PrerequisiteSource = PrerequisiteSource.AUTHORED_MODEL
)

/**
 * 前置关系的来源。
 *
 * 系统不提供课程资料，所以模型给出的前置只是**推断**；出厂图谱里那批是人工撰写并
 * 逐条核对过的。两者在 UI 上必须能区分，否则用户会把推断当课程事实。
 *
 * 缺省取 [AUTHORED_MODEL]：来源不明时按「未经核实」处理，宁可多一句提示。
 */
enum class PrerequisiteSource(val wire: String) {
    /** 人工撰写并核对（出厂图谱）。 */
    AUTHORED_HUMAN("AUTHORED_HUMAN"),

    /** 模型推断或本地兜底补齐，未经人工核实。 */
    AUTHORED_MODEL("AUTHORED_MODEL");

    companion object {
        fun fromWire(raw: String?): PrerequisiteSource =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: AUTHORED_MODEL
    }
}

/** 前置的强度。 */
enum class PrerequisiteKind(val wire: String, val label: String) {
    /** 不先学就学不懂。实线。 */
    REQUIRED("required", "必须前置"),

    /** 有帮助，但不学也能跟上。虚线。 */
    RECOMMENDED("recommended", "推荐前置");

    companion object {
        fun fromWire(wire: String?): PrerequisiteKind =
            entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) } ?: RECOMMENDED
    }
}

/**
 * 广度视图的一张卡片（渲染用，已与运行时数据合并）。
 *
 * 与 assets 里的 `card` 相比多出：
 * - [claims]：已合并出厂命题与已确认的云端增量命题；
 * - [badgeStatus]：从 Room 派生的状态（广度侧不存储状态，只派生）；
 * - [prerequisites]：卡片级前置关系（必须 / 推荐）。
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
    val badgeStatus: ProbeStatus? = null,
    /** 前置关系：本卡**依赖**的卡片（`to` 指向本卡）。 */
    val prerequisites: List<PrerequisiteLink> = emptyList(),

    /**
     * 条件级事实（回写广度）。
     *
     * 卡片上显示的是「在这里错过几次、错在哪条机制」，**不是掌握度百分比**：
     * 百分比既不驱动行动，也容易把「只答过一次」说成「80% 掌握」。
     */
    val conditionFacts: List<ConditionFact> = emptyList()
) {
    /** 是否可下钻：只有挂了命题的叶子卡才有下钻入口。 */
    val descendable: Boolean get() = claims.isNotEmpty()

    /** 易错点注记（回写价值最高的部分）。 */
    val misconceptionNotes: List<String>
        get() = claims.flatMap { it.probe.misconceptions }.distinct()
}

/**
 * 一条「在这里错过」的事实。
 *
 * 由 `kg_condition_probe` 派生，**不存进图谱数据**：状态是运行时数据，
 * 图谱是出版物；两者混在一起就又会回到「广度侧存状态」的老问题。
 */
data class ConditionFact(
    val claimId: String,
    val interventionId: String,
    /** 干预动作，如「把输入最后一维从 4 改成 5」。 */
    val action: String,
    /** 这条干预所属的条件，如「输入最后一维必须等于权重输入维度」。 */
    val statement: String,
    /** 错过的累计次数。 */
    val wrongCount: Int,
    /** 最近一次留下的误解摘要。 */
    val misconception: String? = null,
)

/**
 * 理解路径的一步：**提问 → 预测 → 揭示**（对上了就是「改念」）。
 *
 * 第一版刻意只做一条线：一个干预点只留最近一次预测，不做分支树。
 * 数据来自 `kg_condition_probe`（每点一行，重复作答覆盖 + 计数）。
 */
data class ConditionPathStep(
    val interventionId: String,
    /** 题目正文（干预动作）。 */
    val action: String,
    /** 题目所属条件。 */
    val statement: String,
    /** 用户最近一次的选择（中文标签）。 */
    val predicted: String,
    /** 上一次的选择；首次作答为 null。 */
    val previousPredicted: String? = null,
    /** 实际后果（本地求值或预置答案）。 */
    val actual: String,
    /** 最近一次是否对上。 */
    val matched: Boolean,
    val attempts: Int,
    /** 历史上错过的次数；>0 且 [matched] 为 true 才是真正的「改念」。 */
    val wrongAttempts: Int,
) {
    /** 是否已经改过来了：错过、但最近一次答对。 */
    val revised: Boolean get() = wrongAttempts > 0 && matched
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
    val bundled: Boolean,
    /**
     * 理解路径（第一版，单线）：按最近作答倒序的「预测 → 揭示」。
     *
     * 只包含可拨条件（`triples` 里有的干预点）；云端增量命题没有三元组，恒为空。
     */
    val conditionPath: List<ConditionPathStep> = emptyList()
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
    val edges: Map<String, GraphEdge>,
    /**
     * 深度三元组（条件—机制—反事实）。
     *
     * 缺省为空：旧图谱或生成图谱没有这个字段时，图谱页照常渲染，只是卡片下钻时
     * 没有「先猜一下」这一步，直接进入原来的判断题面板。
     */
    val triples: List<ConditionTriple> = emptyList()
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
