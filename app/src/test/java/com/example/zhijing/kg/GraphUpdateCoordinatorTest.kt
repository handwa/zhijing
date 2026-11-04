package com.example.zhijing.kg

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.zhijing.cloud.CloudProvider
import com.zhijing.data.ZhiJingData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 图谱更新编排的模式行为测试。
 *
 * **最关键的一条：离线模式绝不联网。**
 * 这是用户选「离线」时唯一的预期；任何情况下的联网都是对信任的破坏。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GraphUpdateCoordinatorTest {

    private lateinit var data: ZhiJingData

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        data = ZhiJingData.get(context)
        runBlocking { data.kgClaims.deleteAll() }
    }

    @After
    fun tearDown() {
        runBlocking { data.kgClaims.deleteAll() }
    }

    private val provider = CloudProvider.DEEPSEEK.copy(apiKey = "sk-test")

    private fun coordinator() = GraphUpdateCoordinator(data)

    @Test
    fun `离线模式直接跳过`() = runBlocking {
        val outcome = coordinator().update(
            content = "ReLU 是逐元素激活。",
            path = "arch.ffn.relu",
            cardId = "crd.s1-2-relu",
            outlinePath = "arch.ffn.relu",
            provider = provider,
            mode = GraphMode.OFFLINE
        )
        assertEquals(GraphUpdateCoordinator.Outcome.Skipped, outcome)
    }

    @Test
    fun `云端增强但未配置服务商时同样跳过`() = runBlocking {
        val outcome = coordinator().update(
            content = "ReLU 是逐元素激活。",
            path = "arch.ffn.relu",
            cardId = "crd.s1-2-relu",
            outlinePath = "arch.ffn.relu",
            provider = null,
            mode = GraphMode.CLOUD_ENHANCED
        )
        assertEquals(GraphUpdateCoordinator.Outcome.Skipped, outcome)
    }

    @Test
    fun `服务商配置不完整时跳过而不发请求`() = runBlocking {
        // 缺 API Key：readyFailureReason 非空，应在联网前就拦下
        val broken = CloudProvider.DEEPSEEK.copy(apiKey = "")
        val outcome = coordinator().update(
            content = "ReLU 是逐元素激活。",
            path = "arch.ffn.relu",
            cardId = "crd.s1-2-relu",
            outlinePath = "arch.ffn.relu",
            provider = broken,
            mode = GraphMode.CLOUD_ENHANCED
        )
        assertEquals(GraphUpdateCoordinator.Outcome.Skipped, outcome)
    }

    @Test
    fun `空内容不触发抽取`() = runBlocking {
        val outcome = coordinator().update(
            content = "   ",
            path = "arch.ffn.relu",
            cardId = "crd.s1-2-relu",
            outlinePath = "arch.ffn.relu",
            provider = provider,
            mode = GraphMode.CLOUD_ENHANCED
        )
        assertEquals(GraphUpdateCoordinator.Outcome.Skipped, outcome)
    }

    @Test
    fun `模式枚举区分两种来源`() {
        // 防止有人误把两个模式合成一个，导致「离线也联网」
        assertTrue(GraphMode.OFFLINE != GraphMode.CLOUD_ENHANCED)
        assertEquals("offline", GraphMode.OFFLINE.wire)
        assertEquals("cloud_enhanced", GraphMode.CLOUD_ENHANCED.wire)
    }
}
