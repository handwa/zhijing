package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zhijing.data.entity.WakePlanEntity
import kotlinx.coroutines.flow.Flow

/** wake_plan_table 的数据访问接口（说明书 3.4.5，记忆唤醒）。 */
@Dao
internal interface WakePlanDao {

    @Insert
    suspend fun insert(entity: WakePlanEntity): Long

    @Query("SELECT * FROM wake_plan_table WHERE id = :id")
    suspend fun findById(id: Long): WakePlanEntity?

    @Query("SELECT * FROM wake_plan_table ORDER BY id ASC")
    fun observeAll(): Flow<List<WakePlanEntity>>

    /** WorkManager 定时任务取启用的计划。 */
    @Query("SELECT * FROM wake_plan_table WHERE enabled = 1 ORDER BY next_run_time ASC")
    suspend fun findEnabled(): List<WakePlanEntity>

    @Query("UPDATE wake_plan_table SET enabled = :enabled, next_run_time = :nextRunTime WHERE id = :id")
    suspend fun updateSchedule(id: Long, enabled: Boolean, nextRunTime: Long)

    @Query("DELETE FROM wake_plan_table WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM wake_plan_table")
    suspend fun deleteAll()
}
