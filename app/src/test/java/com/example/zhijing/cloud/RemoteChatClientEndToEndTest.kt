package com.example.zhijing.cloud

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端到端验证：[RemoteInferenceEngine] → [RemoteChatClient] → 真实 TCP 服务端。
 *
 * 用裸 [ServerSocket] 写一个最小的假 OpenAI 兼容服务，不依赖任何 HTTP 框架
 * （Android 单测的 JDK 模块里没有 `com.sun.net.httpserver`）。这样验证的是：
 * 1. 请求体确实是 OpenAI 格式（model / messages / stream / Authorization）；
 * 2. HTTP 是 chunked 传输、SSE 分多段到达时，token 能按序拼出（模拟真实网络分包）；
 * 3. HTTP 错误码被翻译成带状态码、可判断是否重试的 [CloudException]；
 * 4. 云端 prefill 不发网络请求、reset 清空历史。
 */
class RemoteChatClientEndToEndTest {

    private val sockets = mutableListOf<Closeable>()
    private val serverSockets = mutableListOf<ServerSocket>()

    /** 记录收到的请求体，线程安全。 */
    private val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val authHeaders: MutableList<String?> = Collections.synchronizedList(mutableListOf())

    @After
    fun tearDown() {
        sockets.forEach { runCatching { it.close() } }
        serverSockets.forEach { runCatching { it.close() } }
        sockets.clear()
        serverSockets.clear()
    }

    private interface Closeable { fun close() }

    /**
     * 起一个假服务端。
     *
     * @param statusCode HTTP 状态码
     * @param chunks 响应体分段（每段单独 flush，模拟分包）
     * @param contentType 响应 Content-Type
     * @return 可作为 baseUrl 的地址
     */
    private fun startServer(
        statusCode: Int = 200,
        contentType: String = "text/event-stream",
        chunks: List<String> = emptyList(),
    ): String {
        val server = ServerSocket(0)
        serverSockets.add(server)
        thread(isDaemon = true, name = "fake-llm-server") {
            while (!server.isClosed) {
                val sock = try {
                    server.accept()
                } catch (e: Exception) {
                    break
                }
                thread(isDaemon = true) { handle(sock, statusCode, contentType, chunks) }
            }
        }
        return "http://127.0.0.1:${server.localPort}/v1"
    }

