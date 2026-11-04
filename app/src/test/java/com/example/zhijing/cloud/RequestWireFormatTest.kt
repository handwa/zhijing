package com.example.zhijing.cloud

import java.net.ServerSocket
import java.util.Collections
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 线上协议契约测试：把 [RemoteChatClient] 实际发出的请求头整包打印并断言。
 *
 * 相比逐字段断言，这里校验的是**真实传输层**的样子：方法、路径、鉴权头、
 * 内容类型、编码策略。换服务商或改客户端时，这条用例能立刻暴露协议层回归。
 */
class RequestWireFormatTest {

    private val headLines: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Test
    fun `request uses POST with bearer auth and json body`() = runBlocking {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            runCatching {
                val sock = server.accept()
                val input = sock.getInputStream()
                val sb = StringBuilder()
                while (true) {
                    val b = input.read()
                    if (b < 0) break
                    if (b == '\n'.code) {
                        val line = sb.toString().trimEnd('\r')
                        sb.setLength(0)
                        if (line.isEmpty()) break
                        headLines.add(line)
                    } else {
                        sb.append(b.toChar())
                    }
                }
                // 回一个最小可用的 SSE 响应
                val payload = "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n\n"
                val body = payload.toByteArray(Charsets.UTF_8)
                val out = sock.getOutputStream()
                out.write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n" +
                        "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                        .toByteArray(Charsets.US_ASCII)
                )
                out.write(body)
                out.flush()
                Thread.sleep(200)
                runCatching { sock.close() }
            }
            runCatching { server.close() }
        }

        val provider = CloudProvider(
            id = "t",
            displayName = "t",
            baseUrl = "http://127.0.0.1:${server.localPort}/v1",
            model = "m",
            apiKey = "sk-1",
            stream = true,
        )
        val client = RemoteChatClient(connectTimeoutMs = 3_000, readTimeoutMs = 3_000)
        val tokens = client.streamChat(provider, listOf(CloudMessage("user", "hi"))).toList()

        assertEquals(listOf("ok"), tokens)
        val joined = headLines.joinToString("\n")
        assertTrue(joined, headLines.first().startsWith("POST /v1/chat/completions HTTP/1.1"))
        assertTrue(joined, joined.contains("Authorization: Bearer sk-1"))
        assertTrue(joined, joined.contains("Content-Type: application/json; charset=utf-8"))
        assertTrue(joined, joined.contains("Accept: text/event-stream"))
        // SSE 下必须禁用压缩，否则 CDN 会缓冲导致"不流式"
        assertTrue(joined, joined.contains("Accept-Encoding: identity"))
    }
}
