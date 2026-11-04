package com.zhijing.data.repository

import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.dao.KgClaimStateDao
import com.zhijing.data.knowledge.ProbeRules
import com.zhijing.data.mapper.toDomain
import com.zhijing.data.mapper.toEntity
import com.zhijing.data.model.ClaimProbeState
import com.zhijing.data.model.ProbeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 命题探测状态门面（深度侧 → 广度侧的回写）。
 *
 * 只存设计文档允许回写的**摘要**：状态、最近探测时间、误解、置信度，
 * 加上深度侧自用的计数（attempts / correct / streak）。逐题作答日志不放这里。
 *
 * 回写走派生：广度侧不存储状态，渲染时按 `claimIds` 查本仓库即可。
 * 见 [observeStates]。
 */
interface KgStateRepository {

    /** 读取一条命题的状态；从未探测过返回 [ClaimProbeState] 的默认值。 */
    suspend fun getState(claimId: String): ClaimProbeState

    /** 批量读取，广度侧渲染一张卡时使用。 */
    suspend fun getStates(claimIds: List<String>): Map<String, ClaimProbeState>

    /** 广度侧监听一张卡下所有命题的状态变化。 */
    fun observeStates(claimIds: List<String>): Flow<Map<String, ClaimProbeState>>

    /**
     * 监听**全部**状态，返回 `命题ID -> 状态`。
     *
     * 图谱合并加载器在运行时不知道命题全集（出厂命题在 assets 里），
     * 只能整体监听、再在内存里按 ID 对齐。
     */
    fun observeAllStates(): Flow<Map<String, ClaimProbeState>>

    /**
     * 记录一次判断并返回收敛后的状态。
     *
     * @param correct 用户这次判对没有
     * @param misconception 判错时的误解说明；判对时忽略
     */
    suspend fun recordJudgement(
        claimId: String,
        correct: Boolean,
        misconception: String?,
        now: Long = System.currentTimeMillis()
    ): ClaimProbeState

    /** 直接覆盖状态（导入、调试、测试用）。 */
    suspend fun putState(state: ClaimProbeState)

    /** 深度侧「状态汇总」：按状态计数。 */
    suspend fun countByStatus(): Map<ProbeStatus, Int>

    /** 易错优先：返回最需要重读的命题状态，驱动「易错先读」。 */
    suspend fun mostConfused(limit: Int = 10): List<ClaimProbeState>

    /**
     * 清理孤儿状态：传入当前图谱里仍然存在的全部命题 ID。
     *
     * 出厂命题在 assets 里，无法用外键级联，必须在图谱更新后显式调用，
     * 否则下架的命题会永远占着状态表。
     */
    suspend fun pruneOrphans(aliveClaimIds: List<String>): Int

    suspend fun deleteAll()
}

internal class DefaultKgStateRepository(
    private val db: ZhijingDatabase
) : KgStateRepository {

    private val dao: KgClaimStateDao = db.kgClaimStateDao()

    override suspend fun getState(claimId: String): ClaimProbeState =
        dao.find(claimId)?.toDomain() ?: ClaimProbeState(claimId = claimId)

    override suspend fun getStates(claimIds: List<String>): Map<String, ClaimProbeState> {
        if (claimIds.isEmpty()) return emptyMap()
        val stored = dao.findByClaimIds(claimIds).map { it.toDomain() }.associateBy { it.claimId }
        return claimIds.associateWith { stored[it] ?: ClaimProbeState(claimId = it) }
    }

    override fun observeStates(claimIds: List<String>): Flow<Map<String, ClaimProbeState>> =
        dao.observeByClaimIds(claimIds).map { rows ->
            val stored = rows.map { it.toDomain() }.associateBy { it.claimId }
            claimIds.associateWith { stored[it] ?: ClaimProbeState(claimId = it) }
        }

    override fun observeAllStates(): Flow<Map<String, ClaimProbeState>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() }.associateBy { it.claimId } }

    override suspend fun recordJudgement(
        claimId: String,
        correct: Boolean,
        misconception: String?,
        now: Long
    ): ClaimProbeState {
        val prev = getState(claimId)
        val nextStreak = if (correct) prev.streak + 1 else 0
        val nextAttempts = prev.attempts + 1
        val nextCorrect = prev.correct + (if (correct) 1 else 0)
        val next = prev.copy(
            probeStatus = if (correct) ProbeRules.onCorrect(nextStreak) else ProbeRules.onWrong(),
            lastProbedAt = now,
            misconceptions = if (correct) {
                prev.misconceptions
            } else {
                ProbeRules.appendMisconception(prev.misconceptions, misconception)
            },
            confidence = ProbeRules.confidence(nextCorrect, nextAttempts),
            attempts = nextAttempts,
            correct = nextCorrect,
            streak = nextStreak
        )
        dao.upsert(next.toEntity())
        return next
    }

    override suspend fun putState(state: ClaimProbeState) {
        dao.upsert(state.toEntity())
    }

    override suspend fun countByStatus(): Map<ProbeStatus, Int> =
        dao.countByStatus().associate { it.status to it.count }

    override suspend fun mostConfused(limit: Int): List<ClaimProbeState> =
        dao.findMostConfused(limit).map { it.toDomain() }

    override suspend fun pruneOrphans(aliveClaimIds: List<String>): Int =
        if (aliveClaimIds.isEmpty()) {
            // 「现在什么都没有」不等于「可以清空」——空列表多半是调用方加载失败，不能误删。
            0
        } else {
            dao.deleteOrphans(aliveClaimIds)
        }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
