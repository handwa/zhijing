package com.zhijing.data.repository

import com.zhijing.data.ZhijingDatabase
import com.zhijing.data.dao.KgGeneratedGraphDao
import com.zhijing.data.entity.KgGeneratedCardEntity
import com.zhijing.data.entity.KgGeneratedClaimEntity
import com.zhijing.data.entity.KgGeneratedGraphEntity
import com.zhijing.data.entity.KgGeneratedOutlineEntity
import com.zhijing.data.entity.KgGeneratedPrerequisiteEntity
import com.zhijing.data.model.GeneratedCard
import com.zhijing.data.model.GeneratedClaim
import com.zhijing.data.model.GeneratedGraph
import com.zhijing.data.model.GeneratedOutlineNode
import com.zhijing.data.model.GeneratedPrerequisite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 生成图谱门面：用户输入一个主题、由模型生成的那份图谱。
 *
 * 与出厂图谱（`assets/kg/graph.json`）的关系：
 *
 * ```
 * GraphAssembler 读取时 =   出厂图谱（只读）
 *                         + 本仓库（可写，每个主题一份）
 * ```
 *
 * 一份生成图谱 = 元信息 + 大纲节点 + 卡片，三者**整份替换**（见 DAO 的事务方法）。
 */
interface GeneratedGraphRepository {

    /** 列出所有已生成的主题（按时间倒序）。 */
    fun observeGraphs(): Flow<List<GeneratedGraph>>

    suspend fun listGraphs(): List<GeneratedGraph>

    suspend fun getGraph(topicId: String): GeneratedGraph?

    /**
     * 整份保存一个主题的图谱。
     *
     * 同一 topicId 重复调用会**覆盖**旧数据（不保留历史版本）。
     */
    suspend fun save(
        meta: GeneratedGraph,
        outline: List<GeneratedOutlineNode>,
        cards: List<GeneratedCard>,
        prerequisites: List<GeneratedPrerequisite> = emptyList(),
        /** 该主题的命题；与卡片同进同出，重复生成整份覆盖。 */
        claims: List<GeneratedClaim> = emptyList()
    )

    suspend fun outlineOf(topicId: String): List<GeneratedOutlineNode>

    suspend fun cardsOf(topicId: String): List<GeneratedCard>

    /** 某主题的卡片前置关系。 */
    suspend fun prerequisitesOf(topicId: String): List<GeneratedPrerequisite>

    /** 全部生成图谱的前置关系，供合并渲染。 */
    suspend fun allPrerequisites(): List<GeneratedPrerequisite>

    suspend fun deleteGraph(topicId: String)

    /** 全部生成图谱的卡片，供合并渲染。 */
    suspend fun allCards(): List<GeneratedCard>

    /** 全部生成图谱的大纲节点，供合并渲染。 */
    suspend fun allOutline(): List<GeneratedOutlineNode>

    /** 某主题的命题（下钻用）。 */
    suspend fun claimsOf(topicId: String): List<GeneratedClaim>

    /** 全部生成图谱的命题，供合并渲染时挂到卡片上。 */
    suspend fun allClaims(): List<GeneratedClaim>
}

internal class DefaultGeneratedGraphRepository(
    private val db: ZhijingDatabase
) : GeneratedGraphRepository {

    private val dao: KgGeneratedGraphDao = db.kgGeneratedGraphDao()

    override fun observeGraphs(): Flow<List<GeneratedGraph>> =
        dao.observeGraphs().map { rows -> rows.map { it.toDomain() } }

    override suspend fun listGraphs(): List<GeneratedGraph> =
        dao.listGraphs().map { it.toDomain() }

    override suspend fun getGraph(topicId: String): GeneratedGraph? =
        dao.findGraph(topicId)?.toDomain()

    override suspend fun save(
        meta: GeneratedGraph,
        outline: List<GeneratedOutlineNode>,
        cards: List<GeneratedCard>,
        prerequisites: List<GeneratedPrerequisite>,
        claims: List<GeneratedClaim>
    ) {
        dao.replaceGraph(
            meta = meta.toEntity(),
            outline = outline.map { it.toEntity() },
            cards = cards.map { it.toEntity() },
            prerequisites = prerequisites.map { it.toEntity() },
            claims = claims.map { it.toEntity() }
        )
    }

    override suspend fun outlineOf(topicId: String): List<GeneratedOutlineNode> =
        dao.outlineOf(topicId).map { it.toDomain() }

    override suspend fun cardsOf(topicId: String): List<GeneratedCard> =
        dao.cardsOf(topicId).map { it.toDomain() }

    override suspend fun prerequisitesOf(topicId: String): List<GeneratedPrerequisite> =
        dao.prerequisitesOf(topicId).map { it.toDomain() }

    override suspend fun allPrerequisites(): List<GeneratedPrerequisite> =
        dao.allPrerequisites().map { it.toDomain() }

    override suspend fun deleteGraph(topicId: String) {
        dao.deleteWholeGraph(topicId)
    }

    override suspend fun allCards(): List<GeneratedCard> =
        dao.allCards().map { it.toDomain() }

    override suspend fun allOutline(): List<GeneratedOutlineNode> =
        dao.allOutline().map { it.toDomain() }

    override suspend fun claimsOf(topicId: String): List<GeneratedClaim> =
        dao.claimsOf(topicId).map { it.toDomain() }

    override suspend fun allClaims(): List<GeneratedClaim> =
        dao.allClaims().map { it.toDomain() }
}

