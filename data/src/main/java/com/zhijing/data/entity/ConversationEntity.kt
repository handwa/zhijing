package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 会话表 conversation_table。
 *
 * 一次对话会话。digest 保存被挤出上下文的旧消息提要，
 * digest_up_to_seq 标记提要覆盖到第几条消息，用于恢复时跳过重算。
 */
@Entity(tableName = "conversation_table")
internal data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 会话标题。 */
    @ColumnInfo(name = "title")
    val title: String,

    /** 本次会话的系统提示词。 */
    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String,

    /** 被挤出上下文的旧消息提要（规则抽取）。 */
    @ColumnInfo(name = "digest")
    val digest: String? = null,

    /** 提要覆盖到第几条消息（message_table.seq）。 */
    @ColumnInfo(name = "digest_up_to_seq", defaultValue = "0")
    val digestUpToSeq: Int = 0,

    /** 创建时间（毫秒时间戳）。 */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** 最后活跃时间（毫秒时间戳）。 */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)