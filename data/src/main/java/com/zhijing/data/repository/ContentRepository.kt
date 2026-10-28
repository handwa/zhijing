package com.zhijing.data.repository

import androidx.room.withTransaction
import com.zhijing.data.DbConstants
import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.mapper.toDomain
import com.zhijing.data.mapper.toEntity
import com.zhijing.data.model.AnalysisStatus
import com.zhijing.data.model.Chunk
import com.zhijing.data.model.ChunkPayload
import com.zhijing.data.model.Content
import com.zhijing.data.model.EmbeddingStatus
import com.zhijing.data.model.ImportChannel
import com.zhijing.data.model.RetrievedContent
import com.zhijing.data.model.VectorOwnerType
import com.zhijing.data.entity.ContentEntity
import com.zhijing.data.entity.ParseTaskEntity
import com.zhijing.data.entity.VectorEntity
import com.zhijing.data.model.ParseTaskType
import com.zhijing.data.vector.VectorCodec
import com.zhijing.data.vector.VectorSearchStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 收藏内容与分段、向量的统一门面。
 *
 * 这是内容理解、检索、问答、庭院等模块访问 `content_table` / `chunk_table` /
 * `vector_table` 的唯一入口，隐藏 Room 实体与 BLOB 细节（说明书 7.2 代码分层）。
 *
 * 涉及多表的写入一律在单个事务内完成，保证说明书 5.2「内容、标签、向量、关联之间保持一致」。
 */
interface ContentRepository {

    /** 首页「最近收藏」（说明书 6.1.2）。 */
    fun observeRecentContents(limit: Int = DEFAULT_RECENT_LIMIT): Flow<List<Content>>

    fun observeAllContents(): Flow<List<Content>>

    fun observeContentCount(): Flow<Long>

    suspend fun getContent(id: Long): Content?

    suspend fun getContents(ids: List<Long>): List<Content>

    suspend fun getChunks(contentId: Long): List<Chunk>

    suspend fun countContents(): Long

    /**
     * 保存一条分享进来的内容（说明书 4.2.1「原文立即写入本地」）。
     *
     * 一次事务内写入内容、分段，并按需把「理解」与「向量化」两个解析任务入队
     * （说明书 3.4.8、4.2.2）。超过 [DbConstants.MAX_RAW_TEXT_LENGTH] 字的原文会截断。
     *
     * @param chunks 分段载荷；段序号按列表顺序自动编号，调用方无需指定。
     * @param enqueueParseTasks 是否同时入队解析任务，默认 true。
     * @return 新内容的 id。
     * @throws IllegalArgumentException 原文为空时（说明书 4.2.1：空内容不入库）。
     */
    suspend fun saveImportedContent(
        rawText: String,
        source: String?,
        importChannel: ImportChannel,
        remark: String? = null,
        chunks: List<ChunkPayload> = emptyList(),
        enqueueParseTasks: Boolean = true,
        now: Long = System.currentTimeMillis()
    ): Long

    /** 内容理解模块回写解析结果与状态。 */
    suspend fun updateAnalysis(contentId: Long, analysisJson: String?, status: AnalysisStatus)

    /**
     * 向量化模块回写向量。
     *
     * 在同一事务内同时更新 `vector_table`（唯一真源）与 `content_table.embedding_vector`，
     * 避免两份数据漂移。`vector` 为 null 时只更新状态、保留已有向量。
     *
     * @param vector 512 维向量；维度不符会抛 [IllegalArgumentException]。
     */
    suspend fun updateEmbedding(
        contentId: Long,
        vector: FloatArray?,
        modelId: String,
        status: EmbeddingStatus,
        now: Long = System.currentTimeMillis()
    )

    suspend fun updateRemark(contentId: Long, remark: String?)

    /**
     * 批量写入分段级向量（RAG 检索用）。
     *
     * `vector_table` 是多态 owner，无法建外键，所以这里：
     * 1. 先删该内容已有的分段向量，避免重新索引后残留旧段；
     * 2. 再按传入顺序写入新向量，与 `chunk_table.chunk_index` 一一对应。
     *
     * @param vectors 与 [getChunks] 返回顺序一致；长度为 0 时只做清理。
     * @param modelId 产出这些向量的嵌入模型标识，换模型后可据此判断失效。
     */
    suspend fun replaceChunkEmbeddings(
        contentId: Long,
        vectors: List<FloatArray>,
        modelId: String,
        now: Long = System.currentTimeMillis()
    )

