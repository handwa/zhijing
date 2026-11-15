package com.zhijing.data.repository

import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.dao.KgConditionProbeDao
import com.zhijing.data.knowledge.ProbeRules
import com.zhijing.data.mapper.toDomain
import com.zhijing.data.mapper.toEntity
import com.zhijing.data.model.ConditionProbeState
import com.zhijing.data.model.ConditionProbeSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * 条件级探测记录门面（深度侧「预测 → 揭示」的事实来源）。
 *
 * ## 与 [KgStateRepository] 的关系
 *
 * 两者**不是重复**，而是两个粒度：
 * - [KgStateRepository] 记「这条命题掌握没有」，驱动角标、复习队列；
 * - 本仓库记「哪个干预点上把边界想错了」，驱动卡片上的具体事实。
 *
 * **不要在同一次「预测 → 揭示」里两边都写**：题是做两遍（条件题 + 判断题），
 * 各写各的就是两次 attempts；把条件题的结果再抄进命题状态，会让置信度与收敛
 * 提前到达，状态显示「已掌握」而用户其实只答了一遍。
 *
 * ## 回写是派生
 *
 * 广度侧不存状态，渲染时按 `claimIds` 调 [summaries] / [observeSummaries] 现算。
 * 聚合结果刻意只给「次数 + 干预点 ID + 误解文本」，不含百分比。
 */
interface ConditionProbeRepository {

    /** 读一条干预点的记录；从未作答返回 null（与命题状态不同，没有「默认状态」）。 */
    suspend fun getState(tripleId: String, interventionId: String): ConditionProbeState?

    /** 一条三元组下所有干预点的记录，用于「继续拨其他条件」时回填已答情况。 */
    suspend fun statesForTriple(tripleId: String): List<ConditionProbeState>

    /** 一条命题下所有条件的记录。 */
    suspend fun statesForClaim(claimId: String): List<ConditionProbeState>

    /**
     * 批量取多条命题下的记录，按 `claimId` 分组。
     *
     * 图谱合并加载要一次拿全（24 张卡逐卡查会变成 24 次 IO），
     * 返回的 Map 只包含**有记录**的命题；没作答的命题不在里面。
     */
    suspend fun statesForClaims(claimIds: List<String>): Map<String, List<ConditionProbeState>>

    /**
     * 记录一次作答并返回累加后的记录。
     *
     * @param predictedOption 用户的选择（`Outcome` 的 name）
     * @param correctOption 实际后果（本地求值或预置答案）
     * @param misconception 本次猜错对应的误解；答对时传 null，且**不覆盖**已有误解
     * @return 覆盖后的记录（attempts / wrongAttempts 已累加）
     */
    suspend fun record(
        tripleId: String,
        interventionId: String,
        claimId: String,
        predictedOption: String,
        correctOption: String,
        misconception: String? = null,
        now: Long = System.currentTimeMillis()
    ): ConditionProbeState

    /** 直接覆盖一条记录（导入、调试、测试用）。 */
    suspend fun putState(state: ConditionProbeState)

    /**
     * 按命题聚合出卡片要显示的事实。
     *
     * 返回的 Map **包含传入的每一个 claimId**（没答过的给空摘要），
     * 且空入参返回空 Map —— 调用方不需要再做 null 兜底。
     */
    suspend fun summaries(claimIds: List<String>): Map<String, ConditionProbeSummary>

    /** 广度侧监听：复盘结束后卡片上的「错过几次」要立刻变。 */
    fun observeSummaries(claimIds: List<String>): Flow<Map<String, ConditionProbeSummary>>

    /**
     * 清理孤儿记录：传入当前图谱里仍然存在的全部三元组 ID。
     *
     * 三元组在 assets 里、没有外键，必须在图谱更新后显式调用。
     * 空列表视为「调用方没加载成功」，直接返回 0，绝不清空。
     */
    suspend fun pruneOrphans(aliveTripleIds: List<String>): Int

    suspend fun deleteAll()
}

