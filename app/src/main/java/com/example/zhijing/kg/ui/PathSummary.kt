package com.example.zhijing.kg.ui

import com.example.zhijing.kg.ConditionPathStep

/**
 * 理解路径的一句话主干摘要。
 *
 * ## 为什么需要
 *
 * 路径明细是逐条念的（提问 → 你猜 → 实际），读完一串之后用户记不住自己到底变了什么。
 * roadmap 要求补一句「你从 X 走到 Y」—— 这句话才是路径的落点：它描述的是**认知变化**，
 * 而不是又一遍对错记录。
 *
 * ## 为什么没有变化就不显示
 *
 * 只答了一次、或者几次猜的是同一个东西时，摘要会退化成「你从 X 走到 X」，
 * 既没有信息量又像在凑字数。这种时候宁可不渲染。
 */
internal object PathSummary {

    /**
     * @param steps `GraphClaim.conditionPath`，按作答先后排列
     * @return 形如「你从「一步」走到「两步」」；无变化或路径为空时为 null
     */
    fun of(steps: List<ConditionPathStep>): String? {
        if (steps.isEmpty()) return null

        val first = steps.first()
        // 起点优先用「原来猜的」：那才是路径的入口，没有才退回当前猜测
        val start = (first.previousPredicted ?: first.predicted).trim()
        val end = steps.last().predicted.trim()

        if (start.isEmpty() || end.isEmpty()) return null
        if (start == end) return null

        return "你从「$start」走到「$end」"
    }
}
