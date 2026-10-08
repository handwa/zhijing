package com.zhijing.data.model

/** 唤醒计划、问答记录、动物状态、解析任务四类「行为记录」的对外领域模型。 */

/** 记忆唤醒计划（wake_plan_table 的对外视图）。 */
data class WakePlan(
    val id: Long,
    val enabled: Boolean = false,
    val cycleType: String? = null,
    val triggerTime: String? = null,
    val scope: String? = null,
    val mode: WakeMode = WakeMode.STATEMENT,
    val dailyQuestionNum: Int = 0,
    val nextRunTime: Long = 0
)

/** 一次问答记录（qa_record_table 的对外视图）。 */
data class QaRecord(
    val id: Long,
    val questionText: String,
    val answerText: String? = null,
    val sourceContentIds: List<Long> = emptyList(),
    val retrievalMs: Long = 0,
    val generateMs: Long = 0,
    val answerStatus: AnswerStatus = AnswerStatus.SUCCESS
)

/** 养成系动物状态（pet_status_table 的对外视图），全局仅一条。 */
data class PetStatus(
    val phase: PetPhase,
    val totalContents: Int,
    val activeDays: Int,
    val accessories: String?,
    val lastDisplayTime: Long
)

/** 解析队列中的一条任务（parse_task_table 的对外视图）。 */
data class ParseTask(
    val id: Long,
    val contentId: Long,
    val taskType: ParseTaskType,
    val status: ParseTaskStatus,
    val retryCount: Int,
    val createdTime: Long
)
