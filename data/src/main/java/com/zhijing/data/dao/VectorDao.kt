package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zhijing.data.entity.VectorEntity
import com.zhijing.data.model.VectorOwnerType

/**
 * vector_table 的数据访问接口。
 *
 * (owner_type, owner_id) 唯一，因此插入用 REPLACE 语义即为 upsert：
 * 同一内容/分段重复写入向量时覆盖旧值。
 */
@Dao
internal interface VectorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VectorEntity): Long

    @Query("SELECT * FROM vector_table WHERE owner_type = :ownerType AND owner_id = :ownerId")
    suspend fun find(ownerType: VectorOwnerType, ownerId: Long): VectorEntity?

    @Query("SELECT * FROM vector_table WHERE owner_type = :ownerType AND owner_id IN (:ownerIds)")
    suspend fun findByOwnerIds(ownerType: VectorOwnerType, ownerIds: List<Long>): List<VectorEntity>

    /** 检索用：一次取出某一类全部向量，交给 CosineSimilarity 手算。 */
    @Query("SELECT * FROM vector_table WHERE owner_type = :ownerType")
    suspend fun findAllByType(ownerType: VectorOwnerType): List<VectorEntity>

    @Query("DELETE FROM vector_table WHERE owner_type = :ownerType AND owner_id = :ownerId")
    suspend fun delete(ownerType: VectorOwnerType, ownerId: Long)

    @Query("DELETE FROM vector_table WHERE owner_type = :ownerType AND owner_id IN (:ownerIds)")
    suspend fun deleteByOwnerIds(ownerType: VectorOwnerType, ownerIds: List<Long>)

    /** 清空全部向量（vector_table 无外键，删除内容时必须由数据层显式处理）。 */
    @Query("DELETE FROM vector_table")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM vector_table WHERE owner_type = :ownerType")
    suspend fun countByType(ownerType: VectorOwnerType): Long
}
