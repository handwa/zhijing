package com.zhijing.data.model

/**
 * 知识图谱在**运行时**才产生的数据模型。
 *
 * 分工（对应 `docs/knowledge-graph-design(1).md` 第五节「核心约束」）：
 *
 * - **出厂命题**（outline / card / claim / edge）随 APK 打包在
 *   `assets/kg/graph.json`，只读，不在本文件表示；
 * - **运行时数据**（判断状态、云端增量命题、同义合并）都是可写的，落在 Room，
 *   由本文件描述领域对象。
 *
 * 为什么出厂命题不进 Room：它是出版物，不是用户数据。进 Room 只会引入
 * 「两份真相」与迁移负担，而离线可读由 assets 已经保证。
 */

/**
 * 深度侧的探测状态，四态。
 *
 * 对应 graph.json 的 `probe_status` 枚举。设计文档特别强调：
 * **「已探测」不等于「已掌握」**，撞错之后存在「已纠正」与「仍易错」两种状态，
 * 因此 [CONFUSED] 与 [VERIFIED] / [MASTERED] 必须分开表达。
 */
enum class ProbeStatus(val wire: String) {
    /** 从未判断过。 */
    UNPROBED("unprobed"),

    /** 判对过，但尚未收敛（见 [ClaimProbeState.streak]）。 */
    VERIFIED("verified"),

    /** 连续判对并收敛。 */
    MASTERED("mastered"),

    /** 已探测，但易错未解除。 */
    CONFUSED("confused");

    companion object {
        fun fromWire(wire: String?): ProbeStatus =
            entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) } ?: UNPROBED
    }
}

/**
 * 命题的来源。
 *
 * 这是本套表对「ID 漂移」的一道保险：云端生成的命题标 [CLOUD]，
 * 用户改写过的标 [USER]，出厂命题为 [BUNDLED]。回写与清理时按来源分别处理，
 * 避免把用户自己写的命题当成云端垃圾数据清掉。
 */
enum class ClaimSource(val wire: String) {
    /** 随 APK 打包的出厂命题（只出现在 assets，被本表引用时使用该值）。 */
    BUNDLED("bundled"),

    /** 云端模型按 `prompts/extract_claims.md` 生成的候选命题。 */
    CLOUD("cloud"),

    /** 用户在端上手动录入或修改后保存的命题。 */
    USER("user");

    companion object {
        fun fromWire(wire: String?): ClaimSource =
            entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) } ?: CLOUD
    }
}

/**
 * 候选命题的确认状态。
 *
 * 云端生成必须是「先落库 → 再确认 → 才进入广度图谱」，
 * 因为小模型产出的命题质量不可控，直接进图谱会污染阅读体验。
 */
enum class ClaimReviewStatus(val wire: String) {
    /** 刚生成，尚未展示给用户，不进入任何视图。 */
    PENDING("pending"),

    /** 用户已确认，进入广度图谱与深度判断。 */
    CONFIRMED("confirmed"),

    /** 用户否决，保留记录以便后续去重时不重复生成同类内容。 */
    REJECTED("rejected");

    companion object {
        fun fromWire(wire: String?): ClaimReviewStatus =
            entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) } ?: PENDING
    }
}

/**
 * 同义合并的记录。
 *
 * **这是进 Room 后最关键的一张表。** 命题 ID 由 `sha1(规范化 statement)[:4]` 推导，
 * 云端每次生成的措辞略有不同，ID 就会变；判断状态挂在旧 ID 上，于是同一命题
 * 在库里出现两条、状态丢失。
 *
 * [ClaimMerge] 用本地 bge 向量比对，把「新生成 ID」映射到「已有 ID」，
 * 之后所有读写都先过这张表，等价于给 ID 做了一层稳定的重定向。
 */
data class ClaimMerge(
    /** 新生成（或云端重写）的命题 ID。 */
    val sourceClaimId: String,

    /** 合并到的目标命题 ID，通常是已有的出厂命题 ID。 */
    val targetClaimId: String,

    /** 触发合并时的余弦相似度，用于复核与调参。 */
    val similarity: Float,

    /** 合并依据：hash（ID 完全相同）/ vector（向量相似）/ manual（人工指定）。 */
    val basis: String,

    /** 建立时间（毫秒时间戳）。 */
    val createdTime: Long
)

/**
 * 一条命题的探测状态（深度侧收敛后的摘要）。
 *
 * 字段与设计文档「回写：只回写收敛后的摘要」一节严格一致：
 * `probe_status` / `last_probed_at` / `misconceptions` / `confidence` 四个字段回写广度侧；
 * `attempts` / `correct` / `streak` 仅在深度侧留存，**不回写**。
 */
data class ClaimProbeState(
    /** 命题 ID，与 assets 图谱中的 `clm.*` 一一对应。 */
    val claimId: String,

    val probeStatus: ProbeStatus = ProbeStatus.UNPROBED,

    /** 最近一次探测时间（毫秒时间戳）；从未探测为 null。 */
    val lastProbedAt: Long? = null,

    /** 撞错时暴露的误解，回写广度侧做「易错点注记」。 */
    val misconceptions: List<String> = emptyList(),

    /** 判断置信度 0..1，由 correct / attempts 推导，仅作展示，不参与判定。 */
    val confidence: Float = 0f,

    /** 作答总次数（深度侧留存）。 */
    val attempts: Int = 0,

    /** 判对次数（深度侧留存）。 */
    val correct: Int = 0,

    /** 当前连续判对次数；判错清零。收敛判定依赖它。 */
    val streak: Int = 0
) {
    /** 是否已经产生过回写价值（广度侧需要显示角标）。 */
    val hasWriteback: Boolean get() = probeStatus != ProbeStatus.UNPROBED
}

