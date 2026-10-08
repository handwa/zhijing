package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zhijing.data.model.VectorOwnerType

/**
 * 向量表 vector_table（说明书未定义，用于统一承载 512 维向量）。
 *
 * 说明书 3.4.1 要求 content_table 保留 `embedding_vector` 列，同时 6.2 要求
 * 「向量模型输出 512 维向量……支持按内容增删改」。为同时满足两点并支持分段级向量：
 *
 * - `vector_table` 是向量的**唯一真源**，内容级与分段级向量都放这里；
 * - 写入内容级向量时，在同一事务内同步刷新 `content_table.embedding_vector`，
 *   避免两份数据漂移（见 `DefaultContentRepository.updateEmbedding`）。
 *
 * 由于 owner 是多态的（content / chunk），无法建外键，级联清理由 Repository 显式完成。
 * `model_id` 用于换嵌入模型后判定旧向量是否失效。
 */
@Entity(
    tableName = "vector_table",
    indices = [
        Index(value = ["owner_type", "owner_id"], unique = true),
        Index(value = ["owner_type"])
    ]
)
internal data class VectorEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** content 或 chunk。 */
    @ColumnInfo(name = "owner_type")
    val ownerType: VectorOwnerType,

    /** 对应 content_table.id 或 chunk_table.id。 */
    @ColumnInfo(name = "owner_id")
    val ownerId: Long,

    /** 维度，当前恒为 512。 */
    @ColumnInfo(name = "dim")
    val dim: Int,

    /** 小端 float 序列化后的向量。 */
    @ColumnInfo(name = "vector")
    val vector: ByteArray,

    /** 产出该向量的嵌入模型标识。 */
    @ColumnInfo(name = "model_id")
    val modelId: String,

    /** 写入时间（毫秒时间戳）。 */
    @ColumnInfo(name = "created_time")
    val createdTime: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VectorEntity) return false
        return id == other.id &&
            ownerType == other.ownerType &&
            ownerId == other.ownerId &&
            dim == other.dim &&
            modelId == other.modelId &&
            createdTime == other.createdTime &&
            vector.contentEquals(other.vector)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + ownerType.hashCode()
        result = 31 * result + ownerId.hashCode()
        result = 31 * result + dim
        result = 31 * result + modelId.hashCode()
        result = 31 * result + createdTime.hashCode()
        result = 31 * result + vector.contentHashCode()
        return result
    }
}
