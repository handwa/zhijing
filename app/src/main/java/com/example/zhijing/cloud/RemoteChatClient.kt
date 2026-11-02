package com.example.zhijing.cloud

import java.io.Closeable
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * OpenAI 兼容 Chat Completions 客户端，支持流式（SSE）与非流式。
 *
 * 用标准库 [HttpURLConnection] 而不是 OkHttp：随 Android 平台提供，无额外依赖，
 * 原生支持 chunked 读取。代价是要自己处理超时与取消，下面都做了。
 *
 * 线程模型：所有 IO 在 [Dispatchers.IO] 上进行；取消协程时 `finally` 与
 * `awaitClose` 都会断开连接，阻塞中的读取随即抛异常退出。
 */
class RemoteChatClient(
    /** 收到响应头前的等待上限。 */
    private val connectTimeoutMs: Int = 20_000,
    /** 两次收到数据之间的最长间隔，超过即认为卡死。 */
    private val readTimeoutMs: Int = 120_000,
) {

    /**
     * 发起对话请求，逐段返回增量文本。
     *
     * @throws CloudException 网络失败、HTTP 非 2xx、或响应不可解析
     */
    fun streamChat(
        provider: CloudProvider,
        messages: List<CloudMessage>,
        maxTokens: Int? = null,
    ): Flow<String> = callbackFlow {
        val conn = try {
            openConnection(provider)
        } catch (e: Exception) {
            throw CloudException("接口地址不合法：${provider.chatCompletionsUrl}", cause = e)
        }

        var input: Closeable? = null
        try {
            val body = CloudJson.requestBody(
                model = provider.model,
                messages = messages,
                stream = provider.stream,
                maxTokens = maxTokens,
            )
            // 显式 UTF-8 字节，不依赖平台默认字符集
            conn.outputStream.use { out ->
                OutputStreamWriter(out, Charsets.UTF_8).use { w -> w.write(body) }
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = conn.errorStream?.let { readCapped(InputStreamReader(it, Charsets.UTF_8)) } ?: ""
                throw CloudException(
                    message = "HTTP $code：${CloudJson.errorMessage(errBody)}",
                    httpCode = code,
                    // 429 与 5xx 值得重试；400/401/403 重试无意义
                    retryable = code == 429 || code in 500..599,
                )
            }

            val reader = InputStreamReader(conn.inputStream, Charsets.UTF_8).also { input = it }

            if (!provider.stream) {
                val text = CloudJson.fullText(readCapped(reader))
                if (text.isNotEmpty()) trySendBlocking(text)
                close()
                return@callbackFlow
            }

            val parser = SseParser()
            val buf = CharArray(8192)
            // 用显式 lambda 而不是 trySendBlocking 的函数引用：后者会绑到通道上，
            // 语义不同且类型不匹配
            val emit: (String) -> Unit = { token -> trySendBlocking(token) }
            loop@ while (isActive) {
                val n = reader.read(buf)
                if (n < 0) {
                    // 流结束：补出没有空行收尾的最后一帧
                    parser.flush().forEach { emitPayload(it, emit) }
                    break@loop
                }
                if (n == 0) continue@loop
                parser.feed(String(buf, 0, n)).forEach { emitPayload(it, emit) }
            }
            close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudException) {
            close(e)
        } catch (e: Exception) {
            close(CloudException("云端请求失败：${e.message ?: e.javaClass.simpleName}", retryable = true, cause = e))
        } finally {
            runCatching { input?.close() }
            runCatching { conn.disconnect() }
        }

        awaitClose { conn.disconnect() }
    }.flowOn(Dispatchers.IO)

    private fun openConnection(provider: CloudProvider): HttpURLConnection =
        (URL(provider.chatCompletionsUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", if (provider.stream) "text/event-stream" else "application/json")
            // 部分 CDN 默认 gzip，SSE 下会造成缓冲
            setRequestProperty("Accept-Encoding", "identity")
            if (provider.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
            }
        }

    /**
     * 处理一帧 SSE 载荷：`[DONE]` 结束、错误帧抛异常、正常帧取出增量文本。
     */
    private fun emitPayload(payload: String, emit: (String) -> Unit) {
        val trimmed = payload.trim()
        if (trimmed.isEmpty() || trimmed == "[DONE]") return

        if (!trimmed.startsWith("{")) {
            // 未知格式（心跳等）：忽略而不是崩掉整段回答
            return
        }
        // 没有 choices 字段的 JSON 多半是错误帧，抛出去让上层提示
        if (!CloudJson.hasChoices(trimmed)) {
            throw CloudException("云端返回错误：${CloudJson.errorMessage(trimmed)}")
        }

        val text = CloudJson.deltaText(trimmed)
        if (text.isNotEmpty()) emit(text)
    }

    /** 读取上限，防止异常服务端返回超大错误页占满内存。 */
    private fun readCapped(reader: java.io.Reader): String {
        val sb = StringBuilder()
        val buf = CharArray(4096)
        var total = 0
        while (total < MAX_RESPONSE_CHARS) {
            val n = reader.read(buf)
            if (n < 0) break
            sb.append(buf, 0, n)
            total += n
        }
        return sb.toString()
    }

    companion object {
        /** 非流式响应与错误体的字符上限，约 64 万字符。 */
        private const val MAX_RESPONSE_CHARS = 640_000
    }
}