/**
 * 一个「条件 × 干预点」上的作答记录（条件级状态）。
 *
 * ## 与 [ClaimProbeState] 的分工
 *
 * - [ClaimProbeState]：**命题**粒度，回答「这条命题我掌握没有」，用于广度侧角标与复习队列；
 * - [ConditionProbeState]：**干预点**粒度，回答「我把哪条边界想错了」，用于把事实写回卡片。
 *
 * 命题粒度存不下「错在哪条机制」：同一条命题下可能有三个条件、六个干预点，
 * 收敛成一个 CONFUSED 之后，用户根本不知道自己错在「内维要对齐」还是「softmax 沿哪根轴」。
 *
 * ## 选项为什么是字符串
 *
 * 选项集合由 app 层的 `Outcome` 定义（仍然成立 / 变窄 / 变成另一条 / 不成立），
 * 而 `:data` 不依赖 app。存 wire 值而不是自建一份枚举，是为了让选项集合以后扩充时
 * **旧记录仍然可读**（未知值只是渲染不出来，不会解析失败）。
 */
data class ConditionProbeState(
    /** 条件三元组 ID（`tri.*`）。 */
    val tripleId: String,

    /** 干预点 ID，三元组内唯一。 */
    val interventionId: String,

    /** 所属命题 ID（`clm.*`），广度侧按命题聚合用。 */
    val claimId: String,

    /** 用户最近一次的选择（`Outcome` 的 name）。 */
    val predictedOption: String,

    /** 上一次的选择；首次作答为 null。用来讲「原来猜 X，现在改成 Y」。 */
    val previousPredictedOption: String? = null,

    /** 最近一次的真实后果（本地求值或预置答案）。 */
    val correctOption: String,

    /** 最近一次是否猜对。 */
    val matched: Boolean,

    /** 作答次数。 */
    val attempts: Int = 0,

    /** 猜错的累计次数（答对后也不清零）。 */
    val wrongAttempts: Int = 0,

    /** 最近作答时间；从未作答为 null。 */
    val lastProbedAt: Long? = null,

    /** 该干预点上的误解摘要；答对时不覆盖。 */
    val misconception: String? = null
)

/**
 * 一条命题下**所有**条件级记录的聚合，广度侧卡片直接用这个渲染事实。
 *
 * 刻意不含百分比 / 掌握度：设计文档要求卡片显示「在这里错过几次、错在哪条机制」，
 * 百分比既不驱动行动，也容易把「只答过一次」说成「80% 掌握」。
 */
data class ConditionProbeSummary(
    val claimId: String,

    /** 该命题下所有干预点的作答总次数。 */
    val attempts: Int = 0,

    /** 猜错的累计次数，即卡片上的「在这里错过几次」。 */
    val wrongCount: Int = 0,

    /**
     * 错过的干预点 ID（去重，最近错过的在前）。
     *
     * 只给 ID，不给条件文本：条件文本属于出厂图谱，由 UI 回查 `graph.json`，
     * 避免数据层再存一份会过期的文案。
     */
    val missedInterventionIds: List<String> = emptyList(),

    /** 对应干预点上的误解摘要（去重，最近在前）。 */
    val misconceptions: List<String> = emptyList(),

    /** 最近一次作答时间。 */
    val lastProbedAt: Long? = null
) {
    /** 是否有可回写的事实（一次都没答过时卡片保持干净）。 */
    val hasWriteback: Boolean get() = attempts > 0
}

/**
 * 云端生成 / 用户录入的增量命题。
 *
 * 出厂命题不在此列；这里只放「运行时才出现」的命题。
 * [embedding] 用于同义去重，写入时由调用方提供（本地 bge 向量）。
 */
data class RuntimeClaim(
    /** 命题 ID，`clm.*` 形式，由 statement 推导，必须与 assets 的 ID 规范一致。 */
    val claimId: String,

    /** 一句话可判真假的断言。 */
    val statement: String,

    /** 判断面板的标准答案：该命题是否成立。 */
    val expected: Boolean,

    /** 成立条件 / 边界。 */
    val conditions: List<String>,

    /** 撞错解释。 */
    val explanation: String?,

    /** 撞错时暴露的误解。 */
    val misconception: String?,

    /** 证据：原文片段，用于溯源。 */
    val evidence: String?,

    /** 主归属卡 ID（`crd.*`），决定广度视图里挂在哪张卡下。 */
    val cardId: String,

    /** 大纲路径，便于广度侧派生。 */
    val outlinePath: String,

    val source: ClaimSource,

    val reviewStatus: ClaimReviewStatus,

    /** 生成该命题的服务商标识，如 `deepseek`；本地规则抽取为 null。 */
    val providerId: String?,

    val createdTime: Long,

    /** 用户最后一次确认 / 否决的时间。 */
    val reviewedTime: Long?
)
