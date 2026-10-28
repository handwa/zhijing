package com.example.zhijing.chat

import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * DOCX 解析器：DOCX 本质是 ZIP 包，正文在 `word/document.xml` 里。
 *
 * 不引入 Apache POI（它在 Android 上依赖 java.awt 等桌面类，容易崩），
 * 直接用 JDK 自带的 DOM 解析器读 `<w:t>` 文本节点，零额外依赖。
 *
 * 为什么用 DOM 而不是 XmlPullParser：`javax.xml.parsers` 在 Android 与 JVM 上都存在，
 * 因此这个解析器可以在纯 JVM 单测里验证，不需要 Robolectric。
 * word/document.xml 通常远小于整包，整棵 DOM 装入内存是安全的。
 *
 * 已处理：
 * - 每个 `<w:p>` 段落产出一次换行，保留段落结构
 * - 每个 `<w:t>` 文本节点内容原样拼接（run 级切分对索引无意义）
 * - `<w:tab>` 转空格，`<w:br>` / `<w:cr>` 转换行
 *
 * 不处理：表格单元格合并、批注、脚注、页眉页脚。这些对 RAG 检索价值低。
 */
object DocxParser : DocumentParser {

    override val extensions = setOf("docx")

    /** 正文在 ZIP 中的固定路径。 */
    private const val DOCUMENT_XML = "word/document.xml"

    /** WordprocessingML 命名空间。 */
    private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    /** 累积多少字符后回调一次，避免小片段过多。 */
    private const val FLUSH_THRESHOLD = 2000

    override suspend fun parse(file: File, onChunk: (String) -> Unit) {
        require(file.exists()) { "文件不存在：${file.path}" }

        ZipFile(file).use { zip ->
            val entry = zip.getEntry(DOCUMENT_XML)
                ?: throw DocumentParseException(
                    "不是有效的 DOCX（缺少 $DOCUMENT_XML）：${file.name}"
                )

            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // 文档来自外部，关掉外部实体与 DTD 以免 XXE
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                isExpandEntityReferences = false
            }

            val document = zip.getInputStream(entry).use { stream ->
                try {
                    factory.newDocumentBuilder().parse(stream)
                } catch (e: Exception) {
                    throw DocumentParseException("解析 ${file.name} 的 XML 失败", e)
                }
            }

            val buffer = StringBuilder()
            // 逐段落提取：body 下的 w:p
            val paragraphs = document.getElementsByTagNameNS(W_NS, "p")
            for (i in 0 until paragraphs.length) {
                val para = paragraphs.item(i) as? Element ?: continue
                val text = extractParagraph(para)
                if (text.isBlank()) continue

                buffer.append(text).append('\n')
                if (buffer.length >= FLUSH_THRESHOLD) {
                    onChunk(buffer.toString())
                    buffer.setLength(0)
                }
            }
            if (buffer.isNotEmpty()) onChunk(buffer.toString())
        }
    }

    /**
     * 提取一个 `<w:p>` 里的文本。
     *
     * 按文档顺序遍历后代的 `w:t` / `w:tab` / `w:br`，拼成该段落。
     */
    private fun extractParagraph(paragraph: Element): String {
        val sb = StringBuilder()
        val nodes = paragraph.getElementsByTagNameNS(W_NS, "*")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            when (el.localName) {
                "t" -> sb.append(el.textContent ?: "")
                "tab" -> sb.append(' ')
                "br", "cr" -> sb.append('\n')
            }
        }
        return sb.toString().trimEnd()
    }
}

