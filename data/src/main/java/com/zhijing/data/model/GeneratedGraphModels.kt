package com.zhijing.data.model

/**
 * 运行时**生成**的图谱（区别于 `assets/kg/graph.json` 里的出厂图谱）。
 *
 * ## 为什么要单独一张表
 *
 * 出厂图谱是"出版物"（随 APK 发布、只读），而用户输入一个领域、由模型生成的大纲与卡片
 * 是**用户数据**：可增删、可重建、每个主题一份。两者生命周期完全不同，不能混存。
 *
 * | | 出厂图谱 | 生成图谱（本模型） |
 * |---|---|---|
 * | 位置 | `assets/kg/graph.json` | Room `kg_generated_graph` |
 * | 可写 | 否 | 是 |
 * | 份数 | 1 份 | 每个主题 1 份 |
 *
 * 合并渲染时 `GraphAssembler` 把两者拼在一起。
 *
 * @param topicId 主题标识（取自用户输入或追问结果），同一主题重复生成会覆盖
 * @param title 显示名
 * @param matchThreshold 交叉链接相似度阈值，生成时所用（便于追溯）
 * @param createdTime 生成时间（毫秒）
 */
data class GeneratedGraph(
    val topicId: String,
    val title: String,
    val createdTime: Long,
    /** 生成它的来源：cloud / local / manual。 */
    val source: String = "cloud",
    /** 生成时的服务商 id，如 `deepseek`；本地生成为 null。 */
    val providerId: String? = null,
    /** 模型切出的块数（诊断用）。 */
    val blockCount: Int = 0,
    /** 生成过程中的警告，供 UI 提示。 */
    val warnings: List<String> = emptyList(),
    /**
     * 正文是否**可溯源**。
     *
     * - `true`：用户提供了原文，正文从原文逐字截取
     * - `false`：只给了主题，**正文由模型生成，未经核实**
     *
     * UI 必须对 `false` 的图谱显示「AI 生成，未经核实」。
     * 没有原文时，正文就是模型的理解，不是知识。
     */
    val verified: Boolean = true
)

/**
 * 生成图谱里的一张卡片。
 *
 * 与出厂卡片的字段对齐（title / summary / body / outlinePath），
 * 这样渲染层不需要区分来源。
 */
data class GeneratedCard(
    val topicId: String,
    /** 卡片 id，在主题内唯一，如 `crd.gen.<topic>.<n>`。 */
    val cardId: String,
    val outlinePath: String,
    val title: String,
    val summary: String,
    val body: String,
    /** 排序序号，与大纲 order 对应。 */
    val order: Int
)

/**
 * 生成图谱里的一个大纲节点。
 *
 * 层级由 `OutlineBuilder` 从标题编号推出来，不是模型给的。
 */
data class GeneratedOutlineNode(
    val topicId: String,
    /** 层级路径，如 `t-a3f1.t-b2c4`。 */
    val path: String,
    val parentPath: String?,
    val title: String,
    val level: Int,
    val order: Int
)

/**
 * 生成图谱里的一条卡片级前置关系。
 *
 * 由模型在每块的 `requires` 里给出（按 title 引用），本地映射成 cardId 后存下来。
 *
 * @param fromCardId 先学这张
 * @param toCardId 后学这张
 * @param kind required / recommended
 * @param why 为什么存在这条前置
 */
data class GeneratedPrerequisite(
    val topicId: String,
    val fromCardId: String,
    val toCardId: String,
    val kind: String,
    val why: String = ""
)

/**
 * 生成图谱里的一条命题（v8）。
 *
 * 它属于**这份生成图谱**，不进入 `kg_runtime_claim` 的人工审核队列：
 * 主题删掉命题一起删，同一主题重新生成整份覆盖。
 *
 * 与出厂命题的差别只有两点：`evidence` 通常是空串（没有原文可溯源），
 * 以及 UI 必须标注「未经核实」——生成图谱的正文本身就是模型撰写的。
 */
data class GeneratedClaim(
    val topicId: String,
    /** 命题 id，`sha1(规范化 statement)[:4]` 推导，主题内唯一。 */
    val claimId: String,
    /** 挂在哪张卡片下，决定下钻入口。 */
    val cardId: String,
    val outlinePath: String,
    val statement: String,
    val expected: Boolean,
    val conditions: List<String> = emptyList(),
    val explanation: String = "",
    val misconception: String = "",
    val evidence: String = "",
    /** 同卡内排序，保证题目顺序稳定。 */
    val order: Int = 0
)
