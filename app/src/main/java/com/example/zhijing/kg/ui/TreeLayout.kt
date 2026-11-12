package com.example.zhijing.kg.ui

import com.example.zhijing.kg.GraphCard
import com.example.zhijing.kg.GraphOutlineNode
import com.example.zhijing.kg.MergedGraph
import com.example.zhijing.kg.PrerequisiteKind
import kotlin.math.max

/**
 * 结构图的**语义布局**：一级章节一列，列内纵向排列。
 *
 * ## 为什么不用力导向（force-directed）
 *
 * 力导向每次算出来的位置都不同 —— 用户上次记住的「第 3 列是训练」这次就变了，
 * 位置感归零。语义布局把位置绑定到**结构**上（第几章就在第几列），
 * 位置因此稳定、可记忆、可描述。
 *
 * ## 坐标约定
 *
 * 单位是 dp 的概念值（不是像素），由绘制层乘以 density。
 * 原点在内容区左上角，y 轴向下。
 */
object TreeLayout {

    /** 大纲节点（胶囊）的宽度。 */
    const val NODE_WIDTH = 160f

    /** 层级之间额外的横向间距（叠加在节点宽度之上）。 */
    const val COLUMN_GAP = 40f

    /**
     * 一个层级占用的横向宽度（节点 + 间距）。
     *
     * 横向布局里**每深一层就右移这么多**，这是父子关系的唯一视觉线索。
     *
     * 这里**不含卡片宽度**：结构图早已不画卡片（卡片内容改由点击节点后的底部弹窗给出），
     * 但间距里曾残留 `CARD_WIDTH`，使三层树的内容总宽达到 924dp、初始只能缩到 0.42 倍，
     * 字小到看不清。去掉后总宽 584dp，适配比例约 0.67，字号提升约 60%。
     */
    const val LEVEL_WIDTH = NODE_WIDTH + COLUMN_GAP

    /** 顶部的章节标题高度。 */
    const val HEADER_HEIGHT = 32f


    /** 行高：只按节点高度算 —— 卡片已删除，不要把卡片高度算进来。 */
    const val ROW_HEIGHT = HEADER_HEIGHT + 22f





    /** 左上的整体留白。 */
    const val PADDING = 24f

    /** 主题分区的标题栏高度（显示主题名）。 */
    const val GROUP_TITLE_HEIGHT = 34f

    /** 主题分区包围盒的内边距。 */
    const val GROUP_PADDING = 14f

    /** 主题分区之间的间隙 —— 要大，让"两份不同的东西"一眼可见。 */
    const val GROUP_GAP = 56f

    /**
     * 每深一层向右缩进多少 —— 这是父子关系的唯一视觉线索。
     *
     * 取 34f 而不是更小的值：缩进太小时，一旦画布被缩放（结构图允许缩放），
     * 22dp 缩进在 0.45 比例下只剩不到 10px，父子边界就看不出来了。
     */

    /** 缩进后节点宽的保底值，避免深层节点窄到放不下字。 */

    /** 一个待绘制的节点。 */
    /**
     * 一个待绘制的节点（**只有大纲节点，不再单独画卡片**）。
     *
     * 卡片为什么不再画：实测 22/22 张卡片的标题与所属大纲节点完全相同，
     * 且卡片与节点基本 1:1。把两者画成两个元素等于同一件事画两遍，
     * 结构图还会因此多出一整列、总宽超出屏幕两倍。
     *
     * 现在：节点是**唯一点击入口**，卡片内容（标题/摘要/正文/命题）
     * 在点击后的底部弹窗里给出 —— 结构图只负责「骨架」，内容按需展开。
     */
    data class Node(
        /** 大纲节点路径，全局唯一，用作选中标识。 */
        val nodePath: String,
        val title: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        /** 该节点下属卡片一共有几条命题（右上角圆标）。 */
        val claimCount: Int,
        /** 是否有可下钻的卡片（决定边框样式与点击行为）。 */
        val descendable: Boolean,
        /** 该节点直接挂的卡片 id；弹窗据此取内容。 */
        val cardIds: List<String>,
    )

