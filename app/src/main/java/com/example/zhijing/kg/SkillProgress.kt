package com.example.zhijing.kg

import com.zhijing.data.model.ProbeStatus
import kotlin.math.ceil

/**
 * 卡片级「技能三态」：已点亮 / 现在能学 / 未解锁。
 *
 * ## 为什么是派生而不是存表
 *
 * 三态完全由「命题状态 + 前置关系」算出来，两者都已经在库里并且每次都现取。
 * 再存一份就等于给同一件事找第二个真相：改动任一前置关系或判定状态后，
 * 存量三态立刻变旧，而用户不会知道该信哪个。
 *
 * ## 三态的定义（别写成"进度条"）
 *
 * - [State.LIT]：卡内 ≥ [LIT_MASTER_RATIO] 的命题已是 `MASTERED`；
 * - [State.READY]：还没点亮，但**所有必须前置**都已点亮 —— 这就是"现在能学"；
 * - [State.LOCKED]：还有必须前置没点亮。
 *
 * 推荐前置不参与判定（"只提示，不挡路"）。没有命题的组织性卡片
 * （章节导语、总览）视为已点亮：它们没有可判定的内容，不该把后面全锁死。
 *
 * ## 为什么带 [CardProgress.downstreamCount]
 *
 * "现在能学"里有 5 张卡时，先学哪张？先学解锁得多的那张。
 * 这个数就是参考项目里"后续 44"那个标记，用来排序，不用来做评分。
 */
object SkillProgress {

    /** 判「已点亮」所需的命题掌握比例。放常量便于调参。 */
    const val LIT_MASTER_RATIO = 0.8f

    /** 卡片三态。 */
    enum class State {
        /** 已点亮。 */
        LIT,

        /** 现在能学：前置都亮了，自己还没亮。 */
        READY,

        /** 未解锁：还有必须前置没点亮。 */
        LOCKED,
    }

    /** 一张卡片的三态与解锁能力。 */
    data class CardProgress(
        val cardId: String,
        val state: State,
        /** 直接 + 间接解锁的卡片数（不含自己）。 */
        val downstreamCount: Int = 0,
        /** 尚未点亮的必须前置卡片 id（LOCKED 时给出，便于解释"卡在哪"）。 */
        val missingPrerequisites: List<String> = emptyList(),
        /**
         * 是否有可判定内容（挂了命题）。
         *
         * 组织性卡片（章节导语、总览）没有命题，永远"点亮"但不计入进度分母 ——
         * 否则 `✓ 3/22` 里会混进 5 张永远绿着的导语卡，分子分母都失真。
         */
        val learnable: Boolean = true,
    )

    /** 整张图谱的三态结果。 */
    data class Result(
        val cards: Map<String, CardProgress>,
        val lit: Int,
        val ready: Int,
        val locked: Int,
        /** 参与判定的卡片数（**不含**无命题的组织性卡片）。 */
        val total: Int,
    ) {
        fun stateOf(cardId: String): State? = cards[cardId]?.state

        /** 「现在能学」列表：按解锁数量降序，同数量按 id 稳定排序。 */
        val readyCards: List<CardProgress>
            get() = cards.values
                .filter { it.state == State.READY }
                .sortedWith(compareByDescending<CardProgress> { it.downstreamCount }.thenBy { it.cardId })

        /**
         * 一个结构图节点的状态：取它名下卡片里**最靠前**的那个限制。
         *
         * 优先级 LOCKED > READY > LIT：一个节点里只要有一张还没解锁，
         * 就不能显示成"可以学"，否则用户点进去会撞墙。没有卡片时返回 null（不画状态）。
         */
        fun stateOfCards(cardIds: List<String>): State? {
            if (cardIds.isEmpty()) return null
            // 只看向"可学卡片"：整节点都是导语时不该显示成已点亮
            val states = cardIds.mapNotNull { id ->
                cards[id]?.takeIf { it.learnable }?.state
            }
            if (states.isEmpty()) return null
            return when {
                states.any { it == State.LOCKED } -> State.LOCKED
                states.any { it == State.READY } -> State.READY
                else -> State.LIT
            }
        }
    }

    /**
     * 从合并后的图谱算三态。
     *
     * 输入只需要 [MergedGraph]：命题状态已经由 `GraphAssembler` 挂到 `card.claims[].probe` 上，
     * 前置关系挂在 `card.prerequisites` 上（指向本卡）。多传一份状态进来只会让两份数据有机会不同步。
     */
    fun of(graph: MergedGraph): Result {
        val cards = graph.cards
        if (cards.isEmpty()) return Result(emptyMap(), 0, 0, 0, 0)

        // 前置边：`card.prerequisites` 只挂在 to 端，这里统一去重成边集合
        val requiredEdges = cards.asSequence()
            .flatMap { it.prerequisites.asSequence() }
            .filter { it.kind == PrerequisiteKind.REQUIRED }
            .map { it.from to it.to }
            .distinct()
            .toList()

        val predecessors = requiredEdges.groupBy({ it.second }, { it.first })
        val successors = requiredEdges.groupBy({ it.first }, { it.second })

        val lit = cards.associate { it.id to isLit(it) }

        fun downstreamCount(cardId: String): Int {
            // 反向可达：从这张卡出发，能解锁谁（不含自己）
            val seen = HashSet<String>()
            val queue = ArrayDeque(successors[cardId].orEmpty())
            while (queue.isNotEmpty()) {
                val next = queue.removeFirst()
                if (next == cardId || !seen.add(next)) continue
                queue.addAll(successors[next].orEmpty())
            }
            return seen.size
        }

        val progress = cards.associate { card ->
            val missing = predecessors[card.id].orEmpty().filter { !(lit[it] ?: false) }
            val state = when {
                lit[card.id] == true -> State.LIT
                missing.isEmpty() -> State.READY
                else -> State.LOCKED
            }
            card.id to CardProgress(
                cardId = card.id,
                state = state,
                downstreamCount = downstreamCount(card.id),
                missingPrerequisites = missing,
                learnable = card.claims.isNotEmpty(),
            )
        }

        val learnable = progress.values.filter { it.learnable }
        return Result(
            cards = progress,
            lit = learnable.count { it.state == State.LIT },
            ready = learnable.count { it.state == State.READY },
            locked = learnable.count { it.state == State.LOCKED },
            total = learnable.size,
        )
    }

    /** 一张卡片是否已点亮：卡内多数命题已掌握。 */
    private fun isLit(card: GraphCard): Boolean {
        // 没有命题的组织性卡片：没有可判定内容，视为已点亮，避免把后面全锁死
        if (card.claims.isEmpty()) return true

        val mastered = card.claims.count { it.probe.probeStatus == ProbeStatus.MASTERED }
        val needed = ceil(card.claims.size * LIT_MASTER_RATIO).toInt().coerceAtLeast(1)
        return mastered >= needed
    }
}
