package com.zhijing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * 生成图谱的命题表 `kg_generated_claim`（v8 新增）。
 *
 * ## 为什么不复用 `kg_runtime_claim`
 *
 * `kg_runtime_claim` 是「云端/用户给整个知识库新增命题」的入口，要求
 * `pending → confirmed` 才进图谱 —— 它保护的是"别把模型胡说的命题当成知识"。
 * 生成图谱的命题是**那份图谱自己的一部分**：主题删掉就该一起消失，
 * 也不该把用户的审核队列塞满几十条。两件事的生命周期和语义都不一样，所以分表。
 *
 * ## 为什么要 `card_id`
 *
 * 下钻入口是卡片（`GraphCard.descendable = claims.isNotEmpty()`），命题必须能挂回卡片。
 * 同一主题重复生成时整份覆盖（主键 `(topic_id, claim_id)`）。
 */
@Entity(
    tableName = "kg_generated_claim",
    primaryKeys = ["topic_id", "claim_id"],
    indices = [
        Index(value = ["topic_id"]),
        Index(value = ["topic_id", "card_id"])
    ]
)
internal data class KgGeneratedClaimEntity(
    @ColumnInfo(name = "topic_id")
    val topicId: String,

    /** 命题 ID，主题内唯一，由 `sha1(规范化 statement)[:4]` 推导。 */
    @ColumnInfo(name = "claim_id")
    val claimId: String,

    /** 挂在哪张生成卡片下（`kg_generated_card.card_id`）。 */
    @ColumnInfo(name = "card_id")
    val cardId: String,

    @ColumnInfo(name = "outline_path")
    val outlinePath: String,

    /** 一句话断言。 */
    @ColumnInfo(name = "statement")
    val statement: String,

    /** 标准答案：这句话是否成立。 */
    @ColumnInfo(name = "expected")
    val expected: Boolean,

    /** 成立条件 / 边界，JSON 数组（`StringListConverters`）。 */
    @ColumnInfo(name = "conditions")
    val conditions: List<String>,

    @ColumnInfo(name = "explanation")
    val explanation: String,

    @ColumnInfo(name = "misconception")
    val misconception: String,

    /** 依据片段；生成图谱没有原文，通常是空串。 */
    @ColumnInfo(name = "evidence")
    val evidence: String,

    /** 同卡内排序，保证每次下钻的题序一致。 */
    @ColumnInfo(name = "order_index")
    val order: Int
)
