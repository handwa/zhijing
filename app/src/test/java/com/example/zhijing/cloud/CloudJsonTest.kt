package com.example.zhijing.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OpenAI 兼容接口的 JSON 编解码。
 *
 * 纯 JVM 测试：解析走 [WireJson]（只依赖 JDK 字符操作，不使用 `org.json`），
 * 因此这里验证的就是真机上运行的同一份代码。
 */
class CloudJsonTest {

    @Test
    fun `request body carries model messages and stream flag`() {
        val body = CloudJson.requestBody(
            model = "deepseek-chat",
            messages = listOf(
                CloudMessage("system", "你是助手"),
                CloudMessage("user", "你好"),
            ),
            stream = true,
            maxTokens = 256,
        )
        assertTrue(body.contains("\"model\":\"deepseek-chat\""))
        assertTrue(body.contains("\"stream\":true"))
        assertTrue(body.contains("\"max_tokens\":256"))
        // 中文不转义成 \uXXXX，保证与服务端编码一致
        assertTrue(body.contains("你好"))
        assertFalse(body.contains("\\u"))
    }

    @Test
    fun `request body omits max_tokens when null`() {
        val body = CloudJson.requestBody("m", listOf(CloudMessage("user", "x")), stream = true)
        assertFalse(body.contains("max_tokens"))
    }

    @Test
    fun `request body preserves role order`() {
        val body = CloudJson.requestBody(
            "m",
            listOf(
                CloudMessage("system", "S"),
                CloudMessage("user", "U1"),
                CloudMessage("assistant", "A1"),
                CloudMessage("user", "U2"),
            ),
            stream = true,
        )
        val idxSystem = body.indexOf("\"system\"")
        val idxU1 = body.indexOf("U1")
        val idxA1 = body.indexOf("A1")
        val idxU2 = body.indexOf("U2")
        assertTrue(idxSystem in 0 until idxU1)
        assertTrue(idxU1 < idxA1)
        assertTrue(idxA1 < idxU2)
    }

    @Test
    fun `request body escapes quotes and newlines in content`() {
        val body = CloudJson.requestBody(
            "m",
            listOf(CloudMessage("user", "他说\"你好\"\n第二行")),
            stream = false,
        )
        // 引号与换行必须被转义，否则请求体就不是合法 JSON
        assertTrue(body.contains("\\\"你好\\\""))
        assertTrue(body.contains("\\n"))
        // 内容中原生的换行不能裸露在请求体里
        assertFalse(body.contains("你好\"\n"))
    }

    @Test
    fun `delta text from normal chunk`() {
        assertEquals(
            "红烧肉",
            CloudJson.deltaText("""{"choices":[{"delta":{"content":"红烧肉"}}]}"""),
        )
    }

    @Test
    fun `delta text empty when finish frame has no content`() {
        assertEquals("", CloudJson.deltaText("""{"choices":[{"delta":{},"finish_reason":"stop"}]}"""))
    }

    @Test
    fun `delta text tolerates empty choices`() {
        assertEquals("", CloudJson.deltaText("""{"choices":[]}"""))
    }

    @Test
    fun `delta text tolerates missing choices`() {
        assertEquals("", CloudJson.deltaText("""{"id":"x"}"""))
    }

    @Test
    fun `delta text falls back to message node`() {
        assertEquals("嗨", CloudJson.deltaText("""{"choices":[{"message":{"content":"嗨"}}]}"""))
    }

    @Test
    fun `delta text unescapes quotes and newline`() {
        assertEquals(
            "a\n\"b\"",
            CloudJson.deltaText("""{"choices":[{"delta":{"content":"a\n\"b\""}}]}"""),
        )
    }

    @Test
    fun `delta text unescapes unicode`() {
        // 服务端把中文转义成 \uXXXX 发送时也要能还原
        assertEquals("你好", CloudJson.deltaText("""{"choices":[{"delta":{"content":"\u4f60\u597d"}}]}"""))
    }

    @Test
    fun `delta text ignores nested content in non-first choice`() {
        assertEquals(
            "第一个",
            CloudJson.deltaText(
                """{"choices":[{"delta":{"content":"第一个"}},{"delta":{"content":"第二个"}}]}"""
            ),
        )
    }

    @Test
    fun `delta text handles braces inside content`() {
        // 内容里出现大括号不能打乱对象匹配
        assertEquals(
            "函数 {a:1} 结束",
            CloudJson.deltaText("""{"choices":[{"delta":{"content":"函数 {a:1} 结束"}}]}"""),
        )
    }

    @Test
    fun `full text from non streaming response`() {
        val resp = """{"choices":[{"message":{"role":"assistant","content":"完整回答"}}]}"""
        assertEquals("完整回答", CloudJson.fullText(resp))
    }

    @Test
    fun `hasChoices distinguishes error frames`() {
        assertTrue(CloudJson.hasChoices("""{"choices":[{"delta":{}}]}"""))
        assertFalse(CloudJson.hasChoices("""{"error":{"message":"bad"}}"""))
    }

    @Test
    fun `error message extracts nested message`() {
        assertEquals(
            "Invalid API key",
            CloudJson.errorMessage("""{"error":{"message":"Invalid API key","type":"auth"}}"""),
        )
    }

    @Test
    fun `error message falls back to raw body for html`() {
        val html = "<html><body>502 Bad Gateway</body></html>"
        assertEquals(html, CloudJson.errorMessage(html))
    }

    @Test
    fun `error message handles empty body`() {
        assertEquals("服务端未返回错误详情", CloudJson.errorMessage(""))
    }

    @Test
    fun `malformed json never throws`() {
        // 线上遇到脏数据时应当退化为空串，而不是让整段回答崩掉
        assertEquals("", CloudJson.deltaText("{not json"))
        assertEquals("", CloudJson.deltaText(""))
        assertEquals("", CloudJson.deltaText("{"))
        assertEquals("", CloudJson.deltaText("""{"choices":[{"delta":{"content":"""))
    }

    @Test
    fun `quote escapes control characters`() {
        assertEquals("\"a\\tb\"", CloudJson.quote("a\tb"))
        assertEquals("\"a\\\\b\"", CloudJson.quote("a\\b"))
    }
}
