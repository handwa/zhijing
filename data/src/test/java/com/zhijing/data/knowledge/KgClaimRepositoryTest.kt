package com.zhijing.data.knowledge

import com.zhijing.data.TestDatabase
import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.model.ClaimMerge
import com.zhijing.data.model.ClaimReviewStatus
import com.zhijing.data.model.ClaimSource
import com.zhijing.data.model.RuntimeClaim
import com.zhijing.data.repository.DefaultKgClaimRepository
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
 * 运行时命题（云端增量）与同义合并的测试。
 *
 * 守护两条约定：
 * 1. **未确认的候选不能出现在广度视图**（[DefaultKgClaimRepository.observeConfirmed] 只返回 CONFIRMED）；
 * 2. **同义合并把派生 ID 重定向到规范 ID**，避免云端重新生成后出现「同义不同 ID」两套状态。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KgClaimRepositoryTest {

    private lateinit var db: ZhijingDatabase
    private lateinit var repo: DefaultKgClaimRepository

    @Before
    fun setUp() {
        db = TestDatabase.createInMemory()
        repo = DefaultKgClaimRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun claim(
        id: String,
        statement: String = "云端生成的一条命题",
        cardId: String = "crd.s1-1-linear",
        reviewStatus: ClaimReviewStatus = ClaimReviewStatus.PENDING,
        source: ClaimSource = ClaimSource.CLOUD
    ) = RuntimeClaim(
        claimId = id,
        statement = statement,
        expected = true,
        conditions = listOf("形状前提：最后一维对齐", "边界：包含逗号, 的边界"),
        explanation = "解释",
        misconception = "误解",
        evidence = "原文片段",
        cardId = cardId,
        outlinePath = "arch.ffn.linear",
        source = source,
        reviewStatus = reviewStatus,
        providerId = "deepseek",
        createdTime = 1_700_000_000_000,
        reviewedTime = null
    )

    @Test
    fun `候选写入后处于待确认且不出现在广度视图`() = runBlocking {
        assertTrue(repo.addCandidate(claim("clm.arch.cloud.x.1a2b")))
        assertEquals(ClaimReviewStatus.PENDING, repo.getClaim("clm.arch.cloud.x.1a2b")?.reviewStatus)
        assertTrue(repo.observeConfirmed().first().isEmpty())
        assertEquals(1, repo.countByReviewStatus(ClaimReviewStatus.PENDING))
    }

    @Test
    fun `确认后才进入广度视图`() = runBlocking {
        val id = "clm.arch.cloud.x.1a2b"
        repo.addCandidate(claim(id))
        assertEquals(1, repo.review(id, ClaimReviewStatus.CONFIRMED))

        val confirmed = repo.observeConfirmed().first()
        assertEquals(listOf(id), confirmed.map { it.claimId })
        assertEquals(1, repo.observeConfirmedByCard("crd.s1-1-linear").first().size)
    }

    @Test
    fun `否决的候选保留记录但不进入视图`() = runBlocking {
        val id = "clm.arch.cloud.x.1a2b"
        repo.addCandidate(claim(id))
        repo.review(id, ClaimReviewStatus.REJECTED)

        assertTrue(repo.observeConfirmed().first().isEmpty())
        // 保留记录，供后续去重：同一条垃圾命题不应该被反复生成
        assertEquals(ClaimReviewStatus.REJECTED, repo.getClaim(id)?.reviewStatus)
    }

    @Test
    fun `重复生成同一命题不会覆盖已有审核状态`() = runBlocking {
        val id = "clm.arch.cloud.x.1a2b"
        repo.addCandidate(claim(id))
        repo.review(id, ClaimReviewStatus.CONFIRMED)

        // 云端再次生成同一条（ID 相同）：insertIgnore 应返回 false，且不把状态打回 pending
        assertFalse(repo.addCandidate(claim(id)))
        assertEquals(ClaimReviewStatus.CONFIRMED, repo.getClaim(id)?.reviewStatus)
    }

    @Test
    fun `列表字段含逗号也能原样读回`() = runBlocking {
        val id = "clm.arch.cloud.x.1a2b"
        repo.addCandidate(claim(id))
        val back = repo.getClaim(id)!!
        assertEquals(listOf("形状前提：最后一维对齐", "边界：包含逗号, 的边界"), back.conditions)
        assertEquals("deepseek", back.providerId)
        assertEquals(ClaimSource.CLOUD, back.source)
    }

    @Test
    fun `同义合并把派生ID解析到规范ID`() = runBlocking {
        val derived = "clm.arch.ffn.linear-in-dim.9f9f"
        val canonical = "clm.arch.ffn.linear-in-dim.4a3e"
        repo.linkMerge(
            ClaimMerge(
                sourceClaimId = derived,
                targetClaimId = canonical,
                similarity = 0.93f,
                basis = "vector",
                createdTime = 1_700_000_000_000
            )
        )

        assertEquals(canonical, repo.resolveClaimId(derived))
        // 没有映射的 ID 原样返回
        assertEquals("clm.arch.other.0000", repo.resolveClaimId("clm.arch.other.0000"))

        val resolved = repo.resolveClaimIds(listOf(derived, "clm.arch.other.0000"))
        assertEquals(canonical, resolved.getValue(derived))
        assertEquals("clm.arch.other.0000", resolved.getValue("clm.arch.other.0000"))
    }

    @Test
    fun `一条源ID只能指向一个目标，重复合并覆盖旧映射`() = runBlocking {
        val derived = "clm.arch.ffn.linear-in-dim.9f9f"
        repo.linkMerge(ClaimMerge(derived, "clm.a.1", 0.9f, "vector", 1))
        repo.linkMerge(ClaimMerge(derived, "clm.b.2", 0.95f, "manual", 2))

        assertEquals("clm.b.2", repo.resolveClaimId(derived))
        assertEquals("clm.b.2", repo.getMerge(derived)?.targetClaimId)
    }

    @Test
    fun `删除合并映射后回到原ID`() = runBlocking {
        val derived = "clm.arch.ffn.linear-in-dim.9f9f"
        repo.linkMerge(ClaimMerge(derived, "clm.a.1", 0.9f, "vector", 1))
        repo.deleteMerge(derived)
        assertEquals(derived, repo.resolveClaimId(derived))
        assertNull(repo.getMerge(derived))
    }

    @Test
    fun `清除云端配置只删云端生成的命题`() = runBlocking {
        repo.addCandidate(claim("clm.cloud.1"))
        repo.addCandidate(claim("clm.user.1", source = ClaimSource.USER))

        assertEquals(1, repo.deleteBySource(ClaimSource.CLOUD))
        assertNull(repo.getClaim("clm.cloud.1"))
        assertEquals(ClaimSource.USER, repo.getClaim("clm.user.1")?.source)
    }
}
