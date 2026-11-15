package com.zhijing.data.knowledge

import com.zhijing.data.TestDatabase
import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.model.ConditionProbeState
import com.zhijing.data.repository.DefaultConditionProbeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 条件级探测记录的持久化与聚合测试。
 *
 * 守护的是「回写是摘要而非明细」这张表最容易写错的四条：
 * 1. 同一干预点重复作答是**累加**，不是新增行；
 * 2. 「在这里错过几次」是历史事实，答对之后不清零；
 * 3. 「错在哪条机制」按命题聚合，且同一干预点只算一次；
 * 4. 空列表不等于「可以清空」——加载失败时不能误删记录。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConditionProbeRepositoryTest {

    private lateinit var db: ZhijingDatabase
    private lateinit var repo: DefaultConditionProbeRepository

    private val triple = "tri.ffn-linear-in-dim"
    private val otherTriple = "tri.softmax-axis"

    private val claimA = "clm.arch.ffn.linear-in-dim.4a3e"
    private val claimB = "clm.arch.ffn.softmax-sum.a505"

    private val intX = "int.in-x.4-to-5"
    private val intWeight = "int.w-x.4-to-3"

    @Before
    fun setUp() {
        db = TestDatabase.createInMemory()
        repo = DefaultConditionProbeRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `未作答的干预点没有记录`() = runBlocking {
        assertNull(repo.getState(triple, intX))
        assertTrue(repo.statesForTriple(triple).isEmpty())
    }

    @Test
    fun `首次作答记录预测与实际`() = runBlocking {
        val state = repo.record(
            tripleId = triple,
            interventionId = intX,
            claimId = claimA,
            predictedOption = "HOLDS",
            correctOption = "FAILS",
            misconception = "以为维度不等只会让结果变宽",
            now = 1_000L
        )

        assertFalse(state.matched)
        assertEquals(1, state.attempts)
        assertEquals(1, state.wrongAttempts)
        assertEquals("HOLDS", state.predictedOption)
        assertEquals("FAILS", state.correctOption)
        assertEquals(1_000L, state.lastProbedAt)
        assertEquals("以为维度不等只会让结果变宽", repo.getState(triple, intX)?.misconception)
    }

    @Test
    fun `重复作答是累加而不是新增行`() = runBlocking {
        repo.record(triple, intX, claimA, "HOLDS", "FAILS", "误解一", now = 1_000L)
        val second = repo.record(triple, intX, claimA, "FAILS", "FAILS", null, now = 2_000L)

        assertEquals(2, second.attempts)
        assertEquals(1, second.wrongAttempts)
        assertTrue(second.matched)
        // 最近一次的选择被覆盖
        assertEquals("FAILS", second.predictedOption)
        assertEquals(2_000L, second.lastProbedAt)
        assertEquals(1, repo.statesForTriple(triple).size)
    }

    @Test
    fun `答对之后错过的次数不会被清零`() = runBlocking {
        repo.record(triple, intX, claimA, "HOLDS", "FAILS", "误解一", now = 1_000L)
        repo.record(triple, intX, claimA, "FAILS", "FAILS", null, now = 2_000L)

        val summary = repo.summaries(listOf(claimA)).getValue(claimA)
        assertEquals(2, summary.attempts)
        assertEquals(1, summary.wrongCount)
        assertEquals(listOf(intX), summary.missedInterventionIds)
        // 卡片的「错在哪条机制」需要误解文本，答对时也不能抹掉
        assertEquals(listOf("误解一"), summary.misconceptions)
    }

    @Test
    fun `按命题聚合时同一干预点只算一次`() = runBlocking {
        repo.record(triple, intX, claimA, "HOLDS", "FAILS", "误解一", now = 1_000L)
        repo.record(triple, intX, claimA, "WEAKENED", "FAILS", "误解一", now = 2_000L)
        repo.record(triple, intWeight, claimA, "FAILS", "FAILS", null, now = 3_000L)

        val summary = repo.summaries(listOf(claimA)).getValue(claimA)
        assertEquals(3, summary.attempts)
        assertEquals(2, summary.wrongCount)
        assertEquals(listOf(intX), summary.missedInterventionIds)
        assertEquals(listOf("误解一"), summary.misconceptions)
    }

    @Test
    fun `聚合摘要不会无限长`() = runBlocking {
        repeat(ProbeRules.MAX_MISCONCEPTIONS + 2) { index ->
            repo.record(
                tripleId = triple,
                interventionId = "int.$index",
                claimId = claimA,
                predictedOption = "HOLDS",
                correctOption = "FAILS",
                misconception = "误解 $index",
                now = 1_000L + index
            )
        }

        val summary = repo.summaries(listOf(claimA)).getValue(claimA)
        assertEquals(ProbeRules.MAX_MISCONCEPTIONS, summary.missedInterventionIds.size)
        assertEquals(ProbeRules.MAX_MISCONCEPTIONS, summary.misconceptions.size)
        // 最近错的在前
        assertEquals("误解 4", summary.misconceptions.first())
    }

    @Test
    fun `摘要包含没作答过的命题并保持干净`() = runBlocking {
        repo.record(triple, intX, claimA, "HOLDS", "FAILS", "误解一", now = 1_000L)

        val summaries = repo.summaries(listOf(claimA, claimB))
        assertTrue(summaries.getValue(claimB).let { !it.hasWriteback && it.attempts == 0 })
        assertNull(repo.getState(triple, intWeight))
        assertTrue(repo.summaries(emptyList()).isEmpty())
    }

    @Test
    fun `广度侧监听能收到摘要变化`() = runBlocking {
        repo.record(triple, intX, claimA, "HOLDS", "FAILS", "误解一", now = 1_000L)

        val observed = repo.observeSummaries(listOf(claimA)).first()
        assertEquals(1, observed.getValue(claimA).wrongCount)
        assertTrue(repo.observeSummaries(emptyList()).first().isEmpty())
    }

    @Test
    fun `按三元组与按命题都能取回记录`() = runBlocking {
        repo.record(triple, intX, claimA, "HOLDS", "FAILS", null, now = 1_000L)
        repo.record(otherTriple, "int.axis.0-to-1", claimB, "FAILS", "FAILS", null, now = 2_000L)

        assertEquals(listOf(intX), repo.statesForTriple(triple).map { it.interventionId })
        assertEquals(1, repo.statesForClaim(claimB).size)
        assertTrue(repo.statesForTriple("tri.不存在").isEmpty())
    }

    @Test
    fun `清理孤儿记录只删图谱里已不存在的三元组`() = runBlocking {
        repo.record(triple, intX, claimA, "HOLDS", "FAILS", null, now = 1_000L)
        repo.record(otherTriple, "int.axis.0-to-1", claimB, "HOLDS", "HOLDS", null, now = 2_000L)

        val removed = repo.pruneOrphans(aliveTripleIds = listOf(otherTriple))
        assertEquals(1, removed)
        assertNull(repo.getState(triple, intX))
        assertEquals(1, repo.getState(otherTriple, "int.axis.0-to-1")?.attempts)
    }

    @Test
    fun `空存活列表不会误删记录`() = runBlocking {
        repo.record(triple, intX, claimA, "HOLDS", "FAILS", null, now = 1_000L)

        // 空列表多半是图谱加载失败，不能当成「全部下架」
        assertEquals(0, repo.pruneOrphans(emptyList()))
        assertEquals(1, repo.getState(triple, intX)?.attempts)
    }

    @Test
    fun `直接写入的记录可以被读回`() = runBlocking {
        repo.putState(
            ConditionProbeState(
                tripleId = triple,
                interventionId = intX,
                claimId = claimA,
                predictedOption = "WEAKENED",
                correctOption = "FAILS",
                matched = false,
                attempts = 3,
                wrongAttempts = 2,
                lastProbedAt = 1_700_000_000_000,
                misconception = "把适用范围变窄当成了仍然成立"
            )
        )

        val back = repo.getState(triple, intX)!!
        assertEquals(3, back.attempts)
        assertEquals(2, back.wrongAttempts)
        assertEquals(1_700_000_000_000, back.lastProbedAt)
        assertEquals("把适用范围变窄当成了仍然成立", back.misconception)
    }

    @Test
    fun `清空只影响本表`() = runBlocking {
        repo.record(triple, intX, claimA, "HOLDS", "FAILS", null, now = 1_000L)

        repo.deleteAll()
        assertNull(repo.getState(triple, intX))
        assertTrue(repo.summaries(listOf(claimA)).getValue(claimA).attempts == 0)
    }
}
