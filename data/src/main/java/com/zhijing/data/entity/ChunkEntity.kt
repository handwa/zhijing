package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 分段表 chunk_table（说明书未定义，为段落级 RAG 预留）。
 *
 * 说明书 4.2.3 的问答与 4.2.4 的关联推荐都需要「引用原文片段」，
 * 而 content_table.raw_text 最长 5000 字，直接整篇转向量粒度太粗，
 * 因此把长文切成分段，段级向量存放在 vector_table 中（owner_type = "chunk"）。
 *
 * 外键指向 content_table 并级联删除，(content_id, chunk_index) 唯一。
 */
@Entity(
    tableName = "chunk_table",
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
        Index(value = ["content_id", "chunk_index"], unique = true)
    ]
)
internal data class ChunkEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 所属内容。 */
    @ColumnInfo(name = "content_id")
    val contentId: Long,

    /** 段序号，从 0 开始。 */
    @ColumnInfo(name = "chunk_index")
    val chunkIndex: Int,

    /** 段文本。 */
    @ColumnInfo(name = "text")
    val text: String,

    /** 在原文中的起始字符下标（含）。 */
    @ColumnInfo(name = "start_offset")
    val startOffset: Int,

    /** 在原文中的结束字符下标（不含）。 */
    @ColumnInfo(name = "end_offset")
    val endOffset: Int,

    /** 段文本字符数，便于检索时快速判断片段长度。 */
    @ColumnInfo(name = "char_count")
    val charCount: Int
)
