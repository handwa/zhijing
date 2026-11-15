package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zhijing.data.entity.KgConditionProbeEntity
import kotlinx.coroutines.flow.Flow

/**
 * `kg_condition_probe` 的数据访问接口。
 *
 * 只做「按主键读写 + 按命题/三元组取行」，**聚合在仓库层用 Kotlin 做**：
 * 「错过几次」「错在哪几条干预点」「误解有哪些」都要去重后按最近顺序排，
 * 用 SQL 拼会变成三四个子查询，反而不如读出来算清楚。
 */
@Dao
internal interface KgConditionProbeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KgConditionProbeEntity)

    @Query(
        """
        SELECT * FROM kg_condition_probe
        WHERE triple_id = :tripleId AND intervention_id = :interventionId
        """
    )
    suspend fun find(tripleId: String, interventionId: String): KgConditionProbeEntity?

    /** 一条三元组下所有干预点的作答记录（「继续拨其他条件」时回填已答状态）。 */
    @Query("SELECT * FROM kg_condition_probe WHERE triple_id = :tripleId")
    suspend fun findByTriple(tripleId: String): List<KgConditionProbeEntity>

    /** 一条命题下所有条件的作答记录。 */
    @Query("SELECT * FROM kg_condition_probe WHERE claim_id = :claimId")
    suspend fun findByClaim(claimId: String): List<KgConditionProbeEntity>

    /** 广度侧渲染若干张卡时的批量读取（宽度视图按 claimIds 派生）。 */
    @Query("SELECT * FROM kg_condition_probe WHERE claim_id IN (:claimIds)")
    suspend fun findByClaimIds(claimIds: List<String>): List<KgConditionProbeEntity>

    /** 广度侧监听：复盘结束后卡片上的「错过几次」要立刻变。 */
    @Query("SELECT * FROM kg_condition_probe WHERE claim_id IN (:claimIds)")
    fun observeByClaimIds(claimIds: List<String>): Flow<List<KgConditionProbeEntity>>

    @Query(
        """
        SELECT * FROM kg_condition_probe
        WHERE triple_id = :tripleId AND intervention_id = :interventionId
        """
    )
    fun observe(
        tripleId: String,
        interventionId: String
    ): Flow<KgConditionProbeEntity?>

    @Query(
        """
        DELETE FROM kg_condition_probe
        WHERE triple_id = :tripleId AND intervention_id = :interventionId
        """
    )
    suspend fun delete(tripleId: String, interventionId: String)

    /**
     * 清理孤儿记录：三元组从图谱里下架后，记录不应永远留着。
     *
     * 三元组不在数据库中（在 assets），无法用外键级联，只能由调用方传入当前图谱的全部三元组 ID。
     * 单个干预点被删的极端情况（三元组还在、某一问没了）由三元组级清理兜底不到，
     * 但这类改动必然伴随三元组语义变化，实际会被当成新三元组处理。
     */
    @Query("DELETE FROM kg_condition_probe WHERE triple_id NOT IN (:aliveTripleIds)")
    suspend fun deleteOrphans(aliveTripleIds: List<String>): Int

    @Query("DELETE FROM kg_condition_probe")
    suspend fun deleteAll()
}
