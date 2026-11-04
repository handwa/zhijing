package com.example.zhijing.kg

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 解析器内部括号匹配的定向测试。
 *
 * 这些用例来自真实踩过的坑：模型输出里 `]}]}` 这类交错收尾曾把数组体切错，
 * 表现为「边全部丢失」。这里把匹配逻辑单独钉死，避免以后重构再犯。
 */
class ClaimOutputParserStructureTest {

    @Test
    fun `嵌套数组与对象的交错收尾能正确配对`() {
        // arrayBodyField 返回「数组内容」（剥掉最外层方括号）
        val json = """{"a":[[1],[2],[3]]}"""
        val r = ClaimOutputParser.arrayBodyField(json, "a")
        assertEquals("[1],[2],[3]", r)
    }

    @Test
    fun `对象数组里的嵌套对象能正确配对`() {
        val json = """{"edges":[{"to":"x"},{"to":"y"}]}"""
        val r = ClaimOutputParser.arrayBodyField(json, "edges")
        assertEquals("""{"to":"x"},{"to":"y"}""", r)
    }

    @Test
    fun `数组收尾紧挨外层对象收尾时不越界`() {
        val json = """{"claims":[{"edges":[{"to":"x"}]}]}"""
        val r = ClaimOutputParser.arrayBodyField(json, "edges")
        assertEquals("""{"to":"x"}""", r)
    }

    @Test
    fun `字符串里的括号不参与配对`() {
        val json = """{"edges":[{"to":"a]b"}],"x":1}"""
        val r = ClaimOutputParser.arrayBodyField(json, "edges")
        assertEquals("""{"to":"a]b"}""", r)
    }
}
