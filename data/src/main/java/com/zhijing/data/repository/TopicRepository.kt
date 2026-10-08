package com.zhijing.data.repository

import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.mapper.toDomain
import com.zhijing.data.mapper.toEntity
import com.zhijing.data.model.Topic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 主题门面（说明书 3.4.3，认知庭院的主题树）。
 *
 * 主题名是主键，保存即 upsert；层级由 `parentTag` 表达。
 * `contentCount` 由内容理解模块在打标签后调用 [updateContentCount] 维护。
 */
interface TopicRepository {

    fun observeTopics(): Flow<List<Topic>>

    suspend fun getTopic(tagName: String): Topic?

    /** 顶层主题，用于主题树的根节点。 */
    suspend fun getRootTopics(): List<Topic>

    suspend fun getChildTopics(parentTag: String): List<Topic>

    suspend fun save(topic: Topic)

    suspend fun updateContentCount(tagName: String, count: Int)

    suspend fun deleteTopic(tagName: String)

    suspend fun deleteAll()
}

internal class DefaultTopicRepository(
    private val db: ZhijingDatabase
) : TopicRepository {

    private val topicDao = db.topicDao()

    override fun observeTopics(): Flow<List<Topic>> =
        topicDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getTopic(tagName: String): Topic? = topicDao.findByTag(tagName)?.toDomain()

    override suspend fun getRootTopics(): List<Topic> = topicDao.findRoots().map { it.toDomain() }

    override suspend fun getChildTopics(parentTag: String): List<Topic> =
        topicDao.findChildren(parentTag).map { it.toDomain() }

    override suspend fun save(topic: Topic) {
        topicDao.upsert(topic.toEntity())
    }

    override suspend fun updateContentCount(tagName: String, count: Int) {
        topicDao.updateContentCount(tagName, count)
    }

    override suspend fun deleteTopic(tagName: String) {
        topicDao.deleteByTag(tagName)
    }

    override suspend fun deleteAll() {
        topicDao.deleteAll()
    }
}