    /** 一条连线。 */
    data class Line(
        val fromX: Float,
        val fromY: Float,
        val toX: Float,
        val toY: Float,
        val style: Style,
        /** 细枝：层级连线用更细的样式，避免与依赖线一样重。 */
        val weak: Boolean = false,
        /**
         * 两端所属的大纲节点路径。
         *
         * 用于「选中某节点时高亮与它相关的连线」：
         * 只要 [fromNode] 或 [toNode] 等于选中节点，这条线就高亮。
         * 用路径而不是坐标判断，是因为坐标比较有浮点误差。
         */
        val fromNode: String? = null,
        val toNode: String? = null,
    ) {
        /**
         * 连线样式。
         *
         * 视觉契约（对应设计讨论）：
         * - [HIERARCHY]：章节 → 首张卡，实线，最淡
         * - [PREREQUISITE_REQUIRED]：必须前置，**实线**，最醒目
         * - [PREREQUISITE_RECOMMENDED]：推荐前置，**虚线**
         * - [CROSS]：交叉链接（仅"有关"），细虚线，最淡
         */
        enum class Style { HIERARCHY, PREREQUISITE_REQUIRED, PREREQUISITE_RECOMMENDED, CROSS }
    }

    /** 一次布局的完整结果。 */
    data class Result(
        val nodes: List<Node>,
        val lines: List<Line>,
        /** 内容总宽高，供缩放适配与滚动范围。 */
        val contentWidth: Float,
        val contentHeight: Float,
        /**
         * 主题分区：每个主题一棵独立的树，含标题与包围盒。
         *
         * 出厂图谱是一个主题（"预置图谱"），每个生成图谱各是一个主题。
         * 渲染时按分区画背景框与标题，让用户一眼看出「这是两份不同的东西」。
         */
        val groups: List<Group> = emptyList()
    ) {
        val isEmpty: Boolean get() = nodes.isEmpty()
    }

    /**
     * 一个主题分区。
     *
     * 为什么需要分区：出厂图谱与生成图谱来源不同（出版物 vs 用户生成），
     * 平铺在一起会让用户分不清。分区用**视觉隔断**表达这个边界。
     */
    data class Group(
        val title: String,
        /** 分区包围盒（含内边距）。 */
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        /** 是否可溯源的出厂图谱（用于选不同的边框颜色）。 */
        val isBundled: Boolean
    )

