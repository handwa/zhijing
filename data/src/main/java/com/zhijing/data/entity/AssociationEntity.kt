package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zhijing.data.model.AssociationFeedback

/**
 * 说明书 3.4.2 关联记录表 association_table。
 *
 * 两端都外键指向 content_table 并在删除内容时级联清理，满足 6.3/7.3
 * 「删除或一键清空的数据，连同向量、关联、对照、问答记录一起删干净」。
 */
@Entity(
    tableName = "association_table",
    foreignKeys = [
        ForeignKey(
            entity = ContentEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_content_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ContentEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_content_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["source_content_id"]),
        Index(value = ["target_content_id"])
    ]
)
internal data class AssociationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 新收藏的内容。 */
    @ColumnInfo(name = "source_content_id")
    val sourceContentId: Long,

    /** 与之关联的历史内容。 */
    @ColumnInfo(name = "target_content_id")
    val targetContentId: Long,

    /** 相似度。 */
    @ColumnInfo(name = "similarity_score")
    val similarityScore: Float,

    /** 共同的标签或关键词。 */
    @ColumnInfo(name = "shared_topics")
    val sharedTopics: String? = null,

    /** 支撑关联的原文片段。 */
    @ColumnInfo(name = "evidence_snippet")
    val evidenceSnippet: String? = null,

    /** 0 未评价 / 1 很好 / 2 平常 / 3 不好。 */
    @ColumnInfo(name = "feedback", defaultValue = "0")
    val feedback: AssociationFeedback = AssociationFeedback.NOT_RATED,

    /** 权重，受反馈影响。 */
    @ColumnInfo(name = "weight", defaultValue = "0")
    val weight: Double = 0.0
)
