package com.example.zhijing.kg.ui

import com.example.zhijing.kg.GraphCard
import com.example.zhijing.kg.GraphOutlineNode
import com.example.zhijing.kg.MergedGraph
import com.example.zhijing.kg.PrerequisiteKind
import kotlin.math.abs
import kotlin.math.hypot
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


    /**
     * 同一列里上下两个节点之间的缝隙。
     *
     * 从 22 提到 28：依赖线不再走直线，而是从上一个节点底边**弧**到下一个节点顶边，
     * 22dp 的缝隙里弧线会被压成几乎垂直的一段，和原来的直线没有差别。
     * 只加 6dp，13 个节点多出不到 80dp，不影响一屏。
     */
    const val ROW_GAP = 28f

    /** 车道与列右边缘的距离。 */
    private const val LANE_GAP = 10f

    /** 同一列有多条长跳时，车道依次外推的间距。 */
    private const val LANE_STEP = 8f

    /** 每列最多复用几条车道；再多就该拆依赖了。 */
    private const val LANE_SLOTS = 3

    /** 有车道时给内容宽度留的余量，否则车道会被平移钳制裁掉。 */
    private const val LANE_RESERVE = 40f

    /** 行高：只按节点高度算 —— 卡片已删除，不要把卡片高度算进来。 */
    const val ROW_HEIGHT = HEADER_HEIGHT + ROW_GAP





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
        /**
         * 所属章节（一级大纲节点）的 path；主题根与章节自己是自己。
         *
         * 布局期算好，是因为「跨章节依赖要淡化」只有这里知道父子关系；
         * 放到绘制层再反查就等于维护第二份「path → 章节」映射。
         */
        val chapterPath: String,
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
        /** 线的走向，默认直连（旧行为）。 */
        val route: Route = Route.STRAIGHT,
        /**
         * [Route.CURVE] 的控制点（dp 绝对坐标）。
         *
         * 在布局阶段算好而不是绘制阶段算：控制点要避开节点框，只有布局知道几何，
         * DrawScope 里手算会把「节点位置」这份唯一真相复制一份出去。
         */
        val ctrlX: Float = 0f,
        val ctrlY: Float = 0f,
        /**
         * 两端是否分属不同章节。
         *
         * 跨章节依赖在选中时会被淡化一档：它仍然存在（不能删），
         * 但不该和「同一章里的下一步」抢同样的视觉权重。
         */
        val crossChapter: Boolean = false,
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

        /**
         * 连线走向。
         *
         * - [STRAIGHT]：两点直连（交叉链接）
         * - [ELBOW]：正交折线，与层级线同一套走法（必须前置跨列时复用它，少一种线型）
         * - [CURVE]：二次贝塞尔，控制点见 [ctrlX] / [ctrlY]
         */
        /**
         * [LANE]：列外车道。同列长跳（中间隔着节点）走出列→沿槽下行→回车，
         * 不再从中间卡片底下穿过（那样只剩断断续续的线段，读不出依赖）。
         */
        enum class Route { STRAIGHT, ELBOW, CURVE, LANE }
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
    fun layout(graph: MergedGraph, mode: LayoutMode = LayoutMode.HIERARCHY): Result {
        val topics = graph.outline.sortedBy { it.order }
        if (topics.isEmpty()) return Result(emptyList(), emptyList(), 0f, 0f)

        // 依赖视图：把「卡片级前置」上卷成节点级边，**只在兄弟集合内**做稳定拓扑排序。
        // 层级视图返回空集合，走原来的大纲顺序 —— 这条分支保证旧行为与旧测试完全不变。
        val dependencyEdgeSet =
            if (mode == LayoutMode.DEPENDENCY) dependencyEdges(graph) else emptySet()

        fun orderChildren(children: List<GraphOutlineNode>): List<GraphOutlineNode> =
            if (dependencyEdgeSet.isEmpty()) {
                children.sortedBy { it.order }
            } else {
                sortSiblingsByDependency(children, dependencyEdgeSet)
            }

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
                val children = orderChildren(node.children)
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

            /**
             * @param chapterPath 所属章节 path；depth == 1 的节点自己就是章节
             */
            fun place(
                node: GraphOutlineNode,
                depth: Int,
                parentNode: Node?,
                chapterPath: String,
            ) {
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
                    chapterPath = chapterPath,
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
                        route = Line.Route.ELBOW,
                        crossChapter = parentNode.chapterPath != chapterPath,
                    )
                }

                node.children.sortedBy { it.order }.forEach { child ->
                    // 章节 = topic 的直接子节点；再深的节点继承上一层的章节
                    place(
                        node = child,
                        depth = depth + 1,
                        parentNode = self,
                        chapterPath = if (depth == 0) child.path else chapterPath,
                    )
                }
            }

            place(topic, depth = 0, parentNode = null, chapterPath = topic.path)

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

        // 列外车道：同列长跳要绕开中间节点，只能借这一列右侧的空白。
        // 先记下每列最右边界，再给同列多条长跳依次外推，避免两条车道互相压。
        val columnRight = HashMap<Float, Float>()
        positionOf.values.forEach { node ->
            columnRight[node.x] = max(columnRight[node.x] ?: 0f, node.x + node.width)
        }
        val laneUsed = HashMap<Float, Int>()
        var laneCount = 0

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
                // 端点几何：横向 tidy tree 里同层节点共享一列，所以大多数前置
                // 是「同列上下两节点」。
                //
                // 画法（刻意不是直线）：
                // - 同列：源下边中 → 目标上边中，但走**二次贝塞尔**向列左侧鼓出。
                //   直线会把「前一个的底」和「后一个的顶」连成一条竖线，看上去像
                //   表格的分隔线而不是关系；弧线在 28dp 的缝隙里绕一下，才像"连过去"。
                //   跨多个节点的长跳鼓得更大，否则弧线会贴着中间节点压过去。
                // - 跨列 + 必须前置：**复用层级线的正交折线**（父右中 → 中缝 → 左中）。
                //   既然树已经有这条路径，再画一条斜线就是同一条关系画两遍。
                // - 跨列 + 推荐前置：虚线自己一条浅弧，不与层级线共用通道 ——
                //   "建议"不该看起来像"结构"。
                val sameColumn = abs(from.x - to.x) < 1f
                val required = link.kind == PrerequisiteKind.REQUIRED
                val startX = if (sameColumn) from.x + from.width / 2 else from.x + from.width
                val startY = if (sameColumn) from.y + from.height else from.y + from.height / 2
                val endX = if (sameColumn) to.x + to.width / 2 else to.x
                val endY = if (sameColumn) to.y else to.y + to.height / 2

                // 同列长跳（中间隔着节点）：走列外车道，否则线从中间卡片底下穿过，
                // 屏幕上只剩断断续续的线段，用户读不出「谁依赖谁」。
                //
                // 判据用**节点顶部的行距**而不是两个端点：端点还包含节点高度，
                // 相邻两行算出来是 ROW_HEIGHT - HEADER_HEIGHT，会把两行间距误判成相邻。
                val longJump = sameColumn && abs(to.y - from.y) > ROW_HEIGHT * 1.5f
                val route = when {
                    longJump -> Line.Route.LANE
                    sameColumn -> Line.Route.CURVE
                    required -> Line.Route.ELBOW
                    else -> Line.Route.CURVE
                }
                val bulge = when {
                    // 同列相邻：小弧（10dp），在 28dp 的缝隙里绕一下就够
                    sameColumn -> 10f
                    // 跨列虚线：浅弧，别和层级折线共用通道
                    else -> 14f
                }
                val ctrl = curveControl(startX, startY, endX, endY, bulge)

                // 车道横坐标：该列最右 + 10dp，同列第 n 条再往外推 8dp（3 条一循环）
                val laneX = if (longJump) {
                    val right = columnRight[from.x] ?: (from.x + from.width)
                    val index = laneUsed[from.x] ?: 0
                    laneUsed[from.x] = index + 1
                    laneCount += 1
                    right + LANE_GAP + (index % LANE_SLOTS) * LANE_STEP
                } else {
                    0f
                }

                // 车道两端落在**侧边中点**：箭头指在节点右边缘上，
                // 不再沿上边缘横穿到节点中心（那样箭头会浮在卡片里面/边上，读数很别扭）
                val lineStartX = if (longJump) from.x + from.width else startX
                val lineStartY = if (longJump) from.y + from.height / 2f else startY
                val lineEndX = if (longJump) to.x + to.width else endX
                val lineEndY = if (longJump) to.y + to.height / 2f else endY

                lines += Line(
                    fromX = lineStartX,
                    fromY = lineStartY,
                    toX = lineEndX,
                    toY = lineEndY,
                    style = when (link.kind) {
                        PrerequisiteKind.REQUIRED -> Line.Style.PREREQUISITE_REQUIRED
                        PrerequisiteKind.RECOMMENDED -> Line.Style.PREREQUISITE_RECOMMENDED
                    },
                    fromNode = fromPath,
                    toNode = toPath,
                    route = route,
                    ctrlX = if (longJump) laneX else ctrl.first,
                    ctrlY = if (longJump) 0f else ctrl.second,
                    crossChapter = from.chapterPath != to.chapterPath,
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
                    crossChapter = from.chapterPath != to.chapterPath,
                )
            }
        }

        // 有车道就把右侧余量放大，否则最外那条车道会被平移钳制当成「内容外」裁掉
        val contentWidth = maxRight + PADDING * 2 + if (laneCount > 0) LANE_RESERVE else 0f
        val contentHeight = groupTop
        return Result(nodes, lines, contentWidth, contentHeight, groups)
    }

    /**
     * 二次贝塞尔的控制点：中点沿**垂直方向**偏移 [bulge]。
     *
     * 符号约定：向下走的线（同列）正数向**左**鼓，水平走的线（跨列）正数向**下**鼓。
     * 抽出成纯函数是为了能单测——弧线的形状一旦错，截图里会立刻变成「线穿过卡片」。
     */
    internal fun curveControl(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        bulge: Float,
    ): Pair<Float, Float> {
        val dx = toX - fromX
        val dy = toY - fromY
        val len = hypot(dx, dy).coerceAtLeast(0.001f)
        val mx = (fromX + toX) / 2f
        val my = (fromY + toY) / 2f
        return Pair(mx - dy / len * bulge, my + dx / len * bulge)
    }

    /** 结构图的两种读法。 */
    enum class LayoutMode {
        /** 默认：按大纲顺序排，回答「这个主题由哪些部分组成」。 */
        HIERARCHY,

        /** 只看依赖：同一父节点下按依赖拓扑序排，回答「我该按什么顺序学」。 */
        DEPENDENCY,
    }

    /**
     * 节点级前置边（仅依赖视图使用）。
     *
     * 前置本来是**卡片级**的，这里上卷到节点级：一条边 `from → to` 表示「先学 from，再学 to」。
     * 同一节点内部卡片互相指向（`from == to`）直接丢弃，否则会产生自环。
     */
    internal fun dependencyEdges(graph: MergedGraph): Set<Pair<String, String>> {
        val nodeOfCard = HashMap<String, String>()
        graph.cards.forEach { nodeOfCard[it.id] = it.outlinePath }

        val edges = LinkedHashSet<Pair<String, String>>()
        graph.cards.forEach { card ->
            card.prerequisites.forEach { link ->
                val from = nodeOfCard[link.from]
                val to = nodeOfCard[link.to]
                if (from != null && to != null && from != to) {
                    edges += Pair(from, to)
                }
            }
        }
        return edges
    }

    /**
     * 兄弟节点内的**稳定**拓扑排序。
     *
     * ## 为什么只在兄弟之间排，而不是按全局深度排
     *
     * 曾经用「全局最长路径深度」当排序键，结果：**没有任何前置的节点深度为 0，
     * 会被顶到同组最前**。`2.3 统计量依赖` 就是这样跑到 `2.1 形状依赖` 前面的 ——
     * 数据少一条边，界面就给出一个看起来像 bug 的顺序（生成图谱里同样会发生）。
     *
     * 局部排序只修正「前置被排到了后面」这一种情况，不制造新顺序：
     * 没有依赖关系的节点保持大纲顺序不动。
     *
     * 成环时退化为「按大纲顺序依次取出」，保证不死循环、不丢节点。
     */
    private fun sortSiblingsByDependency(
        siblings: List<GraphOutlineNode>,
        edges: Set<Pair<String, String>>,
    ): List<GraphOutlineNode> {
        val ordered = siblings.sortedBy { it.order }
        val inGroup = ordered.mapTo(HashSet()) { it.path }

        val indegree = HashMap<String, Int>()
        val outgoing = HashMap<String, MutableList<String>>()
        ordered.forEach { indegree[it.path] = 0 }

        edges.forEach { (from, to) ->
            if (from in inGroup && to in inGroup) {
                outgoing.getOrPut(from) { ArrayList() }.add(to)
                indegree[to] = (indegree[to] ?: 0) + 1
            }
        }
        // 组内没有任何依赖：保持大纲顺序
        if (outgoing.isEmpty()) return ordered

        val result = ArrayList<GraphOutlineNode>(ordered.size)
        val remaining = ordered.toMutableList()
        while (remaining.isNotEmpty()) {
            // 稳定：每一步都取「大纲顺序最靠前的可排节点」
            val next = remaining.firstOrNull { (indegree[it.path] ?: 0) == 0 }
                ?: remaining.first() // 成环兜底
            remaining.remove(next)
            result.add(next)
            outgoing[next.path].orEmpty().forEach { target ->
                indegree[target] = (indegree[target] ?: 1) - 1
            }
        }
        return result
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
