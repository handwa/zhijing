package com.example.zhijing.kg.ui

import com.example.zhijing.kg.SkillProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 点亮动效的触发条件。
 *
 * 写错的表现是「一进图所有绿点一起闪」或者「点亮了却没反馈」—— 都不崩，只能靠断言。
 */
class SkillAnimationTest {

    @Test
    fun `首次进入不产生动画`() {
        val current = mapOf("a" to SkillProgress.State.LIT, "b" to SkillProgress.State.READY)
        // 上一次为空 = 刚打开图谱，不是「刚刚点亮」
        assertTrue(SkillAnimation.newlyLit(emptyMap(), current).isEmpty())
    }

    @Test
    fun `只有从非点亮变成点亮才算刚点亮`() {
        val previous = mapOf(
            "a" to SkillProgress.State.READY,
            "b" to SkillProgress.State.LIT,
            "c" to SkillProgress.State.LOCKED,
        )
        val current = mapOf(
            "a" to SkillProgress.State.LIT,      // 刚点亮 ✓
            "b" to SkillProgress.State.LIT,      // 本来就亮
            "c" to SkillProgress.State.READY,    // 只是解锁，没点亮
            "d" to SkillProgress.State.LIT,      // 新出现的节点：没有上一次基线，不闪
        )

        assertEquals(listOf("a"), SkillAnimation.newlyLit(previous, current))
    }

    @Test
    fun `状态没变就不重复触发`() {
        val states = mapOf("a" to SkillProgress.State.LIT)
        assertTrue(SkillAnimation.newlyLit(states, states).isEmpty())
    }
}
