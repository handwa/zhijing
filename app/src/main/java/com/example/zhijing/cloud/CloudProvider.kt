package com.example.zhijing.cloud

/**
 * 一个 OpenAI 兼容的大模型服务商配置。
 *
 * "OpenAI 兼容"是当前事实标准：DeepSeek、Kimi（Moonshot）、通义千问、智谱、
 * 硅基流动、vLLM/Ollama 自建服务都提供 `/chat/completions` 接口，差别只在
 * baseUrl、模型名和鉴权头。所以一套客户端即可覆盖绝大多数服务商。
 *
 * @param id 服务商标识，如 `deepseek`。仅用于本地区分与日志，不发给服务端。
 * @param displayName 设置页显示的名字。
 * @param baseUrl 接口根地址，**不含** `/chat/completions`。
 *        例：`https://api.deepseek.com/v1`
 * @param model 模型名，如 `deepseek-chat`、`qwen-plus`、`moonshot-v1-8k`。
 * @param apiKey 明文密钥，只在内存中短暂存在；持久化时由 [ProviderStore]
 *        用 Android Keystore 加密后写盘。
 * @param stream 是否要求服务端流式返回（SSE）。默认开启，与端侧逐字输出一致。
 */
data class CloudProvider(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String = "",
    val stream: Boolean = true,
) {
    /** 完整接口地址。允许用户误填结尾斜杠或误填 `/chat/completions`。 */
    val chatCompletionsUrl: String
        get() {
            var url = baseUrl.trim().trimEnd('/')
            // 用户可能直接粘贴了完整路径，避免拼出 .../chat/completions/chat/completions
            if (url.endsWith("/chat/completions")) return url
            return "$url/chat/completions"
        }

    /** 配置是否足以发起请求。 */
    fun readyFailureReason(): String? = when {
        baseUrl.isBlank() -> "接口地址为空"
        // 本机/局域网调试（Ollama、vLLM）常用 http，必须放行；
        // 顺序很重要：先判本地地址，再要求公网地址走 https。
        !isLocalEndpoint() && !baseUrl.startsWith("https://") -> "接口地址必须以 https:// 开头"
        model.isBlank() -> "模型名为空"
        // 本机/局域网调试允许无密钥
        apiKey.isBlank() && !isLocalEndpoint() -> "API Key 为空"
        else -> null
    }

    /** 是否指向本机或内网地址（Ollama、vLLM 调试常用，通常无鉴权）。 */
    internal fun isLocalEndpoint(): Boolean {
        val host = runCatching { java.net.URI(baseUrl.trim()).host ?: "" }.getOrDefault("").lowercase()
        return host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" ||
            host.startsWith("192.168.") || host.startsWith("10.")
    }

    companion object {
        /** DeepSeek 官方接口（OpenAI 兼容）。国内可直连，价格低，适合做默认值。 */
        val DEEPSEEK = CloudProvider(
            id = "deepseek",
            displayName = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            model = "deepseek-chat",
        )

        /**
         * 常见服务商的预设，用于设置页一键填充。
         * 用户仍可改成任意 OpenAI 兼容地址（含自建 vLLM / Ollama）。
         */
        val PRESETS: List<CloudProvider> = listOf(
            DEEPSEEK,
            CloudProvider(
                id = "moonshot",
                displayName = "Kimi / Moonshot",
                baseUrl = "https://api.moonshot.cn/v1",
                model = "moonshot-v1-8k",
            ),
            CloudProvider(
                id = "dashscope",
                displayName = "通义千问 / 百炼",
                baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                model = "qwen-plus",
            ),
            CloudProvider(
                id = "zhipu",
                displayName = "智谱 GLM",
                baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                model = "glm-4-flash",
            ),
            CloudProvider(
                id = "siliconflow",
                displayName = "硅基流动",
                baseUrl = "https://api.siliconflow.cn/v1",
                model = "Qwen/Qwen2.5-7B-Instruct",
            ),
            CloudProvider(
                id = "ollama",
                displayName = "本地 Ollama（调试）",
                baseUrl = "http://localhost:11434/v1",
                model = "qwen2.5:3b",
            ),
        )
    }
}

/** 一条发给云端的上文消息。role 取 `system` / `user` / `assistant`。 */
data class CloudMessage(val role: String, val content: String)

/**
 * 云端调用失败。
 *
 * 刻意把 [httpCode] 和 [retryable] 暴露出来：上层据此决定"回退到端侧"还是
 * "提示用户改配置"。例如 401 说明密钥错了，重试或回退都没有意义。
 */
class CloudException(
    message: String,
    val httpCode: Int? = null,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)
