package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.zhijing.data.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ConversationDao {

    @Insert
    suspend fun insert(entity: ConversationEntity): Long

    @Update
    suspend fun update(entity: ConversationEntity)

    @Query("SELECT * FROM conversation_table WHERE id = :id")
    suspend fun findById(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversation_table ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("UPDATE conversation_table SET digest = :digest, digest_up_to_seq = :upTo, updated_at = :now WHERE id = :id")
    suspend fun updateDigest(id: Long, digest: String, upTo: Int, now: Long)

    @Query("DELETE FROM conversation_table WHERE id = :id")
    suspend fun deleteById(id: Long)
}