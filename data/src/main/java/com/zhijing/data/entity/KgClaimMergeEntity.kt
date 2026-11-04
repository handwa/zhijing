package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * 同义合并表 `kg_claim_merge`。
 *
 * 解决的问题：命题 ID 由 `sha1(规范化 statement)[:4]` 推导（见 `kg/schema/id-spec.md`）。
 * 出厂命题 ID 固定，没问题；但云端每次生成的措辞略有不同，ID 就会变。
 * 若不处理，数据库里会出现「同义不同 ID」两条记录，而判断状态还挂在旧 ID 上。
 *
 * 本表把「派生 ID」映射到「规范 ID」，所有读写先过这张表，等价于给 ID 加了一层重定向。
 * 合并依据三种：
 *
 * - `hash`：ID 完全相同（无语义漂移，无需合并，不会写入本表）；
 * - `vector`：本地 bge 向量余弦相似度超过阈值；
 * - `manual`：用户在 UI 上手动指定合并。
 *
 * 为什么 source 也要建索引：一次云端生成可能批量产出十几条候选，
 * 需要「这批里哪些已经被合并过」的快速查询。
 */
@Entity(
    tableName = "kg_claim_merge",
    primaryKeys = ["source_claim_id"],
    indices = [
        Index(value = ["target_claim_id"]),
        Index(value = ["created_time"])
    ]
)
internal data class KgClaimMergeEntity(

    /** 派生出来的命题 ID，作为主键：一条源 ID 只能指向一个规范 ID，避免链式映射。 */
    @ColumnInfo(name = "source_claim_id")
    val sourceClaimId: String,

    /** 合并到的规范命题 ID。 */
    @ColumnInfo(name = "target_claim_id")
    val targetClaimId: String,

    /** 触发合并时的余弦相似度；manual 合并填 1.0。 */
    @ColumnInfo(name = "similarity", defaultValue = "1.0")
    val similarity: Float = 1.0f,

    /** 依据：vector / manual。 */
    @ColumnInfo(name = "basis", defaultValue = "'vector'")
    val basis: String = "vector",

    /** 建立时间（毫秒时间戳）。 */
    @ColumnInfo(name = "created_time")
    val createdTime: Long
)
