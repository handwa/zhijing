package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 生成图谱的元信息表 `kg_generated_graph`。
 *
 * 每个用户输入的主题一行。**与出厂图谱的区别**：
 * 出厂图谱在 `assets/kg/graph.json`（只读出版物），这张表存的是用户生成的（可写、可删）。
 *
 * 主键是 `topic_id`：同一主题重复生成会整份覆盖（先删卡再写），
 * 不保留历史版本 —— 保留多版本会让"当前图谱是哪一份"变模糊。
 */
@Entity(
    tableName = "kg_generated_graph",
    indices = [Index(value = ["created_time"])]
)
internal data class KgGeneratedGraphEntity(
    /** 主题标识，如 `topic-linear-algebra`。 */
    @PrimaryKey
    @ColumnInfo(name = "topic_id")
    val topicId: String,

    @ColumnInfo(name = "title")
    val title: String,

    /** 生成时间（毫秒）。 */
    @ColumnInfo(name = "created_time")
    val createdTime: Long,

    /** 来源：cloud / local / manual。 */
    @ColumnInfo(name = "source", defaultValue = "'cloud'")
    val source: String = "cloud",

    /** 服务商 id；本地生成为 null。 */
    @ColumnInfo(name = "provider_id")
    val providerId: String? = null,

    /** 模型切出的块数。 */
    @ColumnInfo(name = "block_count", defaultValue = "0")
    val blockCount: Int = 0,

    /** 生成警告，JSON 数组字符串。 */
    @ColumnInfo(name = "warnings", defaultValue = "'[]'")
    val warnings: String = "[]",

    /**
     * 正文是否可溯源。
     *
     * 1 = 有原文，正文逐字截取；0 = 只有主题，正文由模型生成（未核实）。
     */
    @ColumnInfo(name = "verified", defaultValue = "1")
    val verified: Boolean = true
)