    /**
     * 计算布局。
     *
     * ## 结构
     *
     * 每个主题一个分区，分区内是**真正的树**：
     *
     * ```
     * ┌─ 主题分区 ──────────────────────────┐
     * │ 主题名                               │
     * │  ├ 1 第一章          ← level 1      │
     * │  │  ├ 1.1 节          ← level 2      │
     * │  │  │  └ [卡片]       ← 卡片         │
     * │  │  └ 1.2 节                         │
     * │  └ 2 第二章                          │
     * └──────────────────────────────────────┘
     * ```
     *
     * **按层级缩进**而不是拍平成一列：`x` 由层级深度决定，`y` 由先序顺序决定。
     * 这样父子关系一眼可见，不会出现「所有卡看起来都一样、只是越来越低」。
     */
    /**
     * 计算布局（**横向 tidy tree**）。
     *
     * ## 为什么是横向
     *
     * 纵向先序布局（层级靠 x 缩进、节点沿 y 递增）在真实数据上会拉成一根长竖条：
     * 22 张卡排成 46 行，层级线、前置线、关联线全是竖线，方向无法区分 ——
     * 用户看到的是「一堆竖着的线」，看不出谁依赖谁。
     *
     * 横向布局把层级放到 x 轴上：
     *
     * ```
     * 列0 根           列1 章            列2 节
     * ┌──────────┐   ┌────────────┐    ┌──────────┐
     * │Transformer│──▶│1 前馈网络  │───▶│1.1 Linear│──▶[卡片]
     * └──────────┘   │            │    │1.2 ReLU  │──▶[卡片]
     *                │            │    │1.3 Softmax│─▶[卡片]
     *                │2 层间依赖  │───▶│2.1 形状  │──▶[卡片]
     *                └────────────┘    └──────────┘
     * ```
     *
     * 父在左、子在右，**依赖的方向就是阅读方向**，一眼能看出「先学哪个」。
     *
     * ## y 的分配
     *
     * 用 tidy tree 的经典做法：先给每个叶子分配一个行号（纵向累加），
     * 父节点的行号取其第一个子与最后一个子的中点。这样父节点总是垂直居中对齐子节点，
     * 连线不会出现无谓的斜拉。
     */
    fun layout(graph: MergedGraph): Result {
        val topics = graph.outline.sortedBy { it.order }
        if (topics.isEmpty()) return Result(emptyList(), emptyList(), 0f, 0f)

        val nodes = ArrayList<Node>()
        val lines = ArrayList<Line>()
        val groups = ArrayList<Group>()
        val positionOf = HashMap<String, Node>()

        var groupTop = PADDING
        var maxRight = 0f

        topics.forEach { topic ->
            val groupLeft = PADDING
            val treeTop = groupTop + GROUP_TITLE_HEIGHT

            // ---- 第一遍：给每个节点分配一个「行号」（浮点，便于父节点取中点）----
            val rowOf = HashMap<String, Float>()
            var nextRow = 0f

            fun assignRows(node: GraphOutlineNode) {
                val children = node.children.sortedBy { it.order }
                if (children.isEmpty()) {
                    // 叶子：一个叶子独占一行。
                    // 若它挂了多张卡，行高会被撑大（见下面的 rowHeight），
                    // 但行号只占一个 —— 卡片是同行的内容，不是更深的结构。
                    rowOf[node.id] = nextRow
                    nextRow += 1f
                } else {
                    children.forEach { assignRows(it) }
                    val firstChildRow = rowOf[children.first().id]!!
                    val lastChildRow = rowOf[children.last().id]!!
                    rowOf[node.id] = (firstChildRow + lastChildRow) / 2f
                }
            }
            assignRows(topic)

            // ---- 第二遍：按行号放置节点 ----
            var deepestRight = groupLeft

            fun place(node: GraphOutlineNode, depth: Int, parentNode: Node?) {
                val x = groupLeft + depth * LEVEL_WIDTH
                val y = treeTop + rowOf[node.id]!! * ROW_HEIGHT

                // 该节点直接挂的卡片（通常 0–1 张，个别有多张）
                val cards = graph.cards.filter { it.outlinePath == node.path }
                val claims = cards.sumOf { it.claims.size }

                val self = Node(
                    nodePath = node.path,
                    title = node.title,
                    x = x,
                    y = y,
                    width = NODE_WIDTH,
                    height = HEADER_HEIGHT,
                    claimCount = claims,
                    // 有卡片就能点开看内容；没有卡片的组织性节点置灰
                    descendable = cards.isNotEmpty(),
                    cardIds = cards.map { it.id },
                )
                nodes += self
                positionOf[node.path] = self
                deepestRight = max(deepestRight, self.x + self.width)

                if (parentNode != null) {
                    // 父右中 → 子左中：横向折线，方向与阅读方向一致
                    lines += Line(
                        fromX = parentNode.x + parentNode.width,
                        fromY = parentNode.y + parentNode.height / 2,
                        toX = self.x,
                        toY = self.y + self.height / 2,
                        style = Line.Style.HIERARCHY,
                        fromNode = parentNode.nodePath,
                        toNode = node.path,
                    )
                }

                node.children.sortedBy { it.order }.forEach { child ->
                    place(child, depth + 1, self)
                }
            }

            place(topic, depth = 0, parentNode = null)

            val contentBottom = treeTop + maxOf(nextRow, 1f) * ROW_HEIGHT
            groups += Group(
                title = topic.title,
                x = groupLeft - GROUP_PADDING,
                y = groupTop,
                width = (deepestRight - groupLeft) + GROUP_PADDING * 2 + COLUMN_GAP,
                height = (contentBottom - groupTop) + GROUP_PADDING,
                isBundled = !topic.path.startsWith("gen.")
            )
            maxRight = max(maxRight, deepestRight)

            groupTop = contentBottom + GROUP_GAP
        }

        // 前置关系：压在交叉链接之上（前置比"相关"更重要）
        //
        // ## 依赖本来是卡片级的，这里**上卷到节点级**
        //
        // `card.prerequisites` 的端点是 cardId。结构图不再画卡片，
        // 因此先把 cardId 映射到它所属的大纲节点，再连节点与节点。
        //
        // 实测这层映射是无损的：21 条卡片级前置全部映射成功，
        // 0 条自环（两端同节点）、0 条重复。
        val nodeOfCard = HashMap<String, String>()
        graph.cards.forEach { nodeOfCard[it.id] = it.outlinePath }

        val seenPrereq = HashSet<String>()
        graph.cards.forEach { card ->
            card.prerequisites.forEach { link ->
                val fromPath = nodeOfCard[link.from] ?: return@forEach
                val toPath = nodeOfCard[link.to] ?: return@forEach
                // 同一节点的两张卡之间的依赖，在节点级退化成自环，跳过
                if (fromPath == toPath) return@forEach
                if (!seenPrereq.add("$fromPath->$toPath")) return@forEach
                val from = positionOf[fromPath] ?: return@forEach
                val to = positionOf[toPath] ?: return@forEach
                lines += Line(
                    fromX = from.x + from.width / 2,
                    fromY = from.y,
                    toX = to.x + to.width / 2,
                    toY = to.y + to.height,
                    style = when (link.kind) {
                        PrerequisiteKind.REQUIRED -> Line.Style.PREREQUISITE_REQUIRED
                        PrerequisiteKind.RECOMMENDED -> Line.Style.PREREQUISITE_RECOMMENDED
                    },
                    fromNode = fromPath,
                    toNode = toPath,
                )
            }
        }

        // 交叉链接：最淡的虚线，去重
        val drawn = HashSet<String>()
        graph.cards.forEach { card ->
            val fromPath = nodeOfCard[card.id] ?: return@forEach
            val from = positionOf[fromPath] ?: return@forEach
            card.crossLinks.take(5).forEach { link ->
                val toPath = nodeOfCard[link.target] ?: return@forEach
                if (fromPath == toPath) return@forEach
                val to = positionOf[toPath] ?: return@forEach
                val key = listOf(fromPath, toPath).sorted().joinToString("|")
                if (!drawn.add(key)) return@forEach
                lines += Line(
                    fromX = from.x + from.width,
                    fromY = from.y + from.height / 2,
                    toX = to.x,
                    toY = to.y + to.height / 2,
                    style = Line.Style.CROSS,
                    fromNode = fromPath,
                    toNode = toPath,
                )
            }
        }

        val contentWidth = maxRight + PADDING * 2
        val contentHeight = groupTop
        return Result(nodes, lines, contentWidth, contentHeight, groups)
    }

