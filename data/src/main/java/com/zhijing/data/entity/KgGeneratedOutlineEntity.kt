package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * 生成图谱的大纲节点表 `kg_generated_outline`。
 *
 * 层级由 `OutlineBuilder` 从标题编号推出（模型不给 `level` / `parentPath`），
 * 因此这里存的是**本地算法加工后**的结果，可直接渲染。
 */
@Entity(
    tableName = "kg_generated_outline",
    primaryKeys = ["topic_id", "path"],
    indices = [Index(value = ["topic_id"])]
)
internal data class KgGeneratedOutlineEntity(
    @ColumnInfo(name = "topic_id")
    val topicId: String,

    /** 层级路径，如 `t-a3f1.t-b2c4`。 */
    @ColumnInfo(name = "path")
    val path: String,

    /** 父节点路径；顶层为 null。 */
    @ColumnInfo(name = "parent_path")
    val parentPath: String? = null,

    @ColumnInfo(name = "title")
    val title: String,

    /** 1–3 级。 */
    @ColumnInfo(name = "level")
    val level: Int,

    /** 同级排序。 */
    @ColumnInfo(name = "order_index")
    val order: Int
)
