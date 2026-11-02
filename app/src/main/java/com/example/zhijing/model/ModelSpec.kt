package com.example.zhijing.model

/**
 * 一个端侧模型的装配说明书。
 *
 * 换模型时只改这里：assets 路径、落盘文件名、体积、上下文长度、系统提示词。
 * 业务层（MainActivity）不再出现任何模型相关的魔数。
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val assetPath: String,
    val fileName: String,
    val sizeBytes: Long,
    /** 请求的上下文窗口（tokens）。0 表示使用模型自身训练上下文。 */
    val contextSize: Int,
    val systemPrompt: String,
    val predictLength: Int = 512,
)

/**
 * 嵌入模型的装配说明书（RAG 用）。
 *
 * 与生成模型分开加载、常驻内存；bge-small-zh 是 512 维 BERT，约 48 MB，
 * 和 3B 生成模型共存的开销可以忽略。
 */
data class EmbeddingSpec(
    val id: String,
    val assetPath: String,
    val fileName: String,
    val sizeBytes: Long,
    /** 输出维度，需与 DbConstants.VECTOR_DIM 一致。 */
    val dim: Int,
)

object ModelRegistry {

    /**
     * 系统提示词。
     *
     * 刻意写短：它每轮都要 prefill，而端侧 prefill 很贵（实测约 5 tok/s）。
     * 原来的四条规则约 109 token，现在压到一半左右。
     */
    private const val SYSTEM_PROMPT = """你是知境，手机本地的中文助手。用简体中文、口语化回答；不确定就说不确定，不要编造；你不能联网、查天气、放音乐，别声称会这些。"""

    /**
     * Qwen2.5-1.5B-Instruct，Q4_K_M 量化，1.04 GB。
     *
     * 元数据实测：architecture=qwen2，context_length=32768，28 层，GQA（head_count_kv=2）。
     * 取 8192 窗口，KV cache 约 224 MiB，总内存占用约 1.5 GB，6 GB 以上机型可跑。
     */
    val QWEN_2_5_1_5B = ModelSpec(
        id = "qwen2.5-1.5b",
        displayName = "Qwen2.5 1.5B",
        assetPath = "model/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        // 与 assets 中的文件逐字节一致，用作拷贝完整性校验
        sizeBytes = 1_117_320_736L,
        contextSize = 8192,
        systemPrompt = SYSTEM_PROMPT.trimIndent(),
        predictLength = 512,
    )

    /**
     * Qwen2.5-3B-Instruct，Q4_K_M 量化，2.0 GB。
     *
     * 元数据实测：architecture=qwen2，context_length=32768，36 层，GQA（head_count_kv=2）。
     * 取 8192 窗口，KV cache 约 288 MiB，总内存占用约 2.6 GB，8 GB 以上机型可跑。
     * 生成速度约为 1.5B 的一半，故 predictLength 收窄到 256。
     */
    val QWEN_2_5_3B = ModelSpec(
        id = "qwen2.5-3b",
        displayName = "Qwen2.5 3B",
        assetPath = "model/qwen2.5-3b-instruct-q4_k_m.gguf",
        fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
        sizeBytes = 2_104_932_768L,
        contextSize = 8192,
        systemPrompt = SYSTEM_PROMPT.trimIndent(),
        predictLength = 256,
    )

    /**
     * bge-small-zh-v1.5，f16，47.9 MB。
     *
     * 元数据实测：architecture=bert，4 层，embedding_length=512，context 512，MIT 许可。
     * 输出维度 512 与 DbConstants.VECTOR_DIM 一致。
     * 用 f16 而非量化版：嵌入模型对量化敏感，Q4 会明显降低检索质量。
     */
    val BGE_SMALL_ZH = EmbeddingSpec(
        id = "bge-small-zh-v1.5",
        assetPath = "model/bge-small-zh-v1.5-f16.gguf",
        fileName = "bge-small-zh-v1.5-f16.gguf",
        sizeBytes = 47_886_240L,
        dim = 512,
    )

    /** 当前使用的嵌入模型。 */
    fun embedding(): EmbeddingSpec = BGE_SMALL_ZH

    /**
     * 按设备内存选型。
     *
     * 3B 需约 2.6 GB 可用内存，对应 8 GB 及以上机型；1.5B 需约 1.5 GB。
     * 注意：当前随包只提供 3B 一档资产，所以统一返回 3B。
     * 若将来同时打包两个 gguf，在这里按 totalRamGb 分流即可，业务代码不用动。
     */
    fun forDevice(@Suppress("UNUSED_PARAMETER") totalRamGb: Int): ModelSpec = QWEN_2_5_3B
}
