package com.example.zhijing.kg.ui

import com.example.zhijing.kg.SkillProgress

/**
 * 「刚点亮」的差分计算。
 *
 * ## 为什么要单独一层
 *
 * 动效的触发条件比动画本身更容易写错：写错的表现是「一进图所有绿点一起闪」，
 * 或者「明明点亮了却没反馈」。这两种都不会崩，只能靠断言钉住，所以把判定抽成纯函数。
 *
 * ## 首次进入不闪
 *
 * 上一次状态表为空时**不产生**任何 newlyLit —— 那是「刚打开图谱」，
 * 不是「刚刚点亮」。否则每次进图谱页都像中了奖。
 */
internal object SkillAnimation {

    /** 每次最多同时做几个扩散动画：低端机上全图一起动会掉帧。 */
    const val MAX_CONCURRENT = 6

    /** 每个节点的三态（按节点名下卡片聚合，规则见 [SkillProgress.Result.stateOfCards]）。 */
    fun nodeStates(
        layout: TreeLayout.Result,
        skill: SkillProgress.Result,
    ): Map<String, SkillProgress.State> = layout.nodes
        .mapNotNull { node -> skill.stateOfCards(node.cardIds)?.let { node.nodePath to it } }
        .toMap()

    /**
     * 本次「从非点亮变成已点亮」的节点。
     *
     * @param previous 上一次的节点状态；空表表示首次进入，直接返回空
     */
    fun newlyLit(
        previous: Map<String, SkillProgress.State>,
        current: Map<String, SkillProgress.State>,
    ): List<String> {
        if (previous.isEmpty()) return emptyList()
        return current
            .filter { (path, state) ->
                state == SkillProgress.State.LIT &&
                    previous[path] != null &&
                    previous[path] != SkillProgress.State.LIT
            }
            .keys
            .sorted()
    }
}
