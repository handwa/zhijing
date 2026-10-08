package com.zhijing.data.repository

import com.zhijing.data.DbConstants
import com.zhijing.data.TestDatabase
import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.entity.VectorEntity
import com.zhijing.data.model.ChunkPayload
import com.zhijing.data.model.EmbeddingStatus
import com.zhijing.data.model.ImportChannel
import com.zhijing.data.model.VectorOwnerType
import com.zhijing.data.vector.VectorCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 内容门面的端到端测试：写入校验、向量双写一致性与相似检索。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentRepositoryTest {

    private lateinit var db: ZhijingDatabase
    private lateinit var repository: DefaultContentRepository

    @Before
    fun setUp() {
        db = TestDatabase.createInMemory()
        repository = TestDatabase.contentRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertWithVector(text: String, vectorIndex: Int, now: Long): Long {
        val id = repository.saveImportedContent(
            rawText = text,
            source = "com.example.app",
            importChannel = ImportChannel.SHARE,
            enqueueParseTasks = false,
            now = now
        )
        repository.updateEmbedding(id, TestDatabase.unitVector(vectorIndex), "bge-small-zh", EmbeddingStatus.DONE)
        return id
    }

    @Test
    fun saveImportedContentRejectsBlankText() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.saveImportedContent(
                    rawText = "   \n  ",
                    source = null,
                    importChannel = ImportChannel.MANUAL
                )
            }
        }
        assertEquals(0L, runBlocking { repository.countContents() })
    }

    @Test
    fun saveImportedContentTruncatesOverlongText() = runBlocking {
        val overlong = "字".repeat(DbConstants.MAX_RAW_TEXT_LENGTH + 500)

        val id = repository.saveImportedContent(
            rawText = overlong,
            source = null,
            importChannel = ImportChannel.MANUAL,
            enqueueParseTasks = false
        )

        val stored = repository.getContent(id)!!
        assertEquals(DbConstants.MAX_RAW_TEXT_LENGTH, stored.rawText.length)
    }

    @Test
    fun saveImportedContentDefaultsToEnqueuingParseTasks() = runBlocking {
        val id = repository.saveImportedContent(
            rawText = "默认应入队解析任务",
            source = null,
            importChannel = ImportChannel.SHARE
        )

        assertEquals(2, db.parseTaskDao().findByContentId(id).size)
    }

    @Test
    fun updateEmbeddingWritesVectorTableAndContentColumnTogether() = runBlocking {
        val id = repository.saveImportedContent(
            rawText = "需要向量化的内容",
            source = null,
            importChannel = ImportChannel.SHARE,
            enqueueParseTasks = false
        )

        repository.updateEmbedding(id, TestDatabase.unitVector(0), "bge-small-zh", EmbeddingStatus.DONE)

        val stored = repository.getContent(id)!!
        assertEquals(EmbeddingStatus.DONE, stored.embeddingStatus)
        assertTrue(stored.hasEmbedding)
        val vectorRow = db.vectorDao().find(VectorOwnerType.CONTENT, id)
        assertNotNull(vectorRow)
        assertEquals(VectorCodec.DIM, vectorRow!!.dim)
        assertEquals("bge-small-zh", vectorRow.modelId)
    }

    @Test
    fun updateEmbeddingWithNullVectorKeepsPreviousVectorAndOnlyChangesStatus() = runBlocking {
        val id = insertWithVector("已有向量", 0, 1_000L)

        repository.updateEmbedding(id, null, "bge-small-zh", EmbeddingStatus.FAILED)

        val stored = repository.getContent(id)!!
        assertEquals(EmbeddingStatus.FAILED, stored.embeddingStatus)
        assertTrue(stored.hasEmbedding)
        assertEquals(1L, db.vectorDao().countByType(VectorOwnerType.CONTENT))
    }

    @Test
    fun updateEmbeddingRejectsWrongDimension() {
        val id = runBlocking {
            repository.saveImportedContent(
                rawText = "维度不对",
                source = null,
                importChannel = ImportChannel.MANUAL,
                enqueueParseTasks = false
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.updateEmbedding(id, FloatArray(8), "bge-small-zh", EmbeddingStatus.DONE)
            }
        }
    }

    @Test
    fun searchSimilarFiltersByThresholdAndOrdersByScore() = runBlocking {
        val exact = insertWithVector("与问题完全同向", 0, 2_000L)
        val diagonal = insertWithVector("部分相关", 0, 1_000L)
        // 让 diagonal 与 query 的余弦落到 0.707，更接近真实检索场景
        db.vectorDao().upsert(
            VectorEntity(
                ownerType = VectorOwnerType.CONTENT,
                ownerId = diagonal,
                dim = VectorCodec.DIM,
                vector = VectorCodec.encode(
                    FloatArray(VectorCodec.DIM).also {
                        it[0] = 0.70710677f
                        it[1] = 0.70710677f
                    }
                ),
                modelId = "bge-small-zh",
                createdTime = 1_000L
            )
        )
        insertWithVector("不相关", 1, 3_000L)

        val hits = repository.searchSimilar(
            queryVector = TestDatabase.unitVector(0),
            topK = DbConstants.RETRIEVAL_TOP_K,
            threshold = DbConstants.SIMILARITY_THRESHOLD
        )

        assertEquals(listOf(exact, diagonal), hits.map { it.contentId })
        assertEquals(1f, hits[0].score, 1e-5f)
        assertEquals(0.707f, hits[1].score, 1e-3f)
        assertEquals("与问题完全同向", hits[0].rawText)
    }

    @Test
    fun searchSimilarRespectsTopKLimit() = runBlocking {
        insertWithVector("A", 0, 1_000L)
        insertWithVector("B", 0, 2_000L)

        val hits = repository.searchSimilar(
            queryVector = TestDatabase.unitVector(0),
            topK = 1,
            threshold = 0f
        )

        assertEquals(1, hits.size)
    }

    @Test
    fun searchSimilarOnChunksReturnsMatchedChunkText() = runBlocking {
        val contentId = repository.saveImportedContent(
            rawText = "第一段的完整原文。第二段的完整原文。",
            source = "com.example.app",
            importChannel = ImportChannel.SHARE,
            chunks = listOf(
                ChunkPayload("第一段的完整原文。", 0, 9),
                ChunkPayload("第二段的完整原文。", 9, 18)
            ),
            enqueueParseTasks = false
        )
        val chunkIds = db.chunkDao().findIdsByContentId(contentId)
        db.vectorDao().upsert(
            VectorEntity(
                ownerType = VectorOwnerType.CHUNK,
                ownerId = chunkIds[1],
                dim = VectorCodec.DIM,
                vector = VectorCodec.encode(TestDatabase.unitVector(0)),
                modelId = "bge-small-zh",
                createdTime = 1_000L
            )
        )

        val hits = repository.searchSimilar(
            queryVector = TestDatabase.unitVector(0),
            topK = 5,
            threshold = DbConstants.SIMILARITY_THRESHOLD,
            includeChunks = true
        )

        assertEquals(1, hits.size)
        assertEquals(contentId, hits[0].contentId)
        assertEquals(1, hits[0].matchedChunkIndex)
        assertEquals("第二段的完整原文。", hits[0].matchedText)
    }

    @Test
    fun searchSimilarReturnsEmptyWhenQueryDimensionIsWrong() = runBlocking {
        insertWithVector("有向量的内容", 0, 1_000L)

        val hits = repository.searchSimilar(queryVector = FloatArray(16))

        assertTrue(hits.isEmpty())
    }

    @Test
    fun updateRemarkIsPersistedAndObservable() = runBlocking {
        val id = repository.saveImportedContent(
            rawText = "带备注的内容",
            source = null,
            importChannel = ImportChannel.MANUAL,
            enqueueParseTasks = false
        )

        repository.updateRemark(id, "复习时再看")

        assertEquals("复习时再看", repository.getContent(id)!!.remark)
        val recent = repository.observeRecentContents(limit = 10).first()
        assertEquals("复习时再看", recent.first { it.id == id }.remark)
    }
}
