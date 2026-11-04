package com.example.zhijing.kg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 图谱模式的枚举与默认值约定。 */
class GraphModeTest {

    @Test
    fun `从存储值解析`() {
        assertEquals(GraphMode.OFFLINE, GraphMode.fromWire("offline"))
        assertEquals(GraphMode.CLOUD_ENHANCED, GraphMode.fromWire("cloud_enhanced"))
    }

    @Test
    fun `未知或空值一律回落到离线`() {
        // 离线是最安全的默认：不会联网，不会把收藏原文发出去
        assertEquals(GraphMode.OFFLINE, GraphMode.fromWire(null))
        assertEquals(GraphMode.OFFLINE, GraphMode.fromWire(""))
        assertEquals(GraphMode.OFFLINE, GraphMode.fromWire("something_else"))
    }

    @Test
    fun `两个模式的书面值互不相同`() {
        val wires = GraphMode.entries.map { it.wire }
        assertEquals(wires.size, wires.toSet().size)
        assertTrue(wires.all { it.isNotBlank() })
    }

    @Test
    fun `每个模式都有可读标签`() {
        GraphMode.entries.forEach {
            assertTrue("标签不能为空：$it", it.label.isNotBlank())
        }
    }
}
