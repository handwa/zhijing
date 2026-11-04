package com.example.zhijing.kg

import android.content.Context

/**
 * 图谱内容来源模式。
 *
 * 与**聊天路由**（`RoutePolicy.RouteMode`）是两回事：聊天路由决定「谁来回答」，
 * 图谱模式决定「图谱从哪来」。两者独立设置，因为用户可能想用云端聊天但不想让图谱
 * 走云端（那要把收藏原文发出去），反之亦然。
 */
enum class GraphMode(val wire: String, val label: String) {

    /**
     * 离线：只读 `assets/kg/graph.json` 的出厂图谱。
     *
     * 阅读与判断完全可用，不产生任何网络请求，不发送收藏原文。
     */
    OFFLINE("offline", "离线（只用预置图谱）"),

    /**
     * 云端增强：出厂图谱 + 云端按 prompt 抽取的增量命题。
     *
     * 需要已配置云服务商。新产生的命题先进 Room 待确认，确认后才进入图谱。
     * 注意：这意味着**用户收藏的原文会被发送给服务商**，必须逐次告知。
     */
    CLOUD_ENHANCED("cloud_enhanced", "云端增强（AI 抽取新命题）");

    companion object {
        fun fromWire(wire: String?): GraphMode =
            entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) } ?: OFFLINE
    }
}

/**
 * 图谱模式的持久化。
 *
 * 单独一份 SharedPreferences，不塞进 [com.example.zhijing.cloud.CloudConfigStore]：
 * 后者被「清除云端配置」整体 `clear()`，而图谱模式属于用户偏好，
 * 不该因为清了 API Key 就跟着被重置回默认值。
 */
class GraphModeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读取当前模式；从未设置过时默认 [GraphMode.OFFLINE]（不联网是最安全的默认）。 */
    fun load(): GraphMode = GraphMode.fromWire(prefs.getString(KEY_MODE, null))

    fun save(mode: GraphMode) {
        prefs.edit().putString(KEY_MODE, mode.wire).apply()
    }

    /**
     * 当前模式是否真的能用。
     *
     * 云端增强需要已配置服务商：用户可能先选了云端增强，之后又清掉了 API Key。
     * 此时应自动降级为离线，而不是每次生成都失败。
     *
     * @param hasCloud 云端是否已配置且校验通过
     * @return 实际生效的模式
     */
    fun effective(hasCloud: Boolean): GraphMode =
        if (load() == GraphMode.CLOUD_ENHANCED && !hasCloud) GraphMode.OFFLINE else load()

    private companion object {
        const val PREFS_NAME = "graph_mode"
        const val KEY_MODE = "mode"
    }
}
