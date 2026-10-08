package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zhijing.data.model.ParseTaskStatus
import com.zhijing.data.model.ParseTaskType

/**
 * 说明书 3.4.8 解析任务表 parse_task_table。
 *
 * 说明书未给该表定义主键，Room 要求必须有主键，这里补自增主键 `id`；
 * 其余字段与说明书一致。外键指向 content_table 并级联删除，
 * 内容被删除时其排队任务一并清理。
 */
@Entity(
    tableName = "parse_task_table",
    foreignKeys = [
        ForeignKey(
            entity = ContentEntity::class,
            parentColumns = ["id"],
            childColumns = ["content_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["content_id"]),
        Index(value = ["status"])
    ]
)
internal data class ParseTaskEntity(
    /** 数据层补充的自增主键，非说明书字段。 */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 要处理的内容。 */
    @ColumnInfo(name = "content_id")
    val contentId: Long,

    /** analyze（大模型理解）或 embed（向量化）。 */
    @ColumnInfo(name = "task_type")
    val taskType: ParseTaskType,

    /** 0 排队 / 1 执行 / 2 完成 / 3 失败。 */
    @ColumnInfo(name = "status", defaultValue = "0")
    val status: ParseTaskStatus = ParseTaskStatus.QUEUED,

    /** 已重试次数（最多 3 次）。 */
    @ColumnInfo(name = "retry_count", defaultValue = "0")
    val retryCount: Int = 0,

    /** 入队时间（毫秒时间戳）。 */
    @ColumnInfo(name = "created_time")
    val createdTime: Long
)
