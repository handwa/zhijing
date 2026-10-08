package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.zhijing.data.entity.ContentEntity
import com.zhijing.data.model.AnalysisStatus
import com.zhijing.data.model.EmbeddingStatus
import kotlinx.coroutines.flow.Flow

/**
 * content_table 的数据访问接口（说明书 3.4.1）。
 *
 * 只暴露给同模块的 Repository；写操作全部是 `suspend`，由 Room 派发到 IO 线程，
 * 满足说明书 6.2「写操作都在后台线程」。
 */
@Dao
internal interface ContentDao {

    @Insert
    suspend fun insert(entity: ContentEntity): Long

    @Insert
    suspend fun insertAll(entities: List<ContentEntity>): List<Long>

    @Update
    suspend fun update(entity: ContentEntity)

    @Query("SELECT * FROM content_table WHERE id = :id")
    suspend fun findById(id: Long): ContentEntity?

    @Query("SELECT * FROM content_table WHERE id = :id")
    fun observeById(id: Long): Flow<ContentEntity?>

    @Query("SELECT * FROM content_table WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Long>): List<ContentEntity>

    /** 首页「最近收藏」。 */
    @Query("SELECT * FROM content_table ORDER BY import_time DESC")
    fun observeAll(): Flow<List<ContentEntity>>

    @Query("SELECT * FROM content_table ORDER BY import_time DESC LIMIT :limit")
    suspend fun findRecent(limit: Int): List<ContentEntity>

    @Query("SELECT * FROM content_table ORDER BY import_time DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ContentEntity>>

    /** 解析队列按状态取待处理内容。 */
    @Query("SELECT * FROM content_table WHERE analysis_status = :status ORDER BY import_time ASC LIMIT :limit")
    suspend fun findByAnalysisStatus(status: AnalysisStatus, limit: Int): List<ContentEntity>

    @Query("SELECT * FROM content_table WHERE embedding_status = :status ORDER BY import_time ASC LIMIT :limit")
    suspend fun findByEmbeddingStatus(status: EmbeddingStatus, limit: Int): List<ContentEntity>

    /** 内容理解模块回写解析结果。 */
    @Query("UPDATE content_table SET analysis_json = :analysisJson, analysis_status = :status WHERE id = :id")
    suspend fun updateAnalysis(id: Long, analysisJson: String?, status: AnalysisStatus)

    /** 只改解析状态（例如标记失败），保留已有解析结果。 */
    @Query("UPDATE content_table SET analysis_status = :status WHERE id = :id")
    suspend fun updateAnalysisStatus(id: Long, status: AnalysisStatus)

    /** 向量化模块回写向量与状态。 */
    @Query("UPDATE content_table SET embedding_vector = :vector, embedding_status = :status WHERE id = :id")
    suspend fun updateEmbedding(id: Long, vector: ByteArray?, status: EmbeddingStatus)

    /** 只改向量化状态，保留已有向量（向量化失败时不应丢掉上一次的可用结果）。 */
    @Query("UPDATE content_table SET embedding_status = :status WHERE id = :id")
    suspend fun updateEmbeddingStatus(id: Long, status: EmbeddingStatus)

    @Query("UPDATE content_table SET remark = :remark WHERE id = :id")
    suspend fun updateRemark(id: Long, remark: String?)

    @Query("DELETE FROM content_table WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM content_table")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM content_table")
    suspend fun count(): Long

    @Query("SELECT COUNT(*) FROM content_table")
    fun observeCount(): Flow<Long>
}
