package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zhijing.data.entity.KgRuntimeClaimEntity
import com.zhijing.data.model.ClaimReviewStatus
import com.zhijing.data.model.ClaimSource
import kotlinx.coroutines.flow.Flow

/**
 * `kg_runtime_claim` 的数据访问接口。
 *
 * 所有读方法都默认只返回 `confirmed` 的命题（[observeConfirmed]），
 * 只有审核界面才通过 [observeByReviewStatus] 拿到 pending / rejected。
 * 这样「云端生成的候选不会未经确认就出现在广度图谱」成为 API 层面的约定，
 * 而不是靠调用方自觉。
 */
@Dao
internal interface KgRuntimeClaimDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KgRuntimeClaimEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: KgRuntimeClaimEntity): Long

    @Query("SELECT * FROM kg_runtime_claim WHERE claim_id = :claimId")
    suspend fun find(claimId: String): KgRuntimeClaimEntity?

    /** 广度侧：某张卡下所有已确认的增量命题。 */
    @Query(
        """
        SELECT * FROM kg_runtime_claim
        WHERE card_id = :cardId AND review_status = 'confirmed'
        ORDER BY created_time ASC
        """
    )
    fun observeConfirmedByCard(cardId: String): Flow<List<KgRuntimeClaimEntity>>

    /** 广度侧：全部已确认的增量命题，用于与 assets 图谱合并渲染。 */
    @Query(
        """
        SELECT * FROM kg_runtime_claim
        WHERE review_status = 'confirmed'
        ORDER BY created_time ASC
        """
    )
    fun observeConfirmed(): Flow<List<KgRuntimeClaimEntity>>

    /** 审核队列 / 调试。 */
    @Query("SELECT * FROM kg_runtime_claim WHERE review_status = :status ORDER BY created_time DESC")
    fun observeByReviewStatus(status: ClaimReviewStatus): Flow<List<KgRuntimeClaimEntity>>

    @Query("SELECT * FROM kg_runtime_claim WHERE review_status = :status ORDER BY created_time DESC")
    suspend fun findByReviewStatus(status: ClaimReviewStatus): List<KgRuntimeClaimEntity>

    @Query("SELECT * FROM kg_runtime_claim WHERE card_id = :cardId")
    suspend fun findByCard(cardId: String): List<KgRuntimeClaimEntity>

    /** 审核动作：确认或否决。 */
    @Query(
        """
        UPDATE kg_runtime_claim
        SET review_status = :status, reviewed_time = :reviewedTime
        WHERE claim_id = :claimId
        """
    )
    suspend fun updateReviewStatus(
        claimId: String,
        status: ClaimReviewStatus,
        reviewedTime: Long
    ): Int

    @Query("DELETE FROM kg_runtime_claim WHERE claim_id = :claimId")
    suspend fun delete(claimId: String)

    /** 清空某来源的数据：「清除云端配置时一并清掉云端生成的命题」用。 */
    @Query("DELETE FROM kg_runtime_claim WHERE source = :source")
    suspend fun deleteBySource(source: ClaimSource): Int

    @Query("SELECT COUNT(*) FROM kg_runtime_claim WHERE review_status = :status")
    suspend fun countByReviewStatus(status: ClaimReviewStatus): Int

    @Query("DELETE FROM kg_runtime_claim")
    suspend fun deleteAll()
}