    /**
     * 删除一条内容及其全部派生数据。
     *
     * 说明书 6.3/7.3：删除的数据要连同向量、关联、对照、问答记录一起删干净。
     * `chunk_table` / `association_table` / `parse_task_table` 有外键级联，
     * `vector_table` 是多态 owner 无法建外键，因此这里显式清理，形成双保险。
     */
    suspend fun deleteContent(contentId: Long)

    /** 清空全部收藏及其派生数据（分段、向量、关联、解析任务、观点对照）。 */
    suspend fun deleteAllContents()

    /**
     * 相似度检索（说明书 4.2.3 本地问答、4.2.4 关联推荐）。
     *
     * @param includeChunks true 时在分段级向量上检索，命中结果带 [RetrievedContent.matchedText]，
     *                      便于回答里引用原文片段；false 时在内容级向量上检索。
     * @param topK 默认 5，对齐说明书 retrieval_top_k。
     * @param threshold 默认 0.65，对齐说明书 similarity_threshold。
     */
    suspend fun searchSimilar(
        queryVector: FloatArray,
        topK: Int = DbConstants.RETRIEVAL_TOP_K,
        threshold: Float = DbConstants.SIMILARITY_THRESHOLD,
        includeChunks: Boolean = false
    ): List<RetrievedContent>

    companion object {
        const val DEFAULT_RECENT_LIMIT = 50
    }
}