    private fun handle(
        sock: Socket,
        statusCode: Int,
        contentType: String,
        chunks: List<String>,
    ) {
        try {
            val raw = sock.getInputStream()
            // 请求头按行读（ASCII），请求体按**字节**读。
            // 注意：不能用 BufferedReader 读请求体——Content-Length 是字节数，
            // 而 Reader 按字符计，中文各占 3 字节会导致读不到预期长度而卡死。
            val heads = mutableListOf<String>()
            val lineBuf = StringBuilder()
            while (true) {
                val b = raw.read()
                if (b < 0) return
                if (b == '\n'.code) {
                    val line = lineBuf.toString().trimEnd('\r')
                    if (line.isEmpty()) break
                    heads.add(line)
                    lineBuf.setLength(0)
                } else {
                    lineBuf.append(b.toChar())
                }
            }

            var contentLength = 0
            var chunked = false
            var auth: String? = null
            for (line in heads) {
                val low = line.lowercase()
                if (low.startsWith("content-length:")) {
                    contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
                if (low.startsWith("transfer-encoding:") && low.contains("chunked")) {
                    chunked = true
                }
                if (low.startsWith("authorization:")) {
                    auth = line.substringAfter(':').trim()
                }
            }

            val body = if (chunked) readChunkedBytes(raw) else readFixedBytes(raw, contentLength)
            requests.add(String(body, Charsets.UTF_8))
            authHeaders.add(auth)

            // --- 写响应（chunked，每段单独 flush）---
            val out: OutputStream = sock.getOutputStream()
            val head = buildString {
                append("HTTP/1.1 $statusCode ${if (statusCode == 200) "OK" else "Error"}\r\n")
                append("Content-Type: $contentType\r\n")
                append("Transfer-Encoding: chunked\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            out.write(head.toByteArray(Charsets.UTF_8))
            out.flush()
            for (chunk in chunks) {
                val bytes = chunk.toByteArray(Charsets.UTF_8)
                out.write(Integer.toHexString(bytes.size).toByteArray(Charsets.US_ASCII))
                out.write("\r\n".toByteArray(Charsets.US_ASCII))
                out.write(bytes)
                out.write("\r\n".toByteArray(Charsets.US_ASCII))
                out.flush()
                // 稍微间隔，确保客户端分多次 read 到（验证增量解析）
                Thread.sleep(15)
            }
            out.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
            out.flush()
        } catch (_: Exception) {
            // 客户端提前断开属正常
        } finally {
            runCatching { sock.close() }
        }
    }

    /** 读固定长度的请求体（按字节）。 */
    private fun readFixedBytes(input: java.io.InputStream, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        return body.copyOf(read)
    }

    /** 读 chunked 编码的请求体（按字节）。 */
    private fun readChunkedBytes(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val sizeLine = readAsciiLine(input) ?: break
            val size = sizeLine.trim().split(';').first().toIntOrNull(16) ?: break
            if (size == 0) {
                // 读掉 trailer 直到空行
                while (true) {
                    val t = readAsciiLine(input) ?: break
                    if (t.isEmpty()) break
                }
                break
            }
            out.write(readFixedBytes(input, size))
            readAsciiLine(input) // 块尾 CRLF
        }
        return out.toByteArray()
    }

    private fun readAsciiLine(input: java.io.InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
    }

    private fun provider(baseUrl: String) = CloudProvider(
        id = "test",
        displayName = "test",
        baseUrl = baseUrl,
        model = "fake-model",
        apiKey = "sk-fake",
        stream = true,
    )

    private fun sseOf(vararg texts: String): String = buildString {
        for (t in texts) {
            append("data: {\"choices\":[{\"delta\":{\"content\":\"$t\"}}]}\n\n")
        }
        append("data: [DONE]\n\n")
    }

    @Test
    fun `streams tokens in order and sends openai format`() = runBlocking {
        // 故意把一帧拆成两段发送，模拟网络分包
        val baseUrl = startServer(
            chunks = listOf(
                "data: {\"choices\":[{\"delta\":{\"content\":\"红烧\"}}]}\n\n",
                "data: {\"choices\":[{\"delta\":{\"content\":\"肉\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"很简单\"}}]}\n\n",
                "data: [DONE]\n\n",
            ),
        )
        val client = RemoteChatClient(connectTimeoutMs = 5_000, readTimeoutMs = 5_000)
        val tokens = client.streamChat(
            provider = provider(baseUrl),
            messages = listOf(CloudMessage("system", "你是助手"), CloudMessage("user", "红烧肉怎么做")),
        ).toList()

        assertEquals(listOf("红烧", "肉", "很简单"), tokens)

        assertEquals(1, requests.size)
        val body = requests.first()
        assertTrue(body, body.contains("\"model\":\"fake-model\""))
        assertTrue(body, body.contains("\"stream\":true"))
        assertTrue(body, body.contains("\"role\":\"system\""))
        assertTrue(body, body.contains("红烧肉怎么做"))
        // 鉴权头按 Bearer 格式发送
        assertEquals("Bearer sk-fake", authHeaders.first())
    }

    @Test
    fun `http 401 becomes non retryable CloudException`() = runBlocking {
        val baseUrl = startServer(
            statusCode = 401,
            contentType = "application/json",
            chunks = listOf("""{"error":{"message":"Invalid API key"}}"""),
        )
        val client = RemoteChatClient(connectTimeoutMs = 5_000, readTimeoutMs = 5_000)
        val err = runCatching {
            client.streamChat(provider(baseUrl), listOf(CloudMessage("user", "hi"))).toList()
        }.exceptionOrNull()

        assertTrue("应抛 CloudException，实际：$err", err is CloudException)
        err as CloudException
        assertEquals(401, err.httpCode)
        // 密钥错：重试与回退都无意义
        assertEquals(false, err.retryable)
        assertTrue(err.message!!, err.message!!.contains("Invalid API key"))
    }

    @Test
    fun `http 429 is retryable`() = runBlocking {
        val baseUrl = startServer(
            statusCode = 429,
            contentType = "application/json",
            chunks = listOf("""{"error":{"message":"rate limited"}}"""),
        )
        val client = RemoteChatClient(connectTimeoutMs = 5_000, readTimeoutMs = 5_000)
        val err = runCatching {
            client.streamChat(provider(baseUrl), listOf(CloudMessage("user", "hi"))).toList()
        }.exceptionOrNull() as CloudException

        assertEquals(429, err.httpCode)
        assertTrue(err.retryable)
    }

    @Test
    fun `engine streams through and carries history in each request`() = runBlocking {
        val baseUrl = startServer(chunks = listOf(sseOf("你好")))

        val engine = RemoteInferenceEngine(provider(baseUrl))
        engine.setSystemPrompt("你是知境")
        engine.prefillMessage("user", "上一轮问题")
        engine.prefillMessage("assistant", "上一轮回答")

        // 云端 prefill 不发网络请求：此时还没任何请求
        assertEquals("prefill 不应触发网络请求", 0, requests.size)

        val out = engine.sendUserPrompt("这一轮问题", predictLength = 64).toList()
        assertEquals(listOf("你好"), out)

        // 历史 = system + 2 条预填 + 本轮 user + assistant
        assertEquals(5, engine.historySize())

        assertEquals(1, requests.size)
        val body = requests.first()
        // 请求体带上完整历史，因此云端无需本地预填（与端侧 KV cache 语义对齐）
        assertTrue(body, body.contains("上一轮问题"))
        assertTrue(body, body.contains("上一轮回答"))
        assertTrue(body, body.contains("你是知境"))
        assertTrue(body, body.contains("这一轮问题"))
    }

    @Test
    fun `resetConversation clears history`() = runBlocking {
        val baseUrl = startServer(chunks = listOf(sseOf("ok")))
        val engine = RemoteInferenceEngine(provider(baseUrl))

        engine.prefillMessage("user", "a")
        engine.prefillMessage("assistant", "b")
        assertTrue(engine.historySize() > 0)

        engine.resetConversation()
        assertEquals(0, engine.historySize())
    }

    @Test
    fun `missing config fails fast without network call`() = runBlocking {
        val engine = RemoteInferenceEngine(CloudProvider.DEEPSEEK.copy(apiKey = ""))
        val err = runCatching {
            engine.sendUserPrompt("hi", predictLength = 16).toList()
        }.exceptionOrNull()

        assertTrue(err is CloudException)
        assertTrue(err!!.message!!.contains("API Key"))
        assertEquals(0, requests.size)
    }
}
