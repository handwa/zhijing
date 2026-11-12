package com.example.zhijing.kg.depth

import com.example.zhijing.kg.MiniJson
import com.example.zhijing.kg.asMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `triples` 解析测试。
 *
 * 重点不是「能解析正常数据」，而是**坏数据不能把图谱页带崩**：
 * 旧版 assets 没有这个字段、字段类型写错、单条写坏，都必须降级成「没有可拨条件」。
 */
class TripleParserTest {

    private fun parse(json: String): List<ConditionTriple> =
        TripleParser.parse(MiniJson.parse(json).asMap())

    private val shapeTripleJson = """
        {"triples":[{
          "id":"tri.a","claimId":"clm.a","cardId":"crd.a",
          "statement":"输入最后一维等于权重输入维","kind":"shape",
          "mechanism":"矩阵乘要沿内维求和","computable":true,
          "interventions":[{
            "id":"inv.1","action":"把 in_x 从 4 改成 5",
            "overrides":{"in_x":5},
            "contract":{
              "symbols":{"N":8,"in_x":4,"in_w":4,"out_w":6},
              "op":{"kind":"matmul","lhs":["N","in_x"],"rhs":["out_w","in_w"],"rhsTransposed":true},
              "constraints":[{"kind":"equals","left":"in_x","right":"in_w","describe":"内维相等"}]
            },
            "consequence":"内维不等，运算未定义","source":"COMPUTED",
            "misjudgements":[{"predicted":"HOLDS","why":"以为只会变宽","correction":"内维必须相等"}]
          }]
        }]}
    """.trimIndent()

    @Test
    fun parsesFullTriple() {
        val triples = parse(shapeTripleJson)

        assertEquals(1, triples.size)
        val triple = triples.single()
        assertEquals("tri.a", triple.id)
        assertEquals("clm.a", triple.claimId)
        assertEquals("crd.a", triple.cardId)
        assertEquals(ConditionKind.SHAPE, triple.kind)
        assertTrue(triple.computable)
        assertTrue("完整三元组应通过入库门槛", triple.isValid)

        val inv = triple.interventions.single()
        assertEquals("inv.1", inv.id)
        assertEquals(mapOf("in_x" to 5), inv.overrides)
        assertEquals(TruthSource.COMPUTED, inv.source)
        assertNotNull(inv.contract)
        assertEquals(1, inv.misjudgements.size)
        assertEquals(Outcome.HOLDS, inv.misjudgements.single().predicted)
    }

    @Test
    fun parsedContractIsEvaluatedLocally() {
        val inv = parse(shapeTripleJson).single().interventions.single()

        // in_x 被覆盖成 5，而 in_w 仍是 4 → 矩阵乘内维不等
        val resolved = inv.resolveOutcome()
        assertEquals(Outcome.FAILS, resolved.outcome)
        assertEquals(TruthSource.COMPUTED, resolved.source)
        assertFalse("算出真值不需要额外提示", resolved.needsCaveat)
    }

    @Test
    fun parsesSoftmaxSumContract() {
        val json = """
            {"triples":[{
              "id":"tri.b","claimId":"clm.b","cardId":"crd.b",
              "statement":"沿类别维求和为 1","kind":"statistics",
              "mechanism":"softmax 在类别维归一化","computable":true,
              "interventions":[
                {"id":"inv.b1","action":"沿类别维求和","contract":{
                  "symbols":{"N":8,"C":10},
                  "op":{"kind":"softmaxSum","input":["N","C"],"softmaxAxis":1,"sumAxis":1},
                  "constraints":[]},"consequence":"等于 1","source":"COMPUTED",
                 "misjudgements":[{"predicted":"FAILS","why":"当成计数","correction":"已归一化"}]},
                {"id":"inv.b2","action":"沿 batch 维求和","contract":{
                  "symbols":{"N":8,"C":10},
                  "op":{"kind":"softmaxSum","input":["N","C"],"softmaxAxis":1,"sumAxis":0},
                  "constraints":[]},"consequence":"不等于 1","source":"COMPUTED",
                 "misjudgements":[{"predicted":"HOLDS","why":"以为怎么加都是 1","correction":"只在归一化轴"}]}
              ]
            }]}
        """.trimIndent()

        val interventions = parse(json).single().interventions

        assertEquals(Outcome.HOLDS, interventions[0].resolveOutcome().outcome)
        val batch = interventions[1].resolveOutcome()
        assertEquals(Outcome.FAILS, batch.outcome)
        assertTrue("失败原因要能直接给用户看：${batch.detail}", batch.detail.contains("不是 1"))
    }

