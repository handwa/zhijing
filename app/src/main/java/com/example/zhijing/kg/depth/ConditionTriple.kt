package com.example.zhijing.kg.depth

/**
 * 「条件—机制—反事实」三元组 —— 深度图谱的真正原子。
 *
 * ## 三段各管什么
 *
 * | 段 | 回答的问题 | 缺了会怎样 |
 * |---|---|---|
 * | 条件 [statement] | 依赖什么前提 | 用户看到一句结论，不知道它的边界 |
 * | 机制 [mechanism] | **为什么**这条条件存在 | 用户只能背，不能推理 |
 * | 反事实 [interventions] | 拨动它，命题往哪漂 | 用户无法检验自己的因果模型 |
 *
 * ## 入库的硬门槛
 *
 * 三者缺一不可，且：
 * - [mechanism] 不能为空 —— 没有机制的条件只是一句前提，用户只能背
 * - [interventions] 至少一条 —— 不能拨的条件是装饰品
 * - 可计算的干预必须带 [ShapeContract]，且必须通过 `ContractEvaluator.validate`
 *
 * 写不出来的就不入库。这不是严格，是因为**写不出来本身就说明这条命题没被真正理解**。
 */
data class ConditionTriple(
    /** 全局唯一。形如 `tri.<claimId>.<n>`。 */
    val id: String,
    /** 所属命题。深度图谱里一条命题可以有多个条件三元组。 */
    val claimId: String,
    /** 卡片级归属，用于点卡片时聚合展示。 */
    val cardId: String,

    /* ------------------------------------------------ 条件 */
    /** 条件本身，一句话。如「输入张量最后一维必须等于权重输入维」。 */
    val statement: String,
    /** 条件类型，决定它能否被形式化。 */
    val kind: ConditionKind,

    /* ------------------------------------------------ 机制 */
    /**
     * 为什么这条条件存在。
     *
     * 判据：能回答「如果不满足，是哪一步塌掉的」。
     * 「因为形状要对」不算机制，那是同义反复。
     * 「矩阵乘按内维归约，内维不等则求和未定义」才算。
     */
    val mechanism: String,

    /* ------------------------------------------------ 反事实 */
    /** 可拨动的干预，至少一条。 */
    val interventions: List<Intervention>,

    /** 本条条件的可拨点是否由本地计算判定（true）还是查预置答案（false）。 */
    val computable: Boolean,
) {
    /**
     * 入库校验。
     *
     * @return 问题列表；空 = 可以入库。
     */
    fun problems(): List<String> {
        val out = ArrayList<String>()
        if (statement.isBlank()) out += "条件为空"
        if (mechanism.isBlank()) out += "缺机制 —— 没有机制的条件只能背，不能拨"
        if (interventions.isEmpty()) out += "没有可拨动的反事实 —— 这条条件是装饰品"
        if (computable && interventions.any { it.contract == null }) {
            out += "标为可计算，但有干预没带契约"
        }
        interventions.forEach { inv ->
            if (inv.action.isBlank()) out += "干预 ${inv.id} 缺动作描述"
            if (inv.consequence.isBlank()) out += "干预 ${inv.id} 缺后果描述"
            if (inv.misjudgements.isEmpty()) {
                out += "干预 ${inv.id} 没有误判库 —— 用户答错时无法解释"
            }
        }
        return out
    }

    /** 通过门槛才算合格。 */
    val isValid: Boolean get() = problems().isEmpty()
}

/**
 * 一个可拨动的干预。
 *
 * ## 关键设计：干预是**有限预设**，不是自由输入
 *
 * 自由输入无法判定（除非运行时调模型，那答案就不可复现）。
 * 因此每条条件预设 1–3 个干预点，每个干预点的后果在入库时就已确定：
 * - [contract] 非空 → 运行时本地求值，真值
 * - [contract] 为空 → 查 [outcome]，人工/模型预置的答案
 */
