package com.example.zhijing.kg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型输出解析的容错测试。
 *
 * 覆盖 `output_contract.md` 第 5 节的容错链，以及第 4 步的补全规则。
 * 这一层是「云端能生成图谱」能否成立的真实门槛：大模型返回不合法 JSON 是常态，
 * 解析器必须能救出尽可能多，同时**不能把脏数据当高质量数据入库**。
 */
class ClaimOutputParserTest {

    private val minimal = """
        {"claims":[{"statement":"ReLU 不改变输入形状。","expected":true,
         "conditions":["逐元素操作"],"evidence":"ReLU 是逐元素激活"}]}
    """.trimIndent()

    @Test
    fun `标准输出能解析`() {
        val result = ClaimOutputParser.parse(minimal)
        assertEquals(1, result.claims.size)
        val claim = result.claims.first()
        assertEquals("ReLU 不改变输入形状。", claim.statement)
        assertTrue(claim.expected)
        assertEquals(listOf("逐元素操作"), claim.conditions)
        assertEquals("ReLU 是逐元素激活", claim.evidence)
        assertTrue(result.warnings.isEmpty())
        assertTrue(claim.isUsable)
    }

    @Test
    fun `空 claims 数组是合法的没有命题`() {
        val result = ClaimOutputParser.parse("""{"claims":[]}""")
        assertTrue(result.claims.isEmpty())
        // 模型按契约合法表示「没有合格命题」，不应被当成错误
        assertTrue(result.warnings.none { it.contains("未找到") })
    }

    @Test
    fun `剥除markdown代码围栏`() {
        val raw = """
            这是分析结果：
            ```json
            $minimal
            ```
            以上。
        """.trimIndent()
        val result = ClaimOutputParser.parse(raw)
        assertEquals(1, result.claims.size)
        assertTrue(result.warnings.any { it.contains("围栏") })
    }

    @Test
    fun `夹带解释文字也能取出JSON`() {
        val raw = "好的，我来分析。\n$minimal\n希望有帮助。"
        val result = ClaimOutputParser.parse(raw)
        assertEquals(1, result.claims.size)
    }

    @Test
    fun `缺少expected时按false处理并警告`() {
        val raw = """{"claims":[{"statement":"某命题","conditions":["条件"]}]}"""
        val result = ClaimOutputParser.parse(raw)
        val claim = result.claims.single()
        assertFalse(claim.expected)
        assertTrue(result.warnings.any { it.contains("expected") })
    }

    @Test
    fun `缺少conditions时补默认条件并警告`() {
        val raw = """{"claims":[{"statement":"某命题","expected":true}]}"""
        val result = ClaimOutputParser.parse(raw)
        val claim = result.claims.single()
        assertEquals(listOf("未标注条件"), claim.conditions)
        assertTrue(result.warnings.any { it.contains("conditions") })
        // 补出来的条件仍然可用，但警告会提示需人工补全
        assertTrue(claim.isUsable)
    }

    @Test
    fun `缺少statement的条目整条丢弃`() {
        val raw = """
            {"claims":[
              {"expected":true,"conditions":["a"]},
              {"statement":"有断言的这条","expected":true,"conditions":["b"]}
            ]}
        """.trimIndent()
        val result = ClaimOutputParser.parse(raw)
        assertEquals(1, result.claims.size)
        assertEquals("有断言的这条", result.claims.single().statement)
        assertTrue(result.warnings.any { it.contains("statement") })
    }

    @Test
    fun `非法错点类型被过滤`() {
        val raw = """
            {"claims":[{"statement":"某命题","expected":true,"conditions":["a"],
             "errorKinds":[{"kind":"shape","why":"形状对不上"},{"kind":"magic","why":"乱写"}]}]}
        """.trimIndent()
        val result = ClaimOutputParser.parse(raw)
        val kinds = result.claims.single().errorKinds
        assertEquals(1, kinds.size)
        assertEquals("shape", kinds.single().kind)
        assertTrue(result.warnings.any { it.contains("五类") })
    }

