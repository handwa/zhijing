package com.example.zhijing.kg

import com.zhijing.data.model.GeneratedCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 生成命题的入库闸门。
 *
 * 模型输出不能直接落库：太长的是段落、太短的是标签、同一句话会换个说法出现两次。
 * 这些规则以前只能联网调模型才测得到，抽成纯函数后可以逐条钉住。
 */
class GeneratedClaimBuilderTest {

    @Test
    fun `过长过短与重复的断言被丢弃`() {
        val card = card("crd.1", "s1")
        val claims = GeneratedClaimBuilder.build(
            topicId = "t",
            cards = listOf(card),
            extractedByCard = mapOf(
                card.cardId to listOf(
                    extracted("向量"), // 太短
                    extracted("这是一条明显超过八十个字符的断言".repeat(6)), // 太长
                    extracted("矩阵加法满足交换律"),
                    // 只差空格与标点，规范化后与上一条相同
                    extracted("矩阵加法满足交换律。"),
                )
            )
        )

        assertEquals(1, claims.size)
        assertEquals("矩阵加法满足交换律", claims.single().statement)
    }

    @Test
    fun `每卡最多保留两条`() {
        val card = card("crd.1", "s1")
        val claims = GeneratedClaimBuilder.build(
            topicId = "t",
            cards = listOf(card),
            extractedByCard = mapOf(
                card.cardId to listOf(
                    extracted("矩阵加法满足交换律"),
                    extracted("矩阵加法满足结合律"),
                    extracted("矩阵加法存在零元"),
                )
            )
        )

        assertEquals(GeneratedClaimBuilder.MAX_CLAIMS_PER_CARD, claims.size)
        // 顺序保留模型给的顺序，题序才稳定
        assertEquals(listOf(0, 1), claims.map { it.order })
    }

    @Test
    fun `ID 由大纲路径与断言推导且挂回卡片`() {
        val card = card("crd.1", "s1.linear")
        val claims = GeneratedClaimBuilder.build(
            topicId = "t",
            cards = listOf(card),
            extractedByCard = mapOf(card.cardId to listOf(extracted("矩阵乘法不满足交换律")))
        )

        val claim = claims.single()
        assertEquals("crd.1", claim.cardId)
        assertEquals("t", claim.topicId)
        assertTrue("ID 必须符合 clm.* 规范：${claim.claimId}", claim.claimId.startsWith("clm."))
        assertEquals(ClaimIds.claimId("s1.linear", "矩阵乘法不满足交换律"), claim.claimId)
        // 生成图谱没有原文可溯源
        assertEquals("", claim.evidence)
    }

    @Test
    fun `缺少条件的断言补一句未标注条件`() {
        val card = card("crd.1", "s1")
        val claims = GeneratedClaimBuilder.build(
            topicId = "t",
            cards = listOf(card),
            extractedByCard = mapOf(
                card.cardId to listOf(extracted("行列式为零则矩阵不可逆"))
            )
        )

        // 判断面板靠 conditions 解释「什么前提下才成立」，空着会让解释区出现空白
        assertEquals(listOf("未标注条件"), claims.single().conditions)
    }

    @Test
    fun `没有抽到命题的卡片不产生记录`() {
        val claims = GeneratedClaimBuilder.build(
            topicId = "t",
            cards = listOf(card("crd.1", "s1"), card("crd.2", "s2")),
            extractedByCard = mapOf("crd.2" to listOf(extracted("矩阵加法满足交换律")))
        )

        assertEquals(listOf("crd.2"), claims.map { it.cardId })
    }

    /* ------------------------------------------------------------ 工具 */

    private fun card(cardId: String, outlinePath: String) = GeneratedCard(
        topicId = "t",
        cardId = cardId,
        outlinePath = outlinePath,
        title = "1.1 卡",
        summary = "",
        body = "正文",
        order = 0
    )

    private fun extracted(statement: String) = ExtractedClaim(
        statement = statement,
        expected = true,
        conditions = emptyList(),
    )
}
