package com.example.zhijing.kg

/**
 * 广度图谱的**本地算法**：模型只切块，结构与关系全部在这里算。
 *
 * 分工（对应 `kg/prompts/extract_outline.md`）：
 *
 * | 环节 | 谁负责 |
 * |---|---|
 * | 切出「有哪些部分」 | 模型 |
 * | 层级、正文、交叉链接 | **本地（本文件）** |
 *
 * 为什么不让模型做这些：
 * - 层级：模型对「1.2 应该挂在 1 下面」这种结构推理很不稳，而编号本身已经写明了；
 * - 正文：让模型重写会失真，直接截原文零失真；
 * - 链接：词共现是确定性的，模型给不出来还不稳定。
 */
object OutlineBuilder {

    /** 模型返回的一个内容块（区间是原文的字符下标）。 */
    data class RawBlock(
        val title: String,
        val summary: String,
        val bodyStart: Int,
        val bodyEnd: Int
    )

    /** 加工后的大纲节点。 */
    data class OutlineNode(
        val path: String,
        val title: String,
        val level: Int,
        val order: Int,
        val parentPath: String?
    )

    /** 加工后的卡片。 */
    data class Card(
        val outlinePath: String,
        val title: String,
        val summary: String,
        /** 从原文逐字截取的正文。 */
        val body: String
    )

    /** 一次广度构建的结果。 */
    data class Result(
        val outline: List<OutlineNode>,
        val cards: List<Card>,
        val warnings: List<String>
    )

    /**
     * 把模型的分块加工成「大纲 + 卡片」。
     *
     * @param content 原文（区间基于它计算）
     * @param blocks 模型给的分块
     */
    fun build(content: String, blocks: List<RawBlock>): Result {
        val warnings = ArrayList<String>()
        val valid = ArrayList<RawBlock>()

        // 1) 区间校验：越界、倒置、重叠的一律丢弃
        var lastEnd = 0
        blocks.forEachIndexed { i, b ->
            val title = b.title.trim()
            if (title.isEmpty()) {
                warnings += "#${i + 1} 缺少 title，已丢弃"
                return@forEachIndexed
            }
            if (b.bodyStart < 0 || b.bodyEnd > content.length || b.bodyStart >= b.bodyEnd) {
                warnings += "#${i + 1}「$title」区间非法（${b.bodyStart}..${b.bodyEnd}，原文 ${content.length} 字），已丢弃"
                return@forEachIndexed
            }
            if (b.bodyStart < lastEnd) {
                warnings += "#${i + 1}「$title」与前一块重叠，已丢弃重叠部分之外仍无意义，整块丢弃"
                return@forEachIndexed
            }
            lastEnd = b.bodyEnd
            valid += b.copy(title = title, summary = b.summary.trim())
        }

        // 2) 层级推断
        val numbered = valid.map { numberDepth(it.title) }
        val levels: List<Int> = if (numbered.all { it != null }) {
            val maxDepth = numbered.filterNotNull().maxOrNull() ?: 1
            numbered.map { depth -> (depth ?: 1).coerceIn(1, MAX_LEVEL).coerceAtMost(maxDepth.coerceAtLeast(1)) }
        } else {
            // 无编号（或部分有）：首块为根，其余与首块同级
            valid.indices.map { if (it == 0) 1 else 1 }
        }

        // 3) 路径与父子关系
        val paths = ArrayList<String>()
        val nodes = ArrayList<OutlineNode>()
        val usedSlugs = HashMap<String, Int>()

        valid.forEachIndexed { i, block ->
            val level = levels[i]
            val slug = uniqueSlug(slugifyTitle(block.title), usedSlugs)
            // 找最近的、层级恰好小一级的前驱作为父节点
            val parentPath = if (level <= 1) null else {
                var j = i - 1
                var found: String? = null
                while (j >= 0) {
                    if (levels[j] < level) {
                        found = paths[j]
                        break
                    }
                    j--
                }
                found
            }
            val path = if (parentPath == null) slug else "$parentPath.$slug"
            paths += path
            nodes += OutlineNode(
                path = path,
                title = block.title,
                level = level,
                order = i,
                parentPath = parentPath
            )
        }

        // 4) 卡片：正文直接截原文
        val cards = valid.mapIndexed { i, block ->
            Card(
                outlinePath = paths[i],
                title = block.title,
                summary = block.summary.ifBlank { block.title },
                body = content.substring(block.bodyStart, block.bodyEnd).trim()
            )
        }

        return Result(nodes, cards, warnings)
    }

    /** 大纲最大层级，与 `graph.schema.json` 的 `level ≤ 3` 一致。 */
    const val MAX_LEVEL = 3

    /**
     * 从标题里读编号深度。
     *
     * 认这些写法：`1`、`1.2`、`1.2.3`、`一、`、`（一）`、`第一章`。
     * 读不出返回 null。
     */
    fun numberDepth(title: String): Int? {
        val t = title.trim()
        // 1 / 1.2 / 1.2.3
        Regex("^(\\d+(?:\\.\\d+)*)").find(t)?.let { m ->
            return m.groupValues[1].split('.').size
        }
        // （一）/ (一)
        if (Regex("^[（(][一二三四五六七八九十]+[）)]").containsMatchIn(t)) return 1
        // 一、 / 一.
        if (Regex("^[一二三四五六七八九十]+[、.．]").containsMatchIn(t)) return 1
        // 第X章 / 第X节
        Regex("^第[一二三四五六七八九十百\\d]+[章节篇]").find(t)?.let {
            return if (t.startsWith("第") && (t.contains("节") || t.contains("篇"))) 2 else 1
        }
        return null
    }

    /**
     * 标题 → 可读 slug。
     *
     * 与 `ClaimIds.slugify` 同思路：保留 ASCII 词，中文整体丢弃后补哈希兜底。
     * 中文标题无法转拼音（不引第三方库），所以宁可 slug 不具意义也不能让它为空。
     */
    fun slugifyTitle(title: String): String {
        val ascii = title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-', '.')
        if (ascii.isNotEmpty()) return ascii.take(32)
        // 去掉编号前缀后再取哈希，保证「1.1 Linear」与「Linear」不同块时也有区分
        val stripped = title.replace(Regex("^[\\d.\\s、（）()一二三四五六七八九十]+"), "")
        return "t-" + ClaimIds.shortHash(stripped.ifBlank { title }, 6)
    }

    private fun uniqueSlug(slug: String, used: MutableMap<String, Int>): String {
        val n = (used[slug] ?: 0) + 1
        used[slug] = n
        return if (n == 1) slug else "$slug-$n"
    }
}
