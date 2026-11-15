package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * 条件级探测表 `kg_condition_probe`。
 *
 * ## 为什么还需要一张表
 *
 * `kg_claim_state` 只记到**命题**粒度（「这条命题我错了两次」），
 * 但用户真正拨动的是**条件上的某个干预点**（「我把输入最后一维从 4 改成 5，以为仍然成立」）。
 * 「他错在哪条机制上」这个信息在命题粒度上是丢失的，只能在
 * `(triple_id, intervention_id)` 粒度上存。
 *
 * ## 主键与生命周期
 *
 * 主键是 `(triple_id, intervention_id)`：一个干预点只有一条记录，重复作答是覆盖 + 计数，
 * 不做逐题日志（与 `kg_claim_state` 同一套「回写是摘要而非明细」的取舍）。
 *
 * 三元组本身在 `assets/kg/graph.json` 里，不进数据库，因此**没有外键**；
 * 数据下架后残留的记录由 [com.zhijing.data.dao.KgConditionProbeDao.deleteOrphans] 清理。
 */
@Entity(
    tableName = "kg_condition_probe",
    primaryKeys = ["triple_id", "intervention_id"],
    indices = [
        Index(value = ["claim_id"]),
        Index(value = ["last_probed_at"])
    ]
)
internal data class KgConditionProbeEntity(

    /** 条件三元组 ID，如 `tri.ffn-linear-in-dim`。与 assets 里的 `triples[].id` 一致。 */
    @ColumnInfo(name = "triple_id")
    val tripleId: String,

    /** 干预点 ID，三元组内唯一，如 `int.in-x.4-to-5`。 */
    @ColumnInfo(name = "intervention_id")
    val interventionId: String,

    /** 所属命题 ID，广度侧按命题聚合用（`clm.*`）。 */
    @ColumnInfo(name = "claim_id")
    val claimId: String,

    /** 用户最近一次选择的后果（`Outcome` 的 name，如 `FAILS`）。 */
    @ColumnInfo(name = "predicted_option")
    val predictedOption: String,

    /**
     * 上一次的选择，用来讲「你原来猜 X，现在改成 Y」。
     *
     * 只留一格而不是逐次日志：理解路径第一版做的是**一条线**，
     * 要的是「改念前后」这个对比，不是完整作答历史。
     */
    @ColumnInfo(name = "previous_predicted_option")
    val previousPredictedOption: String? = null,

    /** 最近一次的真实后果（由本地求值器或预置答案给出）。 */
    @ColumnInfo(name = "correct_option")
    val correctOption: String,

    /**
     * 最近一次是否猜对。
     *
     * 只表示「这一次」，不能拿来算总错次数 —— 猜错后回头纠正，本列会变 true，
     * 而「在这里错过几次」是历史事实，看 [wrongAttempts]。
     */
    @ColumnInfo(name = "matched", defaultValue = "0")
    val matched: Boolean = false,

    /** 作答次数（同一条干预点反复拨动会累加）。 */
    @ColumnInfo(name = "attempts", defaultValue = "0")
    val attempts: Int = 0,

    /**
     * 猜错的累计次数。
     *
     * 为什么不能只留 [matched]：广度侧卡片要显示「在这里错过几次」，
     * 那是**发生过的事实**，用户后来答对了也不该被抹掉。
     */
    @ColumnInfo(name = "wrong_attempts", defaultValue = "0")
    val wrongAttempts: Int = 0,

    /** 最近作答时间（毫秒时间戳）。 */
    @ColumnInfo(name = "last_probed_at")
    val lastProbedAt: Long? = null,

    /**
     * 该干预点对应的误解摘要（来自 `misjudgements[].why/correction`）；答对时不覆盖。
     *
     * 存文本而不是外键：误解属于出厂图谱的出版物内容，改版后旧文本仍需可读。
     */
    @ColumnInfo(name = "misconception")
    val misconception: String? = null
)
