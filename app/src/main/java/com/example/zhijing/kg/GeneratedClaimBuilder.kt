package com.example.zhijing.kg

import com.zhijing.data.model.GeneratedCard
import com.zhijing.data.model.GeneratedClaim

/**
 * 把模型抽出的候选命题收敛成**可入库**的生成命题。
 *
 * ## 为什么单独一层
 *
 * 模型的输出不能直接落库：太长的是段落、太短的是标签、同一句话会换个说法出现两次、
 * 一张卡能吐出六条。这些过滤如果写进 `GraphGenerator`，就只能靠联网调模型来测；
 * 抽成纯函数后，用普通 JUnit 就能钉住「哪些留下、哪些丢掉」。
 *
 * ## 四道闸门
 *
 * 1. 长度 6–80 字：更短多半是术语，更长不是判断题；
 * 2. 规范化去重：同一句断言换措辞只留第一次（ID 相同，留着也是覆盖）；
 * 3. 每卡最多 [MAX_CLAIMS_PER_CARD] 条：下钻一次答 6 道题没人受得了；
 * 4. ID 不能为空白：`ClaimIds.claimId` 用大纲路径 + 断言哈希，`clm.*` 才配得上后续的状态表。
 */
internal object GeneratedClaimBuilder {

    /** 每张卡最多留几条命题。 */
    const val MAX_CLAIMS_PER_CARD = 2

    private const val MIN_STATEMENT = 6
    private const val MAX_STATEMENT = 80

    /**
     * @param extractedByCard 模型输出，按卡片 id 分组（缺卡视为这条没抽到）
     */
    fun build(
        topicId: String,
        cards: List<GeneratedCard>,
        extractedByCard: Map<String, List<ExtractedClaim>>,
    ): List<GeneratedClaim> {
        val seenStatements = HashSet<String>()
        val seenIds = HashSet<String>()
        val out = ArrayList<GeneratedClaim>()

        cards.forEach { card ->
            var accepted = 0
            for (candidate in extractedByCard[card.cardId].orEmpty()) {
                if (accepted >= MAX_CLAIMS_PER_CARD) break

                val statement = candidate.statement.trim()
                if (statement.length !in MIN_STATEMENT..MAX_STATEMENT) continue
                if (!seenStatements.add(ClaimIds.normalize(statement))) continue

                val claimId = ClaimIds.claimId(card.outlinePath, statement)
                if (!seenIds.add(claimId)) continue

                out += GeneratedClaim(
                    topicId = topicId,
                    claimId = claimId,
                    cardId = card.cardId,
                    outlinePath = card.outlinePath,
                    statement = statement,
                    expected = candidate.expected,
                    // 空条件也要给一句：判断面板靠它解释「在什么前提下才成立」
                    conditions = candidate.conditions.ifEmpty { listOf("未标注条件") },
                    explanation = candidate.explanation.orEmpty(),
                    misconception = candidate.misconception.orEmpty(),
                    // 生成图谱没有原文可溯源，evidence 一律留空；UI 会标「未经核实」
                    evidence = "",
                    order = accepted
                )
                accepted++
            }
        }
        return out
    }
}