/* ------------------------------------------------------------ 映射 */

private fun KgGeneratedGraphEntity.toDomain(): GeneratedGraph = GeneratedGraph(
    topicId = topicId,
    title = title,
    createdTime = createdTime,
    source = source,
    providerId = providerId,
    blockCount = blockCount,
    warnings = parseStringList(warnings),
    verified = verified
)

private fun GeneratedGraph.toEntity(): KgGeneratedGraphEntity = KgGeneratedGraphEntity(
    topicId = topicId,
    title = title,
    createdTime = createdTime,
    source = source,
    providerId = providerId,
    blockCount = blockCount,
    warnings = encodeStringList(warnings),
    verified = verified
)

private fun KgGeneratedOutlineEntity.toDomain(): GeneratedOutlineNode = GeneratedOutlineNode(
    topicId = topicId,
    path = path,
    parentPath = parentPath,
    title = title,
    level = level,
    order = order
)

private fun GeneratedOutlineNode.toEntity(): KgGeneratedOutlineEntity = KgGeneratedOutlineEntity(
    topicId = topicId,
    path = path,
    parentPath = parentPath,
    title = title,
    level = level,
    order = order
)

private fun KgGeneratedCardEntity.toDomain(): GeneratedCard = GeneratedCard(
    topicId = topicId,
    cardId = cardId,
    outlinePath = outlinePath,
    title = title,
    summary = summary,
    body = body,
    order = order
)

private fun GeneratedCard.toEntity(): KgGeneratedCardEntity = KgGeneratedCardEntity(
    topicId = topicId,
    cardId = cardId,
    outlinePath = outlinePath,
    title = title,
    summary = summary,
    body = body,
    order = order
)

private fun KgGeneratedPrerequisiteEntity.toDomain(): GeneratedPrerequisite = GeneratedPrerequisite(
    topicId = topicId,
    fromCardId = fromCardId,
    toCardId = toCardId,
    kind = kind,
    why = why
)

private fun KgGeneratedClaimEntity.toDomain(): GeneratedClaim = GeneratedClaim(
    topicId = topicId,
    claimId = claimId,
    cardId = cardId,
    outlinePath = outlinePath,
    statement = statement,
    expected = expected,
    conditions = conditions,
    explanation = explanation,
    misconception = misconception,
    evidence = evidence,
    order = order
)

private fun GeneratedClaim.toEntity(): KgGeneratedClaimEntity = KgGeneratedClaimEntity(
    topicId = topicId,
    claimId = claimId,
    cardId = cardId,
    outlinePath = outlinePath,
    statement = statement,
    expected = expected,
    conditions = conditions,
    explanation = explanation,
    misconception = misconception,
    evidence = evidence,
    order = order
)

private fun GeneratedPrerequisite.toEntity(): KgGeneratedPrerequisiteEntity =
    KgGeneratedPrerequisiteEntity(
        topicId = topicId,
        fromCardId = fromCardId,
        toCardId = toCardId,
        kind = kind,
        why = why
    )

/**
 * 列表列编解码。
 *
 * 只用于 `warnings` 这一个短字符串列表；格式非法时降级为空列表，
 * 不让一列脏数据阻断整张图谱的读取。
 */
private fun encodeStringList(values: List<String>): String =
    values.joinToString(separator = ",", prefix = "[", postfix = "]") { entry ->
        buildString {
            append('"')
            for (ch in entry) {
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    else -> append(ch)
                }
            }
            append('"')
        }
    }

private fun parseStringList(raw: String): List<String> {
    val text = raw.trim()
    if (text.length < 2 || text.first() != '[' || text.last() != ']') return emptyList()
    val body = text.substring(1, text.length - 1).trim()
    if (body.isEmpty()) return emptyList()
    val out = ArrayList<String>()
    val current = StringBuilder()
    var inString = false
    var escaping = false
    for (ch in body) {
        when {
            escaping -> {
                current.append(if (ch == 'n') '\n' else ch)
                escaping = false
            }

            ch == '\\' && inString -> escaping = true
            ch == '"' -> {
                if (inString) {
                    out.add(current.toString())
                    current.setLength(0)
                }
                inString = !inString
            }

            inString -> current.append(ch)
            ch == ',' || ch.isWhitespace() -> Unit
            else -> return emptyList()
        }
    }
    return out
}
