package com.zhijing.data.repository

import androidx.room.withTransaction
import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.mapper.toDomain
import com.zhijing.data.mapper.toEntity
import com.zhijing.data.model.Association
import com.zhijing.data.model.AssociationFeedback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 关联记录门面（说明书 3.4.2、4.2.4 关联卡片）。
 *
 * 卡片反馈会按说明书内置参数 feedback_weights（很好 +0.1 / 平常 0 / 不好 -0.05）
 * 累加到 weight 上，影响后续推荐排序。
 */
interface AssociationRepository {

    fun observeAssociations(): Flow<List<Association>>

    suspend fun getAssociation(id: Long): Association?

    /** 与某条内容相关的全部关联。 */
    suspend fun getAssociationsFor(contentId: Long): List<Association>

    /** 保存一条关联；[Association.id] 传 0 表示新增。 */
    suspend fun save(association: Association): Long

    /**
     * 提交卡片反馈并累加权重。
     *
     * @return 更新后的 weight。
     * @throws IllegalArgumentException 关联记录不存在时。
     */
    suspend fun submitFeedback(id: Long, feedback: AssociationFeedback): Double

    suspend fun deleteAssociation(id: Long)

    suspend fun deleteAll()
}

internal class DefaultAssociationRepository(
    private val db: ZhijingDatabase
) : AssociationRepository {

    private val associationDao = db.associationDao()

    override fun observeAssociations(): Flow<List<Association>> =
        associationDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAssociation(id: Long): Association? =
        associationDao.findById(id)?.toDomain()

    override suspend fun getAssociationsFor(contentId: Long): List<Association> =
        associationDao.findRelatedTo(contentId).map { it.toDomain() }

    override suspend fun save(association: Association): Long =
        associationDao.insert(association.toEntity())

    override suspend fun submitFeedback(id: Long, feedback: AssociationFeedback): Double =
        db.withTransaction {
            val entity = requireNotNull(associationDao.findById(id)) { "关联记录不存在: $id" }
            val newWeight = entity.weight + feedback.weightDelta
            associationDao.updateFeedback(id, feedback, newWeight)
            newWeight
        }

    override suspend fun deleteAssociation(id: Long) {
        associationDao.deleteById(id)
    }

    override suspend fun deleteAll() {
        associationDao.deleteAll()
    }
}
