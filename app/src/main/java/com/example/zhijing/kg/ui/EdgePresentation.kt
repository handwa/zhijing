package com.example.zhijing.kg.ui

import com.example.zhijing.kg.ui.TreeLayout.Line

/**
 * 「选中一张卡片时，哪些线该出现、以什么身份出现」。
 *
 * ## 为什么需要单独一层
 *
 * 线有两种属性，来源不同：
 * - **关系本身**（谁依赖谁、是必须还是推荐）在布局阶段就定了，写进 [Line.style]；
 * - **显示身份**（对我而言它是"前置"还是"后续"）取决于**当前选中谁**，是渲染期的事。
 *
 * 把身份判定抽成纯函数，而不是塞在 DrawScope 里，是因为这是交互契约：
 * 没选中时只有层级线、选中后只出与它相关的线。写错了用户看到的是一张糊图，
 * 单测里却什么都发现不了 —— 所以这里必须能脱离 Compose 断言。
 */
internal object EdgePresentation {

    /** 一条线在当前选中状态下的显示身份；`null` 表示不画。 */
    enum class Role {
        /** 层级线：结构骨架，始终可见。 */
        HIERARCHY,

        /** 必须前置：指向当前选中的节点（选中在 `to` 端）。暗红实线 + 箭头。 */
        REQUIRED,

        /** 推荐前置：指向当前选中的节点。暖橙虚线，**无箭头**。 */
        RECOMMENDED,

        /** 后续线：从当前选中的节点出发。柔和蓝实线 + 箭头。 */
        SUCCESSOR,

        /** 交叉关联：最淡的虚线。 */
        CROSS,
    }

    /**
     * 判定一条线的显示身份。
     *
     * 规则（用户定的，不要"优化"）：
     * 1. 未选中任何节点时，**只显示层级线**，前置 / 推荐 / 关联全部隐藏；
     * 2. 选中后只显示与它相连的线；
     * 3. 同一条依赖边，选中 `to` 端是「前置」，选中 `from` 端是「后续」——
     *    方向因此不需要用户去猜，是随选中对象换称谓的。
     */
    fun roleOf(
        style: Line.Style,
        fromNode: String?,
        toNode: String?,
        selected: String?,
        /**
         * 「只看依赖」视图：这个视图的全部内容就是依赖关系，
         * 隐藏之后画布上只剩节点，所以未选中时也要把依赖线画出来。
         */
        alwaysShowDependencies: Boolean = false,
    ): Role? {
        // 层级线是骨架，不受选中影响
        if (style == Line.Style.HIERARCHY) return Role.HIERARCHY

        if (selected == null) {
            if (!alwaysShowDependencies) return null
            // 未选中时按边的原始方向画：指向 to 的那一端就是"后学"
            return when (style) {
                Line.Style.PREREQUISITE_REQUIRED -> Role.REQUIRED
                Line.Style.PREREQUISITE_RECOMMENDED -> Role.RECOMMENDED
                Line.Style.CROSS -> null
                Line.Style.HIERARCHY -> Role.HIERARCHY
            }
        }

        val touches = fromNode == selected || toNode == selected
        if (!touches) return null

        return when (style) {
            Line.Style.CROSS -> Role.CROSS
            Line.Style.PREREQUISITE_REQUIRED ->
                if (toNode == selected) Role.REQUIRED else Role.SUCCESSOR

            // 推荐关系被反向看时也按「后续线」画：用户要的是"顺着这条线能走到哪"，
            // 强弱信息在这一侧不重要（前置视角里仍然用虚线表达）。
            Line.Style.PREREQUISITE_RECOMMENDED ->
                if (toNode == selected) Role.RECOMMENDED else Role.SUCCESSOR

            Line.Style.HIERARCHY -> Role.HIERARCHY
        }
    }

    /**
     * 选中节点时，与它直接相连的**其它节点**（用于把相关卡片一起高亮）。
     *
     * 只算依赖与关联，不算层级线：层级线连着父子，把它们全点亮等于点亮半张图。
     */
    fun relatedNodes(lines: List<Line>, selected: String?): Set<String> {
        if (selected == null) return emptySet()
        return lines.asSequence()
            .filter { it.style != Line.Style.HIERARCHY }
            .filter { it.fromNode == selected || it.toNode == selected }
            .flatMap { sequenceOf(it.fromNode, it.toNode) }
            .filterNotNull()
            .filter { it != selected }
            .toSet()
    }
}
