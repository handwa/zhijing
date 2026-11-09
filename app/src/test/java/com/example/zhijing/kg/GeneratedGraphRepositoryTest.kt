package com.example.zhijing.kg

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zhijing.data.ZhiJingData
import com.zhijing.data.model.GeneratedCard
import com.zhijing.data.model.GeneratedGraph
import com.zhijing.data.model.GeneratedOutlineNode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 生成图谱的**持久化与合并**测试。
 *
 * 守护的是「生成完能不能看到」这条链的最后一环：
 * 1. 保存后能按主题读回来；
 * 2. 三张表整份替换（重复生成不堆积）；
 * 3. 删除后不影响其它主题；
 * 4. `GraphAssembler` 能把生成图谱与出厂图谱**并列**合并出来。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeneratedGraphRepositoryTest {

    private lateinit var data: ZhiJingData

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        data = ZhiJingData.get(context)
        runBlocking {
            data.kgGenerated.listGraphs().forEach { data.kgGenerated.deleteGraph(it.topicId) }
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            data.kgGenerated.listGraphs().forEach { data.kgGenerated.deleteGraph(it.topicId) }
        }
    }

    private fun meta(topicId: String, title: String = "测试主题", now: Long = 1000) =
        GeneratedGraph(topicId = topicId, title = title, createdTime = now)

    private fun outline(topic: String) = listOf(
        GeneratedOutlineNode(topic, "p1", null, "第一部分", 1, 0),
        GeneratedOutlineNode(topic, "p1.p2", "p1", "子部分", 2, 1)
    )

    private fun cards(topic: String) = listOf(
        GeneratedCard(topic, "crd.gen.$topic.0", "p1", "卡片一", "摘要一", "正文一", 0),
        GeneratedCard(topic, "crd.gen.$topic.1", "p1.p2", "卡片二", "摘要二", "正文二", 1)
    )

    @Test
    fun `保存后能按主题读回`() = runBlocking {
        data.kgGenerated.save(meta("t1"), outline("t1"), cards("t1"))

        val g = data.kgGenerated.getGraph("t1")
        assertNotNull(g)
        assertEquals("测试主题", g!!.title)
        assertEquals(2, data.kgGenerated.outlineOf("t1").size)
        assertEquals(2, data.kgGenerated.cardsOf("t1").size)
    }

    @Test
    fun `重复生成整份覆盖不堆积`() = runBlocking {
        data.kgGenerated.save(meta("t1", now = 1000), outline("t1"), cards("t1"))
        // 第二次只有一张卡
        data.kgGenerated.save(
            meta("t1", title = "新版", now = 2000),
            listOf(GeneratedOutlineNode("t1", "p1", null, "唯一", 1, 0)),
            listOf(GeneratedCard("t1", "crd.gen.t1.0", "p1", "唯一卡", "s", "b", 0))
        )

        assertEquals(1, data.kgGenerated.listGraphs().size)
        assertEquals("新版", data.kgGenerated.getGraph("t1")!!.title)
        // 旧的第二张卡必须被清掉
        assertEquals(1, data.kgGenerated.cardsOf("t1").size)
        assertEquals(1, data.kgGenerated.outlineOf("t1").size)
    }

    @Test
    fun `删除一个主题不影响另一个`() = runBlocking {
        data.kgGenerated.save(meta("t1"), outline("t1"), cards("t1"))
        data.kgGenerated.save(meta("t2"), outline("t2"), cards("t2"))

        data.kgGenerated.deleteGraph("t1")

        assertNull(data.kgGenerated.getGraph("t1"))
        assertTrue(data.kgGenerated.cardsOf("t1").isEmpty())
        assertNotNull(data.kgGenerated.getGraph("t2"))
        assertEquals(2, data.kgGenerated.cardsOf("t2").size)
    }

    @Test
    fun `没保存过的主题返回空而不是报错`() = runBlocking {
        assertNull(data.kgGenerated.getGraph("nope"))
        assertTrue(data.kgGenerated.outlineOf("nope").isEmpty())
        assertTrue(data.kgGenerated.cardsOf("nope").isEmpty())
    }

    @Test
    fun `主题按时间倒序`() = runBlocking {
        data.kgGenerated.save(meta("old", now = 1000), emptyList(), emptyList())
        data.kgGenerated.save(meta("new", now = 2000), emptyList(), emptyList())

        val list = data.kgGenerated.listGraphs()
        assertEquals(listOf("new", "old"), list.map { it.topicId })
    }

    @Test
    fun `生成图谱与出厂图谱并列合并`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assembler = GraphAssembler(context, data)
        assembler.seedBundled(emptyBundled())

        data.kgGenerated.save(
            meta("topic-x", title = "线性代数"),
            outline("topic-x"),
            cards("topic-x")
        )

        val merged = assembler.load(includeRuntimeClaims = false)
        // 出厂为空，只剩生成图谱
        assertEquals(2, merged.cards.size)
        assertEquals(1, merged.outline.size)
        val root = merged.outline.single()
        assertEquals("线性代数", root.title)
        assertTrue(root.path.startsWith("gen."))
        // 子节点挂在主题根下
        assertEquals(root.id, root.children.single().parentId)
    }

    @Test
    fun `生成图谱的卡片不可下钻`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assembler = GraphAssembler(context, data)
        assembler.seedBundled(emptyBundled())

        data.kgGenerated.save(meta("topic-y"), outline("topic-y"), cards("topic-y"))

        val merged = assembler.load(includeRuntimeClaims = false)
        // 生成卡片没有命题，因此不可下钻、无角标
        merged.cards.forEach { c ->
            assertTrue("生成卡片不应可下钻", !c.descendable)
            assertNull(c.badgeStatus)
        }
    }

    private fun emptyBundled() = ParsedGraph(
        schemaVersion = "1.0",
        generatedAt = "",
        outline = emptyList(),
        cards = emptyList(),
        claims = emptyList(),
        edges = emptyList()
    )
}
