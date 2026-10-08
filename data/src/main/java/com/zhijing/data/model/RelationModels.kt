package com.zhijing.data.model

/** 关联、主题、观点对照三类「知识关系」的对外领域模型。 */

/** 一条关联记录（association_table 的对外视图）。说明书 4.2.4 的关联卡片。 */
data class Association(
    val id: Long,
    /** 新收藏的内容。 */
    val sourceContentId: Long,
    /** 与之关联的历史内容。 */
    val targetContentId: Long,
    val similarityScore: Float,
    val sharedTopics: String? = null,
    val evidenceSnippet: String? = null,
    val feedback: AssociationFeedback = AssociationFeedback.NOT_RATED,
    val weight: Double = 0.0
)

/** 一个主题（topic_table 的对外视图），`parentTag` 构成层级。 */
data class Topic(
    val tagName: String,
    val parentTag: String? = null,
    val contentCount: Int = 0,
    val color: Int = 0
)

/** 一组观点对照（stance_compare_table 的对外视图）。 */
data class StanceCompare(
    val id: Long,
    val topicTag: String,
    val sideAContentIds: List<Long> = emptyList(),
    val sideBContentIds: List<Long> = emptyList(),
    val sideASummary: String? = null,
    val sideBSummary: String? = null,
    val status: StanceStatus = StanceStatus.PENDING
)