    /* ------------------------------------------------------------ 降级 */

    @Test
    fun missingTriplesFieldYieldsEmpty() {
        assertTrue(parse("""{"outline":[],"claims":[]}""").isEmpty())
    }

    @Test
    fun triplesNotAnArrayYieldsEmpty() {
        assertTrue(parse("""{"triples":{"id":"tri.a"}}""").isEmpty())
    }

    @Test
    fun malformedTripleIsSkippedOthersKept() {
        val json = """
            {"triples":[
              {"id":"tri.bad","statement":"缺 claimId"},
              {"id":"tri.ok","claimId":"clm.ok","cardId":"crd.ok","statement":"条件",
               "mechanism":"机制","kind":"shape",
               "interventions":[{"id":"inv.ok","action":"拨一下","consequence":"后果",
                 "misjudgements":[{"predicted":"FAILS","why":"w","correction":"c"}]}]}
            ]}
        """.trimIndent()

        val triples = parse(json)

        assertEquals(1, triples.size)
        assertEquals("tri.ok", triples.single().id)
    }

    @Test
    fun incompleteTripleIsParsedButRejectedByThreshold() {
        val json = """
            {"triples":[{"id":"tri.a","claimId":"clm.a","cardId":"crd.a",
              "statement":"条件","mechanism":"",
              "interventions":[{"id":"inv.1","action":"拨一下","consequence":"后果",
                "misjudgements":[{"predicted":"FAILS","why":"w","correction":"c"}]}]}]}
        """.trimIndent()

        val triple = parse(json).single()

        assertFalse("缺机制不应通过门槛", triple.isValid)
        assertTrue(triple.problems().any { it.contains("缺机制") })
    }

    @Test
    fun missingSourceDefaultsToUnverified() {
        val json = """
            {"triples":[{"id":"tri.a","claimId":"clm.a","cardId":"crd.a",
              "statement":"条件","mechanism":"机制",
              "interventions":[{"id":"inv.1","action":"拨一下","consequence":"后果",
                "misjudgements":[{"predicted":"FAILS","why":"w","correction":"c"}]}]}]}
        """.trimIndent()

        val resolved = parse(json).single().interventions.single().resolveOutcome()

        assertEquals("来源不明时按未核实处理", TruthSource.AUTHORED_MODEL, resolved.source)
        assertTrue(resolved.needsCaveat)
    }

    @Test
    fun dimensionExpressionSupportsOffset() {
        val json = """
            {"triples":[{"id":"tri.a","claimId":"clm.a","cardId":"crd.a",
              "statement":"条件","mechanism":"机制",
              "interventions":[{"id":"inv.1","action":"拨一下","consequence":"后果",
                "contract":{
                  "symbols":{"in_x":4},
                  "op":{"kind":"elementWise","input":["in_x"]},
                  "constraints":[{"kind":"compare","left":"in_x+1","right":"4","lessOrEqual":false}]},
                "misjudgements":[{"predicted":"FAILS","why":"w","correction":"c"}]}]}]}
        """.trimIndent()

        val constraint = parse(json).single().interventions.single().contract!!.constraints.single()
        val compare = constraint as Constraint.DimCompare

        assertEquals(DimExpr.Sym("in_x", offset = 1), compare.left)
        assertEquals(DimExpr.Const(4), compare.right)
    }

    @Test
    fun unknownOperatorKindDropsContract() {
        val json = """
            {"triples":[{"id":"tri.a","claimId":"clm.a","cardId":"crd.a",
              "statement":"条件","mechanism":"机制","computable":true,
              "interventions":[{"id":"inv.1","action":"拨一下","consequence":"后果",
                "contract":{"symbols":{"a":1},"op":{"kind":"conv2d"},"constraints":[]},
                "misjudgements":[{"predicted":"FAILS","why":"w","correction":"c"}]}]}]}
        """.trimIndent()

        val triple = parse(json).single()

        // 契约被丢弃 → 标为 computable 却没有契约 → 门槛拒绝
        assertEquals(null, triple.interventions.single().contract)
        assertFalse(triple.isValid)
    }
}
