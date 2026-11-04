package com.example.zhijing.kg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MiniJson 解析器测试。
 *
 * 它是图谱加载的地基：解析错一个字符，整份 130KB 图谱就读不出来。
 * 所以这里覆盖 JSON 的各个语法要素，而不只是「能解析」。
 */
class MiniJsonTest {

    @Test
    fun `解析对象与嵌套结构`() {
        val v = MiniJson.parse("""{"a":{"b":[1,2,{"c":true}]}}""").asMap()
        val list = v.obj("a").list("b")
        assertEquals(3, list.size)
        assertEquals(1.0, list[0])
        assertEquals(true, list[2].asMap().bool("c"))
    }

    @Test
    fun `解析字符串转义`() {
        val v = MiniJson.parse("""{"s":"a\"b\\c\nd\t\u4e2d"}""").asMap()
        assertEquals("a\"b\\c\nd\t中", v.str("s"))
    }

    @Test
    fun `解析数字与布尔与null`() {
        val v = MiniJson.parse("""{"i":42,"f":3.5,"neg":-7,"t":true,"n":false,"z":null}""").asMap()
        assertEquals(42, v.int("i"))
        assertEquals(3.5, v["f"])
        assertEquals(-7, v.int("neg"))
        assertEquals(true, v.bool("t"))
        assertEquals(false, v.bool("n"))
        assertEquals(null, v["z"])
    }

    @Test
    fun `解析空对象与空数组`() {
        val v = MiniJson.parse("""{"o":{},"a":[]}""").asMap()
        assertTrue(v.obj("o").isEmpty())
        assertTrue(v.list("a").isEmpty())
    }

    @Test
    fun `解析中文与全角标点`() {
        val v = MiniJson.parse("""{"s":"Linear 层，要求输入维度对齐。"}""").asMap()
        assertEquals("Linear 层，要求输入维度对齐。", v.str("s"))
    }

    @Test
    fun `变量为数组根节点`() {
        val v = MiniJson.parse("""[{"id":"a"},{"id":"b"}]""").asList()
        assertEquals("a", v[0].asMap().str("id"))
        assertEquals("b", v[1].asMap().str("id"))
    }

    @Test
    fun `格式错误会带上位置信息`() {
        val e = runCatching { MiniJson.parse("""{"a":}""") }.exceptionOrNull()
        assertNotNull(e)
        assertTrue(e is MiniJson.GraphFormatException)
        assertTrue(e!!.message!!.contains("位置"))
    }

    @Test
    fun `字符串未闭合会报错`() {
        val e = runCatching { MiniJson.parse("""{"a":"abc""") }.exceptionOrNull()
        assertNotNull(e)
        assertTrue(e is MiniJson.GraphFormatException)
    }
}
