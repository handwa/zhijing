package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息表 message_table。
 *
 * 归档区：会话内所有消息按 seq 顺序保存，永不删除。
 * 删除会话时级联删除消息。
 */
@Entity(
    tableName = "message_table",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversation_id"]),
        Index(value = ["conversation_id", "seq"], unique = true)
    ]
)
internal data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 所属会话。 */
    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,

    /** system / user / assistant。 */
    @ColumnInfo(name = "role")
    val role: String,

    /** 消息正文。 */
    @ColumnInfo(name = "content")
    val content: String,

    /** 估算的 token 数，用于预算计算。 */
    @ColumnInfo(name = "token_count")
    val tokenCount: Int,

    /** 会话内序号，从 1 递增。 */
    @ColumnInfo(name = "seq")
    val seq: Int,

    /** 创建时间（毫秒时间戳）。 */
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)