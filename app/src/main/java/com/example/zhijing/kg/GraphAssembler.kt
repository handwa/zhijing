package com.example.zhijing.kg

import android.content.Context
import com.zhijing.data.ZhiJingData
import com.zhijing.data.model.ClaimProbeState
import com.zhijing.data.model.ClaimReviewStatus
import com.zhijing.data.model.ClaimSource
import com.zhijing.data.model.ProbeStatus
import com.zhijing.data.model.RuntimeClaim
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * 图谱合并加载器：把 **assets 出厂图谱** 与 **Room 运行时数据** 合成一份 [MergedGraph]。
 *
 * 这是分层存储的最后一段：UI 只认 [MergedGraph]，不分别去读 assets 和 Room。
 *
 * ```
 * assets/kg/graph.json  ─┐
 *                        ├─ com.zhijing.kg.GraphAssembler ──► MergedGraph ──► UI
 * Room 状态 + 增量命题  ─┘
 * ```
 *
 * 合并规则：
 *
 * | 来源 | 处理 |
 * |---|---|
 * | 出厂命题 | 原样保留，`bundled = true`，不可删除 |
 * | 已确认的云端命题 | 追加到 `cardId` 对应的卡片，`bundled = false`，可删除 |
 * | pending / rejected 候选 | **不进入图谱**（未经确认不出现） |
 * | 状态 | 按命题 ID 从 Room 派生；`assets` 里的 `probeState` 只作为展示态、不写库 |
 *
 * 关于「ID 漂移」：组装时先让 Room 的合并映射把增量命题 ID 规范化，
 * 保证同一命题不会在两种来源下出现两次。
 */
