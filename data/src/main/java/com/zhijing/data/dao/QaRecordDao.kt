package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zhijing.data.entity.QaRecordEntity
import kotlinx.coroutines.flow.Flow

/** qa_record_table 的数据访问接口（说明书 3.4.6，问答记录）。 */
@Dao
internal interface QaRecordDao {

    @Insert
    suspend fun insert(entity: QaRecordEntity): Long

    @Query("SELECT * FROM qa_record_table WHERE id = :id")
    suspend fun findById(id: Long): QaRecordEntity?

    @Query("SELECT * FROM qa_record_table ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<QaRecordEntity>>

    @Query("SELECT * FROM qa_record_table ORDER BY id DESC LIMIT :limit")
    suspend fun findRecent(limit: Int): List<QaRecordEntity>

    @Query("DELETE FROM qa_record_table WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM qa_record_table")
    suspend fun deleteAll()
}
