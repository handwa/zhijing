package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zhijing.data.model.StanceStatus

/**
 * 说明书 3.4.4 观点对照组表 stance_compare_table。
 *
 * 双侧内容 ID 以逗号分隔的 TEXT 存储，由 ListConverters 负责转换。
 * 说明书 4.2.5 要求「内容清空后，相关的话题记录一并删掉」，由 Repository 显式清理。
 */
@Entity(
    tableName = "stance_compare_table",
    indices = [Index(value = ["topic_tag"]), Index(value = ["status"])]
)
internal data class StanceCompareEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 争议话题。 */
    @ColumnInfo(name = "topic_tag")
    val topicTag: String,

    /** 立场 A 的内容 ID。 */
    @ColumnInfo(name = "side_a_content_ids")
    val sideAContentIds: List<Long> = emptyList(),

    /** 立场 B 的内容 ID。 */
    @ColumnInfo(name = "side_b_content_ids")
    val sideBContentIds: List<Long> = emptyList(),

    /** 立场 A 观点摘要（基于各自原文生成）。 */
    @ColumnInfo(name = "side_a_summary")
    val sideASummary: String? = null,

    /** 立场 B 观点摘要（基于各自原文生成）。 */
    @ColumnInfo(name = "side_b_summary")
    val sideBSummary: String? = null,

    /** 0 待展示 / 1 已展示 / 2 用户已看完。 */
    @ColumnInfo(name = "status", defaultValue = "0")
    val status: StanceStatus = StanceStatus.PENDING
)
