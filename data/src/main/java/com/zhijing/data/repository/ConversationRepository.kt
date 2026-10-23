package com.zhijing.data.repository

import kotlinx.coroutines.flow.Flow

//会话与消息历史（上下文功能的归档区）
public interface Conversation{
    suspend fun createConversation(title:String,systemPrompt:String):Long
    suspend fun findConversation(id:Long):ConversationEntitySnapshot?
    fun observeConversations(): Flow<List<ConversationEntitySnapshot>>
    suspend fun deleteConversation(id:Long)
    suspend fun appendMessage(conversationId:Long,role:String,content:String,tokenCount:Int):Long
    suspend fun messageAfter(conversationId:Long,afterSeq:Int):List<MessageSnapshot>
    suspend fun allMessage(conversationID:Long):List<MessageSnapshot>
    suspend fun updateDigest(conversationId:Long,digest:String,upToSeq:Int)
}


public data class ConversationEntitySnapshot(
    val id:Long,
    val title:String,
    val systemPrompt:String,
    val digest:String?,
    val digestUpToSeq:Int,
    val createdAt:Long,
    val updatedAt:Long,
)
public data class MessageSnapshot(
    val id:Long,
    val role:String,
    val content:String,
    val tokenCount:Int,
    val seq:Int,
    val createdAt:Long,
)