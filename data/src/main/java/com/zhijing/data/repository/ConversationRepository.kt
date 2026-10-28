package com.zhijing.data.repository

import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.entity.ConversationEntity
import com.zhijing.data.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 会话与消息历史（上下文功能的归档区）
public interface ConversationRepository {
    suspend fun createConversation(title: String, systemPrompt: String): Long
    suspend fun findConversation(id: Long): ConversationEntitySnapshot?
    fun observeConversations(): Flow<List<ConversationEntitySnapshot>>
    suspend fun deleteConversation(id: Long)
    suspend fun appendMessage(conversationId: Long, role: String, content: String, tokenCount: Int): Int
    suspend fun messagesAfter(conversationId: Long, afterSeq: Int): List<MessageSnapshot>
    suspend fun allMessages(conversationId: Long): List<MessageSnapshot>
    suspend fun updateDigest(conversationId: Long, digest: String, upToSeq: Int)
    suspend fun renameConversation(conversationId: Long, title: String)
}

public data class ConversationEntitySnapshot(
    val id: Long,
    val title: String,
    val systemPrompt: String,
    val digest: String?,
    val digestUpToSeq: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

public data class MessageSnapshot(
    val id: Long,
    val role: String,
    val content: String,
    val tokenCount: Int,
    val seq: Int,
    val createdAt: Long,
)

internal class DefaultConversationRepository(
    private val db: ZhijingDatabase,
) : ConversationRepository {

    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()

    override suspend fun createConversation(title: String, systemPrompt: String): Long {
        val now = System.currentTimeMillis()
        return conversationDao.insert(
            ConversationEntity(
                title = title,
                systemPrompt = systemPrompt,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun findConversation(id: Long): ConversationEntitySnapshot? =
        conversationDao.findById(id)?.toSnapshot()

    override fun observeConversations(): Flow<List<ConversationEntitySnapshot>> =
        conversationDao.observeAll().map { list -> list.map { it.toSnapshot() } }

    override suspend fun deleteConversation(id: Long) {
        // 消息由外键 CASCADE 自动删除
        conversationDao.deleteById(id)
    }

    /**
     * 追加一条消息，返回它在会话内的序号 seq。
     *
     * seq 由这里统一分配（maxSeq + 1），调用方必须使用返回值，
     * 不要自己推算，否则内存与数据库会错位。
     */
    override suspend fun appendMessage(
        conversationId: Long,
        role: String,
        content: String,
        tokenCount: Int,
    ): Int {
        val now = System.currentTimeMillis()
        val seq = messageDao.maxSeq(conversationId) + 1
        messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                tokenCount = tokenCount,
                seq = seq,
                createdAt = now,
            )
        )
        conversationDao.touch(conversationId, now)
        return seq
    }

    override suspend fun messagesAfter(
        conversationId: Long,
        afterSeq: Int,
    ): List<MessageSnapshot> =
        messageDao.findAfterSeq(conversationId, afterSeq).map { it.toSnapshot() }

    override suspend fun allMessages(conversationId: Long): List<MessageSnapshot> =
        messageDao.findByConversation(conversationId).map { it.toSnapshot() }

    override suspend fun updateDigest(conversationId: Long, digest: String, upToSeq: Int) {
        conversationDao.updateDigest(conversationId, digest, upToSeq, System.currentTimeMillis())
    }

    override suspend fun renameConversation(conversationId: Long, title: String) {
        conversationDao.updateTitle(conversationId, title, System.currentTimeMillis())
    }
}

private fun ConversationEntity.toSnapshot() = ConversationEntitySnapshot(
    id = id,
    title = title,
    systemPrompt = systemPrompt,
    digest = digest,
    digestUpToSeq = digestUpToSeq,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun MessageEntity.toSnapshot() = MessageSnapshot(
    id = id,
    role = role,
    content = content,
    tokenCount = tokenCount,
    seq = seq,
    createdAt = createdAt,
)