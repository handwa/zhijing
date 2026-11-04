package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * 命题探测状态表 `kg_claim_state`。
 *
 * 对应设计文档「回写是摘要而非明细」：本表只存**收敛后的四字段摘要**
 * （probe_status / last_probed_at / misconceptions / confidence）加上深度侧自用的
 * 计数列（attempts / correct / streak）。逐题作答日志不进这里。
 *
 * 主键直接是命题 ID，与 `assets/kg/graph.json` 里的 `clm.*` 同一个字符串，
 * 因此不存在「ID 映射表」这种额外的真相源。
 *
 * 为什么不是外键：出厂命题在 assets 里，不在数据库中，无法建外键；
 * 命题被下架后残留的状态由 [KgClaimStateDao.deleteOrphans] 清理。
 */
@Entity(
    tableName = "kg_claim_state",
    primaryKeys = ["claim_id"],
    indices = [
        androidx.room.Index(value = ["probe_status"]),
        androidx.room.Index(value = ["last_probed_at"])
    ]
)
internal data class KgClaimStateEntity(

    /** 命题 ID，如 `clm.arch.ffn.relu-shape.6092`。 */
    @ColumnInfo(name = "claim_id")
    val claimId: String,

    /** 四态：unprobed / verified / mastered / confused。 */
    @ColumnInfo(name = "probe_status", defaultValue = "'unprobed'")
    val probeStatus: String = "unprobed",

    /** 最近一次探测时间（毫秒时间戳）；从未探测为 null。 */
    @ColumnInfo(name = "last_probed_at")
    val lastProbedAt: Long? = null,

    /** 误解列表。刻意不建子表：回写只需要「最近几条」，不需要行级查询。 */
    @ColumnInfo(name = "misconceptions", defaultValue = "'[]'")
    val misconceptions: List<String> = emptyList(),

    /** 置信度 0..1，仅展示用。 */
    @ColumnInfo(name = "confidence", defaultValue = "0")
    val confidence: Float = 0f,

    /** 作答总次数（深度侧留存，不回写广度侧）。 */
    @ColumnInfo(name = "attempts", defaultValue = "0")
    val attempts: Int = 0,

    /** 判对次数（深度侧留存，不回写广度侧）。 */
    @ColumnInfo(name = "correct", defaultValue = "0")
    val correct: Int = 0,

    /** 当前连续判对次数；判错清零。收敛判定依赖它，会话可中断。 */
    @ColumnInfo(name = "streak", defaultValue = "0")
    val streak: Int = 0
)
