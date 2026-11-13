package com.example.zhijing.kg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 前置关系（模型在每块 `requires` 里给）的解析测试。
 *
 * 守护的是「按标题引用 → 按卡片下标连边」这一步的正确性，
 * 以及三类必须丢弃的错误引用：找不到、指向自己、指向后面的块。
 */
class RequiresParsingTest {

    private fun block(
        title: String,
        body: String = "正文",
        requires: List<OutlineBuilder.RawRequire> = emptyList()
    ) = OutlineBuilder.RawBlock(
        title = title,
        summary = "摘要",
        bodyStart = 0,
        bodyEnd = 0,
        body = body,
        requires = requires
    )

    private fun req(on: String, kind: PrerequisiteKind = PrerequisiteKind.REQUIRED) =
        OutlineBuilder.RawRequire(on = on, kind = kind, why = "依赖点")

    @Test
    fun `正常前置被解析成卡片下标`() {
        val r = OutlineBuilder.buildFromBodies(
            listOf(
                block("1 基础"),
                block("2 进阶", requires = listOf(req("1 基础")))
            )
        )
        assertEquals(1, r.prerequisites.size)
        val p = r.prerequisites.single()
        assertEquals(0, p.fromIndex)
        assertEquals(1, p.toIndex)
        assertEquals(PrerequisiteKind.REQUIRED, p.kind)
    }

    @Test
    fun `推荐前置被保留`() {
        val r = OutlineBuilder.buildFromBodies(
            listOf(
                block("1 基础"),
                block("2 进阶", requires = listOf(req("1 基础", PrerequisiteKind.RECOMMENDED)))
            )
        )
        assertEquals(PrerequisiteKind.RECOMMENDED, r.prerequisites.single().kind)
    }

    @Test
    fun `引用不存在的块被丢弃`() {
        val r = OutlineBuilder.buildFromBodies(
            listOf(
                block("1 基础"),
                block("2 进阶", requires = listOf(req("不存在的块")))
            )
        )
        // 模型的非法引用必须被丢弃：结果里不允许出现 REQUIRED 边。
        // （全部被丢弃后会触发学习顺序兜底链，因此剩下的是 RECOMMENDED 的 0→1。）
        assertTrue(r.prerequisites.none { it.kind == PrerequisiteKind.REQUIRED })
        assertEquals(listOf(0 to 1), r.prerequisites.map { it.fromIndex to it.toIndex })
    }

    @Test
    fun `引用自己被打断`() {
        val r = OutlineBuilder.buildFromBodies(
            listOf(block("1 基础", requires = listOf(req("1 基础"))))
        )
        assertTrue(r.prerequisites.isEmpty())
    }

    @Test
    fun `引用排在后面的块被丢弃`() {
        // 先修必须在前：2 引用后面的 3 不合理
        val r = OutlineBuilder.buildFromBodies(
            listOf(
                block("1 基础"),
                block("2 进阶", requires = listOf(req("3 后记"))),
                block("3 后记")
            )
        )
        // 非法的前向引用被丢弃（不允许出现 REQUIRED 边）；
        // 全部被丢弃后触发学习顺序兜底链：0→1、1→2
        assertTrue(r.prerequisites.none { it.kind == PrerequisiteKind.REQUIRED })
        assertEquals(
            listOf(0 to 1, 1 to 2),
            r.prerequisites.map { it.fromIndex to it.toIndex }
        )
    }

    @Test
    fun `多块引用同一先修都保留`() {
        val r = OutlineBuilder.buildFromBodies(
            listOf(
                block("1 基础"),
                block("2 进阶", requires = listOf(req("1 基础"))),
                block("3 高级", requires = listOf(req("1 基础", PrerequisiteKind.RECOMMENDED)))
            )
        )
        assertEquals(2, r.prerequisites.size)
        assertTrue(r.prerequisites.all { it.fromIndex == 0 })
    }

    @Test
    fun `有原文模式同样解析 requires`() {
        val content = "第一部分内容。第二部分内容。"
        val r = OutlineBuilder.build(
            content,
            listOf(
                OutlineBuilder.RawBlock("1 甲", "s", 0, 7),
                OutlineBuilder.RawBlock(
                    "2 乙", "s", 7, content.length,
                    requires = listOf(req("1 甲"))
                )
            )
        )
        assertEquals(1, r.prerequisites.size)
        assertEquals(0, r.prerequisites.single().fromIndex)
        assertEquals(1, r.prerequisites.single().toIndex)
    }
}
