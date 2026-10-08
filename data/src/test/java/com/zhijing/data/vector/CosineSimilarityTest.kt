package com.zhijing.data.vector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [CosineSimilarity] 的纯 JVM 测试，覆盖阈值过滤、Top-K 排序与除零防护。 */
class CosineSimilarityTest {

    /** 第 [index] 个分量为 1 的单位向量。 */
    private fun unit(index: Int): FloatArray = FloatArray(VectorCodec.DIM).also { it[index] = 1f }

    /** a、b 两个单位方向各占一半，模长归一化后余弦为 √2/2 ≈ 0.707。 */
    private fun diagonal(): FloatArray = FloatArray(VectorCodec.DIM).also {
        it[0] = 0.70710677f
        it[1] = 0.70710677f
    }

    @Test
    fun identicalVectorsScoreOne() {
        assertEquals(1f, CosineSimilarity.cosine(unit(0), unit(0)), 1e-5f)
    }

    @Test
    fun orthogonalVectorsScoreZero() {
        assertEquals(0f, CosineSimilarity.cosine(unit(0), unit(1)), 1e-6f)
    }

    @Test
    fun oppositeVectorsScoreMinusOne() {
        val negative = FloatArray(VectorCodec.DIM).also { it[0] = -1f }
        assertEquals(-1f, CosineSimilarity.cosine(unit(0), negative), 1e-5f)
    }

    @Test
    fun zeroVectorDoesNotProduceNaN() {
        val zero = FloatArray(VectorCodec.DIM)
        val score = CosineSimilarity.cosine(zero, unit(0))
        assertEquals(0f, score, 0f)
        assertTrue(score == score) // 不是 NaN
    }

    @Test
    fun mismatchedLengthOrEmptyReturnsZero() {
        assertEquals(0f, CosineSimilarity.cosine(FloatArray(4), FloatArray(8)), 0f)
        assertEquals(0f, CosineSimilarity.cosine(FloatArray(0), FloatArray(0)), 0f)
    }

    @Test
    fun topKFiltersByThresholdAndKeepsDescendingOrder() {
        val candidates = listOf(
            1L to unit(0),      // 与 query 完全一致 -> 1.0
            2L to diagonal(),   // -> 0.707
            3L to unit(1)       // -> 0.0，应被阈值过滤
        )

        val hits = CosineSimilarity.topK(query = unit(0), candidates = candidates, topK = 5, threshold = 0.6f)

        assertEquals(listOf(1L, 2L), hits.map { it.first })
        assertEquals(1f, hits[0].second, 1e-5f)
        assertEquals(0.707f, hits[1].second, 1e-3f)
    }

    @Test
    fun topKLimitsResultCount() {
        val candidates = listOf(
            1L to unit(0),
            2L to diagonal(),
            3L to unit(1)
        )

        val hits = CosineSimilarity.topK(query = unit(0), candidates = candidates, topK = 1, threshold = 0f)

        assertEquals(1, hits.size)
        assertEquals(1L, hits.first().first)
    }

    @Test
    fun topKWithNonPositiveCountReturnsEmpty() {
        val hits = CosineSimilarity.topK(query = unit(0), candidates = listOf(1L to unit(0)), topK = 0, threshold = 0f)
        assertTrue(hits.isEmpty())
    }
}
