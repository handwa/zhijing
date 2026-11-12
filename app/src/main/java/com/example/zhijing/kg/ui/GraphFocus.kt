package com.example.zhijing.kg.ui

import com.example.zhijing.kg.GraphOutlineNode

/**
 * 决定「从卡片流切到结构图时，该定位到哪个节点」。
 *
 * ## 为什么要单独抽出来
 *
 * 这里踩过一个坑：章节 chip 存的是节点 **id**（`out.s1`），
 * 而结构图的聚焦按节点 **path**（`s1`）匹配。直接拿 chip 的值去聚焦不会报错，
 * 只是**静默不生效**——症状是「切过去跟以前一模一样」，很难从界面看出原因。
 * 抽成纯函数后可以直接断言返回的是 path 而不是 id。
 */
internal object GraphFocus {

    /**
     * @param cardOutlinePath 刚点过的那张卡片所属的节点 path（没有则为 null）
     * @param outline 大纲树
     * @param selectedOutlineId 当前筛选的章节节点 **id**
     * @return 结构图应当定位的节点 path；null 表示不主动移动视图
     */
    fun resolvePath(
        cardOutlinePath: String?,
        outline: List<GraphOutlineNode>,
        selectedOutlineId: String?,
    ): String? {
        // 1) 「刚看过的那张卡」优先级最高
        if (!cardOutlinePath.isNullOrBlank()) return cardOutlinePath

        // 2) 回落到当前筛选的章节：id → path
        if (selectedOutlineId.isNullOrBlank()) return null
        return findById(outline, selectedOutlineId)?.path
    }

    private fun findById(nodes: List<GraphOutlineNode>, id: String): GraphOutlineNode? {
        nodes.forEach { node ->
            if (node.id == id) return node
            findById(node.children, id)?.let { return it }
        }
        return null
    }
}
