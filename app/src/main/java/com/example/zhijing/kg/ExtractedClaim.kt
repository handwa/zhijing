package com.example.zhijing.kg

/**
 * 云端抽取返回的一条候选命题（**解析产物**，尚未入库）。
 *
 * 与 `data` 模块的 `RuntimeClaim` 有意分开：
 *
 * - `ExtractedClaim` 是「模型说它是什么」，字段全部按模型可能给出的宽松形态声明
 *   （`conditions` 可能是空、`expected` 可能没给）；
 * - `RuntimeClaim` 是「落库后的样子」，必须补齐 ID、来源、审核状态等字段。
 *
 * 中间隔一层的好处：解析质量问题在这一层就能被单测抓住，不需要连数据库。
 */
data class ExtractedClaim(
    /** 一句话断言。缺了这条的候选直接丢弃（没有断言就无从判断）。 */
    val statement: String,

    /** 该命题是否成立。模型没给时按 `false` 处理 —— 宁可判错也不静默当成立。 */
    val expected: Boolean,

    /** 成立条件 / 边界。可能为空，入库前会补一条「未标注条件」。 */
    val conditions: List<String> = emptyList(),

    /** 判断面板文案；为空时由 statement 拼接。 */
    val question: String? = null,

    /** 撞错解释。 */
    val explanation: String? = null,

    /** 撞错时暴露的误解。 */
    val misconception: String? = null,

    /** 原文证据片段。无法逐字命中原文时会被丢弃（见 [ClaimOutputParser]）。 */
    val evidence: String = "",

    /** 错点类型，五类之一：shape / range / statistics / gradient / semantic。 */
    val errorKinds: List<ExtractedErrorKind> = emptyList(),

    /** 出边（候选命题之间的关系），可空。 */
    val edges: List<ExtractedEdge> = emptyList(),

    /** 解析过程中产生的问题，用于诊断与日志，不参与渲染。 */
    val warnings: List<String> = emptyList()
) {
    /** 是否具备入库的最低要求：有断言、有条件（补过默认条件也算）。 */
    val isUsable: Boolean get() = statement.isNotBlank() && conditions.isNotEmpty()
}

/** 一条错点定位：类型 + 为什么。 */
data class ExtractedErrorKind(
    val kind: String,
    val why: String
) {
    companion object {
        /** 五类条件依赖，来自 `graph.schema.json` 的 enumeration。 */
        val ALLOWED = setOf("shape", "range", "statistics", "gradient", "semantic")
    }
}

/**
 * 一条出边。
 *
 * `to` 允许为空：prompt 允许模型不写目标（[output_contract.md] 第 5 节），
 * 由上层按主题解析，或直接丢弃。
 */
data class ExtractedEdge(
    val to: String,
    val type: String,
    val kind: String,
    val premise: String,
    val boundary: String,
    val counterexample: String? = null,
    val weight: Double = 0.5
) {
    companion object {
        /** 六类边；与 `graph.schema.json` 的 `edge.type` 枚举一致。 */
        val ALLOWED_TYPES =
            setOf("depends_on", "affects", "contains", "contrasts", "refutes", "prerequisite")
    }
}
