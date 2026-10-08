package com.zhijing.data.model

/**
 * 说明书中以 INTEGER / TEXT 约定的枚举字段，在 Kotlin 侧以类型安全的枚举暴露，
 * 由 `com.zhijing.data.converter` 中的 TypeConverter 负责与数据库表示互转。
 * 数值取值严格对齐说明书 3.4 各表，不得改动。
 */

/** content_table.analysis_status：0 待处理 / 1 处理中 / 2 完成 / 3 失败 */
enum class AnalysisStatus(val code: Int) {
    PENDING(0),
    RUNNING(1),
    DONE(2),
    FAILED(3);

    companion object {
        fun fromCode(code: Int): AnalysisStatus = entries.firstOrNull { it.code == code } ?: PENDING
    }
}

/** content_table.embedding_status：向量是否已生成 */
enum class EmbeddingStatus(val code: Int) {
    PENDING(0),
    DONE(1),
    FAILED(2);

    companion object {
        fun fromCode(code: Int): EmbeddingStatus = entries.firstOrNull { it.code == code } ?: PENDING
    }
}

/** content_table.import_channel：share / whitelist / manual */
enum class ImportChannel(val wire: String) {
    SHARE("share"),
    WHITELIST("whitelist"),
    MANUAL("manual");

    companion object {
        fun fromWire(wire: String?): ImportChannel =
            entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) } ?: MANUAL
    }
}

/**
 * association_table.feedback：0 未评价 / 1 很好 / 2 平常 / 3 不好。
 * [weightDelta] 对应说明书内置参数 feedback_weights = {"很好":+0.1,"平常":0,"不好":-0.05}。
 */
enum class AssociationFeedback(val code: Int, val weightDelta: Double) {
    NOT_RATED(0, 0.0),
    GOOD(1, 0.1),
    NORMAL(2, 0.0),
    BAD(3, -0.05);

    companion object {
        fun fromCode(code: Int): AssociationFeedback = entries.firstOrNull { it.code == code } ?: NOT_RATED
    }
}

/** stance_compare_table.status：0 待展示 / 1 已展示 / 2 用户已看完 */
enum class StanceStatus(val code: Int) {
    PENDING(0),
    SHOWN(1),
    READ(2);

    companion object {
        fun fromCode(code: Int): StanceStatus = entries.firstOrNull { it.code == code } ?: PENDING
    }
}

/** wake_plan_table.mode：question（提问）或 statement（直述要点） */
enum class WakeMode(val wire: String) {
    QUESTION("question"),
    STATEMENT("statement");

    companion object {
        fun fromWire(wire: String?): WakeMode =
            entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) } ?: STATEMENT
    }
}

/** qa_record_table.answer_status：0 成功 / 1 无相关内容 / 2 超时 / 3 用户取消 */
enum class AnswerStatus(val code: Int) {
    SUCCESS(0),
    NO_CONTENT(1),
    TIMEOUT(2),
    CANCELLED(3);

    companion object {
        fun fromCode(code: Int): AnswerStatus = entries.firstOrNull { it.code == code } ?: SUCCESS
    }
}

/** pet_status_table.phase：0 种子 / 1 萌芽 / 2 生长 / 3 绽放 */
enum class PetPhase(val code: Int) {
    SEED(0),
    SPROUT(1),
    GROWING(2),
    BLOOM(3);

    companion object {
        fun fromCode(code: Int): PetPhase = entries.firstOrNull { it.code == code } ?: SEED
    }
}

/** parse_task_table.task_type：analyze（大模型理解）或 embed（向量化） */
enum class ParseTaskType(val wire: String) {
    ANALYZE("analyze"),
    EMBED("embed");

    companion object {
        fun fromWire(wire: String?): ParseTaskType =
            entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) } ?: ANALYZE
    }
}

/** parse_task_table.status：0 排队 / 1 执行 / 2 完成 / 3 失败 */
enum class ParseTaskStatus(val code: Int) {
    QUEUED(0),
    RUNNING(1),
    DONE(2),
    FAILED(3);

    companion object {
        fun fromCode(code: Int): ParseTaskStatus = entries.firstOrNull { it.code == code } ?: QUEUED
    }
}

/**
 * vector_table.owner_type：区分内容级向量与分段级向量。
 * 说明书 3.4.1 只要求 512 维内容向量，分段级为段落级 RAG 预留。
 */
enum class VectorOwnerType(val wire: String) {
    CONTENT("content"),
    CHUNK("chunk");

    companion object {
        fun fromWire(wire: String?): VectorOwnerType =
            entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) } ?: CONTENT
    }
}