class GraphAssembler(
    private val context: Context,
    private val data: ZhiJingData,
    private val assetPath: String = DEFAULT_ASSET_PATH
) {

    /** 出厂图谱的解析缓存：assets 在 APK 生命周期内不变，没必要每次都解析。 */
    @Volatile
    private var cached: ParsedGraph? = null

    /**
     * 测试与调试用：直接注入一份已解析的出厂图谱，绕过 assets 读取。
     *
     * Robolectric 的单元测试默认不把 `app/src/main/assets` 挂到 classpath，
     * 而合并逻辑本身值得被完整覆盖，因此留出这个注入口。
     */
    fun seedBundled(graph: ParsedGraph) {
        cached = graph
    }

    /**
     * 加载一次合并后的图谱。
     *
     * @param includeRuntimeClaims 是否合并云端增量命题。
     *        离线模式传 `false` —— 此时**不查询**增量命题表，结果与只读 assets 完全一致。
     */
    suspend fun load(includeRuntimeClaims: Boolean = true): MergedGraph {
        val bundled = bundledGraph()
        val runtimeClaims = if (includeRuntimeClaims) {
            data.kgClaims.observeConfirmed().first()
        } else {
            emptyList()
        }
        val claimIds = bundled.claims.map { it.id } + runtimeClaims.map { it.claimId }
        val states = data.kgStates.getStates(claimIds)
        return merge(bundled, runtimeClaims, states)
    }

    /**
     * 持续观察：状态变化（用户判断）或增量命题变化（云端确认）时自动重新合并。
     *
     * 广度侧「状态回写」就是靠这个 Flow 生效的。
     */
    fun observe(includeRuntimeClaims: Boolean = true): Flow<MergedGraph> {
        val runtimeFlow = if (includeRuntimeClaims) {
            data.kgClaims.observeConfirmed()
        } else {
            flowOf(emptyList())
        }
        return combine(runtimeFlow, data.kgStates.observeAllStates()) { runtime, states ->
            // 用悬挂点保证 assets 已解析（首次调用会在 Default 线程完成解析）
            val bundled = withContext(Dispatchers.IO) { bundledGraph() }
            merge(bundled, runtime, states)
        }.flowOn(Dispatchers.Default)
    }

    /**
     * 按模式加载。
     *
     * 离线模式不查询增量命题表，因此结果与只读 assets 完全一致；这也是「离线不联网」的
     * 存储侧保证 —— 连本地已经确认过的云端命题都不会混进来，用户切到离线时视图是纯净的。
     */
    suspend fun load(mode: GraphMode): MergedGraph =
        load(includeRuntimeClaims = mode == GraphMode.CLOUD_ENHANCED)

    /** 按模式持续观察，模式变化时由调用方重新订阅。 */
    fun observe(mode: GraphMode): Flow<MergedGraph> =
        observe(includeRuntimeClaims = mode == GraphMode.CLOUD_ENHANCED)

    /**
     * 待确认的候选命题。
     *
     * 与 [load] 分开：候选**不属于图谱**（未确认就不该被当作图谱的一部分），
     * 它们是「待用户裁决的提案」，因此单独查询、单独渲染。
     */
    suspend fun pendingCandidates(): List<RuntimeClaim> =
        data.kgClaims.observeByReviewStatus(ClaimReviewStatus.PENDING).first()

    /** 持续观察候选队列，供确认界面实时刷新。 */
    fun observePendingCandidates(): Flow<List<RuntimeClaim>> =
        data.kgClaims.observeByReviewStatus(ClaimReviewStatus.PENDING)

    /**
     * 给候选命题找可读的归属卡标题，便于用户判断它属于哪里。
     *
     * 云端抽取的命题可能挂在 app 里新建的卡片（如 `crd.content.42`）上，
     * 这类卡不在出厂图谱里，因此返回 null，由 UI 退化为显示 outlinePath。
     */
    suspend fun cardTitleOf(cardId: String): String? =
        bundledGraphCompat().cards.firstOrNull { it.id == cardId }?.title

    /** 容错版：assets 读不到时返回空图谱，避免确认界面因为缺卡标题就崩掉。 */
    private suspend fun bundledGraphCompat(): ParsedGraph =
        runCatching { bundledGraph() }.getOrElse {
            ParsedGraph(
                schemaVersion = "",
                generatedAt = "",
                outline = emptyList(),
                cards = emptyList(),
                claims = emptyList(),
                edges = emptyList()
            )
        }

    /** 只读 assets 的图谱结构（不含任何运行时数据）。 */
    suspend fun bundledGraph(): ParsedGraph = cached ?: withContext(Dispatchers.IO) {
        cached ?: parseAssets().also { cached = it }
    }

    // --- 解析 assets ---------------------------------------------------------

    @Throws(IOException::class)
    private fun parseAssets(): ParsedGraph {
        val text = context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return ParsedGraph.fromJson(text, assetPath)
    }

    // --- 合并 ---------------------------------------------------------------

    private suspend fun merge(
        bundled: ParsedGraph,
        runtime: List<RuntimeClaim>,
        states: Map<String, ClaimProbeState>
    ): MergedGraph {
        // 1) 出厂命题：状态按 ID 派生
        val claims = LinkedHashMap<String, GraphClaim>()
        bundled.claims.forEach { raw ->
            claims[raw.id] = raw.toGraphClaim(
                probe = states[raw.id] ?: ClaimProbeState(claimId = raw.id),
                source = ClaimSource.BUNDLED,
                bundled = true
            )
        }

        // 2) 已确认的增量命题：追加到对应卡片。
        //    先一次性解析合并映射（suspend，放在循环外），再逐条判断是否已被出厂命题代表。
        val resolved = data.kgClaims.resolveClaimIds(runtime.map { it.claimId })
        runtime.forEach { rc ->
            val targetId = resolved[rc.claimId] ?: rc.claimId
            // 已被合并到某条命题、且该命题已存在于图谱：不再重复挂
            if (targetId in claims) return@forEach

            claims[targetId] = GraphClaim(
                id = targetId,
                statement = rc.statement,
                expected = rc.expected,
                conditions = rc.conditions,
                evidence = rc.evidence.orEmpty(),
                cardId = rc.cardId,
                outlinePath = rc.outlinePath,
                question = "这个说法成立吗？",
                explanation = rc.explanation.orEmpty(),
                errorKinds = emptyList(),
                misconception = rc.misconception.orEmpty(),
                edgeIds = emptyList(),
                probe = states[targetId] ?: ClaimProbeState(claimId = targetId),
                source = rc.source,
                bundled = false
            )
        }

        // 3) 卡片：挂上命题，并算角标
        val cards = bundled.cards.map { raw ->
            val cardClaims = claims.values.filter { it.cardId == raw.id }
            GraphCard(
                id = raw.id,
                title = raw.title,
                outlinePath = raw.outlinePath,
                summary = raw.summary,
                body = raw.body,
                chain = raw.chain,
                crossLinks = raw.crossLinks,
                claims = cardClaims,
                badgeStatus = MergedGraph.badgeOf(cardClaims.map { c -> c.probe.probeStatus })
            )
        }

        // 4) 大纲树
        val outline = buildOutline(bundled.outline, cards)

        return MergedGraph(
            schemaVersion = bundled.schemaVersion,
            generatedAt = bundled.generatedAt,
            outline = outline,
            cards = cards,
            claims = claims,
            edges = bundled.edges.associateBy { it.id }
        )
    }

    private fun buildOutline(nodes: List<RawOutline>, cards: List<GraphCard>): List<GraphOutlineNode> {
        fun build(node: RawOutline, parentPath: String): GraphOutlineNode {
            val childNodes = nodes.filter { it.parentId == node.id }.sortedBy { it.order }
            return GraphOutlineNode(
                id = node.id,
                title = node.title,
                level = node.level,
                parentId = node.parentId,
                path = node.path,
                order = node.order,
                children = childNodes.map { build(it, node.path) },
                cardIds = cards.filter { it.outlinePath == node.path }.map { it.id }
            )
        }
        return nodes.filter { it.parentId == null }.sortedBy { it.order }.map { build(it, "") }
    }

    companion object {
        /** 与 `export.py --target assets` 的产物路径一致。 */
        const val DEFAULT_ASSET_PATH = "kg/graph.json"
    }
}

