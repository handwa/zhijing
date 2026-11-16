package com.zhijing.data.knowledge

import com.zhijing.data.TestDatabase
import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.model.GeneratedCard
import com.zhijing.data.model.GeneratedClaim
import com.zhijing.data.model.GeneratedGraph
import com.zhijing.data.model.GeneratedOutlineNode
import com.zhijing.data.repository.DefaultGeneratedGraphRepository
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
 * 生成图谱的命题表（v8）。
 *
 * 守护三件事，都是这张表最容易出错的地方：
 * 1. 命题跟着**主题**走：重新生成整份覆盖，不会堆出上一版残留；
 * 2. 删除主题连带删命题（没有外键，DAO 事务里手工删）；
 * 3. 换了主题不会串数据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeneratedClaimRepositoryTest {

    private lateinit var db: ZhijingDatabase
    private lateinit var repo: DefaultGeneratedGraphRepository

    @Before
    fun setUp() {
        db = TestDatabase.createInMemory()
        repo = DefaultGeneratedGraphRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `命题随图谱一起保存并按主题读回`() = runBlocking {
        repo.save(
            meta = graph("topic.a"),
            outline = listOf(node("topic.a")),
            cards = listOf(card("topic.a", "crd.a.0")),
            claims = listOf(claim("topic.a", "clm.a.0", "crd.a.0", "矩阵加法满足交换律"))
        )

        val claims = repo.claimsOf("topic.a")
        assertEquals(1, claims.size)
        assertEquals("矩阵加法满足交换律", claims.single().statement)
        assertEquals("crd.a.0", claims.single().cardId)
    }

    @Test
    fun `重复生成整份覆盖命题`() = runBlocking {
        repo.save(
            meta = graph("topic.a"),
            outline = listOf(node("topic.a")),
            cards = listOf(card("topic.a", "crd.a.0")),
            claims = listOf(claim("topic.a", "clm.old", "crd.a.0", "旧命题"))
        )
        repo.save(
            meta = graph("topic.a"),
            outline = listOf(node("topic.a")),
            cards = listOf(card("topic.a", "crd.a.0")),
            claims = listOf(claim("topic.a", "clm.new", "crd.a.0", "新命题"))
        )

        // 上一版的命题不能残留：它指向的卡片可能已经不存在了
        val claims = repo.claimsOf("topic.a")
        assertEquals(listOf("clm.new"), claims.map { it.claimId })
    }

    @Test
    fun `删除主题连带删除命题且不影响其它主题`() = runBlocking {
        repo.save(
            meta = graph("topic.a"),
            outline = listOf(node("topic.a")),
            cards = listOf(card("topic.a", "crd.a.0")),
            claims = listOf(claim("topic.a", "clm.a", "crd.a.0", "A 的命题"))
        )
        repo.save(
            meta = graph("topic.b"),
            outline = listOf(node("topic.b")),
            cards = listOf(card("topic.b", "crd.b.0")),
            claims = listOf(claim("topic.b", "clm.b", "crd.b.0", "B 的命题"))
        )

        repo.deleteGraph("topic.a")

        assertTrue(repo.claimsOf("topic.a").isEmpty())
        assertEquals(listOf("clm.b"), repo.claimsOf("topic.b").map { it.claimId })
        // 汇总接口同样不能带出已删除主题
        assertEquals(listOf("clm.b"), repo.allClaims().map { it.claimId })
    }

    /* ------------------------------------------------------------ 工具 */

    private fun graph(topicId: String) = GeneratedGraph(
        topicId = topicId,
        title = topicId,
        createdTime = 1L,
        source = "cloud",
        providerId = "test",
        blockCount = 1,
        warnings = emptyList(),
        verified = false
    )

    private fun node(topicId: String) = GeneratedOutlineNode(
        topicId = topicId,
        path = "$topicId.t1",
        parentPath = null,
        title = "1 章",
        level = 1,
        order = 0
    )

    private fun card(topicId: String, cardId: String) = GeneratedCard(
        topicId = topicId,
        cardId = cardId,
        outlinePath = "$topicId.t1",
        title = "1.1 卡",
        summary = "",
        body = "正文",
        order = 0
    )

    private fun claim(topicId: String, claimId: String, cardId: String, statement: String) =
        GeneratedClaim(
            topicId = topicId,
            claimId = claimId,
            cardId = cardId,
            outlinePath = "$topicId.t1",
            statement = statement,
            expected = true,
            conditions = listOf("条件一"),
            explanation = "解释",
            misconception = "误解",
            evidence = "",
            order = 0
        )
}
