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

    /** 单列宽度：章节列比卡片宽，因为要放章节名。 */
    const val COLUMN_WIDTH = 150f

    /** 列与列之间的水平间距。 */
    const val COLUMN_GAP = 72f

    /** 节点（卡片）宽高。 */
    const val NODE_WIDTH = 118f
    const val NODE_HEIGHT = 42f

    /** 同一列内节点的垂直间距。 */
    const val NODE_GAP = 10f

    /** 顶部的章节标题高度。 */
    const val HEADER_HEIGHT = 30f

    /** 左上的整体留白。 */
    const val PADDING = 24f

    /** 一个待绘制的节点。 */
    data class Node(
        /** 卡片 ID；章节标题节点为 null。 */
        val cardId: String?,
        val title: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        /** 该节点挂了几条命题（卡片右上角的圆标）。 */
        val claimCount: Int,
        /** 是否是可下钻的叶子卡。 */
        val descendable: Boolean,
        /** 章节节点标记（绘制样式不同）。 */
        val isSection: Boolean
    )

    /** 一条连线。 */
    data class Line(
        val fromX: Float,
        val fromY: Float,
        val toX: Float,
        val toY: Float,
        val style: Style
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
        val contentHeight: Float
    ) {
        val isEmpty: Boolean get() = nodes.isEmpty()
    }

    /**
     * 计算布局。
     *
     * ## 分列的层级选择
     *
     * 设计文档写的是「一级章节一列」，但**只对多章节的图谱成立**。
     * 实际数据常常只有一个根节点（如「Transformer 架构」下分 6 章），
     * 此时按一级分列会退化成 1 列、22 张卡纵向排成一根长条 —— 既看不出结构，
     * 交叉链接也几乎全是同列内的竖线。
     *
     * 因此这里按「**第一个产生多列的层级**」分列：
     * 若一级章节 ≤ 1，就下沉一级用二级章节分列；仍 ≤ 1 则再下沉。
     * 这样无论数据是「多根」还是「单根多章」，都能得到有意义的列结构。
     */
    fun layout(graph: MergedGraph): Result {
        val nodes = ArrayList<Node>()
        val lines = ArrayList<Line>()

        val columns = chooseColumns(graph.outline)
        if (columns.isEmpty()) return Result(emptyList(), emptyList(), 0f, 0f)

        var maxColumnBottom = 0f
        val positionOf = HashMap<String, Node>() // cardId -> 节点（用于连交叉链接）

        columns.forEachIndexed { columnIndex, section ->
            val columnX = PADDING + columnIndex * (COLUMN_WIDTH + COLUMN_GAP)

            // 章节标题节点
            val header = Node(
                cardId = null,
                title = section.title,
                x = columnX,
                y = PADDING,
                width = COLUMN_WIDTH,
                height = HEADER_HEIGHT,
                claimCount = 0,
                descendable = false,
                isSection = true
            )
            nodes += header

            // 该列下所有卡片（含子章节），按大纲顺序纵向排
            val ordered = orderedCards(graph, section)
            var cursorY = PADDING + HEADER_HEIGHT + NODE_GAP
            ordered.forEach { card ->
                val node = Node(
                    cardId = card.id,
                    title = card.title,
                    x = columnX,
                    y = cursorY,
                    width = COLUMN_WIDTH,
                    height = NODE_HEIGHT,
                    claimCount = card.claims.size,
                    descendable = card.descendable,
                    isSection = false
                )
                nodes += node
                positionOf[card.id] = node

                // 层级连线：章节标题 → 本列的第一张卡（只画一条，避免视觉噪音）
                if (card === ordered.first()) {
                    lines += Line(
                        fromX = columnX + COLUMN_WIDTH / 2,
                        fromY = PADDING + HEADER_HEIGHT,
                        toX = columnX + COLUMN_WIDTH / 2,
                        toY = cursorY,
                        style = Line.Style.HIERARCHY
                    )
                }

                cursorY += NODE_HEIGHT + NODE_GAP
            }
            maxColumnBottom = max(maxColumnBottom, cursorY)
        }

        // 前置关系：先画，让它们压在交叉链接之上（前置比"相关"更重要）
        // 必须前置 = 实线，推荐前置 = 虚线
        val seenPrereq = HashSet<String>()
        graph.cards.forEach { card ->
            card.prerequisites.forEach { link ->
                val from = positionOf[link.from] ?: return@forEach
                val to = positionOf[link.to] ?: return@forEach
                if (!seenPrereq.add("${link.from}->${link.to}")) return@forEach
                lines += Line(
                    fromX = from.x + from.width / 2,
                    fromY = from.y + from.height,
                    toX = to.x + to.width / 2,
                    toY = to.y,
                    style = when (link.kind) {
                        PrerequisiteKind.REQUIRED -> Line.Style.PREREQUISITE_REQUIRED
                        PrerequisiteKind.RECOMMENDED -> Line.Style.PREREQUISITE_RECOMMENDED
                    }
                )
            }
        }

        // 交叉链接：最淡的虚线，去重（A→B 与 B→A 只画一条）
        val drawn = HashSet<String>()
        graph.cards.forEach { card ->
            val from = positionOf[card.id] ?: return@forEach
            card.crossLinks.take(5).forEach { link ->
                val to = positionOf[link.target] ?: return@forEach
                val key = listOf(card.id, link.target).sorted().joinToString("|")
                if (!drawn.add(key)) return@forEach
                lines += Line(
                    fromX = from.x + from.width,
                    fromY = from.y + from.height / 2,
                    toX = to.x,
                    toY = to.y + to.height / 2,
                    style = Line.Style.CROSS
                )
            }
        }

        val contentWidth = PADDING * 2 + columns.size * COLUMN_WIDTH +
            (columns.size - 1).coerceAtLeast(0) * COLUMN_GAP
        val contentHeight = maxColumnBottom + PADDING
        return Result(nodes, lines, contentWidth, contentHeight)
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