internal class DefaultContentRepository(
    private val db: ZhijingDatabase,
    private val searchStore: VectorSearchStore
) : ContentRepository {

    private val contentDao = db.contentDao()
    private val chunkDao = db.chunkDao()
    private val vectorDao = db.vectorDao()
    private val associationDao = db.associationDao()
    private val parseTaskDao = db.parseTaskDao()

    override fun observeRecentContents(limit: Int): Flow<List<Content>> =
        contentDao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllContents(): Flow<List<Content>> =
        contentDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeContentCount(): Flow<Long> = contentDao.observeCount()

    override suspend fun getContent(id: Long): Content? = contentDao.findById(id)?.toDomain()

    override suspend fun getContents(ids: List<Long>): List<Content> =
        if (ids.isEmpty()) emptyList() else contentDao.findByIds(ids).map { it.toDomain() }

    override suspend fun getChunks(contentId: Long): List<Chunk> =
        chunkDao.findByContentId(contentId).map { it.toDomain() }

    override suspend fun countContents(): Long = contentDao.count()

    override suspend fun saveImportedContent(
        rawText: String,
        source: String?,
        importChannel: ImportChannel,
        remark: String?,
        chunks: List<ChunkPayload>,
        enqueueParseTasks: Boolean,
        now: Long
    ): Long {
        val trimmed = rawText.trim()
        require(trimmed.isNotEmpty()) { "空内容不入库" }
        val limited = trimmed.take(DbConstants.MAX_RAW_TEXT_LENGTH)

        return db.withTransaction {
            val contentId = contentDao.insert(
                ContentEntity(
                    rawText = limited,
                    source = source,
                    importTime = now,
                    importChannel = importChannel,
                    remark = remark
                )
            )

            if (chunks.isNotEmpty()) {
                chunkDao.insertAll(chunks.mapIndexed { index, payload -> payload.toEntity(contentId, index) })
            }

            if (enqueueParseTasks) {
                parseTaskDao.insertAll(
                    listOf(
                        ParseTaskEntity(contentId = contentId, taskType = ParseTaskType.ANALYZE, createdTime = now),
                        ParseTaskEntity(contentId = contentId, taskType = ParseTaskType.EMBED, createdTime = now)
                    )
                )
            }

            contentId
        }
    }

    override suspend fun updateAnalysis(contentId: Long, analysisJson: String?, status: AnalysisStatus) {
        contentDao.updateAnalysis(contentId, analysisJson, status)
    }

    override suspend fun updateEmbedding(
        contentId: Long,
        vector: FloatArray?,
        modelId: String,
        status: EmbeddingStatus,
        now: Long
    ) {
        val bytes = vector?.let { VectorCodec.encode(it) }
        if (bytes == null) {
            // 没有新向量：只更新状态，保留上一次可用的向量，避免失败时把数据抹掉。
            contentDao.updateEmbeddingStatus(contentId, status)
            return
        }

        db.withTransaction {
            vectorDao.upsert(
                VectorEntity(
                    ownerType = VectorOwnerType.CONTENT,
                    ownerId = contentId,
                    dim = VectorCodec.DIM,
                    vector = bytes,
                    modelId = modelId,
                    createdTime = now
                )
            )
            contentDao.updateEmbedding(contentId, bytes, status)
        }
    }

    override suspend fun updateRemark(contentId: Long, remark: String?) {
        contentDao.updateRemark(contentId, remark)
    }

    override suspend fun replaceChunkEmbeddings(
        contentId: Long,
        vectors: List<FloatArray>,
        modelId: String,
        now: Long,
    ) {
        db.withTransaction {
            val chunkIds = chunkDao.findIdsByContentId(contentId)

            // 先清掉旧的分段向量，防止重新索引后段数变少时残留脏数据
            if (chunkIds.isNotEmpty()) {
                vectorDao.deleteByOwnerIds(VectorOwnerType.CHUNK, chunkIds)
            }

            if (chunkIds.size != vectors.size) {
                // 段与向量必须一一对应，否则检索会张冠李戴
                throw IllegalArgumentException(
                    "chunk count (${chunkIds.size}) != vector count (${vectors.size}) for content $contentId"
                )
            }

            chunkIds.forEachIndexed { index, chunkId ->
                val bytes = VectorCodec.encode(vectors[index])
                vectorDao.upsert(
                    VectorEntity(
                        ownerType = VectorOwnerType.CHUNK,
                        ownerId = chunkId,
                        dim = VectorCodec.DIM,
                        vector = bytes,
                        modelId = modelId,
                        createdTime = now,
                    )
                )
            }
        }
    }

    override suspend fun deleteContent(contentId: Long) {
        db.withTransaction {
            val chunkIds = chunkDao.findIdsByContentId(contentId)

            vectorDao.delete(VectorOwnerType.CONTENT, contentId)
            if (chunkIds.isNotEmpty()) {
                vectorDao.deleteByOwnerIds(VectorOwnerType.CHUNK, chunkIds)
            }
            chunkDao.deleteByContentId(contentId)
            associationDao.deleteByContentId(contentId)
            parseTaskDao.deleteByContentId(contentId)

            // content_table 删除后，外键 CASCADE 会再次清理 chunk/association/parse_task。
            contentDao.deleteById(contentId)
        }
    }

    override suspend fun deleteAllContents() {
        db.withTransaction {
            // 说明书 4.2.5：内容清空后，相关的话题记录一并删掉。
            db.stanceCompareDao().deleteAll()
            vectorDao.deleteAll()
            chunkDao.deleteAll()
            associationDao.deleteAll()
            parseTaskDao.deleteAll()
            contentDao.deleteAll()
        }
    }

    override suspend fun searchSimilar(
        queryVector: FloatArray,
        topK: Int,
        threshold: Float,
        includeChunks: Boolean
    ): List<RetrievedContent> {
        if (queryVector.size != VectorCodec.DIM) return emptyList()

        val ownerType = if (includeChunks) VectorOwnerType.CHUNK else VectorOwnerType.CONTENT
        val hits = searchStore.topK(queryVector, ownerType, topK, threshold)
        if (hits.isEmpty()) return emptyList()

        if (ownerType == VectorOwnerType.CONTENT) {
            val contents = contentDao.findByIds(hits.map { it.first }).associateBy { it.id }
            return hits.mapNotNull { (contentId, score) ->
                contents[contentId]?.let { entity ->
                    RetrievedContent(
                        contentId = entity.id,
                        score = score,
                        rawText = entity.rawText,
                        source = entity.source,
                        importTime = entity.importTime
                    )
                }
            }
        }

        val chunks = chunkDao.findByIds(hits.map { it.first }).associateBy { it.id }
        val contents = contentDao.findByIds(chunks.values.map { it.contentId }.distinct()).associateBy { it.id }
        return hits.mapNotNull { (chunkId, score) ->
            val chunk = chunks[chunkId] ?: return@mapNotNull null
            val entity = contents[chunk.contentId] ?: return@mapNotNull null
            RetrievedContent(
                contentId = entity.id,
                score = score,
                rawText = entity.rawText,
                source = entity.source,
                importTime = entity.importTime,
                matchedChunkIndex = chunk.chunkIndex,
                matchedText = chunk.text
            )
        }
    }
}
