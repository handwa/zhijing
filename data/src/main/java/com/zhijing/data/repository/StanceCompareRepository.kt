package com.zhijing.data.repository

import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.mapper.toDomain
import com.zhijing.data.mapper.toEntity
import com.zhijing.data.model.StanceCompare
import com.zhijing.data.model.StanceStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 观点对照门面（说明书 3.4.4、4.2.5）。 */
interface StanceCompareRepository {

    fun observeByStatus(status: StanceStatus): Flow<List<StanceCompare>>

    suspend fun getById(id: Long): StanceCompare?

    suspend fun getByTopic(topicTag: String): List<StanceCompare>

    /** 保存一组对照；[StanceCompare.id] 传 0 表示新增。 */
    suspend fun save(compare: StanceCompare): Long

    suspend fun updateStatus(id: Long, status: StanceStatus)

    /** 说明书 4.2.5：内容清空后，相关的话题记录一并删掉。 */
    suspend fun deleteByTopic(topicTag: String)

    suspend fun deleteAll()
}

internal class DefaultStanceCompareRepository(
    private val db: ZhijingDatabase
) : StanceCompareRepository {

    private val stanceCompareDao = db.stanceCompareDao()

    override fun observeByStatus(status: StanceStatus): Flow<List<StanceCompare>> =
        stanceCompareDao.observeByStatus(status).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: Long): StanceCompare? =
        stanceCompareDao.findById(id)?.toDomain()

    override suspend fun getByTopic(topicTag: String): List<StanceCompare> =
        stanceCompareDao.findByTopic(topicTag).map { it.toDomain() }

    override suspend fun save(compare: StanceCompare): Long =
        stanceCompareDao.insert(compare.toEntity())

    override suspend fun updateStatus(id: Long, status: StanceStatus) {
        stanceCompareDao.updateStatus(id, status)
    }

    override suspend fun deleteByTopic(topicTag: String) {
        stanceCompareDao.deleteByTopic(topicTag)
    }

    override suspend fun deleteAll() {
        stanceCompareDao.deleteAll()
    }
}