/** assets 图谱的解析产物（不含运行时数据）。 */
data class ParsedGraph(
    val schemaVersion: String,
    val generatedAt: String,
    val outline: List<RawOutline>,
    val cards: List<RawCard>,
    val claims: List<RawClaim>,
    val edges: List<GraphEdge>
) {
    companion object {
        /**
         * 从 JSON 文本构造。
         *
         * 字段缺失一律走默认值，不抛异常：出厂图谱是构建产物，但**读侧不该因为
         * 一个可选字段缺失就整份白屏**。结构与 schema 不符时由 `kg/validate_graph.py`
         * 在构建阶段拦下，运行时不重复做严格校验。
         */
        fun fromJson(text: String, source: String = ""): ParsedGraph {
            val root = try {
                MiniJson.parse(text).asMap()
            } catch (e: MiniJson.GraphFormatException) {
                throw IOException("图谱 JSON 解析失败（$source）：${e.message}", e)
            }
            val meta = root.obj("meta")

            val outline = root.list("outline").mapNotNull { item ->
                val m = item.asMap()
                val id = m.str("id") ?: return@mapNotNull null
                RawOutline(
                    id = id,
                    title = m.strOrEmpty("title"),
                    level = m.int("level") ?: 1,
                    parentId = m.str("parentId"),
                    path = m.strOrEmpty("path"),
                    order = m.int("order") ?: 0
                )
            }

            val cards = root.list("cards").mapNotNull { item ->
                val m = item.asMap()
                val id = m.str("id") ?: return@mapNotNull null
                RawCard(
                    id = id,
                    title = m.strOrEmpty("title"),
                    outlinePath = m.strOrEmpty("outlinePath"),
                    summary = m.strOrEmpty("summary"),
                    body = m.strOrEmpty("body"),
                    chain = m.strings("chain"),
                    crossLinks = m.list("crossLinks").mapNotNull { link ->
                        val lm = link.asMap()
                        val target = lm.str("target") ?: return@mapNotNull null
                        CrossLink(
                            target = target,
                            label = lm.strOrEmpty("label"),
                            weight = (lm["weight"] as? Double) ?: 0.0,
                            reason = lm.strOrEmpty("reason")
                        )
                    }
                )
            }

            val claims = root.list("claims").mapNotNull { item ->
                val m = item.asMap()
                val id = m.str("id") ?: return@mapNotNull null
                val breadth = m.obj("breadth")
                val depth = m.obj("depth")
                val panel = depth.obj("judgementPanel")
                val reveal = panel.obj("reveal")
                RawClaim(
                    id = id,
                    statement = m.strOrEmpty("statement"),
                    conditions = m.strings("conditions"),
                    evidence = m.strOrEmpty("evidence"),
                    cardId = breadth.strOrEmpty("cardId"),
                    outlinePath = breadth.strOrEmpty("outlinePath"),
                    question = panel.strOrEmpty("question"),
                    expected = panel.bool("expected") ?: false,
                    explanation = reveal.strOrEmpty("explanation"),
                    errorKinds = reveal.list("errorKinds").mapNotNull { ek ->
                        val em = ek.asMap()
                        val kind = em.str("kind") ?: return@mapNotNull null
                        ExtractedErrorKind(kind, em.strOrEmpty("why"))
                    },
                    misconception = reveal.strOrEmpty("misconception"),
                    edgeIds = depth.strings("edges")
                )
            }

            val edges = root.list("edges").mapNotNull { item ->
                val m = item.asMap()
                val id = m.str("id") ?: return@mapNotNull null
                val condition = m.obj("condition")
                GraphEdge(
                    id = id,
                    from = m.strOrEmpty("from"),
                    to = m.strOrEmpty("to"),
                    type = m.strOrEmpty("type"),
                    kind = condition.strOrEmpty("kind"),
                    premise = condition.strOrEmpty("premise"),
                    boundary = condition.strOrEmpty("boundary"),
                    counterexample = condition.strOrEmpty("counterexample")
                )
            }

            return ParsedGraph(
                schemaVersion = meta.strOrEmpty("schemaVersion"),
                generatedAt = meta.strOrEmpty("generatedAt"),
                outline = outline,
                cards = cards,
                claims = claims,
                edges = edges
            )
        }
    }
}

