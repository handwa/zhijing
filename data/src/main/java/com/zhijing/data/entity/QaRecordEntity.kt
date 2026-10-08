package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zhijing.data.model.AnswerStatus

/**
 * 说明书 3.4.6 问答记录表 qa_record_table。
 *
 * `source_content_ids` 用于回答引用溯源（说明书 4.2.7：生成记录里保存引用来源）。
 */
@Entity(
    tableName = "qa_record_table",
    indices = [Index(value = ["answer_status"])]
)
internal data class QaRecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 用户的问题。 */
    @ColumnInfo(name = "question_text")
    val questionText: String,

    /** 生成的回答。 */
    @ColumnInfo(name = "answer_text")
    val answerText: String? = null,

    /** 回答引用的内容 ID，用于追溯。 */
    @ColumnInfo(name = "source_content_ids")
    val sourceContentIds: List<Long> = emptyList(),

    /** 检索耗时（毫秒）。 */
    @ColumnInfo(name = "retrieval_ms", defaultValue = "0")
    val retrievalMs: Long = 0,

    /** 生成耗时（毫秒）。 */
    @ColumnInfo(name = "generate_ms", defaultValue = "0")
    val generateMs: Long = 0,

    /** 0 成功 / 1 无相关内容 / 2 超时 / 3 用户取消。 */
    @ColumnInfo(name = "answer_status", defaultValue = "0")
    val answerStatus: AnswerStatus = AnswerStatus.SUCCESS
)
