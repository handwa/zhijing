package com.zhijing.data.knowledge

import com.zhijing.data.model.ProbeStatus

/**
 * 判断收敛规则。
 *
 * 把 `kg/web/app.js` 里已经验证过的收敛逻辑（连续判对 2 次 → 已掌握）搬到数据层，
 * 让端侧 UI 与 Web 演示页共用同一份判定，避免两处各写一套导致状态口径不一致。
 *
 * 设计文档明确要求：**「已探测」不等于「已掌握」**，撞错后存在「已纠正」与「仍易错」
 * 两种状态，因此 [ProbeStatus.CONFUSED] 与 [ProbeStatus.VERIFIED] 必须分开。
 */
object ProbeRules {

    /** 连续判对多少次收敛为「已掌握」。 */
    const val MASTER_STREAK = 2

    /** 广度侧最多保留几条误解注记（回写是摘要而非明细）。 */
    const val MAX_MISCONCEPTIONS = 3

    /** 判对一次后的状态。 */
    fun onCorrect(streak: Int): ProbeStatus =
        if (streak >= MASTER_STREAK) ProbeStatus.MASTERED else ProbeStatus.VERIFIED

    /** 判错一次后的状态：无论之前是否已掌握，都回到易混淆。 */
    fun onWrong(): ProbeStatus = ProbeStatus.CONFUSED

    /** 追加误解，保留最近 [MAX_MISCONCEPTIONS] 条，去重。 */
    fun appendMisconception(existing: List<String>, incoming: String?): List<String> {
        if (incoming.isNullOrBlank()) return existing
        val merged = (existing + incoming).distinct()
        return if (merged.size <= MAX_MISCONCEPTIONS) merged else merged.takeLast(MAX_MISCONCEPTIONS)
    }

    /** 置信度 = 判对率，纯展示用，不参与判定。 */
    fun confidence(correct: Int, attempts: Int): Float =
        if (attempts <= 0) 0f else (correct.toFloat() / attempts).coerceIn(0f, 1f)
}