/** 原始大纲节点。 */
data class RawOutline(
    val id: String,
    val title: String,
    val level: Int,
    val parentId: String?,
    val path: String,
    val order: Int
)

/** 原始卡片。 */
data class RawCard(
    val id: String,
    val title: String,
    val outlinePath: String,
    val summary: String,
    val body: String,
    val chain: List<String>,
    val crossLinks: List<CrossLink>
)

/** 原始命题。 */
data class RawClaim(
    val id: String,
    val statement: String,
    val conditions: List<String>,
    val evidence: String,
    val cardId: String,
    val outlinePath: String,
    val question: String,
    val expected: Boolean,
    val explanation: String,
    val errorKinds: List<ExtractedErrorKind>,
    val misconception: String,
    val edgeIds: List<String>
) {
    fun toGraphClaim(
        probe: ClaimProbeState,
        source: ClaimSource,
        bundled: Boolean
    ): GraphClaim = GraphClaim(
        id = id,
        statement = statement,
        expected = expected,
        conditions = conditions,
        evidence = evidence,
        cardId = cardId,
        outlinePath = outlinePath,
        question = question.ifBlank { "这个说法成立吗？" },
        explanation = explanation,
        errorKinds = errorKinds,
        misconception = misconception,
        edgeIds = edgeIds,
        probe = probe,
        source = source,
        bundled = bundled
    )
}
