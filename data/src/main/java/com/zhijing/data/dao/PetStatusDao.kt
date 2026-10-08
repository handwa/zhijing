package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zhijing.data.entity.PetStatusEntity
import kotlinx.coroutines.flow.Flow

/**
 * pet_status_table 的数据访问接口（说明书 3.4.7）。
 *
 * 全表固定只有 id = 1 这一行，因此读写都是单行操作。
 */
@Dao
internal interface PetStatusDao {

    @Query("SELECT * FROM pet_status_table WHERE id = 1")
    suspend fun get(): PetStatusEntity?

    @Query("SELECT * FROM pet_status_table WHERE id = 1")
    fun observe(): Flow<PetStatusEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PetStatusEntity)

    @Query("DELETE FROM pet_status_table")
    suspend fun deleteAll()
}
