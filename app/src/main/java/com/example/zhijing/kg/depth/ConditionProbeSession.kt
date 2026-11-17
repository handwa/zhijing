package com.example.zhijing.kg.depth

/**
 * 「预测 → 揭示」的会话状态机。
 *
 * ## 为什么是纯逻辑
 *
 * 交互流程是这条产品线里唯一需要验证「方案是否成立」的环节，必须能被单测覆盖：
 * 出题顺序、不能跳过、判定是否与本地求值器一致、答错时是否匹配到对应误解。
 * 因此这里不依赖 Compose，也不碰 Room；UI 只负责把 [Step] 画出来。
 *
 * ## 单一真相
 *
 * 「现在是第几题 / 这一题答没答 / 该显示题目还是揭示」全部由 [step] 一处给出。
 * UI **不要**自己再维护「是否已揭示」之类的并行状态 —— 两份状态一旦不同步，
 * 就会出现「题号 3/2」「四个选项都点不动」这类问题。
 */
internal class ConditionProbeSession private constructor(
    private val slots: List<Slot>,
    /** 起始位置：从路径里「重走」某一题时直接从它开始，而不是从头再答一遍。 */
    startIndex: Int = 0,
) {

    /**
     * 一个「条件 × 干预点」的组合，是出题的最小单位。
     *
     * [position] 是它在排序后的位置（0 起）。**题号必须来自位置，不能用已答计数**：
     * 已答计数在「答完但未推进」时等于位置 +1，一混用就会出现 `3/2`。
     */
    private data class Slot(
        val position: Int,
        val triple: ConditionTriple,
        val intervention: Intervention,
    )

    /** 一道待答的题。 */
    data class Question(
        val tripleId: String,
        val claimId: String,
        val cardId: String,
        val interventionId: String,
        /** 条件本身，帮用户知道自己在拨什么。 */
        val condition: String,
        /** 干预动作，即题目正文。 */
        val action: String,
        /** 可选项，不含 [Outcome.UNKNOWN]。 */
        val options: List<Outcome>,
        /** 第几题（1 起）。 */
        val ordinal: Int,
        /** 本题组总题数。 */
        val total: Int,
    )

    /** 一次作答的结果（揭示屏所需的一切）。 */
    data class Answer(
        val question: Question,
        val predicted: Outcome,
        /** 本地求值器（或预置答案）给出的真实后果。 */
        val actual: ResolvedOutcome,
        val matched: Boolean,
        /** 为什么这条条件存在 —— 揭示屏要讲的就是它。 */
        val mechanism: String,
        /** 用户这次选错所对应的典型误解；答对或没有对应条目时为 null。 */
        val misconception: Misjudgement?,
    )

    /** UI 的三种界面状态，互斥且完备。 */
    sealed interface Step {
        /** 出题：还有没答的题。 */
        data class Asking(val question: Question) : Step

        /** 揭示：当前题已作答、尚未推进。 */
        data class Revealing(val answer: Answer) : Step

        /** 本卡片的题已全部走完，或根本没有可拨条件。 */
        data object Done : Step
    }

    private var index = startIndex.coerceIn(0, slots.size)
    private val answered = LinkedHashMap<String, Answer>()

    /** 是否有可出题的干预点（图谱里没有 triples 时为 false，UI 应完全跳过本流程）。 */
    val hasQuestions: Boolean get() = slots.isNotEmpty()

    /** 全部题目是否走完。 */
    val isFinished: Boolean get() = index >= slots.size

    /**
     * 当前界面的唯一真相。
     *
     * 注意分支顺序：**先看当前题是否已答**，再看是否还有题。
     * 否则「答完最后一题、还没推进」会被当成「已结束」，直接跳过揭示。
     */
    val step: Step
        get() {
            val slot = slots.getOrNull(index) ?: return Step.Done
            val recorded = answered[slot.intervention.id]
            return if (recorded != null) Step.Revealing(recorded) else Step.Asking(slot.toQuestion())
        }

    /** 当前是否有题可答。UI 在点击回调里用它兜底，避免重复作答把界面打崩。 */
    val hasPending: Boolean get() = step is Step.Asking

    /** 当前待答题；已答或已结束时为 null。 */
    val current: Question? get() = (step as? Step.Asking)?.question

    /** 推进后是否还有下一题（用于决定按钮文案）。 */
    val hasNext: Boolean get() = index + 1 < slots.size

    /** 已作答的记录，按作答顺序。阶段 3 会把它写入 `kg_condition_probe`。 */
    val answers: List<Answer> get() = answered.values.toList()

    val total: Int get() = slots.size

    val answeredCount: Int get() = answered.size

    /**
     * 作答。
     *
     * @throws IllegalStateException 当前没有待答题，或本题已答过。
     * @throws IllegalArgumentException 传入 [Outcome.UNKNOWN]（它不是用户的预测选项）。
     */
    fun answer(choice: Outcome): Answer {
        require(choice != Outcome.UNKNOWN) { "UNKNOWN 不是用户的预测选项" }

        val slot = slots.getOrNull(index) ?: error("没有待答的干预点")
        check(!answered.containsKey(slot.intervention.id)) { "干预点 ${slot.intervention.id} 已回答过" }

        val question = slot.toQuestion()
        val actual = slot.intervention.resolveOutcome()
        val result = Answer(
            question = question,
            predicted = choice,
            actual = actual,
            matched = actual.outcome == choice,
            mechanism = slot.triple.mechanism,
            misconception = slot.intervention.misjudgements.firstOrNull { it.predicted == choice },
        )
        answered[question.interventionId] = result
        return result
    }

    /**
     * 进入下一题。
     *
     * @return true 表示还有下一题；false 表示已到末尾**或当前题还没答**（不能跳过）。
     */
    fun advance(): Boolean {
        val slot = slots.getOrNull(index) ?: return false
        // 「必须选一个答案」是产品约束：没答就推进等于允许跳过
        if (!answered.containsKey(slot.intervention.id)) return false

        index++
        return index < slots.size
    }

    private fun Slot.toQuestion(): Question = Question(
        tripleId = triple.id,
        claimId = triple.claimId,
        cardId = triple.cardId,
        interventionId = intervention.id,
        condition = triple.statement,
        action = intervention.action,
        options = Outcome.CHOICES,
        ordinal = position + 1,
        total = slots.size,
    )

    companion object {

        /**
         * 某张卡片下所有合格三元组的题。UI 从卡片下钻时用这个。
         *
         * @param skipInterventionIds 已经拨过的干预点：重新下钻时不再问第二遍。
         *        不跳过的话，一张有 5 个干预点的卡每次进来都要先把 5 道题重答一遍
         *        才能看到判断题面板 —— 用户会以为「答了没反应」。
         */
        /**
         * @param startAt 从哪个干预点开始（理解路径「重走这道」）；命不中就从头开始。
         *        找不到时**不抛异常**：三元组下架后旧路径还在，不能让界面崩掉。
         */
        fun forCard(
            triples: List<ConditionTriple>,
            cardId: String,
            skipInterventionIds: Set<String> = emptySet(),
            startAt: String? = null,
        ): ConditionProbeSession =
            create(triples.filter { it.cardId == cardId }, skipInterventionIds, startAt)

        /** 某条命题下所有合格三元组的题。 */
        fun forClaim(triples: List<ConditionTriple>, claimId: String): ConditionProbeSession =
            create(triples.filter { it.claimId == claimId })

        private fun create(
            triples: List<ConditionTriple>,
            skipInterventionIds: Set<String> = emptySet(),
            startAt: String? = null,
        ): ConditionProbeSession {
            val pairs = triples
                // 入库三门槛：机制为空、没有干预、干预没有误判库的，不进交互
                .filter { it.isValid }
                .flatMap { triple -> triple.interventions.map { intervention -> triple to intervention } }
                // 已经拨过的干预点不再出题（题目顺序与"第几题"都按剩下的内容重算）
                .filter { (_, intervention) -> intervention.id !in skipInterventionIds }
                // 固定顺序，保证同一份数据每次出题一致（也便于单测）
                .sortedWith(compareBy({ it.first.id }, { it.second.id }))

            val slots = pairs.mapIndexed { position, (triple, intervention) ->
                Slot(position = position, triple = triple, intervention = intervention)
            }
            // 重走的起点：命不中（三元组被下架）就从第一题开始
            val startIndex = startAt
                ?.let { id -> slots.indexOfFirst { it.intervention.id == id } }
                ?.takeIf { it >= 0 } ?: 0
            return ConditionProbeSession(slots, startIndex)
        }
    }
}
