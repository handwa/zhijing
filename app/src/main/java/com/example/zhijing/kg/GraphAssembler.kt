package com.example.zhijing.kg

import android.content.Context
import com.example.zhijing.kg.depth.ConditionTriple
import com.example.zhijing.kg.depth.TripleParser
import com.zhijing.data.ZhiJingData
import com.zhijing.data.model.ClaimProbeState
import com.zhijing.data.model.ClaimReviewStatus
import com.zhijing.data.model.GeneratedCard
import com.zhijing.data.model.GeneratedGraph
import com.zhijing.data.model.GeneratedOutlineNode
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
        // 生成图谱与出厂图谱并列呈现，各自成树
        val generated = data.kgGenerated.listGraphs()
        return merge(bundled, runtimeClaims, states, generated)
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
            // 生成图谱也要并入：merge 的 generated 参数默认 emptyList，
            // 之前漏传导致 observe 流里永远只有出厂图谱（一次性 load() 碰巧没暴露）。
            val generated = data.kgGenerated.listGraphs()
            merge(bundled, runtime, states, generated)
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
        states: Map<String, ClaimProbeState>,
        generated: List<GeneratedGraph> = emptyList()
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

        // 3) 卡片：挂上命题、前置关系，并算角标
        //    前置关系指向本卡（to == 本卡 id），即「学本卡之前要先学哪些卡」
        val prereqByCard: Map<String, List<PrerequisiteLink>> =
            bundled.prerequisites.groupBy { it.to }

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
                badgeStatus = MergedGraph.badgeOf(cardClaims.map { c -> c.probe.probeStatus }),
                prerequisites = prereqByCard[raw.id].orEmpty()
            )
        }

        // 4) 大纲树
        val outline = buildOutline(bundled.outline, cards)

        // 5) 生成图谱：每个主题自成一棵子树，挂在出厂大纲之后。
        //
        //    刻意**不并入**出厂大纲树：两者来源不同（出版物 vs 用户生成），
        //    混在一起会让用户分不清「哪些是系统预置的、哪些是我生成的」。
        //    主题节点的 path 用 `gen.<topicId>` 前缀，避免与出厂路径冲突。
        val genOutlines = if (generated.isEmpty()) emptyList() else {
            data.kgGenerated.allOutline()
        }
        val genCards = if (generated.isEmpty()) emptyList() else {
            data.kgGenerated.allCards()
        }
        val generatedRoots = buildGeneratedTrees(generated, genOutlines, genCards, states)

        // 生成图谱的卡片前置关系：从 Room 读出来，按 to 分组挂到卡片上
        val genPrereqs = if (generated.isEmpty()) emptyList() else data.kgGenerated.allPrerequisites()
        val genPrereqByCard: Map<String, List<PrerequisiteLink>> = genPrereqs.groupBy { it.toCardId }
            .mapValues { (_, list) ->
                list.map { p ->
                    PrerequisiteLink(
                        from = p.fromCardId,
                        to = p.toCardId,
                        kind = PrerequisiteKind.fromWire(p.kind),
                        reason = p.why
                    )
                }
            }

        // 生成图谱的卡片：转成 GraphCard。
        // 它们**没有命题**（广度生成不产出可判断的断言），因此角标为 null、不可下钻。
        val genGraphCards = genCards.map { c ->
            GraphCard(
                id = c.cardId,
                title = c.title,
                outlinePath = c.outlinePath,
                summary = c.summary,
                body = c.body,
                badgeStatus = null,
                prerequisites = genPrereqByCard[c.cardId].orEmpty()
            )
        }

        val allCards = cards + genGraphCards

        return MergedGraph(
            schemaVersion = bundled.schemaVersion,
            generatedAt = bundled.generatedAt,
            outline = outline + generatedRoots,
            cards = allCards,
            claims = claims,
            edges = bundled.edges.associateBy { it.id },
            // 三元组只来自出厂图谱；生成图谱目前不产出可拨条件
            triples = bundled.triples
        )
    }

    /**
     * 把 Room 里的生成图谱组装成大纲子树。
     *
     * 每个主题一个根节点（`gen.<topicId>`），其下是 `OutlineBuilder` 推出的层级。
     * 生成卡片**没有命题**（内容来自模型切块，不是命题抽取），因此 `badgeStatus` 为 null、
     * `descendable` 为 false —— 这是设计决定：广度生成不产出可判断的断言。
     *
     * ## 层级重建：信 level，不信 parentPath
     *
     * 与 [buildOutline] 的教训一致（见其注释）：按 `parentPath` 建树一旦断链
     * （父节点被区间校验丢弃、或旧数据没写 `parent_path`），挂在它下面的整棵子树会
     * **静默消失** —— 结构图上只剩一个孤零零的主题根，没有层级连线、没有节点。
     * 因此这里改为与 [buildOutline] 相同的「祖先栈」：按 order 遍历，把节点挂到
     * 「最近的、level 比自己小的」节点下。OutlineBuilder 本来就是用同一套栈逻辑
     * 算出 parentPath 的，正常数据两种方式结果完全一致。
     */
    private fun buildGeneratedTrees(
        graphs: List<GeneratedGraph>,
        outlines: List<GeneratedOutlineNode>,
        cards: List<GeneratedCard>,
        states: Map<String, ClaimProbeState>
    ): List<GraphOutlineNode> {
        val outlineByTopic = outlines.groupBy { it.topicId }
        val cardsByTopic = cards.groupBy { it.topicId }

        return graphs.map { meta ->
            val topicCards = cardsByTopic[meta.topicId].orEmpty()
            val nodes = outlineByTopic[meta.topicId].orEmpty().sortedBy { it.order }

            // 主题根节点
            val rootPath = "gen.${meta.topicId}"
            val root = GraphOutlineNode(
                id = "out.$rootPath",
                title = meta.title,
                level = 1,
                parentId = null,
                path = rootPath,
                order = 0,
                children = emptyList(),
                cardIds = emptyList()
            )

            // 1) 祖先栈：给每个节点解析出父节点（按 level，不依赖 parentPath）。
            //    nodes 已按 order 升序，「先出现的是父」这一前提与 buildOutline 相同。
            val parentOf = HashMap<GeneratedOutlineNode, GeneratedOutlineNode?>()
            val stack = ArrayList<GeneratedOutlineNode>()
            nodes.forEach { node ->
                while (stack.isNotEmpty() && stack.last().level >= node.level) {
                    stack.removeAt(stack.size - 1)
                }
                parentOf[node] = stack.lastOrNull()
                stack.add(node)
            }

            // 2) 按解析出的父建 children 索引；顶层节点（parentOf 为 null）挂主题根。
            //    groupBy 保留遍历顺序，children 天然按 order 排列。
            val childrenOfNode = nodes.groupBy { parentOf[it] }
            fun build(node: GeneratedOutlineNode): GraphOutlineNode {
                val childCards = topicCards.filter { it.outlinePath == node.path }
                return GraphOutlineNode(
                    id = "out.${node.path}",
                    title = node.title,
                    level = (node.level + 1).coerceAtMost(3), // 主题根占一级
                    parentId = parentOf[node]?.let { "out.${it.path}" } ?: "out.$rootPath",
                    path = node.path,
                    order = node.order,
                    children = childrenOfNode[node].orEmpty().map(::build),
                    cardIds = childCards.map { it.cardId }
                )
            }

            root.copy(children = childrenOfNode[null].orEmpty().map(::build))
        }
    }

    /** 把一棵生成子树里的所有卡片 id 展开（用于统计）。 */
    private fun flattenCards(node: GraphOutlineNode): List<String> =
        node.cardIds + node.children.flatMap(::flattenCards)

    /**
     * 把出厂图谱的扁平大纲列表建成树。
     *
     * ## 为什么不能只依赖 `parentId`
     *
     * `graph.json` 由离线脚本生成，里面**只写了 `level`，没写 `parentId`**
     * （实测 24 个节点，`parentPath` 非空的有 0 个）。
     * 原实现按 `parentId` 找子节点，结果全部节点都被当成根，结构图塌成一列、
     * 层级缩进失效 —— 用户看到的就是「所有卡片一样深，只是越排越低」。
     *
     * 因此这里改为**按 level 顺序推导父子关系**：
     * 维护一条从浅到深的祖先栈，遍历时把节点挂到「最近的、level 比自己小的」节点下。
     * 这与 `OutlineBuilder.numberDepth()` 的做法一致，两处口径统一。
     *
     * 若 JSON 里确实给了 `parentId`，仍以它为准（数据更可信）。
     */
    private fun buildOutline(nodes: List<RawOutline>, cards: List<GraphCard>): List<GraphOutlineNode> {
        if (nodes.isEmpty()) return emptyList()

        // 按 order 排序后遍历，保证「先出现的是父」这一前提成立
        val ordered = nodes.sortedBy { it.order }
        val hasExplicitParent = nodes.any { it.parentId != null }

        // path -> 该节点的直属子节点（顺序即 order）
        val childrenByParent = LinkedHashMap<String, MutableList<RawOutline>>()
        val rootLevel = ArrayList<RawOutline>()

        if (hasExplicitParent) {
            // 数据自带父子指针：直接用
            nodes.filter { it.parentId == null }.forEach { rootLevel += it }
            nodes.filter { it.parentId != null }.forEach { n ->
                childrenByParent.getOrPut(n.parentId!!) { ArrayList() } += n
            }
        } else {
            // 只给了 level：用祖先栈推导
            val stack = ArrayList<RawOutline>()
            ordered.forEach { n ->
                while (stack.isNotEmpty() && stack.last().level >= n.level) {
                    stack.removeAt(stack.size - 1)
                }
                val parent = stack.lastOrNull()
                if (parent == null) {
                    rootLevel += n
                } else {
                    childrenByParent.getOrPut(parent.id) { ArrayList() } += n
                }
                stack += n
            }
        }

        fun build(node: RawOutline): GraphOutlineNode = GraphOutlineNode(
            id = node.id,
            title = node.title,
            level = node.level,
            parentId = node.parentId
                ?: childrenByParent.entries.firstOrNull { e -> e.value.any { it.id == node.id } }?.key,
            path = node.path.ifBlank { node.id },
            order = node.order,
            children = childrenByParent[node.id].orEmpty()
                .sortedBy { it.order }
                .map(::build),
            cardIds = cards.filter { it.outlinePath == node.path }.map { it.id }
        )

        return rootLevel.sortedBy { it.order }.map(::build)
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
    val edges: List<GraphEdge>,
    /**
     * 卡片级前置关系（必须 / 推荐）。
     *
     * 来自顶层 `prerequisites` 字段。**缺省为空** ——
     * 老图谱没有这个字段时照常加载，只是结构图上没有前置连线。
     * 形态：`[{"from":"crd.a","to":"crd.b","kind":"required","reason":"..."}]`
     */
    val prerequisites: List<PrerequisiteLink> = emptyList(),
    /**
     * 深度三元组。
     *
     * 同样**缺省为空**：老图谱没有 `triples` 字段时照常加载，只是没有可拨条件。
     */
    val triples: List<ConditionTriple> = emptyList()
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

            // 卡片级前置关系：顶层字段，缺省为空（老数据兼容）
            val prerequisites = root.list("prerequisites").mapNotNull { item ->
                val m = item.asMap()
                val from = m.str("from") ?: return@mapNotNull null
                val to = m.str("to") ?: return@mapNotNull null
                if (from == to) return@mapNotNull null
                PrerequisiteLink(
                    from = from,
                    to = to,
                    kind = PrerequisiteKind.fromWire(m.str("kind")),
                    reason = m.strOrEmpty("reason")
                )
            }

            // 深度三元组：缺省为空（旧图谱没有这个字段），解析器内部逐条降级
            val triples = TripleParser.parse(root)

            return ParsedGraph(
                schemaVersion = meta.strOrEmpty("schemaVersion"),
                generatedAt = meta.strOrEmpty("generatedAt"),
                outline = outline,
                cards = cards,
                claims = claims,
                edges = edges,
                prerequisites = prerequisites,
                triples = triples
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
