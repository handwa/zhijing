package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zhijing.data.entity.TopicEntity
import kotlinx.coroutines.flow.Flow

/** topic_table 的数据访问接口（说明书 3.4.3，认知庭院主题树）。 */
@Dao
internal interface TopicDao {

    /** 主题名是主键，REPLACE 即为 upsert。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TopicEntity)

    @Query("SELECT * FROM topic_table WHERE tag_name = :tagName")
    suspend fun findByTag(tagName: String): TopicEntity?

    /** 顶层主题（认知庭院的根节点）。 */
    @Query("SELECT * FROM topic_table WHERE parent_tag IS NULL ORDER BY tag_name ASC")
    suspend fun findRoots(): List<TopicEntity>

    @Query("SELECT * FROM topic_table WHERE parent_tag = :parentTag ORDER BY tag_name ASC")
    suspend fun findChildren(parentTag: String): List<TopicEntity>

    @Query("SELECT * FROM topic_table ORDER BY content_count DESC")
    fun observeAll(): Flow<List<TopicEntity>>

    /** 说明书 3.4.3：content_count 为主题下内容条数，随内容增删维护。 */
    @Query("UPDATE topic_table SET content_count = :count WHERE tag_name = :tagName")
    suspend fun updateContentCount(tagName: String, count: Int)

    @Query("DELETE FROM topic_table WHERE tag_name = :tagName")
    suspend fun deleteByTag(tagName: String)

    @Query("DELETE FROM topic_table")
    suspend fun deleteAll()
}
