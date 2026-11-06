package com.example.zhijing.kg

/**
 * 前置关系的**本地校验**：模型给的边不能直接信。
 *
 * 模型产出的前置关系有三类必然问题，必须由代码拦住：
 *
 * | 问题 | 后果 |
 * |---|---|
 * | 指向不存在的卡片 | 画不出来，浪费数据 |
 * | 成环（A→B→A） | 学习路径无法开始，逻辑错误 |
 * | 单卡入边过多 / 整体过密 | 全是前置等于没有前置，视觉上成一团乱麻 |
 *
 * 校验策略一律「**丢弃优先**」：宁可少画，不要画错。
 * 这与图谱其它部分（区间越界即丢、边缺边界即丢）保持一致。
 */
object PrerequisiteValidator {

    /** 每张卡最多被指出几条前置（入边上限）。 */
    const val MAX_INCOMING = 3

    /** 整体密度：边数 / 卡片数。低于 1.2 偏稀疏，高于 1.8 偏密。 */
    const val MAX_DENSITY = 1.8

    /** 一条被丢弃的边及原因。 */
    data class Rejection(val link: PrerequisiteLink, val reason: String)

    data class Result(
        val accepted: List<PrerequisiteLink>,
        val rejected: List<Rejection>
    )

    /**
     * 校验并清洗一组前置关系。
     *
     * @param links 模型给出的前置关系
     * @param knownCardIds 图谱里实际存在的卡片 id
     * @param now 保留参数位，便于将来加稳定性处理
     */
    fun validate(links: List<PrerequisiteLink>, knownCardIds: Set<String>): Result {
        val accepted = ArrayList<PrerequisiteLink>()
        val rejected = ArrayList<Rejection>()

        // 1) 端点校验 + 去重
        val seen = HashSet<String>()
        links.forEach { link ->
            when {
                link.from !in knownCardIds || link.to !in knownCardIds ->
                    rejected += Rejection(link, "端点不存在")

                link.from == link.to ->
                    rejected += Rejection(link, "自环")

                !seen.add("${link.from}->${link.to}") ->
                    rejected += Rejection(link, "重复")

                else -> accepted += link
            }
        }

        // 2) 去环：按卡数做拓扑排序，成环的边逐条剔除
        val acyclic = ArrayList<PrerequisiteLink>()
        // 必须前置优先保留（它们的约束更强）
        accepted.sortedByDescending { it.kind == PrerequisiteKind.REQUIRED }.forEach { link ->
            if (createsCycle(acyclic, link)) {
                rejected += Rejection(link, "成环")
            } else {
                acyclic += link
            }
        }

        // 3) 入边上限：按 kind 排序后截断（必须前置优先）
        val grouped = acyclic.groupBy { it.to }
        val capped = ArrayList<PrerequisiteLink>()
        grouped.forEach { (_, incoming) ->
            val sorted = incoming.sortedByDescending { it.kind == PrerequisiteKind.REQUIRED }
            sorted.take(MAX_INCOMING).forEach { capped += it }
            sorted.drop(MAX_INCOMING).forEach {
                rejected += Rejection(it, "入边超过 $MAX_INCOMING 条")
            }
        }

        // 4) 整体密度：超限时保留 required
        val cardCount = knownCardIds.size
        // 显式声明为 List，否则会被推断成 ArrayList，后面赋 List 会类型不匹配
        var survivors: List<PrerequisiteLink> = capped
        if (cardCount > 0) {
            val limit = (cardCount * MAX_DENSITY).toInt().coerceAtLeast(1)
            if (capped.size > limit) {
                val keepIds = capped
                    .sortedByDescending { it.kind == PrerequisiteKind.REQUIRED }
                    .take(limit)
                    .map { "${it.from}->${it.to}" }
                    .toSet()
                survivors = capped.filter { "${it.from}->${it.to}" in keepIds }
                capped.filter { "${it.from}->${it.to}" !in keepIds }.forEach {
                    rejected += Rejection(it, "整体密度超限")
                }
            }
        }

        // 恢复稳定顺序：按 from / to 排序，保证渲染确定
        return Result(
            accepted = survivors.sortedWith(compareBy({ it.from }, { it.to })),
            rejected = rejected
        )
    }

    /** 加入 [candidate] 后是否会成环（即 from 已能达到 to 的路径上存在）。 */
    private fun createsCycle(existing: List<PrerequisiteLink>, candidate: PrerequisiteLink): Boolean {
        // 环 = 从 candidate.to 出发，能沿已有边走到 candidate.from
        val adjacency = HashMap<String, MutableList<String>>()
        existing.forEach { adjacency.getOrPut(it.from) { ArrayList() } += it.to }

        val stack = ArrayDeque<String>()
        val visited = HashSet<String>()
        stack.addLast(candidate.to)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node == candidate.from) return true
            if (!visited.add(node)) continue
            adjacency[node]?.forEach { stack.addLast(it) }
        }
        return false
    }
}
