package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 说明书 3.4.3 主题表 topic_table。
 *
 * 主题名本身是主键，`parent_tag` 构成层级，用于认知庭院的主题树。
 */
@Entity(
    tableName = "topic_table",
    indices = [Index(value = ["parent_tag"])]
)
internal data class TopicEntity(
    /** 主题名（主键）。 */
    @PrimaryKey
    @ColumnInfo(name = "tag_name")
    val tagName: String,

    /** 父主题，构成层级；顶层主题为 null。 */
    @ColumnInfo(name = "parent_tag")
    val parentTag: String? = null,

    /** 主题下内容条数。 */
    @ColumnInfo(name = "content_count", defaultValue = "0")
    val contentCount: Int = 0,

    /** 显示颜色。 */
    @ColumnInfo(name = "color", defaultValue = "0")
    val color: Int = 0
)
