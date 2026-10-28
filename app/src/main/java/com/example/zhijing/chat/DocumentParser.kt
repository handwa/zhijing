package com.example.zhijing.chat

import java.io.File

/**
 * 把各种格式的文档解析成纯文本，交给 [RagIndexer] 建索引。
 *
 * 解析必须流式或以合理的批次产出文本，避免把几百页文档一次性读进内存。
 */
interface DocumentParser {

    /** 支持的扩展名（小写，不含点）。 */
    val extensions: Set<String>

    /**
     * 解析文件并把文本逐块交给 [onChunk]。
     *
     * 实现应当在处理完一大块后尽快回调并释放中间结果，以控制内存峰值。
     *
     * @throws IllegalArgumentException 文件不存在或格式不受支持
     * @throws java.io.IOException 读取失败
     */
    suspend fun parse(file: File, onChunk: (String) -> Unit)
}

/** 解析结果为空或文件损坏时抛出，便于上层给出明确提示。 */
class DocumentParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 按扩展名选择解析器。
 *
 * 当前支持纯文本类与 DOCX；PDF、PPTX 可在后续按同一接口追加。
 *
 * 关于旧版 .doc：它是不公开规范的 OLE2 二进制格式，可靠解析需要 Apache POI
 * 的 HWPF，而该模块依赖 java.awt 等桌面类，在 Android 上极易崩溃。因此这里
 * **刻意不支持 .doc**，遇到时明确报错而不是给出残缺文本。
 */
object DocumentParsers {

    private val registry: List<DocumentParser> = listOf(
        PlainTextParser,
        DocxParser,
    )

    /** 所有受支持的扩展名。 */
    val supportedExtensions: Set<String>
        get() = registry.flatMap { it.extensions }.toSet()

    /** 给系统文件选择器用的 MIME 类型。 */
    val supportedMimeTypes: Array<String> = arrayOf(
        "text/plain",
        "text/markdown",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )

    /**
     * 取 [file] 对应的解析器。
     *
     * @return 解析器；格式不受支持时返回 null
     */
    fun forFile(file: File): DocumentParser? {
        val ext = file.extension.lowercase()
        if (ext.isEmpty()) return null
        return registry.firstOrNull { ext in it.extensions }
    }

    /**
     * 解析文件，把所有文本拼接后返回。
     *
     * 适合中小文档；超大文档请直接用 [parse] 流式处理。
     *
     * @throws DocumentParseException 格式不受支持、文件损坏或没有可提取文字
     */
    suspend fun extractText(file: File): String {
        val ext = file.extension.lowercase()
        if (ext == "doc") {
            throw DocumentParseException(
                "暂不支持旧版 .doc 格式，请在 Word 中另存为 .docx 后重试",
            )
        }
        val parser = forFile(file)
            ?: throw DocumentParseException("暂不支持该格式：${file.name}")
        val builder = StringBuilder()
        parser.parse(file) { chunk ->
            if (chunk.isNotBlank()) {
                if (builder.isNotEmpty()) builder.append("\n\n")
                builder.append(chunk.trim())
            }
        }
        val text = builder.toString().trim()
        if (text.isEmpty()) {
            throw DocumentParseException("文档中没有可提取的文字：${file.name}")
        }
        return text
    }
}

/** 纯文本 / Markdown：直接按块读取。 */
object PlainTextParser : DocumentParser {

    override val extensions = setOf("txt", "md", "markdown")

    /** 每次读取的字符数，控制内存峰值。 */
    private const val BLOCK = 8 * 1024

    override suspend fun parse(file: File, onChunk: (String) -> Unit) {
        require(file.exists()) { "文件不存在：${file.path}" }
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(BLOCK)
            while (true) {
                val n = reader.read(buffer)
                if (n <= 0) break
                onChunk(String(buffer, 0, n))
            }
        }
    }
}
