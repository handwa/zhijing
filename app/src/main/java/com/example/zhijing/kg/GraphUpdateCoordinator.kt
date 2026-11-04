package com.example.zhijing.kg

import android.util.Log
import com.example.zhijing.cloud.CloudProvider
import com.zhijing.data.ZhiJingData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 图谱更新编排：按当前模式决定「新内容要不要交给云端抽命题」。
 *
 * 这是 `GraphMode` 的**唯一执行点** —— UI 只切模式，具体行为在这里统一收口：
 *
 * ```
 * OFFLINE        → 直接返回 Skipped，不联网、不调模型
 * CLOUD_ENHANCED → 云端抽取 → 解析 → 入库(pending)
 * ```
 *
 * 三条硬约束：
 *
 * 1. **离线模式绝不联网**。这是用户选离线时唯一的预期，任何降级都不能违反它。
 * 2. **失败不阻塞内容保存**。内容已经落库了；抽命题失败只是「这次没有新命题」，
 *    不能因此让用户的收藏失败。
 * 3. **串行化**。同一次内容导入可能触发多段抽取，并发调模型既贵又容易乱序写库。
 */
class GraphUpdateCoordinator(
    private val data: ZhiJingData,
    private val extractor: CloudClaimExtractor = CloudClaimExtractor()
) {

    private val mutex = Mutex()

    /** 一次更新的结果，供 UI 提示用户。 */
    sealed interface Outcome {
        /** 离线模式或未配置云端：什么都没做。 */
        data object Skipped : Outcome

        /** 成功入库 N 条候选（pending，等待用户确认）。 */
        data class Extracted(val inserted: Int, val skipped: Int, val rejected: Int) : Outcome

        /** 云端可用但抽取/解析失败。内容仍已保存。 */
        data class Failed(val reason: String) : Outcome
    }

    /**
     * 对一段新内容执行图谱更新。
     *
     * @param content 待抽取的原文（建议 ≤900 字，超过时调用方先切段）
     * @param path 语义路径，决定命题 ID 的 slug，如 `arch.ffn.linear`
     * @param cardId 主归属卡（广度视图里挂在哪）
     * @param outlinePath 大纲路径
     * @param provider 已配置的云服务商；null 表示未配置，直接跳过
     * @param mode 当前生效的模式
     * @param embeddingOf 取命题向量的函数，用于近义去重；可为 null
     * @param knownClaimIds 同主题已有命题 ID，供模型连边
     */
    suspend fun update(
        content: String,
        path: String,
        cardId: String,
        outlinePath: String,
        provider: CloudProvider?,
        mode: GraphMode,
        embeddingOf: (suspend (String) -> FloatArray?)? = null,
        knownClaimIds: List<String> = emptyList()
    ): Outcome = mutex.withLock {
        // 1) 离线模式：一律不联网
        if (mode == GraphMode.OFFLINE) return@withLock Outcome.Skipped

        // 2) 云端增强但没有可用服务商：同样不联网，只是什么都不做
        if (provider == null || provider.readyFailureReason() != null) {
            Log.i(TAG, "cloud enhanced selected but no usable provider, skip")
            return@withLock Outcome.Skipped
        }
        if (content.isBlank()) return@withLock Outcome.Skipped

        // 3) 抽取 + 解析
        val parsed = runCatching {
            extractor.extract(
                provider = provider,
                content = content,
                topicHint = outlinePath,
                knownClaimIds = knownClaimIds
            )
        }.getOrElse { e ->
            // 内容已经保存好了；这里失败只影响「这次没有新命题」
            Log.w(TAG, "claim extraction failed: ${e.message}")
            return@withLock Outcome.Failed(e.message ?: e.javaClass.simpleName)
        }

        parsed.warnings.forEach { Log.i(TAG, "parser: $it") }
        if (parsed.claims.isEmpty()) {
            return@withLock Outcome.Extracted(inserted = 0, skipped = 0, rejected = 0)
        }

        // 4) 入库（pending，需用户确认后才进图谱）
        val ingested = runCatching {
            ClaimIngestor(data).ingest(
                claims = parsed.claims,
                path = path,
                cardId = cardId,
                outlinePath = outlinePath,
                providerId = provider.id,
                embeddingOf = embeddingOf
            )
        }.getOrElse { e ->
            Log.w(TAG, "claim ingest failed: ${e.message}")
            return@withLock Outcome.Failed(e.message ?: e.javaClass.simpleName)
        }

        ingested.warnings.forEach { Log.i(TAG, "ingest: $it") }
        Outcome.Extracted(
            inserted = ingested.inserted.size,
            skipped = ingested.skippedDuplicate.size,
            rejected = ingested.rejected.size
        )
    }

    private companion object {
        const val TAG = "GraphUpdate"
    }
}