internal class DefaultConditionProbeRepository(
    private val db: ZhijingDatabase
) : ConditionProbeRepository {

    private val dao: KgConditionProbeDao = db.kgConditionProbeDao()

    override suspend fun getState(tripleId: String, interventionId: String): ConditionProbeState? =
        dao.find(tripleId, interventionId)?.toDomain()

    override suspend fun statesForTriple(tripleId: String): List<ConditionProbeState> =
        if (tripleId.isBlank()) emptyList() else dao.findByTriple(tripleId).map { it.toDomain() }

    override suspend fun statesForClaim(claimId: String): List<ConditionProbeState> =
        if (claimId.isBlank()) emptyList() else dao.findByClaim(claimId).map { it.toDomain() }

    override suspend fun statesForClaims(
        claimIds: List<String>
    ): Map<String, List<ConditionProbeState>> {
        if (claimIds.isEmpty()) return emptyMap()
        return dao.findByClaimIds(claimIds)
            .map { it.toDomain() }
            .groupBy { it.claimId }
    }

    override suspend fun record(
        tripleId: String,
        interventionId: String,
        claimId: String,
        predictedOption: String,
        correctOption: String,
        misconception: String?,
        now: Long
    ): ConditionProbeState {
        require(tripleId.isNotBlank()) { "tripleId 不能为空" }
        require(interventionId.isNotBlank()) { "interventionId 不能为空" }
        require(claimId.isNotBlank()) { "claimId 不能为空" }

        val prev = getState(tripleId, interventionId)
        val matched = predictedOption == correctOption
        val next = ConditionProbeState(
            tripleId = tripleId,
            interventionId = interventionId,
            claimId = claimId,
            predictedOption = predictedOption,
            // 只留上一格：「原来猜 X，现在改成 Y」需要的是前后对比，不是完整历史
            previousPredictedOption = prev?.predictedOption,
            correctOption = correctOption,
            matched = matched,
            attempts = (prev?.attempts ?: 0) + 1,
            wrongAttempts = (prev?.wrongAttempts ?: 0) + if (matched) 0 else 1,
            lastProbedAt = now,
            // 答对时保留上次的误解：它记录的是「我曾经在哪摔倒」，不是「这次错了没有」
            misconception = if (matched) prev?.misconception else (misconception ?: prev?.misconception)
        )
        dao.upsert(next.toEntity())
        return next
    }

    override suspend fun putState(state: ConditionProbeState) {
        dao.upsert(state.toEntity())
    }

    override suspend fun summaries(claimIds: List<String>): Map<String, ConditionProbeSummary> {
        if (claimIds.isEmpty()) return emptyMap()
        val grouped = dao.findByClaimIds(claimIds).map { it.toDomain() }.groupBy { it.claimId }
        return claimIds.associateWith { summarize(it, grouped[it].orEmpty()) }
    }

    override fun observeSummaries(claimIds: List<String>): Flow<Map<String, ConditionProbeSummary>> =
        if (claimIds.isEmpty()) flowOf(emptyMap()) else dao.observeByClaimIds(claimIds).map { rows ->
            val grouped = rows.map { it.toDomain() }.groupBy { it.claimId }
            claimIds.associateWith { summarize(it, grouped[it].orEmpty()) }
        }

    override suspend fun pruneOrphans(aliveTripleIds: List<String>): Int =
        if (aliveTripleIds.isEmpty()) {
            // 与 KgStateRepository 同一取舍：空列表多半是图谱没加载成功，不能误删。
            0
        } else {
            dao.deleteOrphans(aliveTripleIds)
        }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }

    /**
     * 把一条命题下的记录折成卡片事实。
     *
     * 顺序约定：按最近作答时间倒序，所以 `distinct()` 之后自然是「最近错的在前」。
     * 两个列表都截到 [ProbeRules.MAX_MISCONCEPTIONS] 条 —— 回写是摘要，不是明细。
     */
    private fun summarize(claimId: String, rows: List<ConditionProbeState>): ConditionProbeSummary {
        if (rows.isEmpty()) return ConditionProbeSummary(claimId = claimId)

        val byRecent = rows.sortedByDescending { it.lastProbedAt ?: 0L }
        val missed = byRecent.filter { it.wrongAttempts > 0 }

        return ConditionProbeSummary(
            claimId = claimId,
            attempts = rows.sumOf { it.attempts },
            wrongCount = rows.sumOf { it.wrongAttempts },
            missedInterventionIds = missed
                .map { it.interventionId }
                .distinct()
                .take(ProbeRules.MAX_MISCONCEPTIONS),
            misconceptions = missed
                .mapNotNull { it.misconception }
                .distinct()
                .take(ProbeRules.MAX_MISCONCEPTIONS),
            lastProbedAt = rows.maxOfOrNull { it.lastProbedAt ?: 0L }?.takeIf { it > 0L }
        )
    }
}
