package com.example.zhijing.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 服务商配置校验与路由策略。
 *
 * 这两块不依赖 `org.json`（那是 Android 平台类，JVM 单测里是空桩），
 * 因此可以纯 JVM 运行。JSON 编解码的验证见 [CloudJsonTest]。
 */
class CloudProviderTest {

    @Test
    fun `appends chat completions path`() {
        val p = CloudProvider.DEEPSEEK.copy(baseUrl = "https://api.deepseek.com")
        assertEquals("https://api.deepseek.com/chat/completions", p.chatCompletionsUrl)
    }

    @Test
    fun `tolerates trailing slash`() {
        val p = CloudProvider.DEEPSEEK.copy(baseUrl = "https://api.x.com/v1/")
        assertEquals("https://api.x.com/v1/chat/completions", p.chatCompletionsUrl)
    }

    @Test
    fun `does not duplicate path when user pasted full url`() {
        val p = CloudProvider.DEEPSEEK.copy(baseUrl = "https://api.x.com/v1/chat/completions")
        assertEquals("https://api.x.com/v1/chat/completions", p.chatCompletionsUrl)
    }

    @Test
    fun `rejects blank endpoint`() {
        assertEquals("接口地址为空", CloudProvider.DEEPSEEK.copy(baseUrl = "").readyFailureReason())
    }

    @Test
    fun `rejects non https endpoint`() {
        assertEquals(
            "接口地址必须以 https:// 开头",
            CloudProvider.DEEPSEEK.copy(baseUrl = "http://api.x.com").readyFailureReason(),
        )
    }

    @Test
    fun `rejects blank model`() {
        assertEquals(
            "模型名为空",
            CloudProvider.DEEPSEEK.copy(model = " ").readyFailureReason(),
        )
    }

    @Test
    fun `requires api key for public endpoints`() {
        assertEquals("API Key 为空", CloudProvider.DEEPSEEK.copy(apiKey = "").readyFailureReason())
    }

    @Test
    fun `allows keyless local endpoints for debugging`() {
        val p = CloudProvider.DEEPSEEK.copy(baseUrl = "http://localhost:11434/v1", apiKey = "")
        assertNull(p.readyFailureReason())
    }

    @Test
    fun `preset passes validation once key is set`() {
        assertNull(CloudProvider.DEEPSEEK.copy(apiKey = "sk-test").readyFailureReason())
    }

    @Test
    fun `every preset is well formed`() {
        for (p in CloudProvider.PRESETS) {
            assertTrue("id blank: $p", p.id.isNotBlank())
            assertTrue("name blank: $p", p.displayName.isNotBlank())
            assertTrue("model blank: $p", p.model.isNotBlank())
            assertTrue(
                "url should be http(s): $p",
                p.baseUrl.startsWith("https://") || p.baseUrl.startsWith("http://"),
            )
        }
    }

    @Test
    fun `preset ids are unique`() {
        val ids = CloudProvider.PRESETS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `stream defaults to true`() {
        assertTrue(CloudProvider.DEEPSEEK.stream)
        assertFalse(CloudProvider.DEEPSEEK.copy(stream = false).stream)
    }
}
