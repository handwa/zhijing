package com.zhijing.data.vector

import kotlin.math.sqrt

/**
 * 纯 Kotlin 的余弦相似度与 Top-K 召回。
 *
 * 说明书 6.2 允许把向量放进 sqlite-vec 或 FAISS；当前实现选了「BLOB 存储 + Kotlin 手算」，
 * 零原生依赖，面向几十~几百条数据量：N=500 时约 500×512 次乘加，
 * 远低于说明书 5.1「问答检索一秒以内」的预算，因此不额外做内存缓存。
 * 需要时可通过 [VectorSearchStore] 接口替换为 sqlite-vec 实现。
 */
internal object CosineSimilarity {

    /** 余弦相似度。长度不一致、任一为空或模长为 0 时返回 0，避免除零崩溃。 */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            normA += x * x
            normB += y * y
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator <= 0.0) 0f else (dot / denominator).toFloat()
    }

    /**
     * 在候选集中取相似度最高的 [topK] 条。
     *
     * @param candidates (ownerId, 向量) 列表，向量维度应为 512（通过 [VectorCodec.decode] 得到）。
     * @param threshold 相似度阈值，低于该值的结果被过滤，对齐说明书内置参数 similarity_threshold。
     * @return 按相似度降序排列的 (ownerId, score)，最多 [topK] 条。
     */
    fun topK(
        query: FloatArray,
        candidates: List<Pair<Long, FloatArray>>,
        topK: Int,
        threshold: Float
    ): List<Pair<Long, Float>> {
        if (topK <= 0 || query.isEmpty()) return emptyList()
        return candidates.asSequence()
            .map { (ownerId, vector) -> ownerId to cosine(query, vector) }
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
            .take(topK)
            .toList()
    }
}
