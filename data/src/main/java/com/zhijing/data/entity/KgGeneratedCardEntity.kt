package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * 生成图谱的卡片表 `kg_generated_card`。
 *
 * 正文（`body`）来自**原文逐字截取**，不是模型重写 —— 这与 `OutlineBuilder` 的
 * 分工一致：模型只指认区间，正文由本地切。
 *
 * 主键是 `(topic_id, card_id)`：同一主题重新生成时整份覆盖。
 */
@Entity(
    tableName = "kg_generated_card",
    primaryKeys = ["topic_id", "card_id"],
    indices = [
        Index(value = ["topic_id"]),
        Index(value = ["topic_id", "outline_path"])
    ]
)
internal data class KgGeneratedCardEntity(
    @ColumnInfo(name = "topic_id")
    val topicId: String,

    /** 卡片 id，主题内唯一。 */
    @ColumnInfo(name = "card_id")
    val cardId: String,

    /** 挂在哪个大纲节点下。 */
    @ColumnInfo(name = "outline_path")
    val outlinePath: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    /** 正文：原文逐字截取。 */
    @ColumnInfo(name = "body")
    val body: String,

    /** 排序，与大纲 order 对应。 */
    @ColumnInfo(name = "order_index")
    val order: Int
)
