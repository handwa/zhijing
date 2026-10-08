package com.zhijing.data.dao

import com.zhijing.data.TestDatabase
import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.entity.ContentEntity
import com.zhijing.data.model.AnalysisStatus
import com.zhijing.data.model.EmbeddingStatus
import com.zhijing.data.model.ImportChannel
import com.zhijing.data.vector.VectorCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** content_table 的增删改查与状态筛选测试（说明书 3.4.1）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentDaoTest {

    private lateinit var db: ZhijingDatabase
    private lateinit var dao: ContentDao

    @Before
    fun setUp() {
        db = TestDatabase.createInMemory()
        dao = db.contentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun content(text: String, importTime: Long) = ContentEntity(
        rawText = text,
        source = "com.example.app",
        importTime = importTime,
        importChannel = ImportChannel.SHARE
    )

    @Test
    fun insertThenFindById() = runBlocking {
        val id = dao.insert(content("第一篇收藏", 1_000L))

        val loaded = dao.findById(id)

        assertNotNull(loaded)
        assertEquals("第一篇收藏", loaded!!.rawText)
        assertEquals("com.example.app", loaded.source)
        assertEquals(ImportChannel.SHARE, loaded.importChannel)
        assertEquals(AnalysisStatus.PENDING, loaded.analysisStatus)
        assertEquals(EmbeddingStatus.PENDING, loaded.embeddingStatus)
        assertNull(loaded.embeddingVector)
    }

    @Test
    fun observeAllOrdersByImportTimeDescending() = runBlocking {
        dao.insert(content("旧", 1_000L))
        dao.insert(content("新", 3_000L))
        dao.insert(content("中", 2_000L))

        val texts = dao.observeAll().first().map { it.rawText }

        assertEquals(listOf("新", "中", "旧"), texts)
    }

    @Test
    fun findByAnalysisStatusFiltersByStatus() = runBlocking {
        val done = dao.insert(content("已完成", 1_000L))
        dao.insert(content("待处理", 2_000L))
        dao.updateAnalysis(done, """{"keywords":["a"]}""", AnalysisStatus.DONE)

        val pending = dao.findByAnalysisStatus(AnalysisStatus.PENDING, limit = 10)
        val finished = dao.findByAnalysisStatus(AnalysisStatus.DONE, limit = 10)

        assertEquals(listOf("待处理"), pending.map { it.rawText })
        assertEquals(listOf("已完成"), finished.map { it.rawText })
        assertEquals("""{"keywords":["a"]}""", finished.first().analysisJson)
    }

    @Test
    fun updateEmbeddingStoresVectorAndStatus() = runBlocking {
        val id = dao.insert(content("待向量化", 1_000L))
        val bytes = VectorCodec.encode(TestDatabase.unitVector(0))

        dao.updateEmbedding(id, bytes, EmbeddingStatus.DONE)

        val loaded = dao.findById(id)!!
        assertEquals(EmbeddingStatus.DONE, loaded.embeddingStatus)
        assertEquals(VectorCodec.BYTE_SIZE, loaded.embeddingVector!!.size)
        assertTrue(loaded.embeddingVector!!.contentEquals(bytes))
    }

    @Test
    fun updateEmbeddingStatusKeepsPreviouslyStoredVector() = runBlocking {
        val id = dao.insert(content("已向量化", 1_000L))
        val bytes = VectorCodec.encode(TestDatabase.unitVector(3))
        dao.updateEmbedding(id, bytes, EmbeddingStatus.DONE)

        // 重新向量化失败时只改状态，不应丢掉上一次可用的向量。
        dao.updateEmbeddingStatus(id, EmbeddingStatus.FAILED)

        val loaded = dao.findById(id)!!
        assertEquals(EmbeddingStatus.FAILED, loaded.embeddingStatus)
        assertNotNull(loaded.embeddingVector)
        assertTrue(loaded.embeddingVector!!.contentEquals(bytes))
    }

    @Test
    fun updateRemarkAndDeleteById() = runBlocking {
        val id = dao.insert(content("待删除", 1_000L))

        dao.updateRemark(id, "我的备注")
        assertEquals("我的备注", dao.findById(id)!!.remark)

        dao.deleteById(id)
        assertNull(dao.findById(id))
    }

    @Test
    fun countAndObserveCountReflectInserts() = runBlocking {
        assertEquals(0L, dao.count())

        dao.insert(content("A", 1_000L))
        dao.insert(content("B", 2_000L))

        assertEquals(2L, dao.count())
        assertEquals(2L, dao.observeCount().first())
    }

    @Test
    fun findByIdsReturnsMatchingRows() = runBlocking {
        val first = dao.insert(content("A", 1_000L))
        val second = dao.insert(content("B", 2_000L))
        dao.insert(content("C", 3_000L))

        val found = dao.findByIds(listOf(first, second)).map { it.rawText }.sorted()

        assertEquals(listOf("A", "B"), found)
    }
}
