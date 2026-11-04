package com.zhijing.data.knowledge

import com.zhijing.data.TestDatabase
import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.model.ClaimProbeState
import com.zhijing.data.model.ProbeStatus
import com.zhijing.data.repository.DefaultKgStateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 探测状态的持久化与收敛规则测试。
 *
 * 守护的是设计文档里最容易写错的三条：
 * 1. 「已探测」不等于「已掌握」——判对一次是 VERIFIED，连续判对 [ProbeRules.MASTER_STREAK] 次才是 MASTERED；
 * 2. 撞错后存在「已纠正」与「仍易错」——判错立刻回到 CONFUSED，且留下误解注记；
 * 3. 回写是摘要而非明细——误解只保留最近 [ProbeRules.MAX_MISCONCEPTIONS] 条。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KgStateRepositoryTest {

    private lateinit var db: ZhijingDatabase
    private lateinit var repo: DefaultKgStateRepository

    private val claimA = "clm.arch.ffn.relu-shape.6092"
    private val claimB = "clm.arch.ffn.softmax-sum.a505"

    @Before
    fun setUp() {
        db = TestDatabase.createInMemory()
        repo = DefaultKgStateRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `未探测的命题返回默认状态`() = runBlocking {
        val state = repo.getState(claimA)
        assertEquals(ProbeStatus.UNPROBED, state.probeStatus)
        assertNull(state.lastProbedAt)
        assertEquals(0, state.attempts)
        assertTrue(!state.hasWriteback)
    }

    @Test
    fun `判对一次是已验证而不是已掌握`() = runBlocking {
        val state = repo.recordJudgement(claimA, correct = true, misconception = null)
        assertEquals(ProbeStatus.VERIFIED, state.probeStatus)
        assertEquals(1, state.streak)
        assertEquals(1, state.attempts)
        assertEquals(1, state.correct)
    }

    @Test
    fun `连续判对达到阈值才收敛为已掌握`() = runBlocking {
        repo.recordJudgement(claimA, correct = true, misconception = null)
        val state = repo.recordJudgement(claimA, correct = true, misconception = null)
        assertEquals(ProbeStatus.MASTERED, state.probeStatus)
        assertEquals(ProbeRules.MASTER_STREAK, state.streak)
    }

    @Test
    fun `已掌握后判错立刻回到易混淆并留下误解`() = runBlocking {
        repo.recordJudgement(claimA, correct = true, misconception = null)
        repo.recordJudgement(claimA, correct = true, misconception = null)

        val after = repo.recordJudgement(
            claimA,
            correct = false,
            misconception = "把 x=0 处的不可微当成了对全体实数不成立"
        )
        assertEquals(ProbeStatus.CONFUSED, after.probeStatus)
        assertEquals(0, after.streak)
        assertEquals(3, after.attempts)
        assertEquals(2, after.correct)
        assertEquals(listOf("把 x=0 处的不可微当成了对全体实数不成立"), after.misconceptions)
    }

    @Test
    fun `误解注记去重且只保留最近若干条`() = runBlocking {
        // 先灌满（0..4 共 5 条），此时列表已截断到最近 3 条：2 / 3 / 4
        repeat(ProbeRules.MAX_MISCONCEPTIONS + 2) { index ->
            repo.recordJudgement(claimA, correct = false, misconception = "误解 $index")
        }
        assertEquals(
            listOf("误解 2", "误解 3", "误解 4"),
            repo.getState(claimA).misconceptions
        )

        // 重复一条仍在列表里的（「误解 4」）：去重后长度不变，顺序也不动
        val afterDuplicate = repo.recordJudgement(claimA, correct = false, misconception = "误解 4")
        assertEquals(listOf("误解 2", "误解 3", "误解 4"), afterDuplicate.misconceptions)

        // 来一条新的：淘汰最旧，保留最近三条
        val afterNew = repo.recordJudgement(claimA, correct = false, misconception = "误解 5")
        assertEquals(listOf("误解 3", "误解 4", "误解 5"), afterNew.misconceptions)
    }

    @Test
    fun `回写走派生且批量读取能区分未探测`() = runBlocking {
        repo.recordJudgement(claimA, correct = true, misconception = null)
        val states = repo.getStates(listOf(claimA, claimB))
        assertEquals(ProbeStatus.VERIFIED, states.getValue(claimA).probeStatus)
        assertEquals(ProbeStatus.UNPROBED, states.getValue(claimB).probeStatus)
    }

    @Test
    fun `广度侧监听能收到状态变化`() = runBlocking {
        repo.recordJudgement(claimA, correct = true, misconception = null)
        val observed = repo.observeStates(listOf(claimA, claimB)).first()
        assertEquals(ProbeStatus.VERIFIED, observed.getValue(claimA).probeStatus)
    }

    @Test
    fun `状态汇总按四态计数`() = runBlocking {
        repo.recordJudgement(claimA, correct = true, misconception = null)
        repo.recordJudgement(claimB, correct = false, misconception = "误")
        val counts = repo.countByStatus()
        assertEquals(1, counts[ProbeStatus.VERIFIED])
        assertEquals(1, counts[ProbeStatus.CONFUSED])
        assertNull(counts[ProbeStatus.MASTERED])
    }

    @Test
    fun `易错优先级排序把易混淆的排前面`() = runBlocking {
        repo.recordJudgement(claimA, correct = true, misconception = null)
        repo.recordJudgement(claimB, correct = false, misconception = "误")
        val confused = repo.mostConfused(limit = 5)
        assertEquals(listOf(claimB), confused.map { it.claimId })
    }

    @Test
    fun `清理孤儿状态只删掉图谱里已不存在的命题`() = runBlocking {
        repo.recordJudgement(claimA, correct = true, misconception = null)
        repo.recordJudgement(claimB, correct = true, misconception = null)

        val removed = repo.pruneOrphans(aliveClaimIds = listOf(claimA))
        assertEquals(1, removed)
        assertNull(db.kgClaimStateDao().find(claimB))
        assertEquals(1, repo.getState(claimA).attempts)
    }

    @Test
    fun `空存活列表不会误删全部状态`() = runBlocking {
        repo.recordJudgement(claimA, correct = true, misconception = null)
        // 空列表多半是调用方加载图谱失败，不能当成「全部下架」
        assertEquals(0, repo.pruneOrphans(emptyList()))
        assertEquals(1, repo.getState(claimA).attempts)
    }

    @Test
    fun `直接写入状态可以被读回`() = runBlocking {
        repo.putState(
            ClaimProbeState(
                claimId = claimA,
                probeStatus = ProbeStatus.CONFUSED,
                lastProbedAt = 1_700_000_000_000,
                misconceptions = listOf("a, b", "带\"引号\""),
                confidence = 0.5f,
                attempts = 4,
                correct = 2,
                streak = 0
            )
        )
        val back = repo.getState(claimA)
        assertEquals(ProbeStatus.CONFUSED, back.probeStatus)
        // 断言列表编码不会因为逗号、引号而串位
        assertEquals(listOf("a, b", "带\"引号\""), back.misconceptions)
        assertEquals(1_700_000_000_000, back.lastProbedAt)
        assertEquals(0.5f, back.confidence, 0.0001f)
    }
}
