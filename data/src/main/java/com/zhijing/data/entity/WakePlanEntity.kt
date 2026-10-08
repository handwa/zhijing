package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zhijing.data.model.WakeMode

/**
 * 说明书 3.4.5 记忆唤醒计划表 wake_plan_table。
 *
 * `enabled` 在库内是 INTEGER，Kotlin 侧用 Boolean 表达（Room 映射到 0/1）。
 */
@Entity(
    tableName = "wake_plan_table",
    indices = [Index(value = ["enabled"])]
)
internal data class WakePlanEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 是否启用。 */
    @ColumnInfo(name = "enabled", defaultValue = "0")
    val enabled: Boolean = false,

    /** 周期（如 daily/weekly）。 */
    @ColumnInfo(name = "cycle_type")
    val cycleType: String? = null,

    /** 触发时刻（如 20:00）。 */
    @ColumnInfo(name = "trigger_time")
    val triggerTime: String? = null,

    /** 范围：全部或指定主题。 */
    @ColumnInfo(name = "scope")
    val scope: String? = null,

    /** question（提问）或 statement（直述要点）。 */
    @ColumnInfo(name = "mode")
    val mode: WakeMode = WakeMode.STATEMENT,

    /** 每次条数。 */
    @ColumnInfo(name = "daily_question_num", defaultValue = "0")
    val dailyQuestionNum: Int = 0,

    /** 下次执行时间（毫秒时间戳）。 */
    @ColumnInfo(name = "next_run_time", defaultValue = "0")
    val nextRunTime: Long = 0
)
