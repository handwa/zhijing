package com.example.zhijing.kg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 命题 ID 规则测试。
 *
 * 这是整条云端生成链路里**最不能错**的一环：端上算出的哈希必须与
 * `kg/src/kg_model.py` 逐位一致，否则同一条命题会有两个 ID，判断状态立刻分裂。
 *
 * 用例里的期望值全部来自 Python 侧的 `kg_model.short_hash` 实测输出
 * （见 `buildEvidence` 一节的注释），不是照着实现反推的。
 */
class ClaimIdsTest {

    @Test
    fun `规范化去空白去标点转小写`() {
        // 全角空格、中英文标点、大小写都应被抹掉
        assertEquals(
            "relu之后可以直接接softmax输出类别概率",
            ClaimIds.normalize(" ReLU 之后可以直接接 Softmax 输出类别概率。 ")
        )
        assertEquals(
            "abc",
            ClaimIds.normalize("A，B。C！")
        )
        // 全角空格 \u3000 必须先变普通空格再去掉
        assertEquals("ab", ClaimIds.normalize("a\u3000b"))
    }

    @Test
    fun `只改标点或空格不会改变ID`() {
        val a = "ReLU 之后可以直接接 Softmax 输出类别概率。"
        val b = "ReLU之后可以直接接Softmax输出类别概率"
        val c = "ReLU 之后可以直接接 Softmax 输出类别概率 ！"
        assertEquals(ClaimIds.shortHash(a), ClaimIds.shortHash(b))
        // 结尾换成另一个标点，规范化后仍然相同
        assertEquals(ClaimIds.shortHash(a), ClaimIds.shortHash(a.removeSuffix("。") + "！"))
    }

    @Test
    fun `改实词会改变ID`() {
        val a = "ReLU 之后可以直接接 Softmax。"
        val b = "ReLU 之前可以直接接 Softmax。"
        assertFalse(ClaimIds.shortHash(a) == ClaimIds.shortHash(b))
    }

    @Test
    fun `哈希是稳定的小写十六进制`() {
        val hash = ClaimIds.shortHash("Linear 层的参数量与 batch 大小无关。")
        assertEquals(4, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
        // 同样输入必须永远得到同样输出
        assertEquals(hash, ClaimIds.shortHash("Linear 层的参数量与 batch 大小无关。"))
    }

    @Test
    fun `ID 由语义路径与哈希拼成且格式合法`() {
        val id = ClaimIds.claimId("arch.ffn.linear", "Linear 层的参数量与 batch 大小无关。")
        assertTrue(id.startsWith("clm.arch.ffn.linear."))
        assertTrue(ClaimIds.isValidClaimId(id))
    }

    @Test
    fun `不合规的ID会被拒绝`() {
        assertFalse(ClaimIds.isValidClaimId("claim-001"))
        assertFalse(ClaimIds.isValidClaimId("clm.arch.linear"))
        assertFalse(ClaimIds.isValidClaimId("clm.Arch.Linear.3C9A"))
        assertTrue(ClaimIds.isValidClaimId("clm.arch.linear.3c9a"))
    }

    @Test
    fun `语义路径里的中文会被丢弃并保留ASCII部分`() {
        assertEquals("arch-ffn-linear", ClaimIds.slugify("arch/ffn linear"))
        // 纯中文标题没有 ASCII 可保留，兜底补哈希；前缀与 Python 侧 fallback 一致为 node
        val slug = ClaimIds.slugify("前馈网络", fallback = "node")
        assertTrue(slug.startsWith("node-"))
    }

    /**
     * 与 Python 实现的交叉验证。
     *
     * 期望值由 `kg/src/kg_model.py` 实测得出（不是照实现反推）：
     *
     * ```
     * python -c "import kg_model as M; print(M.short_hash('Linear 层的参数量与 batch 大小无关。'))"
     * >>> 2fe7
     * ```
     *
     * 如果哪天有人改了端上的规范化字符集，这里会第一时间红掉。
     */
    @Test
    fun `与Python实现交叉验证`() {
        assertEquals("2fe7", ClaimIds.shortHash("Linear 层的参数量与 batch 大小无关。"))
        assertEquals("6092", ClaimIds.shortHash("ReLU 是逐元素操作，不改变输入张量的形状。"))
        assertEquals("a505", ClaimIds.shortHash("Softmax 的输出沿类别维度求和为 1。"))
        assertEquals("d07e", ClaimIds.shortHash("ReLU 之后可以直接接 Softmax 输出类别概率。"))
    }
}
