package com.example.zhijing.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 文档解析器测试。
 *
 * 纯 JVM 测试：DocxParser 用 `javax.xml.parsers`（JDK 与 Android 都有），
 * 不依赖 Robolectric，因此本机 JDK 版本不影响。
 */
class DocumentParserTest {

    // ---------- txt / md ----------

    @Test
    fun plainText_isReadVerbatim() {
        kotlinx.coroutines.runBlocking {
            val f = File.createTempFile("sample", ".txt")
            try {
                f.writeText("第一段：红烧肉要小火慢炖。\n\n第二段：糖色炒到琥珀色。", Charsets.UTF_8)

                val text = DocumentParsers.extractText(f)
                assertTrue(text.contains("红烧肉要小火慢炖"))
                assertTrue(text.contains("琥珀色"))
            } finally {
                f.delete()
            }
        }
    }

    @Test
    fun plainText_largeFileIsStreamedInBlocks() {
        kotlinx.coroutines.runBlocking {
            val f = File.createTempFile("big", ".md")
            try {
                val line = "这是一行用于测试流式读取的内容。\n"
                f.writeText(line.repeat(2000), Charsets.UTF_8)   // 约 38k 字符

                var blocks = 0
                PlainTextParser.parse(f) { blocks++ }

                // 8KB 一块，38k 字符应至少切 4 块
                assertTrue("expected multiple blocks but got $blocks", blocks >= 4)
            } finally {
                f.delete()
            }
        }
    }

    // ---------- docx ----------

    @Test
    fun docx_extractsParagraphText() {
        kotlinx.coroutines.runBlocking {
            val f = File.createTempFile("sample", ".docx")
            try {
                writeMinimalDocx(
                    f,
                    listOf(
                        "红烧肉的做法",
                        "选五花三层肉，冷水下锅焯水。",
                        "炒糖色用冰糖，小火炒到琥珀色。",
                    ),
                )

                val text = DocumentParsers.extractText(f)
                assertTrue("missing title", text.contains("红烧肉的做法"))
                assertTrue("missing step1", text.contains("五花三层肉"))
                assertTrue("missing step2", text.contains("琥珀色"))
                // 段落之间应有换行
                assertTrue("paragraphs should be separated", text.contains("\n"))
            } finally {
                f.delete()
            }
        }
    }

    @Test
    fun docx_withoutDocumentXml_fails() {
        kotlinx.coroutines.runBlocking {
            val f = File.createTempFile("broken", ".docx")
            try {
                ZipOutputStream(f.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("other.xml"))
                    zip.write("<xml/>".toByteArray())
                    zip.closeEntry()
                }
                var thrown = false
                try {
                    DocumentParsers.extractText(f)
                } catch (e: DocumentParseException) {
                    thrown = true
                }
                assertTrue("expected DocumentParseException", thrown)
            } finally {
                f.delete()
            }
        }
    }

    // ---------- 扩展名分发 ----------

    @Test
    fun dispatcher_mapsExtensions() {
        assertEquals(DocxParser, DocumentParsers.forFile(File("a.docx")))
        assertEquals(PlainTextParser, DocumentParsers.forFile(File("a.txt")))
        assertEquals(PlainTextParser, DocumentParsers.forFile(File("a.md")))
        assertEquals(null, DocumentParsers.forFile(File("a.pdf")))
        assertEquals(null, DocumentParsers.forFile(File("noext")))
    }

    @Test
    fun supportedExtensions_containsDocx() {
        val ext = DocumentParsers.supportedExtensions
        assertTrue(ext.contains("docx"))
        assertTrue(ext.contains("txt"))
        // 旧版 .doc 刻意不支持
        assertTrue(!ext.contains("doc"))
    }

    @Test
    fun doc_isRejectedWithClearMessage() {
        kotlinx.coroutines.runBlocking {
            val f = File.createTempFile("legacy", ".doc")
            try {
                f.writeBytes(ByteArray(1024) { 0x41 })
                val message = try {
                    DocumentParsers.extractText(f)
                    null
                } catch (e: DocumentParseException) {
                    e.message
                }
                assertTrue(
                    "expected a helpful message, got: $message",
                    message?.contains(".docx") == true,
                )
            } finally {
                f.delete()
            }
        }
    }

    /**
     * 构造一个仅含 word/document.xml 的最小 DOCX。
     *
     * 每个字符串成为一个 `<w:p>` 段落，内部的 `<w:t>` 承载文本。
     */
    private fun writeMinimalDocx(file: File, paragraphs: List<String>) {
        val body = paragraphs.joinToString("") { text ->
            "<w:p><w:r><w:t xml:space=\"preserve\">$text</w:t></w:r></w:p>"
        }
        val xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>$body</w:body>
            </w:document>
        """.trimIndent()

        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
}
