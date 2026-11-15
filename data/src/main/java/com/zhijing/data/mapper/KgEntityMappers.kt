package com.zhijing.data.mapper

import com.zhijing.data.entity.KgClaimMergeEntity
import com.zhijing.data.entity.KgClaimStateEntity
import com.zhijing.data.entity.KgConditionProbeEntity
import com.zhijing.data.entity.KgRuntimeClaimEntity
import com.zhijing.data.model.ClaimMerge
import com.zhijing.data.model.ClaimProbeState
import com.zhijing.data.model.ConditionProbeState
import com.zhijing.data.model.ClaimReviewStatus
import com.zhijing.data.model.ClaimSource
import com.zhijing.data.model.ProbeStatus
import com.zhijing.data.model.RuntimeClaim

/**
 * 知识图谱运行时数据（状态 / 增量命题 / 同义合并）的实体 ⇄ 领域模型映射。
 *
 * 与 [Mappers.kt] 同一约定：只有本文件知道数据库里字符串列的具体表示；
 * `List<String>` 列的一致性由 `StringListConverters` 保证，这里只做枚举的 wire ⇄ 类型转换。
 */

internal fun KgClaimStateEntity.toDomain(): ClaimProbeState = ClaimProbeState(
    claimId = claimId,
    probeStatus = ProbeStatus.fromWire(probeStatus),
    lastProbedAt = lastProbedAt,
    misconceptions = misconceptions,
    confidence = confidence,
    attempts = attempts,
    correct = correct,
    streak = streak
)

internal fun ClaimProbeState.toEntity(): KgClaimStateEntity = KgClaimStateEntity(
    claimId = claimId,
    probeStatus = probeStatus.wire,
    lastProbedAt = lastProbedAt,
    misconceptions = misconceptions,
    confidence = confidence,
    attempts = attempts,
    correct = correct,
    streak = streak
)

internal fun KgConditionProbeEntity.toDomain(): ConditionProbeState = ConditionProbeState(
    tripleId = tripleId,
    interventionId = interventionId,
    claimId = claimId,
    predictedOption = predictedOption,
    correctOption = correctOption,
    matched = matched,
    attempts = attempts,
    wrongAttempts = wrongAttempts,
    lastProbedAt = lastProbedAt,
    misconception = misconception
)

internal fun ConditionProbeState.toEntity(): KgConditionProbeEntity = KgConditionProbeEntity(
    tripleId = tripleId,
    interventionId = interventionId,
    claimId = claimId,
    predictedOption = predictedOption,
    correctOption = correctOption,
    matched = matched,
    attempts = attempts,
    wrongAttempts = wrongAttempts,
    lastProbedAt = lastProbedAt,
    misconception = misconception
)

internal fun KgRuntimeClaimEntity.toDomain(): RuntimeClaim = RuntimeClaim(
    claimId = claimId,
    statement = statement,
    expected = expected,
    conditions = conditions,
    explanation = explanation,
    misconception = misconception,
    evidence = evidence,
    cardId = cardId,
    outlinePath = outlinePath,
    source = ClaimSource.fromWire(source),
    reviewStatus = ClaimReviewStatus.fromWire(reviewStatus),
    providerId = providerId,
    createdTime = createdTime,
    reviewedTime = reviewedTime
)

internal fun RuntimeClaim.toEntity(embedding: ByteArray? = null): KgRuntimeClaimEntity =
    KgRuntimeClaimEntity(
        claimId = claimId,
        statement = statement,
        expected = expected,
        conditions = conditions,
        explanation = explanation,
        misconception = misconception,
        evidence = evidence,
        cardId = cardId,
        outlinePath = outlinePath,
        source = source.wire,
        reviewStatus = reviewStatus.wire,
        providerId = providerId,
        embedding = embedding,
        createdTime = createdTime,
        reviewedTime = reviewedTime
    )

internal fun KgClaimMergeEntity.toDomain(): ClaimMerge = ClaimMerge(
    sourceClaimId = sourceClaimId,
    targetClaimId = targetClaimId,
    similarity = similarity,
    basis = basis,
    createdTime = createdTime
)

internal fun ClaimMerge.toEntity(): KgClaimMergeEntity = KgClaimMergeEntity(
    sourceClaimId = sourceClaimId,
    targetClaimId = targetClaimId,
    similarity = similarity,
    basis = basis,
    createdTime = createdTime
)
