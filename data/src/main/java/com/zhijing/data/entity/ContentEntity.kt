package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zhijing.data.model.AnalysisStatus
import com.zhijing.data.model.EmbeddingStatus
import com.zhijing.data.model.ImportChannel

/**
 * 说明书 3.4.1 内容表 content_table。
 *
 * 字段名、类型、语义与说明书逐字对齐，唯一改动是为「最近收藏」与状态筛选补了索引。
 */
@Entity(
    tableName = "content_table",
    indices = [
        Index(value = ["import_time"]),
        Index(value = ["analysis_status"]),
        Index(value = ["embedding_status"])
    ]
)
internal data class ContentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 原文（分享链接提取正文后超过 5000 字截断，截断由数据层保证）。 */
    @ColumnInfo(name = "raw_text")
    val rawText: String,

    /** 来源 App 包名或网址。 */
    @ColumnInfo(name = "source")
    val source: String? = null,

    /** 收藏时间（毫秒时间戳）。 */
    @ColumnInfo(name = "import_time")
    val importTime: Long,

    /** 解析结果（关键词/实体/情感/主题/立场，JSON）。由内容理解模块产出。 */
    @ColumnInfo(name = "analysis_json")
    val analysisJson: String? = null,

    /** 0 待处理 / 1 处理中 / 2 完成 / 3 失败。 */
    @ColumnInfo(name = "analysis_status", defaultValue = "0")
    val analysisStatus: AnalysisStatus = AnalysisStatus.PENDING,

    /** 512 维向量，小端 float 序列化后的 BLOB；未生成时为 null。 */
    @ColumnInfo(name = "embedding_vector")
    val embeddingVector: ByteArray? = null,

    /** 向量是否已生成。 */
    @ColumnInfo(name = "embedding_status", defaultValue = "0")
    val embeddingStatus: EmbeddingStatus = EmbeddingStatus.PENDING,

    /** 用户备注。 */
    @ColumnInfo(name = "remark")
    val remark: String? = null,

    /** share / whitelist / manual。 */
    @ColumnInfo(name = "import_channel")
    val importChannel: ImportChannel = ImportChannel.MANUAL
) {
    // ByteArray 的默认 equals 是引用比较，这里按内容比较，便于测试与去重判断。
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContentEntity) return false
        return id == other.id &&
            rawText == other.rawText &&
            source == other.source &&
            importTime == other.importTime &&
            analysisJson == other.analysisJson &&
            analysisStatus == other.analysisStatus &&
            embeddingStatus == other.embeddingStatus &&
            remark == other.remark &&
            importChannel == other.importChannel &&
            embeddingVector.contentEquals(other.embeddingVector)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + rawText.hashCode()
        result = 31 * result + (source?.hashCode() ?: 0)
        result = 31 * result + importTime.hashCode()
        result = 31 * result + (analysisJson?.hashCode() ?: 0)
        result = 31 * result + analysisStatus.hashCode()
        result = 31 * result + embeddingStatus.hashCode()
        result = 31 * result + (remark?.hashCode() ?: 0)
        result = 31 * result + importChannel.hashCode()
        result = 31 * result + (embeddingVector?.contentHashCode() ?: 0)
        return result
    }
}
