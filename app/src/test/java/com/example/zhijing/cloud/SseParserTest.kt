package com.example.zhijing.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SSE 解析器的行为约束。
 *
 * 这些用例对应真实环境里遇到过的服务端差异：网关合并多帧、`\r\n` 换行、
 * 末尾没有空行、注释心跳等。
 */
class SseParserTest {

    @Test
    fun `parses two events separated by blank line`() {
        val p = SseParser()
        val events = p.feed("data: A\n\ndata: B\n\n")
        assertEquals(listOf("A", "B"), events)
    }

    @Test
    fun `handles CRLF line endings`() {
        val p = SseParser()
        val events = p.feed("data: A\r\n\r\ndata: B\r\n\r\n")
        assertEquals(listOf("A", "B"), events)
    }

    @Test
    fun `does not split a CRLF into two line breaks`() {
        // "\r\n\r\n" 必须是两个换行（一个事件），而不是四个
        val p = SseParser()
        val events = p.feed("data: only\r\n\r\n")
        assertEquals(listOf("only"), events)
    }

    @Test
    fun `joins multi-line data with newline`() {
        val p = SseParser()
        val events = p.feed("data: line1\ndata: line2\n\n")
        assertEquals(listOf("line1\nline2"), events)
    }

    @Test
    fun `strips exactly one leading space after colon`() {
        val p = SseParser()
        assertEquals(listOf("A"), p.feed("data: A\n\n"))
        // "data:  A" 只剥一个空格，保留一个
        assertEquals(listOf(" A"), p.feed("data:  A\n\n"))
        // 没有空格也合法
        assertEquals(listOf("A"), p.feed("data:A\n\n"))
    }

    @Test
    fun `ignores comments and other fields`() {
        val p = SseParser()
        val events = p.feed(": heartbeat\nevent: message\nid: 7\nretry: 1000\ndata: A\n\n")
        assertEquals(listOf("A"), events)
    }

    @Test
    fun `survives chunk boundaries mid-line`() {
        val p = SseParser()
        // 模拟 TCP 分包：一条 data 行被切成三段喂入
        assertTrue(p.feed("dat").isEmpty())
        assertTrue(p.feed("a: hel").isEmpty())
        assertEquals(listOf("hello"), p.feed("lo\n\n"))
    }

    @Test
    fun `flush emits trailing event without blank line`() {
        val p = SseParser()
        assertTrue(p.feed("data: last").isEmpty())
        assertEquals(listOf("last"), p.flush())
    }

    @Test
    fun `flush after complete event yields nothing extra`() {
        val p = SseParser()
        assertEquals(listOf("A"), p.feed("data: A\n\n"))
        assertTrue(p.flush().isEmpty())
    }

    @Test
    fun `parses real openai style chunks`() {
        val p = SseParser()
        val stream =
            "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n" +
                "data: [DONE]\n\n"
        val events = p.feed(stream)
        assertEquals(3, events.size)
        // 这里只断言"帧被完整切出来"，JSON 内容解析由 CloudJsonTest（Robolectric）覆盖
        assertTrue(events[0].contains("\"你\""))
        assertTrue(events[1].contains("\"好\""))
        assertEquals("[DONE]", events[2])
    }

    @Test
    fun `multiple events arriving in one chunk are all returned`() {
        // 网关把多帧合并进一个包里是常见情况，不能只取第一帧
        val p = SseParser()
        val events = p.feed("data: A\n\ndata: B\n\ndata: C\n\n")
        assertEquals(listOf("A", "B", "C"), events)
    }
}
