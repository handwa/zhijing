package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zhijing.data.entity.AssociationEntity
import com.zhijing.data.model.AssociationFeedback
import kotlinx.coroutines.flow.Flow

/** association_table 的数据访问接口（说明书 3.4.2，关联卡片）。 */
@Dao
internal interface AssociationDao {

    @Insert
    suspend fun insert(entity: AssociationEntity): Long

    @Query("SELECT * FROM association_table WHERE id = :id")
    suspend fun findById(id: Long): AssociationEntity?

    /** 与某条内容相关的全部关联（无论它在 source 还是 target 侧）。 */
    @Query(
        "SELECT * FROM association_table " +
            "WHERE source_content_id = :contentId OR target_content_id = :contentId " +
            "ORDER BY similarity_score DESC"
    )
    suspend fun findRelatedTo(contentId: Long): List<AssociationEntity>

    @Query("SELECT * FROM association_table ORDER BY similarity_score DESC")
    fun observeAll(): Flow<List<AssociationEntity>>

    /** 说明书 4.2.4：卡片反馈影响以后推荐的排序。 */
    @Query("UPDATE association_table SET feedback = :feedback, weight = :weight WHERE id = :id")
    suspend fun updateFeedback(id: Long, feedback: AssociationFeedback, weight: Double)

    @Query("DELETE FROM association_table WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM association_table WHERE source_content_id = :contentId OR target_content_id = :contentId")
    suspend fun deleteByContentId(contentId: Long)

    @Query("DELETE FROM association_table")
    suspend fun deleteAll()
}
