package com.example.zhijing.kg

import android.util.Log
import com.example.zhijing.cloud.CloudProvider
import com.zhijing.data.ZhiJingData
import com.zhijing.data.model.GeneratedCard
import com.zhijing.data.model.GeneratedClaim
import com.zhijing.data.model.GeneratedGraph
import com.zhijing.data.model.GeneratedOutlineNode
import com.zhijing.data.model.GeneratedPrerequisite

/**
 * 生成长链路：用户输入一个主题 → 云端切块 → 本地算结构 → 落库。
 *
 * 这是把 [CloudOutlineExtractor] 与 [OutlineBuilder] 真正接起来的一层。
 * 之前两者都写好了，但**没有任何代码把结果存下来**，所以生成完就丢。
 *
 * 分工（与 `kg/prompts/extract_outline.md` 一致）：
 *
 * ```
 * 用户输入主题文本
 *   → CloudOutlineExtractor   调模型切块（只给 title/summary/区间）
 *   → OutlineBuilder          本地推层级、截正文
 *   → CrossLinker             本地算交叉链接
 *   → GeneratedGraphRepository 落库（可写，与出厂图谱分开）
 *   → GraphAssembler          渲染时与出厂图谱并列合并
 * ```
 */
class GraphGenerator(
    private val data: ZhiJingData,
    private val extractor: CloudOutlineExtractor = CloudOutlineExtractor(),
    /** 按卡产命题（下钻用）。与广度抽取分开：它失败不能影响出图。 */
    private val claimExtractor: CloudClaimExtractor = CloudClaimExtractor()
) {

    /** 一次生成的结果。 */
    sealed interface Result {
        /** 成功：落库了 N 个大纲节点、M 张卡片。 */
        data class Success(
            val topicId: String,
            val title: String,
            val outlineCount: Int,
            val cardCount: Int,
            val warnings: List<String>,
            /**
             * 正文是否可溯源。
             *
             * `true` = 有原文，正文逐字截取；
             * `false` = 无原文，**正文由模型生成，未核实**。
             */
            val verified: Boolean = true,
            /** 落库的卡片前置关系条数。 */
            val prerequisiteCount: Int = 0,
            /** 落库的命题条数；为 0 表示这次没产出可下钻的题。 */
            val claimCount: Int = 0
        ) : Result

        /** 模型没有产出任何可用的块（例如原文太短、或返回空）。 */
        data class Empty(val warnings: List<String>) : Result

        /** 调用失败（网络、配置、解析）。 */
        data class Failed(val reason: String) : Result
    }

    /**
     * 生成图谱并落库。**统一入口**，不再区分「有无原文」两种调用方式。
     *
     * @param topic 学习主题
     * @param notes 补充说明，可空
     * @param source 原文，可空。给了就按原文切分（可溯源），没给就由模型撰写（未核实）
     * @param provider 云服务商；为 null 时直接失败
     */
    suspend fun generate(
        topic: String,
        provider: CloudProvider?,
        notes: String = "",
        source: String = "",
        now: Long = System.currentTimeMillis()
    ): Result {
        if (provider == null || provider.readyFailureReason() != null) {
            return Result.Failed(
                if (provider == null) "未配置云端大模型，无法生成图谱"
                else "云端配置不完整：${provider.readyFailureReason()}"
            )
        }
        if (topic.isBlank() && source.isBlank()) {
            return Result.Empty(listOf("主题和原文都为空"))
        }

        val topicId = topicIdOf(topic.ifBlank { source.take(20) })
        val title = topic.trim().ifBlank { "未命名主题" }

        val extraction = runCatching {
            extractor.extract(
                provider = provider,
                topic = title,
                notes = notes,
                source = source,
                maxTokens = if (source.isBlank()) {
                    CloudOutlineExtractor.DEFAULT_TOPIC_MAX_TOKENS
                } else {
                    CloudOutlineExtractor.DEFAULT_MAX_TOKENS
                }
            )
        }.getOrElse { e ->
            Log.w(TAG, "extract failed: ${e.message}")
            return Result.Failed(e.message ?: e.javaClass.simpleName)
        }

        return persist(extraction, topicId, title, provider, now)
    }

    /** 落库：把抽取结果写成三张表。 */
    private suspend fun persist(
        extraction: CloudOutlineExtractor.OutlineExtraction,
        topicId: String,
        title: String,
        provider: CloudProvider,
        now: Long
    ): Result {
        if (extraction.cards.isEmpty()) {
            return Result.Empty(extraction.warnings)
        }

        val outlineNodes = extraction.outline.map { node ->
            GeneratedOutlineNode(
                topicId = topicId,
                path = node.path,
                parentPath = node.parentPath,
                title = node.title,
                level = node.level.coerceIn(1, 3),
                order = node.order
            )
        }
        val cards = extraction.cards.mapIndexed { i, card ->
            GeneratedCard(
                topicId = topicId,
                cardId = "crd.gen.$topicId.$i",
                outlinePath = card.outlinePath,
                title = card.title,
                summary = card.summary,
                body = card.body,
                order = i
            )
        }

        // 前置关系：resolveRequires 已把 title 映射成下标；这里转成 cardId，
        // 再过 PrerequisiteValidator 去环与限量（模型一定会产出一些环和过量）。
        val rawLinks = extraction.prerequisites.map { p ->
            PrerequisiteLink(
                from = cards[p.fromIndex].cardId,
                to = cards[p.toIndex].cardId,
                kind = p.kind,
                reason = p.why
            )
        }
        val validated = PrerequisiteValidator.validate(rawLinks, cards.map { it.cardId }.toSet())
        if (validated.rejected.isNotEmpty()) {
            Log.i(TAG, "prerequisite rejected: ${validated.rejected.take(3).map { it.reason }}")
        }
        val prerequisites = validated.accepted.map { link ->
            GeneratedPrerequisite(
                topicId = topicId,
                fromCardId = link.from,
                toCardId = link.to,
                kind = link.kind.wire,
                why = link.reason
            )
        }

        val meta = GeneratedGraph(
            topicId = topicId,
            title = title,
            createdTime = now,
            source = "cloud",
            providerId = provider.id,
            blockCount = extraction.cards.size,
            warnings = extraction.warnings + validated.rejected.take(5).map {
                "前置关系已丢弃：${it.reason}"
            },
            verified = extraction.verified
        )

        // 命题：每一卡一次模型调用，失败只记 warning，绝不阻塞出图 ——
        // 没有题最多是「点进去没得练」，出不了图是「白等一次」。
        val claimResult = runCatching { extractClaims(provider, topicId, title, cards) }
            .getOrElse { e ->
                Log.w(TAG, "claim extraction failed: ${e.message}")
                ClaimExtraction(
                    claims = emptyList(),
                    warnings = listOf("命题抽取失败：${e.message ?: e.javaClass.simpleName}")
                )
            }

        // 重复生成同一主题时，新命题会随卡片一起整份替换（DAO 事务里删旧建新）
        runCatching {
            data.kgGenerated.save(
                meta = meta.copy(warnings = meta.warnings + claimResult.warnings),
                outline = outlineNodes,
                cards = cards,
                prerequisites = prerequisites,
                claims = claimResult.claims
            )
        }.getOrElse { e ->
            Log.w(TAG, "persist failed: ${e.message}")
            return Result.Failed("保存失败：${e.message ?: e.javaClass.simpleName}")
        }

        return Result.Success(
            topicId = topicId,
            title = title,
            outlineCount = outlineNodes.size,
            cardCount = cards.size,
            warnings = meta.warnings + claimResult.warnings,
            verified = extraction.verified,
            prerequisiteCount = prerequisites.size,
            claimCount = claimResult.claims.size
        )
    }

    /** 一次命题抽取的产物。 */
    private data class ClaimExtraction(
        val claims: List<GeneratedClaim>,
        val warnings: List<String>
    )

    /**
     * 按卡产命题。
     *
     * 只给前 [MAX_CARDS_WITH_CLAIMS] 张卡产题：每张一次模型调用，50 张就是 50 次，
     * 生成一次要等好几分钟。先覆盖前几章（学习的入口），剩下的卡片保留「暂无习题」。
     */
    private suspend fun extractClaims(
        provider: CloudProvider,
        topicId: String,
        title: String,
        cards: List<GeneratedCard>
    ): ClaimExtraction {
        val warnings = ArrayList<String>()
        val extracted = LinkedHashMap<String, List<ExtractedClaim>>()

        cards.take(MAX_CARDS_WITH_CLAIMS).forEach { card ->
            val body = card.body.trim()
            // 太短的正文抽不出可判定的断言，直接跳过，省一次调用
            if (body.length < MIN_BODY_FOR_CLAIM) return@forEach

            runCatching {
                claimExtractor.extract(
                    provider = provider,
                    content = body.take(MAX_BODY_FOR_CLAIM),
                    topicHint = "$title · ${card.title}"
                )
            }.onSuccess { result ->
                if (result.claims.isNotEmpty()) extracted[card.cardId] = result.claims
            }.onFailure { e ->
                Log.w(TAG, "claim extract failed on ${card.cardId}: ${e.message}")
                warnings += "命题抽取失败（${card.title}）：${e.message ?: e.javaClass.simpleName}"
            }
        }

        return ClaimExtraction(
            claims = GeneratedClaimBuilder.build(topicId, cards, extracted),
            warnings = warnings
        )
    }

    /**
     * 主题 id：由标题推导，保证同一主题重复生成会覆盖而不是堆积。
     *
     * 用 `ClaimIds.slugify` 同款逻辑：保留 ASCII，中文兜底补哈希。
     */
    private fun topicIdOf(title: String): String {
        val slug = ClaimIds.slugify(title.trim(), fallback = "topic")
        val hash = ClaimIds.shortHash(title.trim(), 6)
        return "$slug-$hash"
    }

    private companion object {
        const val TAG = "GraphGenerator"

        /** 最多给多少张卡产命题（每张一次模型调用）。 */
        const val MAX_CARDS_WITH_CLAIMS = 12

        /** 正文短于这个长度就不抽了：抽不出能判真假的断言。 */
        const val MIN_BODY_FOR_CLAIM = 20

        /** 单次抽取的正文上限，与 `kg/prompts/extract_claims.md` 的 900 字约定一致。 */
        const val MAX_BODY_FOR_CLAIM = 900
    }
}
