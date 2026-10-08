package com.zhijing.data.model

/**
 * 内容与分段相关的对外领域模型。
 *
 * 这些类型是 `:data` 模块的公开契约，故意与 Room 实体分离，满足说明书 7.2
 * 「数据入口、内容理解、检索、界面展示分开」——上层模块不会看到 BLOB、TypeConverter
 * 或任何存储细节。
 */

/** 一条收藏内容（content_table 的对外视图）。 */
data class Content(
    val id: Long,
    val rawText: String,
    val source: String?,
    val importTime: Long,
    /** 解析结果 JSON，结构由内容理解模块定义。 */
    val analysisJson: String?,
    val analysisStatus: AnalysisStatus,
    val embeddingStatus: EmbeddingStatus,
    val remark: String?,
    val importChannel: ImportChannel,
    /** 是否已生成向量。原始向量不出模块，检索统一走 ContentRepository.searchSimilar。 */
    val hasEmbedding: Boolean
)

/** 一个分段（chunk_table 的对外视图）。 */
data class Chunk(
    val id: Long,
    val contentId: Long,
    val chunkIndex: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val charCount: Int
)

/**
 * 入库时传入的分段载荷。
 *
 * 不要求调用方给段序号：[com.zhijing.data.repository.ContentRepository.saveImportedContent]
 * 会按列表顺序从 0 依次编号，保证 (content_id, chunk_index) 唯一。
 */
data class ChunkPayload(
    val text: String,
    val startOffset: Int,
    val endOffset: Int
)

/** 一次相似度检索的命中结果。 */
data class RetrievedContent(
    val contentId: Long,
    /** 余弦相似度，越大越相关。 */
    val score: Float,
    val rawText: String,
    val source: String?,
    val importTime: Long,
    /** 命中分段时给出段序号与段文本，便于回答里引用原文片段。 */
    val matchedChunkIndex: Int? = null,
    val matchedText: String? = null
)
