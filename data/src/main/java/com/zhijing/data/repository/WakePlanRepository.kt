package com.zhijing.data.repository

import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.mapper.toDomain
import com.zhijing.data.mapper.toEntity
import com.zhijing.data.model.WakePlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 记忆唤醒计划门面（说明书 3.4.5、4.2.6）。WorkManager 定时任务取用 [getEnabled]。 */
interface WakePlanRepository {

    fun observeAll(): Flow<List<WakePlan>>

    suspend fun getById(id: Long): WakePlan?

    /** 已启用的计划，按下次执行时间升序。 */
    suspend fun getEnabled(): List<WakePlan>

    /** 保存计划；[WakePlan.id] 传 0 表示新增。 */
    suspend fun save(plan: WakePlan): Long

    suspend fun updateSchedule(id: Long, enabled: Boolean, nextRunTime: Long)

    suspend fun deleteById(id: Long)

    suspend fun deleteAll()
}

internal class DefaultWakePlanRepository(
    private val db: ZhijingDatabase
) : WakePlanRepository {

    private val wakePlanDao = db.wakePlanDao()

    override fun observeAll(): Flow<List<WakePlan>> =
        wakePlanDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: Long): WakePlan? = wakePlanDao.findById(id)?.toDomain()

    override suspend fun getEnabled(): List<WakePlan> = wakePlanDao.findEnabled().map { it.toDomain() }

    override suspend fun save(plan: WakePlan): Long = wakePlanDao.insert(plan.toEntity())

    override suspend fun updateSchedule(id: Long, enabled: Boolean, nextRunTime: Long) {
        wakePlanDao.updateSchedule(id, enabled, nextRunTime)
    }

    override suspend fun deleteById(id: Long) {
        wakePlanDao.deleteById(id)
    }

    override suspend fun deleteAll() {
        wakePlanDao.deleteAll()
    }
}
