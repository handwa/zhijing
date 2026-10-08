package com.zhijing.data

/**
 * 「知境」数据层的全局常量，取值与《软件需求规格说明书 V2.1》保持一致，
 * 供内容理解、检索、问答等模块直接引用，避免各处各写一份。
 */
object DbConstants {

    /** 说明书 3.4：数据库文件放在应用私有目录，名为 zhijing.db。 */
    const val DATABASE_NAME = "zhijing.db"

    /** 说明书 3.4.1 / 6.2：向量模型输出 512 维向量。 */
    const val VECTOR_DIM = 512

    /** 说明书内置参数 similarity_threshold：检索相似度阈值。 */
    const val SIMILARITY_THRESHOLD = 0.65f

    /** 说明书内置参数 retrieval_top_k：检索返回的最相关条数。 */
    const val RETRIEVAL_TOP_K = 5

    /** 说明书 3.4.8：解析任务失败最多重试 3 次。 */
    const val MAX_PARSE_RETRY = 3

    /** 说明书 3.3.1：分享内容原文超过 5000 字截断。 */
    const val MAX_RAW_TEXT_LENGTH = 5000
}
