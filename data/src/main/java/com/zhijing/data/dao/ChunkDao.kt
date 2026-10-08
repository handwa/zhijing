package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zhijing.data.entity.ChunkEntity

/** chunk_table 的数据访问接口（分段表，为段落级 RAG 预留）。 */
@Dao
internal interface ChunkDao {

    @Insert
    suspend fun insertAll(chunks: List<ChunkEntity>): List<Long>

    @Query("SELECT * FROM chunk_table WHERE content_id = :contentId ORDER BY chunk_index ASC")
    suspend fun findByContentId(contentId: Long): List<ChunkEntity>

    @Query("SELECT * FROM chunk_table WHERE id = :id")
    suspend fun findById(id: Long): ChunkEntity?

    @Query("SELECT * FROM chunk_table WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Long>): List<ChunkEntity>

    @Query("SELECT id FROM chunk_table WHERE content_id = :contentId ORDER BY chunk_index ASC")
    suspend fun findIdsByContentId(contentId: Long): List<Long>

    @Query("SELECT id FROM chunk_table")
    suspend fun findAllIds(): List<Long>

    @Query("DELETE FROM chunk_table WHERE content_id = :contentId")
    suspend fun deleteByContentId(contentId: Long)

    @Query("DELETE FROM chunk_table")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM chunk_table WHERE content_id = :contentId")
    suspend fun countByContentId(contentId: Long): Int
}