    /**
     * 选出用于分列的章节列表。
     *
     * 从一级开始，如果某一层只有一个节点且它还有子节点，就下沉一层；
     * 一旦某一层产生 ≥2 个节点，就用它分列。
     * 所有层都只有 1 个节点时，返回最深的那个（至少有一列）。
     */
    fun chooseColumns(outline: List<GraphOutlineNode>): List<GraphOutlineNode> {
        var current: List<GraphOutlineNode> = outline.sortedBy { it.order }
        while (current.size == 1) {
            val only = current.single()
            val children = only.children.sortedBy { it.order }
            if (children.isEmpty()) break
            current = children
        }
        return current
    }

    /**
     * 收集一个章节下的所有卡片：先本章节自己的卡，再按大纲顺序递归子章节。
     *
     * **必须按 path 分组而不是 `associateBy(path)`**：一个章节下可能挂多张卡，
     * `associateBy` 会把同 path 的卡覆盖成只剩最后一张（曾经因此少画卡片）。
     */
    private fun orderedCards(graph: MergedGraph, section: GraphOutlineNode): List<GraphCard> {
        val byPath = graph.cards.groupBy { it.outlinePath }
        val out = ArrayList<GraphCard>()

        fun walk(node: GraphOutlineNode) {
            byPath[node.path]?.let { out += it }
            node.children.sortedBy { it.order }.forEach(::walk)
        }
        walk(section)
        return out
    }
}
