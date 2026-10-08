package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zhijing.data.model.PetPhase

/**
 * 说明书 3.4.7 养成系动物状态表 pet_status_table。
 *
 * 说明书未给该表定义主键，而 Room 要求每张表必须有主键。这里补了一个固定的
 * 单例主键 [SINGLETON_ID]（取值恒为 1），使全表始终只有一行状态，
 * 其余字段与说明书完全一致。
 */
@Entity(tableName = "pet_status_table")
internal data class PetStatusEntity(
    /** 数据层补充的单例主键，非说明书字段。 */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLETON_ID,

    /** 0 种子 / 1 萌芽 / 2 生长 / 3 绽放。 */
    @ColumnInfo(name = "phase", defaultValue = "0")
    val phase: PetPhase = PetPhase.SEED,

    /** 收藏总数，决定阶段。 */
    @ColumnInfo(name = "total_contents", defaultValue = "0")
    val totalContents: Int = 0,

    /** 活跃天数。 */
    @ColumnInfo(name = "active_days", defaultValue = "0")
    val activeDays: Int = 0,

    /** 已解锁的装饰。 */
    @ColumnInfo(name = "accessories")
    val accessories: String? = null,

    /** 上次展示时间（毫秒时间戳）。 */
    @ColumnInfo(name = "last_display_time", defaultValue = "0")
    val lastDisplayTime: Long = 0
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
