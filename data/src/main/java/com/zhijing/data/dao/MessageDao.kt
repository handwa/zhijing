package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zhijing.data.entity.MessageEntity

/** message_table 的数据访问接口。 */
@Dao
internal interface MessageDao {

    @Insert
    suspend fun insert(entity: MessageEntity): Long

    /** 会话内全部消息，按序号排序。 */
    @Query("SELECT * FROM message_table WHERE conversation_id = :cid ORDER BY seq")
    suspend fun findByConversation(cid: Long): List<MessageEntity>

    /** 序号大于 afterSeq 的消息（即提要边界之后的部分）。 */
    @Query("SELECT * FROM message_table WHERE conversation_id = :cid AND seq > :afterSeq ORDER BY seq")
    suspend fun findAfterSeq(cid: Long, afterSeq: Int): List<MessageEntity>

    /** 会话内最大序号，用于分配下一条。 */
    @Query("SELECT COALESCE(MAX(seq), 0) FROM message_table WHERE conversation_id = :cid")
    suspend fun maxSeq(cid: Long): Int

    /** 提要边界之后消息的 token 总和，用于校验剩余预算。 */
    @Query("SELECT COALESCE(SUM(token_count), 0) FROM message_table WHERE conversation_id = :cid AND seq > :afterSeq")
    suspend fun sumTokensAfter(cid: Long, afterSeq: Int): Int

    @Query("DELETE FROM message_table WHERE conversation_id = :cid")
    suspend fun deleteByConversation(cid: Long)
}