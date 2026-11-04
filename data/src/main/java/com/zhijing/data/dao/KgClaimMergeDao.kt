package com.zhijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.zhijing.data.entity.KgClaimMergeEntity

/**
 * `kg_claim_merge` 的数据访问接口。
 *
 * [resolve] 是热路径：广度侧渲染每张卡都要把「派生 ID → 规范 ID」对齐一次，
 * 因此单独提供按批查询的版本，避免逐条查表。
 */
@Dao
internal interface KgClaimMergeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KgClaimMergeEntity)

    @Query("SELECT * FROM kg_claim_merge WHERE source_claim_id = :sourceClaimId")
    suspend fun find(sourceClaimId: String): KgClaimMergeEntity?

    @Query("SELECT * FROM kg_claim_merge WHERE source_claim_id IN (:sourceClaimIds)")
    suspend fun findBySourceIds(sourceClaimIds: List<String>): List<KgClaimMergeEntity>

    /**
     * 把一批命题 ID 规范化：有映射的替换为目标 ID，没有映射的原样保留。
     *
     * 一条源 ID 只能指向一个目标（主键约束），因此不存在链式映射，
     * 解析一次即可，不需要递归。
     */
    @Transaction
    suspend fun resolve(claimIds: List<String>): Map<String, String> {
        if (claimIds.isEmpty()) return emptyMap()
        val merged = findBySourceIds(claimIds).associate { it.sourceClaimId to it.targetClaimId }
        return claimIds.associateWith { merged[it] ?: it }
    }

    @Query("SELECT * FROM kg_claim_merge WHERE target_claim_id = :targetClaimId")
    suspend fun findByTarget(targetClaimId: String): List<KgClaimMergeEntity>

    @Query("DELETE FROM kg_claim_merge WHERE source_claim_id = :sourceClaimId")
    suspend fun delete(sourceClaimId: String)

    @Query("DELETE FROM kg_claim_merge")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM kg_claim_merge")
    suspend fun count(): Int
}