data class Intervention(
    val id: String,
    /** 干预动作，给用户看。如「把输入最后一维从 4 改成 5」。 */
    val action: String,
    /**
     * 干预的具体改动：符号 → 新值。
     *
     * 为空有两种情况：
     * - 不可计算（[contract] 也为空）—— 后果查 [outcome]；
     * - **结构型可计算**（[contract] 非空）—— 干预写在算子参数里（典型是 softmax 沿哪根轴）。
     *   这类契约本身就是干预后的形态，没有可拨的符号，因此 `ContractEvaluator.validate()`
     *   的「未拨动时应当成立」对它不适用。
     */
    val overrides: Map<String, Int> = emptyMap(),
    /** 可执行契约；非空则 [expected] 由求值器算出，忽略下面的 outcome。 */
    val contract: ShapeContract? = null,

    /** 预置的实际后果类别（仅不可计算时使用）。 */
    val outcome: Outcome = Outcome.HOLDS,
    /**
     * 后果的具体描述，必须写清「变成了什么」。
     *
     * 判据：用户读完能知道**命题往哪漂了**，而不是只知道「错了」。
     */
    val consequence: String,
    /** 后果的依据来源，决定 UI 是否要标「未经核实」。 */
    val source: TruthSource = TruthSource.COMPUTED,

    /** 典型误判库：用户答错时匹配其中一条来解释。 */
    val misjudgements: List<Misjudgement> = emptyList(),
) {
    /**
     * 算出实际后果。
     *
     * 有契约时**以计算结果为准**，预置的 [outcome] 只作为一致性对照。
     */
    fun resolveOutcome(): ResolvedOutcome {
        val c = contract ?: return ResolvedOutcome(
            outcome = outcome,
            detail = consequence,
            source = source,
        )
        return when (val r = ContractEvaluator.evaluate(c, overrides)) {
            is ContractEvaluator.Result.Holds -> ResolvedOutcome(
                outcome = Outcome.HOLDS,
                detail = consequence.ifBlank { "命题仍成立" },
                source = TruthSource.COMPUTED,
            )

            is ContractEvaluator.Result.Fails -> ResolvedOutcome(
                outcome = Outcome.FAILS,
                // 计算出的原因比预置文案更具体，直接用
                detail = r.reason,
                source = TruthSource.COMPUTED,
            )

            is ContractEvaluator.Result.Invalid -> ResolvedOutcome(
                outcome = Outcome.UNKNOWN,
                detail = "契约有问题：${r.reason}",
                source = TruthSource.COMPUTED,
            )
        }
    }

    /** 实际后果与预置答案是否一致（用于质检：不一致说明契约或文案有错）。 */
    fun isConsistent(): Boolean {
        if (contract == null) return true
        return resolveOutcome().outcome == outcome
    }
}

/**
 * 拨动后的后果类别。**四选一**，不设第五种。
 *
 * [UNKNOWN] 只用于契约异常，**不作为用户的预测选项**（见 [CHOICES]）：
 * 让用户去猜「无法判定」既没有教学价值，也会掩盖契约写错的问题。
 */
enum class Outcome(val label: String) {
    /** 仍成立。 */
    HOLDS("仍然成立"),

    /** 弱化：结论还成立但适用范围变窄 / 置信下降。 */
    WEAKENED("只是适用范围变窄"),

    /** 变成另一条命题：不是对错问题，而是描述对象换了。 */
    BECOMES_OTHER("变成另一条命题"),

    /** 不成立。 */
    FAILS("不再成立"),

    /** 无法判定（契约异常）。 */
    UNKNOWN("无法判定");

    companion object {
        /** 提供给用户选择的后果，不含 [UNKNOWN]。 */
        val CHOICES: List<Outcome> = entries.filter { it != UNKNOWN }

        /** 解析 JSON 中的 wire 值；无法识别返回 null，由调用方决定默认值。 */
        fun fromWire(raw: String?): Outcome? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

/** 后果答案的来源，决定 UI 的诚实标注。 */
enum class TruthSource {
    /** 本地求值算出，真值。 */
    COMPUTED,

    /** 人工撰写并审核。 */
    AUTHORED_HUMAN,

    /** 模型生成后固化，未经人工核实。UI 必须标注。 */
    AUTHORED_MODEL;

    companion object {
        /**
         * 解析 JSON 中的 wire 值。
         *
         * 缺省取 [AUTHORED_MODEL] 而不是 [COMPUTED]：来源不明时按「未经核实」处理，
         * 宁可多一句提示，也不能把没验过的结论当成真值展示。
         */
        fun fromWire(raw: String?): TruthSource =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUTHORED_MODEL
    }
}

/** 一个已解析的后果。 */
data class ResolvedOutcome(
    val outcome: Outcome,
    val detail: String,
    val source: TruthSource,
) {
    /** 用户是否应该看到「未经核实」的提示。 */
    val needsCaveat: Boolean get() = source == TruthSource.AUTHORED_MODEL
}

/**
 * 一条典型误判。
 *
 * ## 为什么必须有
 *
 * 只说「答错了」用户学不到东西。要指出**他把哪条约束误当成了什么**。
 *
 * 判据：[correction] 必须指向机制，不能是「再想想」这类空话。
 */
data class Misjudgement(
    /** 用户预测的后果（错的）。 */
    val predicted: Outcome,
    /** 他为什么会这样想 —— 指出认知上的具体滑坡。 */
    val why: String,
    /** 纠正：把误解接到机制上。 */
    val correction: String,
)

/** 条件类型。前四类可形式化，最后一类不行。 */
enum class ConditionKind(val wire: String, val label: String) {
    /** 形状/维度约束。可算。 */
    SHAPE("shape", "形状"),
    /** 取值范围约束。可算。 */
    RANGE("range", "值域"),
    /** 统计量约束（求和、归一化、分布）。半可算。 */
    STATISTICS("statistics", "统计量"),
    /** 梯度/可微性约束。半可算。 */
    GRADIENT("gradient", "梯度"),
    /** 语义/含义约束。**不可算，只能人工预设**。 */
    SEMANTIC("semantic", "语义");

    /** 能否用本地求值器判定。 */
    val computable: Boolean
        get() = this == SHAPE || this == RANGE || this == STATISTICS

    companion object {
        fun fromWire(s: String?): ConditionKind =
            entries.firstOrNull { it.wire == s } ?: SEMANTIC
    }
}
