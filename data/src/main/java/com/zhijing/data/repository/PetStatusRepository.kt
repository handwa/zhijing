package com.zhijing.data.repository

import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.mapper.toDomain
import com.zhijing.data.mapper.toEntity
import com.zhijing.data.model.PetStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 养成系动物状态门面（说明书 3.4.7）。
 *
 * 全表只有一行，[saveStatus] 即整行覆盖；阶段取值见 `PetPhase`
 * （0 种子 / 1 萌芽 / 2 生长 / 3 绽放）。
 */
interface PetStatusRepository {

    fun observeStatus(): Flow<PetStatus?>

    suspend fun getStatus(): PetStatus?

    suspend fun saveStatus(status: PetStatus)

    suspend fun deleteAll()
}

internal class DefaultPetStatusRepository(
    private val db: ZhijingDatabase
) : PetStatusRepository {

    private val petStatusDao = db.petStatusDao()

    override fun observeStatus(): Flow<PetStatus?> =
        petStatusDao.observe().map { it?.toDomain() }

    override suspend fun getStatus(): PetStatus? = petStatusDao.get()?.toDomain()

    override suspend fun saveStatus(status: PetStatus) {
        petStatusDao.upsert(status.toEntity())
    }

    override suspend fun deleteAll() {
        petStatusDao.deleteAll()
    }
}
