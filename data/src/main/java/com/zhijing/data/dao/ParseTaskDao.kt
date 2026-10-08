package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zhijing.data.entity.ParseTaskEntity
import com.zhijing.data.model.ParseTaskStatus
import kotlinx.coroutines.flow.Flow

/** parse_task_table 的数据访问接口（说明书 3.4.8，解析队列）。 */
@Dao
internal interface ParseTaskDao {

    @Insert
    suspend fun insert(entity: ParseTaskEntity): Long

    @Insert
    suspend fun insertAll(entities: List<ParseTaskEntity>): List<Long>

    @Query("SELECT * FROM parse_task_table WHERE id = :id")
    suspend fun findById(id: Long): ParseTaskEntity?

    /** 出队：按入队时间取最早的一条排队任务。 */
    @Query("SELECT * FROM parse_task_table ORDER BY created_time ASC LIMIT 1")
    suspend fun findOldest(): ParseTaskEntity?

    @Query("SELECT * FROM parse_task_table WHERE status = :status ORDER BY created_time ASC LIMIT :limit")
    suspend fun findByStatus(status: ParseTaskStatus, limit: Int): List<ParseTaskEntity>

    @Query("SELECT * FROM parse_task_table WHERE content_id = :contentId ORDER BY created_time ASC")
    suspend fun findByContentId(contentId: Long): List<ParseTaskEntity>

    @Query("SELECT * FROM parse_task_table ORDER BY created_time ASC")
    fun observeAll(): Flow<List<ParseTaskEntity>>

    /** 更新任务状态与重试次数（说明书 3.4.8：最多重试 3 次）。 */
    @Query("UPDATE parse_task_table SET status = :status, retry_count = :retryCount WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ParseTaskStatus, retryCount: Int)

    @Query("DELETE FROM parse_task_table WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM parse_task_table WHERE content_id = :contentId")
    suspend fun deleteByContentId(contentId: Long)

    @Query("DELETE FROM parse_task_table")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM parse_task_table WHERE status = :status")
    suspend fun countByStatus(status: ParseTaskStatus): Long
}
