package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * 生成图谱的卡片前置关系表 `kg_generated_prerequisite`。
 *
 * 数据来自模型在每块 `requires` 字段里的输出，本地映射成 cardId 后存入。
 *
 * 方向：`from_card_id` 是先学的，`to_card_id` 是后学的。
 * 渲染时 **required 画实线、recommended 画虚线**。
 *
 * 主键是 `(topic_id, from_card_id, to_card_id)`：同一对卡片只能有一条关系，
 * 重复写入会覆盖（而不是堆积成多条重复的线）。
 */
@Entity(
    tableName = "kg_generated_prerequisite",
    primaryKeys = ["topic_id", "from_card_id", "to_card_id"],
    indices = [
        Index(value = ["topic_id"]),
        Index(value = ["topic_id", "to_card_id"])
    ]
)
internal data class KgGeneratedPrerequisiteEntity(
    @ColumnInfo(name = "topic_id")
    val topicId: String,

    /** 先学这张卡。 */
    @ColumnInfo(name = "from_card_id")
    val fromCardId: String,

    /** 后学这张卡。 */
    @ColumnInfo(name = "to_card_id")
    val toCardId: String,

    /** required / recommended。 */
    @ColumnInfo(name = "kind", defaultValue = "'recommended'")
    val kind: String = "recommended",

    /** 依赖点说明。 */
    @ColumnInfo(name = "why", defaultValue = "''")
    val why: String = ""
)
