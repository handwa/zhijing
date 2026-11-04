package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zhijing.data.entity.KgClaimStateEntity
import com.zhijing.data.model.ProbeStatus
import kotlinx.coroutines.flow.Flow

/**
 * `kg_claim_state` 的数据访问接口。
 *
 * 只做「按命题 ID 读写状态」与「按状态聚合」，不碰出厂命题本身——
 * 出厂命题在 assets 里，由渲染层加载后与本表在内存里 join。
 */
@Dao
internal interface KgClaimStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KgClaimStateEntity)

    @Query("SELECT * FROM kg_claim_state WHERE claim_id = :claimId")
    suspend fun find(claimId: String): KgClaimStateEntity?

    @Query("SELECT * FROM kg_claim_state WHERE claim_id IN (:claimIds)")
    suspend fun findByClaimIds(claimIds: List<String>): List<KgClaimStateEntity>

    /** 广度侧渲染一张卡时，取这张卡下所有命题的状态。 */
    @Query("SELECT * FROM kg_claim_state WHERE claim_id IN (:claimIds)")
    fun observeByClaimIds(claimIds: List<String>): Flow<List<KgClaimStateEntity>>

    /**
     * 观察**全部**状态变化。
     *
     * 图谱合并加载器需要它：命题集合来自 assets（运行时不进库），
     * 无法预先知道该监听哪些 ID，只能整体监听再在内存里对齐。
     */
    @Query("SELECT * FROM kg_claim_state")
    fun observeAll(): Flow<List<KgClaimStateEntity>>

    /** 深度侧的「状态汇总」：按状态计数。 */
    @Query("SELECT probe_status AS status, COUNT(*) AS count FROM kg_claim_state GROUP BY probe_status")
    suspend fun countByStatus(): List<StatusCount>

    @Query("SELECT * FROM kg_claim_state WHERE probe_status = :status ORDER BY last_probed_at DESC")
    suspend fun findByStatus(status: ProbeStatus): List<KgClaimStateEntity>

    /** 易错回顾：按易错程度排序，供「易错先读」驱动动作。 */
    @Query(
        """
        SELECT * FROM kg_claim_state
        WHERE probe_status = 'confused'
        ORDER BY attempts DESC, last_probed_at DESC
        LIMIT :limit
        """
    )
    suspend fun findMostConfused(limit: Int): List<KgClaimStateEntity>

    @Query("DELETE FROM kg_claim_state WHERE claim_id = :claimId")
    suspend fun delete(claimId: String)

    /**
     * 清理孤儿状态：命题从图谱里下架后，状态不应永远留着。
     *
     * 出厂命题不在数据库里，无法用外键级联，只能由调用方传入当前图谱的全部命题 ID。
     */
    @Query("DELETE FROM kg_claim_state WHERE claim_id NOT IN (:aliveClaimIds)")
    suspend fun deleteOrphans(aliveClaimIds: List<String>): Int

    @Query("DELETE FROM kg_claim_state")
    suspend fun deleteAll()

    data class StatusCount(
        val status: ProbeStatus,
        val count: Int
    )
}
