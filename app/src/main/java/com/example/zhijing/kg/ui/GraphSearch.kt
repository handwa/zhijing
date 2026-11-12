package com.example.zhijing.kg.ui

import com.example.zhijing.kg.GraphOutlineNode

/**
 * 结构图的节点搜索。
 *
 * ## 为什么单独抽出来
 *
 * 纯字符串匹配 + 树遍历，不依赖 Compose，可以直接单测。
 * 图谱已有 24 个节点，生成图谱还可能到 100+，靠眼睛找不现实。
 *
 * ## 匹配规则
 *
 * - 大小写不敏感的子串匹配（中文没有大小写，英文如 `softmax` / `Softmax` 都能命中）；
 * - **匹配标题而不是正文**：搜索的用途是「定位到哪个节点」，正文匹配会带来大量无关结果；
 * - 按大纲先序返回，结果天然与视觉顺序一致；
 * - 结果上限 [DEFAULT_LIMIT]，避免输入单字时把整棵树都列出来。
 */
internal object GraphSearch {

    const val DEFAULT_LIMIT = 20

    /** 一条命中。 */
    data class Hit(
        /** 命中节点的 path，作为选中与定位的键。 */
        val path: String,
        val title: String,
        /** 上级标题链，如「1 前馈网络：从张量到概率」，帮助区分同名节点。 */
        val breadcrumb: String,
    )

    fun search(
        outline: List<GraphOutlineNode>,
        query: String,
        limit: Int = DEFAULT_LIMIT,
    ): List<Hit> {
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) return emptyList()

        val hits = ArrayList<Hit>()
        fun walk(nodes: List<GraphOutlineNode>, trail: List<String>) {
            nodes.sortedBy { it.order }.forEach { node ->
                if (hits.size >= limit) return
                if (node.title.lowercase().contains(keyword)) {
                    hits += Hit(
                        path = node.path,
                        title = node.title,
                        breadcrumb = trail.joinToString(" / "),
                    )
                }
                walk(node.children, trail + node.title)
            }
        }
        walk(outline, emptyList())
        return hits
    }
}
