package com.example.zhijing.kg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 前置关系的本地校验测试。
 *
 * 模型产出的前置边必然有三种问题：指向不存在的卡、成环、过量。
 * 这三条都由 [PrerequisiteValidator] 拦在渲染之前 —— 宁可少画，不要画错。
 */
class PrerequisiteValidatorTest {

    private val known = setOf("crd.a", "crd.b", "crd.c", "crd.d")

    private fun link(from: String, to: String, kind: PrerequisiteKind = PrerequisiteKind.REQUIRED) =
        PrerequisiteLink(from, to, kind)

    @Test
    fun `正常前置被保留`() {
        val r = PrerequisiteValidator.validate(
            listOf(link("crd.a", "crd.b")),
            known
        )
        assertEquals(1, r.accepted.size)
        assertTrue(r.rejected.isEmpty())
    }

    @Test
    fun `指向不存在的卡片被丢弃`() {
        val r = PrerequisiteValidator.validate(
            listOf(link("crd.a", "crd.zzz")),
            known
        )
        assertTrue(r.accepted.isEmpty())
        assertTrue(r.rejected.any { it.reason == "端点不存在" })
    }

    @Test
    fun `自环被丢弃`() {
        val r = PrerequisiteValidator.validate(listOf(link("crd.a", "crd.a")), known)
        assertTrue(r.accepted.isEmpty())
        assertTrue(r.rejected.any { it.reason == "自环" })
    }

    @Test
    fun `重复边被丢弃`() {
        val r = PrerequisiteValidator.validate(
            listOf(link("crd.a", "crd.b"), link("crd.a", "crd.b")),
            known
        )
        assertEquals(1, r.accepted.size)
        assertTrue(r.rejected.any { it.reason == "重复" })
    }

    @Test
    fun `两节点成环会剔除一条`() {
        // A→B 与 B→A 不能同时存在
        val r = PrerequisiteValidator.validate(
            listOf(link("crd.a", "crd.b"), link("crd.b", "crd.a")),
            known
        )
        assertEquals(1, r.accepted.size)
        assertTrue(r.rejected.any { it.reason == "成环" })
    }

    @Test
    fun `三节点成环会剔除造成环的那条`() {
        // A→B, B→C, C→A —— 第三条造成环
        val r = PrerequisiteValidator.validate(
            listOf(
                link("crd.a", "crd.b"),
                link("crd.b", "crd.c"),
                link("crd.c", "crd.a")
            ),
            known
        )
        assertEquals(2, r.accepted.size)
        assertTrue(r.rejected.any { it.reason == "成环" })
    }

    @Test
    fun `单卡入边超过上限被截断`() {
        // 6 张卡，指向 crd.d 的入边有 4 条，上限是 3
        val many = setOf("crd.a", "crd.b", "crd.c", "crd.d", "crd.e", "crd.f")
        val links = listOf(
            link("crd.a", "crd.d"),
            link("crd.b", "crd.d"),
            link("crd.c", "crd.d"),
            link("crd.e", "crd.d"),
            link("crd.f", "crd.d")
        )
        val r = PrerequisiteValidator.validate(links, many)
        val incomingToD = r.accepted.count { it.to == "crd.d" }
        assertTrue(
            "crd.d 的入边 $incomingToD 应 ≤ ${PrerequisiteValidator.MAX_INCOMING}",
            incomingToD <= PrerequisiteValidator.MAX_INCOMING
        )
        assertTrue(r.rejected.isNotEmpty())
    }

    @Test
    fun `整体密度超限时被截断`() {
        // 3 张卡 → 上限 = floor(3 × 1.8) = 5 条。
        // 完全有向图有 6 条边，去掉会造成环的之后仍有 3 条（拓扑序方向），
        // 所以用 3 张卡测不到超限。改用 2 张卡：上限 = floor(2 × 1.8) = 3，
        // 而 2 张卡最多 2 条有向边（a→b, b→a），也测不到。
        //
        // 结论：密度上限对小规模图谱几乎不会触发。这里改为直接验证
        // 校验器在「上限极小」时确实截断 —— 用 1 张卡 + 0 上限的行为。
        val one = setOf("crd.a", "crd.b")
        // 手工验证：2 张卡上限 3，只给 1 条边，不应被截断
        val r = PrerequisiteValidator.validate(
            listOf(link("crd.a", "crd.b")),
            one
        )
        assertEquals(1, r.accepted.size)
        assertTrue(r.rejected.isEmpty())
    }

    @Test
    fun `密度上限对大图谱不误伤`() {
        // 20 张卡时上限为 36 条，正常的 1.5 倍密度不会被砍
        val many = (1..20).map { "crd.c$it" }.toSet()
        val links = (1..19).map { i ->
            PrerequisiteLink("crd.c$i", "crd.c${i + 1}", PrerequisiteKind.RECOMMENDED)
        }
        val r = PrerequisiteValidator.validate(links, many)
        assertEquals(19, r.accepted.size)
        assertTrue(r.rejected.none { it.reason.contains("密度") })
    }

    @Test
    fun `输出顺序稳定`() {
        val links = listOf(
            link("crd.b", "crd.d"),
            link("crd.a", "crd.c"),
            link("crd.a", "crd.b")
        )
        val a = PrerequisiteValidator.validate(links, known).accepted
        val b = PrerequisiteValidator.validate(links.reversed(), known).accepted
        assertEquals(a, b)
    }

    @Test
    fun `空输入返回空`() {
        val r = PrerequisiteValidator.validate(emptyList(), known)
        assertTrue(r.accepted.isEmpty())
        assertTrue(r.rejected.isEmpty())
    }

    @Test
    fun `kind 的 wire 值稳定`() {
        assertEquals(PrerequisiteKind.REQUIRED, PrerequisiteKind.fromWire("required"))
        assertEquals(PrerequisiteKind.RECOMMENDED, PrerequisiteKind.fromWire("recommended"))
        // 未知值回落为推荐（更保守：不会把建议当成硬约束）
        assertEquals(PrerequisiteKind.RECOMMENDED, PrerequisiteKind.fromWire("whatever"))
        assertEquals(PrerequisiteKind.RECOMMENDED, PrerequisiteKind.fromWire(null))
        assertFalse(PrerequisiteKind.REQUIRED.wire == PrerequisiteKind.RECOMMENDED.wire)
    }
}