    @Test
    fun `缺边界的边被丢弃`() {
        val raw = """
            {"claims":[{"statement":"某命题","expected":true,"conditions":["a"],
             "edges":[
               {"to":"clm.x.1","type":"depends_on","kind":"shape","premise":"p","boundary":"b"},
               {"to":"clm.y.1","type":"depends_on","kind":"shape","premise":"只有前提"}
             ]}]}
        """.trimIndent()
        val result = ClaimOutputParser.parse(raw)
        val edges = result.claims.single().edges
        assertEquals(1, edges.size)
        assertEquals("b", edges.single().boundary)
        assertTrue(result.warnings.any { it.contains("边界") })
    }

    @Test
    fun `非法边类型被丢弃`() {
        val raw = """
            {"claims":[{"statement":"某命题","expected":true,"conditions":["a"],
             "edges":[{"to":"clm.x.1","type":"likes","kind":"shape","premise":"p","boundary":"b"}]}]}
        """.trimIndent()
        val result = ClaimOutputParser.parse(raw)
        assertTrue(result.claims.single().edges.isEmpty())
    }

    @Test
    fun `被截断的数组只保留完整条目`() {
        // 模拟 max_tokens 打满，第二条只输出了一半
        val raw = """
            {"claims":[
              {"statement":"完整的这条","expected":true,"conditions":["a"]},
              {"statement":"被截断的这
        """.trimIndent()
        val result = ClaimOutputParser.parse(raw)
        assertEquals(1, result.claims.size)
        assertEquals("完整的这条", result.claims.single().statement)
        assertTrue(result.warnings.any { it.contains("未闭合") })
    }

    @Test
    fun `字符串里的花括号和逗号不会破坏切分`() {
        val raw = """
            {"claims":[{"statement":"含 } 和 , 的断言","expected":false,
             "conditions":["条件里有 {x} 和 , 逗号"],"evidence":"证据"}]}
        """.trimIndent()
        val result = ClaimOutputParser.parse(raw)
        val claim = result.claims.single()
        assertEquals("含 } 和 , 的断言", claim.statement)
        assertEquals(listOf("条件里有 {x} 和 , 逗号"), claim.conditions)
    }

    @Test
    fun `完全不是JSON时返回空并说明原因`() {
        val result = ClaimOutputParser.parse("抱歉，我无法完成这个任务。")
        assertTrue(result.claims.isEmpty())
        assertTrue(result.warnings.any { it.contains("claims 数组") })
    }

    @Test
    fun `空输入返回空结果`() {
        val result = ClaimOutputParser.parse("   ")
        assertTrue(result.claims.isEmpty())
        assertTrue(result.warnings.any { it.contains("为空") })
    }

    @Test
    fun `嵌套对象里的同名key不会串位`() {
        // 顶层 expected=true，内层 edge 里若有 expected 不应被误取
        val raw = """
            {"claims":[{"statement":"外层","expected":true,"conditions":["a"],
             "edges":[{"to":"x","type":"affects","kind":"semantic","premise":"p","boundary":"b","expected":false}]}]}
        """.trimIndent()
        val result = ClaimOutputParser.parse(raw)
        assertTrue(result.claims.single().expected)
    }

    @Test
    fun `unicode转义被正确还原`() {
        val raw = """{"claims":[{"statement":"\u4e2d\u6587\u65ad\u8a00","expected":true,"conditions":["a"]}]}"""
        val result = ClaimOutputParser.parse(raw)
        assertEquals("中文断言", result.claims.single().statement)
    }

    @Test
    fun `权重越界被钳制`() {
        val raw = """
            {"claims":[{"statement":"某命题","expected":true,"conditions":["a"],
             "edges":[{"to":"x","type":"affects","kind":"semantic","premise":"p","boundary":"b","weight":9}]}]}
        """.trimIndent()
        val result = ClaimOutputParser.parse(raw)
        assertEquals(1.0, result.claims.single().edges.single().weight, 0.0001)
    }

    @Test
    fun `question为空时返回null而不是空串`() {
        val raw = """{"claims":[{"statement":"某命题","expected":true,"conditions":["a"],"question":"  "}]}"""
        val result = ClaimOutputParser.parse(raw)
        assertNull(result.claims.single().question)
    }
}
