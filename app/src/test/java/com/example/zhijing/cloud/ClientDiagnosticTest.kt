package com.example.zhijing.cloud

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.Collections
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** 临时诊断：打印客户端实际发送的请求头与响应，定位测试服务端读超时的原因。 */
class ClientDiagnosticTest {

    private val lines: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Test
    fun `print raw request`() {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            val sock = server.accept()
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                lines.add(line)
                if (line.isEmpty()) break
            }
            // 立刻回一个最简单的 SSE 响应，不依赖请求体内容
            val out = sock.getOutputStream()
            val payload = "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n\n"
            val body = payload.toByteArray(Charsets.UTF_8)
            out.write(
                ("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n" +
                    "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
            )
            out.write(body)
            out.flush()
            Thread.sleep(300)
            runCatching { sock.close() }
            runCatching { server.close() }
        }

        val provider = CloudProvider(
            id = "t", displayName = "t",
            baseUrl = "http://127.0.0.1:${server.localPort}/v1",
            model = "m", apiKey = "sk-1", stream = true,
        )
        val client = RemoteChatClient(connectTimeoutMs = 3_000, readTimeoutMs = 3_000)
        val result = runCatching {
            runBlocking {
                client.streamChat(provider, listOf(CloudMessage("user", "hi"))).take(5).toList()
            }
        }
        println("=== REQUEST HEADERS ===")
        lines.forEach { println("  |$it|") }
        println("=== RESULT: $result ===")
    }
}
