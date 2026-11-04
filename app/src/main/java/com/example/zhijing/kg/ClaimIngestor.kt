package com.example.zhijing.kg

import com.zhijing.data.ZhiJingData
import com.zhijing.data.model.ClaimMerge
import com.zhijing.data.model.ClaimReviewStatus
import com.zhijing.data.model.ClaimSource
import com.zhijing.data.model.RuntimeClaim
import kotlinx.coroutines.flow.first

/**
 * 把云端抽取的候选命题变成可入库的 `RuntimeClaim`，并处理 ID 与去重。
 *
 * 这是「图谱由云端生成」链路的**第二半**：`ExtractedClaim` → `RuntimeClaim` → Room。
 *
 * 三个必须在这里解决的问题：
 *
 * 1. **ID 由本地算**，不由模型给。模型写 ID 会瞎编后缀，且每次生成不同，
 *    同一命题无法收敛（见 `extract_claims.md` 第 6 节）。
 * 2. **精确去重**。规范化 statement 相同（只差标点/空格）视为同一条，直接跳过。
 *    近义去重（向量相似度）由 `data` 层负责 —— 向量存储与计算归它管，
 *    这里只传 `embeddingOf` 供其保存。
 * 3. **入库即 pending**。候选不经过用户确认，永远不进广度视图。
 */
class ClaimIngestor(
    private val data: ZhiJingData
) {

    /** 一次入库的结果。 */
    data class Result(
        /** 新插入的候选，等待用户确认。 */
        val inserted: List<RuntimeClaim> = emptyList(),
        /** 因已存在（规范化文本相同）被跳过的命题 ID。 */
        val skippedDuplicate: List<String> = emptyList(),
        /** 因与已有命题近义而建立的合并映射：`派生ID -> 规范ID`。 */
        val merged: Map<String, String> = emptyMap(),
        /** 因不满足最低要求被丢弃的：`(断言, 原因)`。 */
        val rejected: List<Pair<String, String>> = emptyList(),
        val warnings: List<String> = emptyList()
    )

    /**
     * 入库一批候选。
     *
     * @param claims 解析出来的候选
     * @param path 语义路径，决定 ID 中的 slug，如 `arch.ffn.linear`
     * @param cardId 主归属卡，决定候选挂在广度视图的哪张卡下
     * @param outlinePath 大纲路径，与 cardId 对应
     * @param providerId 生成这些候选的服务商 id，如 `deepseek`
     * @param embeddingOf 取命题向量的函数（本地 bge）。返回 null 时只做精确去重。
     * @param now 当前时间戳，便于测试注入
     */
    suspend fun ingest(
        claims: List<ExtractedClaim>,
        path: String,
        cardId: String,
        outlinePath: String,
        providerId: String?,
        embeddingOf: (suspend (String) -> FloatArray?)? = null,
        now: Long = System.currentTimeMillis()
    ): Result {
        val inserted = ArrayList<RuntimeClaim>()
        val skipped = ArrayList<String>()
        val rejected = ArrayList<Pair<String, String>>()
        val warnings = ArrayList<String>()

        // 已有命题（已确认的增量命题）作为精确去重的比对集。
        // 出厂命题在 assets 里，由调用方通过 `existingStatements` 一并传入时才会参与比对。
        val existingNorm = data.kgClaims.observeConfirmed()
            .first()
            .map { ClaimIds.normalize(it.statement) }
            .toMutableSet()

        for (claim in claims) {
            if (!claim.isUsable) {
                rejected += claim.statement to "缺少断言或条件，不满足入库要求"
                continue
            }

            val claimId = ClaimIds.claimId(path, claim.statement)
            if (!ClaimIds.isValidClaimId(claimId)) {
                rejected += claim.statement to "生成的 ID 不合规：$claimId"
                continue
            }

            val norm = ClaimIds.normalize(claim.statement)
            if (norm in existingNorm) {
                skipped += claimId
                warnings += "与已有命题重复，已跳过：「${claim.statement.take(24)}」"
                continue
            }
            // 已被合并到别的命題：本条不再入库，直接复用已有命题
            val canonical = data.kgClaims.resolveClaimId(claimId)
            if (canonical != claimId) {
                skipped += claimId
                warnings += "$claimId 已合并到 $canonical，跳过"
                continue
            }

            val vector = embeddingOf?.invoke(claim.statement)
            val record = RuntimeClaim(
                claimId = claimId,
                statement = claim.statement,
                expected = claim.expected,
                conditions = claim.conditions,
                explanation = claim.explanation,
                misconception = claim.misconception,
                evidence = claim.evidence.ifBlank { null },
                cardId = cardId,
                outlinePath = outlinePath,
                source = ClaimSource.CLOUD,
                reviewStatus = ClaimReviewStatus.PENDING,
                providerId = providerId,
                createdTime = now,
                reviewedTime = null
            )
            val fresh = data.kgClaims.addCandidate(record, vector?.let(VectorBytes::encode))
            if (fresh) {
                inserted += record
                existingNorm += norm
            } else {
                skipped += claimId
            }
        }

        return Result(
            inserted = inserted,
            skippedDuplicate = skipped,
            rejected = rejected,
            warnings = warnings
        )
    }

    /**
     * 建立一条合并映射（供近义去重调用方使用）。
     *
     * 独立成方法而不是让 `ingest` 自己做，是因为相似度计算属于 `data` 层
     * （向量存储在那），这里只负责写映射与留痕。
     */
    suspend fun recordMerge(
        sourceClaimId: String,
        targetClaimId: String,
        similarity: Float,
        basis: String = "vector",
        now: Long = System.currentTimeMillis()
    ) {
        if (sourceClaimId == targetClaimId) return
        data.kgClaims.linkMerge(
            ClaimMerge(
                sourceClaimId = sourceClaimId,
                targetClaimId = targetClaimId,
                similarity = similarity,
                basis = basis,
                createdTime = now
            )
        )
    }
}

