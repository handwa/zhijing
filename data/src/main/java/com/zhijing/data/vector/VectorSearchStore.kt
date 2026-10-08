package com.zhijing.data.vector

import com.zhijing.data.dao.VectorDao
import com.zhijing.data.model.VectorOwnerType

/**
 * 相似度检索的抽象。
 *
 * 说明书 6.2 允许把向量放进 sqlite-vec 或 FAISS。当前实现 [RoomVectorSearchStore]
 * 从 `vector_table` 取出 BLOB 后在 Kotlin 侧手算余弦；日后要换成 sqlite-vec，
 * 只需替换本接口的实现，Repository 与上层无需改动。
 */
internal interface VectorSearchStore {

    /**
     * 返回相似度最高的若干 (ownerId, score)。
     *
     * @param ownerType 检索内容级向量（content）还是分段级向量（chunk）。
     */
    suspend fun topK(
        query: FloatArray,
        ownerType: VectorOwnerType,
        topK: Int,
        threshold: Float
    ): List<Pair<Long, Float>>
}

/** 基于 Room BLOB 列的默认实现：整表取回 → 解码 → 手算余弦 → Top-K。 */
internal class RoomVectorSearchStore(
    private val vectorDao: VectorDao
) : VectorSearchStore {

    override suspend fun topK(
        query: FloatArray,
        ownerType: VectorOwnerType,
        topK: Int,
        threshold: Float
    ): List<Pair<Long, Float>> {
        val rows = vectorDao.findAllByType(ownerType)
        if (rows.isEmpty()) return emptyList()
        // 维度不符或损坏的 BLOB 会被 decode 过滤掉，单条坏数据不影响整体检索。
        val candidates = rows.mapNotNull { row ->
            VectorCodec.decode(row.vector)?.let { row.ownerId to it }
        }
        return CosineSimilarity.topK(query, candidates, topK, threshold)
    }
}
