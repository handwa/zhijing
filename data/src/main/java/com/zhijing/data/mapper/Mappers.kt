package com.zhijing.data.mapper

import com.zhijing.data.entity.AssociationEntity
import com.zhijing.data.entity.ChunkEntity
import com.zhijing.data.entity.ContentEntity
import com.zhijing.data.entity.ParseTaskEntity
import com.zhijing.data.entity.PetStatusEntity
import com.zhijing.data.entity.QaRecordEntity
import com.zhijing.data.entity.StanceCompareEntity
import com.zhijing.data.entity.TopicEntity
import com.zhijing.data.entity.WakePlanEntity
import com.zhijing.data.model.Association
import com.zhijing.data.model.Chunk
import com.zhijing.data.model.ChunkPayload
import com.zhijing.data.model.Content
import com.zhijing.data.model.ParseTask
import com.zhijing.data.model.PetStatus
import com.zhijing.data.model.QaRecord
import com.zhijing.data.model.StanceCompare
import com.zhijing.data.model.Topic
import com.zhijing.data.model.WakePlan

/**
 * 实体 ⇄ 领域模型的双向映射，全部 `internal`，是本模块唯一允许知道
 * 存储细节的地方。上层只看到 `model` 包里的类型。
 */

internal fun ContentEntity.toDomain(): Content = Content(
    id = id,
    rawText = rawText,
    source = source,
    importTime = importTime,
    analysisJson = analysisJson,
    analysisStatus = analysisStatus,
    embeddingStatus = embeddingStatus,
    remark = remark,
    importChannel = importChannel,
    hasEmbedding = embeddingVector != null
)

internal fun ChunkEntity.toDomain(): Chunk = Chunk(
    id = id,
    contentId = contentId,
    chunkIndex = chunkIndex,
    text = text,
    startOffset = startOffset,
    endOffset = endOffset,
    charCount = charCount
)

/** 按列表顺序编号，保证 (content_id, chunk_index) 唯一。 */
internal fun ChunkPayload.toEntity(contentId: Long, index: Int): ChunkEntity = ChunkEntity(
    contentId = contentId,
    chunkIndex = index,
    text = text,
    startOffset = startOffset,
    endOffset = endOffset,
    charCount = text.length
)

internal fun AssociationEntity.toDomain(): Association = Association(
    id = id,
    sourceContentId = sourceContentId,
    targetContentId = targetContentId,
    similarityScore = similarityScore,
    sharedTopics = sharedTopics,
    evidenceSnippet = evidenceSnippet,
    feedback = feedback,
    weight = weight
)

internal fun Association.toEntity(): AssociationEntity = AssociationEntity(
    id = id,
    sourceContentId = sourceContentId,
    targetContentId = targetContentId,
    similarityScore = similarityScore,
    sharedTopics = sharedTopics,
    evidenceSnippet = evidenceSnippet,
    feedback = feedback,
    weight = weight
)

internal fun TopicEntity.toDomain(): Topic = Topic(
    tagName = tagName,
    parentTag = parentTag,
    contentCount = contentCount,
    color = color
)

internal fun Topic.toEntity(): TopicEntity = TopicEntity(
    tagName = tagName,
    parentTag = parentTag,
    contentCount = contentCount,
    color = color
)

internal fun StanceCompareEntity.toDomain(): StanceCompare = StanceCompare(
    id = id,
    topicTag = topicTag,
    sideAContentIds = sideAContentIds,
    sideBContentIds = sideBContentIds,
    sideASummary = sideASummary,
    sideBSummary = sideBSummary,
    status = status
)

internal fun StanceCompare.toEntity(): StanceCompareEntity = StanceCompareEntity(
    id = id,
    topicTag = topicTag,
    sideAContentIds = sideAContentIds,
    sideBContentIds = sideBContentIds,
    sideASummary = sideASummary,
    sideBSummary = sideBSummary,
    status = status
)

internal fun WakePlanEntity.toDomain(): WakePlan = WakePlan(
    id = id,
    enabled = enabled,
    cycleType = cycleType,
    triggerTime = triggerTime,
    scope = scope,
    mode = mode,
    dailyQuestionNum = dailyQuestionNum,
    nextRunTime = nextRunTime
)

internal fun WakePlan.toEntity(): WakePlanEntity = WakePlanEntity(
    id = id,
    enabled = enabled,
    cycleType = cycleType,
    triggerTime = triggerTime,
    scope = scope,
    mode = mode,
    dailyQuestionNum = dailyQuestionNum,
    nextRunTime = nextRunTime
)

internal fun QaRecordEntity.toDomain(): QaRecord = QaRecord(
    id = id,
    questionText = questionText,
    answerText = answerText,
    sourceContentIds = sourceContentIds,
    retrievalMs = retrievalMs,
    generateMs = generateMs,
    answerStatus = answerStatus
)

internal fun QaRecord.toEntity(): QaRecordEntity = QaRecordEntity(
    id = id,
    questionText = questionText,
    answerText = answerText,
    sourceContentIds = sourceContentIds,
    retrievalMs = retrievalMs,
    generateMs = generateMs,
    answerStatus = answerStatus
)

internal fun PetStatusEntity.toDomain(): PetStatus = PetStatus(
    phase = phase,
    totalContents = totalContents,
    activeDays = activeDays,
    accessories = accessories,
    lastDisplayTime = lastDisplayTime
)

internal fun PetStatus.toEntity(): PetStatusEntity = PetStatusEntity(
    id = PetStatusEntity.SINGLETON_ID,
    phase = phase,
    totalContents = totalContents,
    activeDays = activeDays,
    accessories = accessories,
    lastDisplayTime = lastDisplayTime
)

internal fun ParseTaskEntity.toDomain(): ParseTask = ParseTask(
    id = id,
    contentId = contentId,
    taskType = taskType,
    status = status,
    retryCount = retryCount,
    createdTime = createdTime
)

internal fun ParseTask.toEntity(): ParseTaskEntity = ParseTaskEntity(
    id = id,
    contentId = contentId,
    taskType = taskType,
    status = status,
    retryCount = retryCount,
    createdTime = createdTime
)
