package com.example.zhijing.chat

import android.util.Log
import com.arm.aichat.InferenceEngine
import com.zhijing.data.model.EmbeddingStatus
import com.zhijing.data.model.ImportChannel
import com.zhijing.data.repository.ContentRepository
import java.io.File

/**
 * 把一段原始文本变成可检索的向量索引。
 *
 * 流程：分段 → 逐段嵌入 → 写 content/chunk/vector 三张表。
 *
 * 这是 RAG 的「写入侧」，检索侧见 [com.zhijing.data.repository.ContentRepository.searchSimilar]。
 *
 * 内存约束：嵌入是串行的，一次只算一段，不攒大数组。几千段文本也只占几 MB 峰值。
 */
class RagIndexer(
    private val engine: InferenceEngine,
    private val contents: ContentRepository,
    private val embeddingModelId: String,
) {

    companion object {
        private const val TAG = "RagIndexer"
    }

    /**
     * 索引一段文本并落库。
     *
     * @param text 原始文本
     * @param source 来源标识（包名、文件名或网址），用于溯源
     * @param importChannel 导入渠道
     * @param onProgress 进度回调 (已嵌入段数, 总段数)；可为 null
     * @return 新内容的 id；文本为空时返回 0
     */
    suspend fun indexText(
        text: String,
        source: String? = null,
        importChannel: ImportChannel = ImportChannel.MANUAL,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): Long {
        if (text.isBlank()) {
            Log.w(TAG, "empty text, nothing indexed")
            return 0
        }

        if (engine.embeddingDim() == 0) {
            Log.e(TAG, "embedding model not loaded, abort")
            throw IllegalStateException("嵌入模型未加载，无法建立索引")
        }

        // ① 分段
        val pieces = TextChunker.split(text)
        Log.i(TAG, "indexing ${text.length} chars -> ${pieces.size} chunks")

        // ② 落库（内容 + 分段），先得到 chunk 主键
        val contentId = contents.saveImportedContent(
            rawText = text,
            source = source,
            importChannel = importChannel,
            chunks = pieces,
            // 这里的 analyze 任务由内容理解模块消费，与向量化无关，
            // 向量由本类当场算完，不需要 embed 任务排队。
            enqueueParseTasks = false,
        )

        // ③ 逐段嵌入（串行，一次一段，控制内存峰值）
        val vectors = ArrayList<FloatArray>(pieces.size)
        var failed = 0
        pieces.forEachIndexed { index, piece ->
            val vec = engine.embed(piece.text)
            if (vec == null) {
                failed++
                Log.w(TAG, "embedding failed at chunk $index")
            } else {
                vectors += vec
            }
            onProgress?.invoke(index + 1, pieces.size)
        }

        if (failed > 0) {
            // 有段失败就整体标记失败，避免部分向量造成检索结果不一致
            contents.updateEmbedding(
                contentId = contentId,
                vector = null,
                modelId = embeddingModelId,
                status = EmbeddingStatus.FAILED,
            )
            Log.e(TAG, "indexing failed: $failed/${pieces.size} chunks unembedded")
            throw IllegalStateException("有 $failed 段嵌入失败，索引未完成")
        }

        // ④ 写分段向量
        contents.replaceChunkEmbeddings(contentId, vectors, embeddingModelId)

        // ⑤ 内容级向量：用全文的均值，供「整篇相似」检索
        val mean = meanVector(vectors)
        if (mean == null) {
            contents.updateEmbedding(
                contentId = contentId,
                vector = null,
                modelId = embeddingModelId,
                status = EmbeddingStatus.FAILED,
            )
            throw IllegalStateException("分段向量维度不一致，无法合成内容级向量")
        }
        contents.updateEmbedding(
            contentId = contentId,
            vector = mean,
            modelId = embeddingModelId,
            status = EmbeddingStatus.DONE,
        )

        Log.i(TAG, "indexed content $contentId with ${vectors.size} chunk vectors, dim=${mean.size}")
        return contentId
    }

    /**
     * 导入一个文档：解析成文本后走 [indexText] 的完整索引流程。
     *
     * 支持的格式见 [DocumentParsers.supportedExtensions]（当前 txt/md/docx/doc）。
     *
     * @param file 待导入的文件
     * @param onProgress 进度回调 (已处理段数, 总段数)；解析阶段也会回调粗粒度进度
     * @return 索引结果：新内容 id + 解析出的原文
     * @throws DocumentParseException 格式不支持、文件损坏或没有可提取文字
     */
    suspend fun indexDocument(
        file: File,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): IndexedDocument {
        if (engine.embeddingDim() == 0) {
            throw IllegalStateException("嵌入模型未加载，无法建立索引")
        }

        Log.i(TAG, "parsing document: ${file.name} (${file.length()} bytes)")
        val t0 = System.currentTimeMillis()
        val text = DocumentParsers.extractText(file)
        Log.i(
            TAG,
            "parsed ${file.name} -> ${text.length} chars in ${System.currentTimeMillis() - t0} ms",
        )

        val contentId = indexText(
            text = text,
            source = file.name,
            importChannel = ImportChannel.MANUAL,
            onProgress = onProgress,
        )
        // 一并把原文返回：调用方（图谱抽取）需要它，避免再解析一遍文件
        return IndexedDocument(contentId = contentId, text = text, source = file.name)
    }

    /** 一次文档导入的结果。 */
    data class IndexedDocument(
        val contentId: Long,
        /** 解析出的完整原文，供后续抽取（如图谱命题）使用。 */
        val text: String,
        val source: String
    )

    /**
     * 对分段向量取均值再归一化。
     *
     * 整篇内容可能远超 512 token，无法直接嵌入，用分段均值是常见近似做法。
     */
    private fun meanVector(vectors: List<FloatArray>): FloatArray? {
        if (vectors.isEmpty()) return null
        val dim = vectors.first().size
        val sum = FloatArray(dim)
        for (v in vectors) {
            if (v.size != dim) return null
            for (i in 0 until dim) sum[i] += v[i]
        }
        val inv = 1f / vectors.size
        var norm = 0.0
        for (i in 0 until dim) {
            sum[i] *= inv
            norm += sum[i].toDouble() * sum[i]
        }
        norm = kotlin.math.sqrt(norm)
        if (norm > 1e-12) {
            val invNorm = (1.0 / norm).toFloat()
            for (i in 0 until dim) sum[i] *= invNorm
        }
        return sum
    }
}
