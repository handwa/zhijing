package com.zhijing.data.repository

import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.mapper.toDomain
import com.zhijing.data.mapper.toEntity
import com.zhijing.data.model.QaRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 问答记录门面（说明书 3.4.6）。
 *
 * `sourceContentIds` 记录回答引用的内容，支撑说明书 4.2.7
 * 「生成记录里保存模型版本和引用来源，方便排查问题」。
 */
interface QaRecordRepository {

    fun observeRecent(limit: Int = DEFAULT_RECENT_LIMIT): Flow<List<QaRecord>>

    suspend fun getRecent(limit: Int = DEFAULT_RECENT_LIMIT): List<QaRecord>

    suspend fun getById(id: Long): QaRecord?

    /** 保存一条问答；[QaRecord.id] 传 0 表示新增。 */
    suspend fun save(record: QaRecord): Long

    suspend fun deleteById(id: Long)

    suspend fun deleteAll()

    companion object {
        const val DEFAULT_RECENT_LIMIT = 20
    }
}

internal class DefaultQaRecordRepository(
    private val db: ZhijingDatabase
) : QaRecordRepository {

    private val qaRecordDao = db.qaRecordDao()

    override fun observeRecent(limit: Int): Flow<List<QaRecord>> =
        qaRecordDao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getRecent(limit: Int): List<QaRecord> =
        qaRecordDao.findRecent(limit).map { it.toDomain() }

    override suspend fun getById(id: Long): QaRecord? = qaRecordDao.findById(id)?.toDomain()

    override suspend fun save(record: QaRecord): Long = qaRecordDao.insert(record.toEntity())

    override suspend fun deleteById(id: Long) {
        qaRecordDao.deleteById(id)
    }

    override suspend fun deleteAll() {
        qaRecordDao.deleteAll()
    }
}
