package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zhijing.data.entity.StanceCompareEntity
import com.zhijing.data.model.StanceStatus
import kotlinx.coroutines.flow.Flow

/** stance_compare_table 的数据访问接口（说明书 3.4.4，观点对照）。 */
@Dao
internal interface StanceCompareDao {

    @Insert
    suspend fun insert(entity: StanceCompareEntity): Long

    @Query("SELECT * FROM stance_compare_table WHERE id = :id")
    suspend fun findById(id: Long): StanceCompareEntity?

    @Query("SELECT * FROM stance_compare_table WHERE topic_tag = :topicTag")
    suspend fun findByTopic(topicTag: String): List<StanceCompareEntity>

    @Query("SELECT * FROM stance_compare_table WHERE status = :status ORDER BY id DESC")
    fun observeByStatus(status: StanceStatus): Flow<List<StanceCompareEntity>>

    @Query("UPDATE stance_compare_table SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: StanceStatus)

    @Query("DELETE FROM stance_compare_table WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 说明书 4.2.5：内容清空后，相关的话题记录一并删掉。 */
    @Query("DELETE FROM stance_compare_table WHERE topic_tag = :topicTag")
    suspend fun deleteByTopic(topicTag: String)

    @Query("DELETE FROM stance_compare_table")
    suspend fun deleteAll()
}
