package com.zhijing.data.dao

import com.zhijing.data.TestDatabase
import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.entity.AssociationEntity
import com.zhijing.data.entity.VectorEntity
import com.zhijing.data.model.ChunkPayload
import com.zhijing.data.model.ImportChannel
import com.zhijing.data.model.ParseTaskStatus
import com.zhijing.data.model.ParseTaskType
import com.zhijing.data.model.StanceCompare
import com.zhijing.data.model.StanceStatus
import com.zhijing.data.model.VectorOwnerType
import com.zhijing.data.repository.DefaultContentRepository
import com.zhijing.data.repository.DefaultStanceCompareRepository
import com.zhijing.data.vector.VectorCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 删除一致性与事务测试。
 *
 * 说明书 6.3 / 7.3：删除或一键清空的数据，要连同向量、关联、对照、问答记录一起删干净。
 * vector_table 是多态 owner 无法建外键，因此这里重点验证数据层的显式清理。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CascadeDeleteTest {

    private lateinit var db: ZhijingDatabase
    private lateinit var contents: DefaultContentRepository
    private lateinit var stanceCompares: DefaultStanceCompareRepository

    @Before
    fun setUp() {
        db = TestDatabase.createInMemory()
        contents = TestDatabase.contentRepository(db)
        stanceCompares = DefaultStanceCompareRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 写入一条带分段的内容，并补齐向量、关联、观点对照等派生数据。 */
    private suspend fun seedContentWithDerivedData(): Long {
        val contentId = contents.saveImportedContent(
            rawText = "这是一段用于测试级联删除的原文。",
            source = "com.example.app",
            importChannel = ImportChannel.SHARE,
            chunks = listOf(
                ChunkPayload("第一段", 0, 3),
                ChunkPayload("第二段", 3, 6)
            ),
            now = 1_000L
        )

        val chunkIds = db.chunkDao().findIdsByContentId(contentId)
        db.vectorDao().upsert(
            VectorEntity(
                ownerType = VectorOwnerType.CONTENT,
                ownerId = contentId,
                dim = VectorCodec.DIM,
                vector = VectorCodec.encode(TestDatabase.unitVector(0)),
                modelId = "bge-small-zh",
                createdTime = 1_000L
            )
        )
        db.vectorDao().upsert(
            VectorEntity(
                ownerType = VectorOwnerType.CHUNK,
                ownerId = chunkIds.first(),
                dim = VectorCodec.DIM,
                vector = VectorCodec.encode(TestDatabase.unitVector(1)),
                modelId = "bge-small-zh",
                createdTime = 1_000L
            )
        )
        db.associationDao().insert(
            AssociationEntity(
                sourceContentId = contentId,
                targetContentId = contentId,
                similarityScore = 0.9f,
                sharedTopics = "RAG"
            )
        )
        stanceCompares.save(
            StanceCompare(
                id = 0,
                topicTag = "端侧模型",
                sideAContentIds = listOf(contentId),
                sideBContentIds = listOf(contentId),
                status = StanceStatus.PENDING
            )
        )
        return contentId
    }

    @Test
    fun saveImportedContentWritesContentChunksAndBothParseTasks() = runBlocking {
        val contentId = contents.saveImportedContent(
            rawText = "分享进来的原文",
            source = null,
            importChannel = ImportChannel.MANUAL,
            chunks = listOf(
                ChunkPayload("第一段", 0, 3),
                ChunkPayload("第二段", 3, 6),
                ChunkPayload("第三段", 6, 9)
            ),
            now = 1_000L
        )

        val chunks = contents.getChunks(contentId)
        assertEquals(3, chunks.size)
        // 段序号按列表顺序从 0 自动编号，保证 (content_id, chunk_index) 唯一。
        assertEquals(listOf(0, 1, 2), chunks.map { it.chunkIndex })

        val tasks = db.parseTaskDao().findByContentId(contentId)
        assertEquals(2, tasks.size)
        assertEquals(
            setOf(ParseTaskType.ANALYZE, ParseTaskType.EMBED),
            tasks.map { it.taskType }.toSet()
        )
        assertTrue(tasks.all { it.status == ParseTaskStatus.QUEUED })
        // 原文立即可读（说明书 4.2.1）。
        assertEquals("分享进来的原文", contents.getContent(contentId)!!.rawText)
    }

    @Test
    fun deleteContentRemovesContentAndAllDerivedRows() = runBlocking {
        val contentId = seedContentWithDerivedData()
        assertEquals(2, contents.getChunks(contentId).size)

        contents.deleteContent(contentId)

        assertNull(contents.getContent(contentId))
        assertTrue(contents.getChunks(contentId).isEmpty())
        assertEquals(0L, db.vectorDao().countByType(VectorOwnerType.CONTENT))
        assertEquals(0L, db.vectorDao().countByType(VectorOwnerType.CHUNK))
        assertTrue(db.associationDao().findRelatedTo(contentId).isEmpty())
        assertTrue(db.parseTaskDao().findByContentId(contentId).isEmpty())
    }

    @Test
    fun deleteAllContentsAlsoClearsStanceCompares() = runBlocking {
        val first = seedContentWithDerivedData()
        val second = seedContentWithDerivedData()

        contents.deleteAllContents()

        assertEquals(0L, contents.countContents())
        assertEquals(0L, db.vectorDao().countByType(VectorOwnerType.CONTENT))
        assertEquals(0L, db.vectorDao().countByType(VectorOwnerType.CHUNK))
        assertTrue(db.stanceCompareDao().findByTopic("端侧模型").isEmpty())
        assertTrue(db.parseTaskDao().findByContentId(first).isEmpty())
        assertTrue(db.parseTaskDao().findByContentId(second).isEmpty())
    }
}
