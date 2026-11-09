package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.zhijing.data.entity.KgGeneratedCardEntity
import com.zhijing.data.entity.KgGeneratedGraphEntity
import com.zhijing.data.entity.KgGeneratedOutlineEntity
import com.zhijing.data.entity.KgGeneratedPrerequisiteEntity
import kotlinx.coroutines.flow.Flow

/**
 * 生成图谱三张表的数据访问。
 *
 * 三张表必须**同进同出**：一次生成要么整份写入，要么整份不写。
 * 因此写入与删除都包在 [Transaction] 里 —— 否则中途失败会留下
 * 「有卡片但没大纲」的半份数据，渲染时无法定位父节点。
 */
@Dao
internal interface KgGeneratedGraphDao {

    /* ---------------------------------------------------------- 元信息 */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGraph(entity: KgGeneratedGraphEntity)

    @Query("SELECT * FROM kg_generated_graph WHERE topic_id = :topicId")
    suspend fun findGraph(topicId: String): KgGeneratedGraphEntity?

    @Query("SELECT * FROM kg_generated_graph ORDER BY created_time DESC")
    fun observeGraphs(): Flow<List<KgGeneratedGraphEntity>>

    @Query("SELECT * FROM kg_generated_graph ORDER BY created_time DESC")
    suspend fun listGraphs(): List<KgGeneratedGraphEntity>

    @Query("DELETE FROM kg_generated_graph WHERE topic_id = :topicId")
    suspend fun deleteGraph(topicId: String)

    /* ------------------------------------------------------------ 大纲 */

    @Query("SELECT * FROM kg_generated_outline WHERE topic_id = :topicId ORDER BY order_index ASC")
    suspend fun outlineOf(topicId: String): List<KgGeneratedOutlineEntity>

    @Query("SELECT * FROM kg_generated_outline ORDER BY topic_id, order_index ASC")
    suspend fun allOutline(): List<KgGeneratedOutlineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutline(rows: List<KgGeneratedOutlineEntity>)

    @Query("DELETE FROM kg_generated_outline WHERE topic_id = :topicId")
    suspend fun deleteOutline(topicId: String)

    /* ------------------------------------------------------------ 卡片 */

    @Query("SELECT * FROM kg_generated_card WHERE topic_id = :topicId ORDER BY order_index ASC")
    suspend fun cardsOf(topicId: String): List<KgGeneratedCardEntity>

    @Query("SELECT * FROM kg_generated_card ORDER BY topic_id, order_index ASC")
    suspend fun allCards(): List<KgGeneratedCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(rows: List<KgGeneratedCardEntity>)

    @Query("DELETE FROM kg_generated_card WHERE topic_id = :topicId")
    suspend fun deleteCards(topicId: String)

    /* ------------------------------------------------------ 前置关系 */

    @Query("SELECT * FROM kg_generated_prerequisite WHERE topic_id = :topicId")
    suspend fun prerequisitesOf(topicId: String): List<KgGeneratedPrerequisiteEntity>

    @Query("SELECT * FROM kg_generated_prerequisite")
    suspend fun allPrerequisites(): List<KgGeneratedPrerequisiteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrerequisites(rows: List<KgGeneratedPrerequisiteEntity>)

    @Query("DELETE FROM kg_generated_prerequisite WHERE topic_id = :topicId")
    suspend fun deletePrerequisites(topicId: String)

    /* ------------------------------------------------------ 整体替换 */

    /**
     * 整份替换一个主题的图谱。
     *
     * 先删旧数据再写新数据，**在一个事务内完成**：
     * 中途失败会回滚，不会留下「有卡片没大纲」的半份状态。
     */
    @Transaction
    suspend fun replaceGraph(
        meta: KgGeneratedGraphEntity,
        outline: List<KgGeneratedOutlineEntity>,
        cards: List<KgGeneratedCardEntity>,
        prerequisites: List<KgGeneratedPrerequisiteEntity> = emptyList()
    ) {
        deleteOutline(meta.topicId)
        deleteCards(meta.topicId)
        deletePrerequisites(meta.topicId)
        upsertGraph(meta)
        if (outline.isNotEmpty()) insertOutline(outline)
        if (cards.isNotEmpty()) insertCards(cards)
        if (prerequisites.isNotEmpty()) insertPrerequisites(prerequisites)
    }

    @Transaction
    suspend fun deleteWholeGraph(topicId: String) {
        deleteOutline(topicId)
        deleteCards(topicId)
        deletePrerequisites(topicId)
        deleteGraph(topicId)
    }
}
