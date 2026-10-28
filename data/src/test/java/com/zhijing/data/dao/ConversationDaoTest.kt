package com.zhijing.data.dao

import com.zhijing.data.TestDatabase
import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.repository.ConversationRepository
import com.zhijing.data.repository.DefaultConversationRepository
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ConversationDaoTest {
    private lateinit var db: ZhijingDatabase
    private lateinit var repo: ConversationRepository
    @Before
    fun setUp(){
        db= TestDatabase.createInMemory()
        repo= DefaultConversationRepository(db)
    }
    @After
    fun tearDown()=db.close()
    @Test
    fun appendMessage_assignsIncrementingSeq()= runBlocking {
        val cid = repo.createConversation("测试会话", "你是知境")
        repo.appendMessage(cid, "user", "你好", 10)
        repo.appendMessage(cid, "assistant", "我是你的助手",12)
        repo.appendMessage(cid, "user", "再见", 8)

        val all = repo.allMessages(cid)
        assertEquals(3, all.size)
        assertEquals(listOf(1, 2, 3), all.map { it.seq })

    }
    @Test
    fun deleteConversation_cascadesMessage()= runBlocking {
        val cid = repo.createConversation("要删的", "sys")
        repo.appendMessage(cid, "user", "测试", 5)
        repo.deleteConversation(cid)

        assertTrue(repo.allMessages(cid).isEmpty())
    }
    @Test
    fun messageAfter_returnOnlyNewer()= runBlocking {
        val cid = repo.createConversation("边界", "sys")
        repeat(5) { repo.appendMessage(cid, "user", "第${it}", 5) }

        val after = repo.messagesAfter(cid, afterSeq = 3)
        assertEquals(listOf(4, 5), after.map { it.seq })
    }





}