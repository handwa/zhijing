package com.example.zhijing.kg

import com.example.zhijing.cloud.CloudProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 图谱生成质量对比测试：同一段文本，同时跑「本地规则」与「云端大模型」两条路径，量化差异。
 *
 * 这是系统的**评测入口**，也是论文实验的数据来源。它回答三个问题：
 *
 * 1. **召回**：这段文本里能抽出多少条可判断的命题？
 * 2. **可用性**：抽出来的命题有多少满足最低质量要求（有断言、有条件、有证据）？
 * 3. **成本**：各花了多长时间、是否需要联网？
 *
 * 两条路径的比较规则：
 *
 * - 规则抽取是**确定性**的，重跑结果不变；云端受采样影响，会波动。所以对比的是
 *   「一次运行下两条路径各自的产出」，而不是「谁更稳定」（后者需要多次采样）。
 * - **不评判谁更好**，只呈现客观指标。判断优劣需要人工标注，那是研究环节的事，
 *   不该由程序武断打分。
 */
class GraphComparisonRunner(
    private val extractor: CloudClaimExtractor = CloudClaimExtractor()
) {

    /** 一条生成路径的单次结果。 */
    data class PathResult(
        val path: GenerationPath,
        /** 是否成功执行（规则抽取几乎不会失败；云端可能超时/报错）。 */
        val success: Boolean,
        val claims: List<ExtractedClaim>,
        val warnings: List<String>,
        /** 耗时（毫秒）。 */
        val elapsedMs: Long,
        /** 失败原因（success=false 时非空）。 */
        val failure: String? = null
    ) {
        /** 命题数。 */
        val claimCount: Int get() = claims.size

        /**
         * 可用命题数：满足 [ExtractedClaim.isUsable]（有断言、有条件）。
         *
         * 这是「质量」最朴素的可量化指标 —— 一条不合格的命题进不了图谱，
         * 抽得再多也没用。
         */
        val usableCount: Int get() = claims.count { it.isUsable }

        /** 带证据的命题数：`evidence` 非空才能溯源，这是可审核的前提。 */
        val withEvidenceCount: Int get() = claims.count { it.evidence.isNotBlank() }

        /** 带错点归因的命题数：撞错时能定位错在哪类依赖上。 */
        val withErrorKindCount: Int get() = claims.count { it.errorKinds.isNotEmpty() }

        /** 带边的命题数。 */
        val withEdgesCount: Int get() = claims.count { it.edges.isNotEmpty() }

        /** 可用率 0..1。 */
        val usableRate: Float
            get() = if (claims.isEmpty()) 0f else usableCount.toFloat() / claims.size
    }

    /** 生成路径。 */
    enum class GenerationPath(val label: String) {
        /** 本地规则抽取：确定性、零成本、零网络。 */
        LOCAL_RULES("本地规则抽取"),

        /** 云端大模型：按 `extract_claims.md` 的 prompt 抽取。 */
        CLOUD_LLM("云端大模型抽取")
    }

    /** 一次对比测试的结果。 */
    data class ComparisonResult(
        val textPreview: String,
        val textLength: Int,
        val rule: PathResult,
        val cloud: PathResult,
        /** 规则抽取发现的领域词（用于说明规则路径的「视野边界」）。 */
        val ruleEntities: List<String>
    ) {
        /**
         * 两条路径抽出的命题**文本**重合度（规范化后比较）。
         *
         * 说明的是「两种方法看到的是不是同一批断言」，不是质量评价。
         * 重合度低通常意味着规则路径漏了自由文本里的命题 —— 这正是对比的意义。
         */
        val overlap: Float
            get() {
                val a = rule.claims.map { ClaimIds.normalize(it.statement) }.toSet()
                val b = cloud.claims.map { ClaimIds.normalize(it.statement) }.toSet()
                if (a.isEmpty() && b.isEmpty()) return 0f
                val union = (a + b).size
                if (union == 0) return 0f
                return (a intersect b).size.toFloat() / union
            }

        /** 给 UI 用的一行摘要。 */
        fun summaryLine(): String =
            "规则 ${rule.claimCount} 条 · 云端 ${cloud.claimCount} 条 · 重合 ${(overlap * 100).toInt()}%"
    }

    /**
     * 跑一次对比。
     *
     * @param text 待抽取的文本
     * @param path 语义路径，决定命题 ID 的 slug
     * @param provider 云服务商；为 null 时云端路径标记为未执行（不是失败，
     *        因为「没配置云端」是合法状态，不该算成一次失败）
     * @param topicHint 主题提示，喂给云端 prompt
     */
    suspend fun compare(
        text: String,
        path: String,
        provider: CloudProvider?,
        topicHint: String = ""
    ): ComparisonResult = coroutineScope {
        val trimmed = text.trim()

        // 1) 本地规则：同步、确定性、不联网
        val ruleStart = System.currentTimeMillis()
        val rule = runCatching {
            RuleBasedExtractor.extractTableClaims(trimmed, path)
        }.getOrElse { e ->
            RuleBasedExtractor.Result(emptyList(), listOf("规则抽取异常：${e.message}"))
        }
        val ruleElapsed = System.currentTimeMillis() - ruleStart

        // 2) 云端：并发发起，与规则路径互不阻塞
        val cloudDeferred = async {
            if (provider == null || provider.readyFailureReason() != null) {
                PathResult(
                    path = GenerationPath.CLOUD_LLM,
                    success = false,
                    claims = emptyList(),
                    warnings = emptyList(),
                    elapsedMs = 0,
                    failure = if (provider == null) "未配置云端大模型" else
                        "云端配置不完整：${provider.readyFailureReason()}"
                )
            } else {
                val start = System.currentTimeMillis()
                runCatching {
                    extractor.extract(
                        provider = provider,
                        content = trimmed.take(CloudClaimExtractor.MAX_CONTENT_CHARS),
                        topicHint = topicHint
                    )
                }.fold(
                    onSuccess = { parsed ->
                        PathResult(
                            path = GenerationPath.CLOUD_LLM,
                            success = true,
                            claims = parsed.claims,
                            warnings = parsed.warnings,
                            elapsedMs = System.currentTimeMillis() - start
                        )
                    },
                    onFailure = { e ->
                        PathResult(
                            path = GenerationPath.CLOUD_LLM,
                            success = false,
                            claims = emptyList(),
                            warnings = emptyList(),
                            elapsedMs = System.currentTimeMillis() - start,
                            failure = e.message ?: e.javaClass.simpleName
                        )
                    }
                )
            }
        }

        ComparisonResult(
            textPreview = trimmed.take(120),
            textLength = trimmed.length,
            rule = PathResult(
                path = GenerationPath.LOCAL_RULES,
                success = true,
                claims = rule.claims,
                warnings = rule.warnings,
                elapsedMs = ruleElapsed
            ),
            cloud = cloudDeferred.await(),
            ruleEntities = RuleBasedExtractor.extractEntities(trimmed)
        )
    }
}
