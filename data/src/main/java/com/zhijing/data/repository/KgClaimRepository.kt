package com.zhijing.data.repository

import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.dao.KgClaimMergeDao
import com.zhijing.data.dao.KgRuntimeClaimDao
import com.zhijing.data.mapper.toDomain
import com.zhijing.data.mapper.toEntity
import com.zhijing.data.model.ClaimMerge
import com.zhijing.data.model.ClaimReviewStatus
import com.zhijing.data.model.ClaimSource
import com.zhijing.data.model.RuntimeClaim
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 运行时命题门面：云端生成 / 用户录入的增量命题，以及同义合并。
 *
 * 出厂命题留在 `assets/kg/graph.json`，不进 Room。本仓库只负责**运行期产生**的那部分：
 *
 * ```
 * 云端生成候选 ──► PENDING（不进入任何视图）
 *                    ├─ 用户确认 ──► CONFIRMED（进入广度视图、可下钻判断）
 *                    └─ 用户否决 ──► REJECTED（保留做后续去重）
 * ```
 *
 * 「未经确认不出现」是 API 层面的约定：[observeConfirmed] 只返回 CONFIRMED，
 * 审核界面才用 [observeByReviewStatus] 拿全部候选。
 */
interface KgClaimRepository {

    /** 广度侧：全部已确认的增量命题（与 assets 图谱合并渲染）。 */
    fun observeConfirmed(): Flow<List<RuntimeClaim>>

    /** 广度侧：某张卡下的已确认增量命题。 */
    fun observeConfirmedByCard(cardId: String): Flow<List<RuntimeClaim>>

    /** 审核队列：按确认状态取候选。 */
    fun observeByReviewStatus(status: ClaimReviewStatus): Flow<List<RuntimeClaim>>

    suspend fun getClaim(claimId: String): RuntimeClaim?

    /**
     * 写入一条候选命题。
     *
     * 重复 ID 不覆盖（[androidx.room.OnConflictStrategy.IGNORE]）：
     * 同一条命题被云端重复生成时，应保留既有记录与其审核状态。
     *
     * @return true 表示新插入；false 表示已存在（视为重复生成，直接丢弃）
     */
    suspend fun addCandidate(claim: RuntimeClaim, embedding: ByteArray? = null): Boolean

    /** 审核：确认或否决。返回受影响行数，0 表示命题不存在。 */
    suspend fun review(
        claimId: String,
        status: ClaimReviewStatus,
        now: Long = System.currentTimeMillis()
    ): Int

    suspend fun deleteClaim(claimId: String)

    /** 「清除云端配置」时一并清掉云端生成的命题；用户自己录入的不受影响。 */
    suspend fun deleteBySource(source: ClaimSource): Int

    suspend fun countByReviewStatus(status: ClaimReviewStatus): Int

    /* ------------------------------------------------------------ 同义合并 */

    /** 把派生 ID 规范化到已有命题 ID；无映射时返回原 ID。 */
    suspend fun resolveClaimId(claimId: String): String

    /** 批量规范化，返回 `派生ID -> 规范ID`。 */
    suspend fun resolveClaimIds(claimIds: List<String>): Map<String, String>

    /** 建立合并映射（一条源 ID 只能指向一个目标，重复调用会覆盖）。 */
    suspend fun linkMerge(merge: ClaimMerge)

    suspend fun getMerge(sourceClaimId: String): ClaimMerge?

    suspend fun deleteMerge(sourceClaimId: String)

    suspend fun deleteAll()
}

internal class DefaultKgClaimRepository(
    private val db: ZhijingDatabase
) : KgClaimRepository {

    private val dao: KgRuntimeClaimDao = db.kgRuntimeClaimDao()
    private val mergeDao: KgClaimMergeDao = db.kgClaimMergeDao()

    override fun observeConfirmed(): Flow<List<RuntimeClaim>> =
        dao.observeConfirmed().map { rows -> rows.map { it.toDomain() } }

    override fun observeConfirmedByCard(cardId: String): Flow<List<RuntimeClaim>> =
        dao.observeConfirmedByCard(cardId).map { rows -> rows.map { it.toDomain() } }

    override fun observeByReviewStatus(status: ClaimReviewStatus): Flow<List<RuntimeClaim>> =
        dao.observeByReviewStatus(status).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getClaim(claimId: String): RuntimeClaim? =
        dao.find(claimId)?.toDomain()

    override suspend fun addCandidate(claim: RuntimeClaim, embedding: ByteArray?): Boolean =
        dao.insertIgnore(claim.toEntity(embedding)) != -1L

    override suspend fun review(claimId: String, status: ClaimReviewStatus, now: Long): Int =
        dao.updateReviewStatus(claimId, status, now)

    override suspend fun deleteClaim(claimId: String) {
        dao.delete(claimId)
    }

    override suspend fun deleteBySource(source: ClaimSource): Int = dao.deleteBySource(source)

    override suspend fun countByReviewStatus(status: ClaimReviewStatus): Int =
        dao.countByReviewStatus(status)

    override suspend fun resolveClaimId(claimId: String): String =
        mergeDao.find(claimId)?.targetClaimId ?: claimId

    override suspend fun resolveClaimIds(claimIds: List<String>): Map<String, String> =
        mergeDao.resolve(claimIds)

    override suspend fun linkMerge(merge: ClaimMerge) {
        mergeDao.upsert(merge.toEntity())
    }

    override suspend fun getMerge(sourceClaimId: String): ClaimMerge? =
        mergeDao.find(sourceClaimId)?.toDomain()

    override suspend fun deleteMerge(sourceClaimId: String) {
        mergeDao.delete(sourceClaimId)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
        mergeDao.deleteAll()
    }
}
