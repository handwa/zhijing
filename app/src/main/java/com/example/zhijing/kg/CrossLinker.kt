package com.example.zhijing.kg

/**
 * 交叉链接的**本地算法**：按正文的词共现算两张卡的相关度。
 *
 * 为什么由本地算而不是让模型给：
 * - 确定性：同样输入永远同样输出，可复现、可 diff；
 * - 便宜：不需要额外一次模型调用；
 * - 可控：阈值和封顶是硬约束（设计文档要求每卡 ≤5 条），程序守比模型守可靠。
 *
 * 算法刻意简单（词重叠而非向量相似）：
 * 广度图谱的交叉链接只是**阅读时的横向指引**，不需要语义级精度；
 * 而简单算法没有参数可调、没有模型可退化，出问题一眼能看出来。
 */
object CrossLinker {

    /** 两张卡之间的链接。 */
    data class Link(
        val targetCardId: String,
        /** 共享词，最多保留 3 个，用于卡片上显示「关联依据」。 */
        val sharedTerms: List<String>,
        /** 相似度 0..1，用作排序权重。 */
        val weight: Float
    )

    /** 每张卡最多保留的链接数（设计文档硬约束）。 */
    const val MAX_LINKS = 5

    /**
     * 相似度阈值。
     *
     * 0.14 是经验值：按 Jaccard 算共享词占并集的比例，
     * 低于它连出来的多是「都提到某个高频词」这种无意义关联。
     */
    const val THRESHOLD = 0.14f

    /** 参与计算的单词长度下限（过滤「的」「是」这类无意义单字）。 */
    private const val MIN_TERM_LEN = 2

    /**
     * 高频停用词：中文虚词与英文虚词。
     *
     * 2 字滑窗会把「这是一个」切成「这是」「是一」「一个」，
     * 这类词在任何文本里都高频出现，保留下来会让无关卡片被连边。
     */
    private val STOPWORDS = setOf(
        // 指示/判断类
        "这是", "那是", "是一", "一个", "这个", "那个", "这些", "那些", "这种", "那种",
        "可以", "如果", "因为", "所以", "但是", "而且", "并且", "或者", "然后",
        // 人称/虚词
        "就是", "不是", "没有", "我们", "你们", "他们", "自己", "什么", "怎么",
        "这样", "那样", "同一", "直接", "需要", "时候", "问题", "方式", "情况",
        "已经", "还是", "只是", "非常", "比较", "一些", "一样", "也是",
        // 英文虚词
        "the", "and", "for", "that", "this", "with", "from", "are", "was", "you",
        "have", "has", "but", "not", "all", "any", "can", "will"
    )

    /**
     * 给一组卡片算交叉链接。
     *
     * @param cards 卡片 id 与正文
     * @return cardId -> 按权重降序、最多 [MAX_LINKS] 条的链接列表
     */
    fun compute(cards: Map<String, String>): Map<String, List<Link>> {
        val tokens = cards.mapValues { (_, body) -> tokenize(body) }
        val result = LinkedHashMap<String, List<Link>>()

        cards.keys.forEach { from ->
            val fromTokens = tokens[from] ?: return@forEach
            if (fromTokens.isEmpty()) {
                result[from] = emptyList()
                return@forEach
            }
            val links = cards.keys
                .asSequence()
                .filter { it != from }
                .mapNotNull { to ->
                    val toTokens = tokens[to] ?: return@mapNotNull null
                    if (toTokens.isEmpty()) return@mapNotNull null
                    val shared = (fromTokens intersect toTokens)
                    if (shared.isEmpty()) return@mapNotNull null
                    // Jaccard：共享词 / 并集，避免「长卡全连」的偏置
                    val union = (fromTokens union toTokens).size
                    val weight = shared.size.toFloat() / union.coerceAtLeast(1)
                    if (weight < THRESHOLD) return@mapNotNull null
                    Link(
                        targetCardId = to,
                        sharedTerms = shared.take(3).toList(),
                        weight = weight
                    )
                }
                .sortedWith(compareByDescending<Link> { it.weight }.thenBy { it.targetCardId })
                .take(MAX_LINKS)
                .toList()
            result[from] = links
        }
        return result
    }

    /**
     * 分词：中文按 2 字滑窗，英文/数字按连续词。
     *
     * 中文分词需要词典，广度链接用不上那么高的精度 —— 2 字滑窗能把
     * 「张量形状」拆成「张量」「量形」「形状」，其中「形状」这类实词会与
     * 别的卡重合，已经足够把相关卡片找出来。
     */
    fun tokenize(text: String): Set<String> {
        val out = LinkedHashSet<String>()

        // 英文与数字词
        Regex("[A-Za-z][A-Za-z0-9_]{1,}").findAll(text).forEach { m ->
            val w = m.value.lowercase()
            if (w.length >= MIN_TERM_LEN && w !in STOPWORDS) out += w
        }

        // 中文 2 字滑窗
        val cjkRuns = Regex("[\\u4e00-\\u9fff]{2,}").findAll(text)
        cjkRuns.forEach { m ->
            val run = m.value
            for (i in 0..(run.length - MIN_TERM_LEN)) {
                val term = run.substring(i, i + MIN_TERM_LEN)
                if (term !in STOPWORDS) out += term
            }
        }

        return out
    }
}
