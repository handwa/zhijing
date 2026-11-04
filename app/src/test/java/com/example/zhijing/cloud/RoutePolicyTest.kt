package com.example.zhijing.cloud

import com.example.zhijing.chat.FixedRoutePolicy
import com.example.zhijing.chat.RoutePolicy.RouteMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 路由策略。核心不变量：**没有云端，就绝不能路由到云端**。
 */
class RoutePolicyTest {

    @Test
    fun `local only never uses cloud even when configured`() {
        val p = FixedRoutePolicy(RouteMode.LOCAL_ONLY)
        assertEquals(RouteMode.LOCAL_ONLY, p.route(hasCloud = true))
    }

    @Test
    fun `local only stays local without cloud`() {
        val p = FixedRoutePolicy(RouteMode.LOCAL_ONLY)
        assertEquals(RouteMode.LOCAL_ONLY, p.route(hasCloud = false))
    }

    @Test
    fun `cloud first degrades to local when cloud unavailable`() {
        val p = FixedRoutePolicy(RouteMode.CLOUD_FIRST)
        assertEquals(RouteMode.LOCAL_ONLY, p.route(hasCloud = false))
    }

    @Test
    fun `cloud first routes to cloud when available`() {
        val p = FixedRoutePolicy(RouteMode.CLOUD_FIRST)
        assertEquals(RouteMode.CLOUD_FIRST, p.route(hasCloud = true))
    }

    @Test
    fun `cloud only degrades to local when cloud unavailable`() {
        // 配置被清除后，即便用户上次选了"仅云端"，也不能把请求发往不存在的服务
        val p = FixedRoutePolicy(RouteMode.CLOUD_ONLY)
        assertEquals(RouteMode.LOCAL_ONLY, p.route(hasCloud = false))
    }

    @Test
    fun `cloud only keeps cloud when available`() {
        val p = FixedRoutePolicy(RouteMode.CLOUD_ONLY)
        assertEquals(RouteMode.CLOUD_ONLY, p.route(hasCloud = true))
    }
}