/**
 * `FloatArray` ⇄ `ByteArray` 的小端编解码。
 *
 * `data` 模块的 `VectorCodec` 是 internal 的，跨模块取不到；而存进
 * `kg_runtime_claim.embedding` 的向量必须与 `vector_table.vector` 同一种字节序，
 * 否则同一份向量在两张表里读出来不一样。这里复刻同一格式（小端 IEEE-754 float32 × 512）。
 */
internal object VectorBytes {

    /** 与 `VectorCodec.BYTE_SIZE` 一致：512 × 4。 */
    const val DIM = 512
    const val BYTE_SIZE = DIM * Float.SIZE_BYTES

    /** 序列化为小端字节数组；维度不符直接抛错（调用方的编程错误）。 */
    fun encode(values: FloatArray): ByteArray {
        require(values.size == DIM) { "Expected $DIM dimensions but got ${values.size}" }
        val out = ByteArray(BYTE_SIZE)
        for ((i, v) in values.withIndex()) {
            val bits = v.toRawBits()
            val o = i * 4
            out[o] = (bits and 0xFF).toByte()
            out[o + 1] = ((bits ushr 8) and 0xFF).toByte()
            out[o + 2] = ((bits ushr 16) and 0xFF).toByte()
            out[o + 3] = ((bits ushr 24) and 0xFF).toByte()
        }
        return out
    }

    /** 反序列化；字节数不等于 [BYTE_SIZE] 时返回 null，由调用方跳过脏数据。 */
    fun decode(bytes: ByteArray?): FloatArray? {
        if (bytes == null || bytes.size != BYTE_SIZE) return null
        val out = FloatArray(DIM)
        for (i in out.indices) {
            val o = i * 4
            val bits = (bytes[o].toInt() and 0xFF) or
                ((bytes[o + 1].toInt() and 0xFF) shl 8) or
                ((bytes[o + 2].toInt() and 0xFF) shl 16) or
                ((bytes[o + 3].toInt() and 0xFF) shl 24)
            out[i] = Float.fromBits(bits)
        }
        return out
    }
}
