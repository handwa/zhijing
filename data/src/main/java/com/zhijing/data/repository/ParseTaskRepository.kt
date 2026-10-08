package com.zhijing.data.repository

import androidx.room.withTransaction
import com.zhijing.data.DbConstants
import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.mapper.toDomain
import com.zhijing.data.mapper.toEntity
import com.zhijing.data.model.ParseTask
import com.zhijing.data.model.ParseTaskStatus
import com.zhijing.data.model.ParseTaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 解析队列门面（说明书 3.4.8、4.2.2）。
 *
 * 内容入库时由 [ContentRepository.saveImportedContent] 在同一事务内入队「理解」与「向量化」
 * 两个任务；后台 worker 通过 [peekOldest] 取任务、用 [markRunning] / [markDone] / [markFailed]
 * 推进状态。失败会自动重试，重试次数上限为 [DbConstants.MAX_PARSE_RETRY]，超过则标记失败。
 */
interface ParseTaskRepository {

    fun observeAll(): Flow<List<ParseTask>>

    suspend fun getById(id: Long): ParseTask?

    /** 取最早入队的任务；不改变状态，由调用方随后 [markRunning]。 */
    suspend fun peekOldest(): ParseTask?

    suspend fun getByStatus(status: ParseTaskStatus, limit: Int = DEFAULT_LIMIT): List<ParseTask>

    suspend fun getByContentId(contentId: Long): List<ParseTask>

    /** 保存任务；[ParseTask.id] 传 0 表示新增。 */
    suspend fun save(task: ParseTask): Long

    /** 内容入库后按任务类型入队。 */
    suspend fun enqueue(contentId: Long, taskType: ParseTaskType, now: Long = System.currentTimeMillis()): Long

    suspend fun markRunning(id: Long)

    suspend fun markDone(id: Long)

    /**
     * 标记任务失败并累加重试次数。
     *
     * @return true 表示已重新入队等待重试；false 表示重试次数已达上限，任务置为失败。
     */
    suspend fun markFailed(id: Long): Boolean

    suspend fun deleteById(id: Long)

    suspend fun countByStatus(status: ParseTaskStatus): Long

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}

internal class DefaultParseTaskRepository(
    private val db: ZhijingDatabase
) : ParseTaskRepository {

    private val parseTaskDao = db.parseTaskDao()

    override fun observeAll(): Flow<List<ParseTask>> =
        parseTaskDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: Long): ParseTask? = parseTaskDao.findById(id)?.toDomain()

    override suspend fun peekOldest(): ParseTask? {
        val queued = parseTaskDao.findByStatus(ParseTaskStatus.QUEUED, 1)
        return (queued.firstOrNull() ?: parseTaskDao.findOldest())?.toDomain()
    }

    override suspend fun getByStatus(status: ParseTaskStatus, limit: Int): List<ParseTask> =
        parseTaskDao.findByStatus(status, limit).map { it.toDomain() }

    override suspend fun getByContentId(contentId: Long): List<ParseTask> =
        parseTaskDao.findByContentId(contentId).map { it.toDomain() }

    override suspend fun save(task: ParseTask): Long = parseTaskDao.insert(task.toEntity())

    override suspend fun enqueue(contentId: Long, taskType: ParseTaskType, now: Long): Long =
        save(
            ParseTask(
                id = 0,
                contentId = contentId,
                taskType = taskType,
                status = ParseTaskStatus.QUEUED,
                retryCount = 0,
                createdTime = now
            )
        )

    override suspend fun markRunning(id: Long) {
        val task = parseTaskDao.findById(id) ?: return
        parseTaskDao.updateStatus(id, ParseTaskStatus.RUNNING, task.retryCount)
    }

    override suspend fun markDone(id: Long) {
        val task = parseTaskDao.findById(id) ?: return
        parseTaskDao.updateStatus(id, ParseTaskStatus.DONE, task.retryCount)
    }

    override suspend fun markFailed(id: Long): Boolean = db.withTransaction {
        val task = parseTaskDao.findById(id) ?: return@withTransaction false
        val nextRetry = task.retryCount + 1
        val canRetry = nextRetry < DbConstants.MAX_PARSE_RETRY
        parseTaskDao.updateStatus(
            id = id,
            status = if (canRetry) ParseTaskStatus.QUEUED else ParseTaskStatus.FAILED,
            retryCount = nextRetry
        )
        canRetry
    }

    override suspend fun deleteById(id: Long) {
        parseTaskDao.deleteById(id)
    }

    override suspend fun countByStatus(status: ParseTaskStatus): Long =
        parseTaskDao.countByStatus(status)
}
