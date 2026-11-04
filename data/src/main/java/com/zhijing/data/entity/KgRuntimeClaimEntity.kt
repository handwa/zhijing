package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * 运行时命题表 `kg_runtime_claim`。
 *
 * 只存**运行时才出现**的命题：云端按 `prompts/extract_claims.md` 生成的候选，
 * 以及用户在端上录入的命题。出厂命题在 `assets/kg/graph.json`，不进本表。
 *
 * 本表的 `claim_id` 与出厂命题共用同一套 ID 规范（由 statement 推导），
 * 因此广度/深度两侧的渲染代码不需要区分来源；「是否等价于某条出厂命题」
 * 由 `kg_claim_merge` 表回答。
 *
 * 生命周期：生成 → PENDING（不进入任何视图）→ 用户确认 CONFIRMED 或否决 REJECTED。
 * 否决的记录保留，用于后续生成时做去重，避免同一条垃圾命题反复出现。
 */
@Entity(
    tableName = "kg_runtime_claim",
    primaryKeys = ["claim_id"],
    indices = [
        Index(value = ["card_id"]),
        Index(value = ["review_status"]),
        Index(value = ["source"]),
        Index(value = ["review_status", "card_id"])
    ]
)
internal data class KgRuntimeClaimEntity(

    /** 命题 ID（`clm.*`），由 statement 推导。 */
    @ColumnInfo(name = "claim_id")
    val claimId: String,

    /** 一句话可判真假的断言。 */
    @ColumnInfo(name = "statement")
    val statement: String,

    /** 判断面板标准答案：该命题是否成立。1 = 成立，0 = 不成立。 */
    @ColumnInfo(name = "expected")
    val expected: Boolean,

    /** 成立条件 / 边界。 */
    @ColumnInfo(name = "conditions", defaultValue = "'[]'")
    val conditions: List<String> = emptyList(),

    /** 撞错解释。 */
    @ColumnInfo(name = "explanation")
    val explanation: String? = null,

    /** 撞错时暴露的误解。 */
    @ColumnInfo(name = "misconception")
    val misconception: String? = null,

    /** 证据原文片段，用于溯源与「evidence 逐字命中」的验收检查。 */
    @ColumnInfo(name = "evidence")
    val evidence: String? = null,

    /** 主归属卡 ID（`crd.*` 或大纲路径对应的卡）。 */
    @ColumnInfo(name = "card_id")
    val cardId: String,

    /** 大纲路径，广度侧派生用。 */
    @ColumnInfo(name = "outline_path", defaultValue = "''")
    val outlinePath: String = "",

    /** 命题来源：bundled / cloud / user。 */
    @ColumnInfo(name = "source", defaultValue = "'cloud'")
    val source: String = "cloud",

    /** 确认状态：pending / confirmed / rejected。 */
    @ColumnInfo(name = "review_status", defaultValue = "'pending'")
    val reviewStatus: String = "pending",

    /** 生成该命题的服务商标识，如 `deepseek`；规则抽取为 null。 */
    @ColumnInfo(name = "provider_id")
    val providerId: String? = null,

    /** 命题的文本向量（小端 float 序列化），用于同义合并。可为空：未做嵌入时退回字符级比对。 */
    @ColumnInfo(name = "embedding")
    val embedding: ByteArray? = null,

    /** 生成时间（毫秒时间戳）。 */
    @ColumnInfo(name = "created_time")
    val createdTime: Long,

    /** 用户确认 / 否决时间；未审核为 null。 */
    @ColumnInfo(name = "reviewed_time")
    val reviewedTime: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KgRuntimeClaimEntity) return false
        return claimId == other.claimId &&
            statement == other.statement &&
            expected == other.expected &&
            conditions == other.conditions &&
            explanation == other.explanation &&
            misconception == other.misconception &&
            evidence == other.evidence &&
            cardId == other.cardId &&
            outlinePath == other.outlinePath &&
            source == other.source &&
            reviewStatus == other.reviewStatus &&
            providerId == other.providerId &&
            createdTime == other.createdTime &&
            reviewedTime == other.reviewedTime &&
            (embedding?.contentEquals(other.embedding ?: ByteArray(0)) ?: (other.embedding == null))
    }

    override fun hashCode(): Int {
        var result = claimId.hashCode()
        result = 31 * result + statement.hashCode()
        result = 31 * result + expected.hashCode()
        result = 31 * result + cardId.hashCode()
        result = 31 * result + reviewStatus.hashCode()
        return result
    }
}
