package com.zhijing.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zhijing.data.converter.EnumConverters
import com.zhijing.data.converter.ListConverters
import com.zhijing.data.converter.StringListConverters
import com.zhijing.data.entity.AssociationEntity
import com.zhijing.data.entity.ChunkEntity
import com.zhijing.data.entity.ContentEntity
import com.zhijing.data.entity.KgClaimMergeEntity
import com.zhijing.data.entity.KgClaimStateEntity
import com.zhijing.data.entity.KgConditionProbeEntity
import com.zhijing.data.entity.KgGeneratedCardEntity
import com.zhijing.data.entity.KgGeneratedGraphEntity
import com.zhijing.data.entity.KgGeneratedOutlineEntity
import com.zhijing.data.entity.KgGeneratedPrerequisiteEntity
import com.zhijing.data.entity.KgRuntimeClaimEntity
import com.zhijing.data.entity.ParseTaskEntity
import com.zhijing.data.entity.PetStatusEntity
import com.zhijing.data.entity.QaRecordEntity
import com.zhijing.data.entity.StanceCompareEntity
import com.zhijing.data.entity.TopicEntity
import com.zhijing.data.entity.VectorEntity
import com.zhijing.data.entity.WakePlanEntity
import com.zhijing.data.dao.AssociationDao
import com.zhijing.data.dao.ChunkDao
import com.zhijing.data.dao.ContentDao
import com.zhijing.data.dao.ConversationDao
import com.zhijing.data.dao.KgClaimMergeDao
import com.zhijing.data.dao.KgConditionProbeDao
import com.zhijing.data.dao.KgGeneratedGraphDao
import com.zhijing.data.dao.KgClaimStateDao
import com.zhijing.data.dao.KgRuntimeClaimDao
import com.zhijing.data.dao.MessageDao
import com.zhijing.data.dao.ParseTaskDao
import com.zhijing.data.dao.PetStatusDao
import com.zhijing.data.dao.QaRecordDao
import com.zhijing.data.dao.StanceCompareDao
import com.zhijing.data.dao.TopicDao
import com.zhijing.data.dao.VectorDao
import com.zhijing.data.dao.WakePlanDao
import com.zhijing.data.entity.ConversationEntity
import com.zhijing.data.entity.MessageEntity

/**
 * 「知境」本地数据库。
 *
 * 说明书 3.4：用 Room，文件 zhijing.db 放在应用私有目录，其他应用访问不到。
 * 聚合说明书 3.4.1~3.4.8 的 8 张表，加上为段落级 RAG 预留的 chunk_table 与 vector_table，
 * 共 10 张表。
 *
 * v3 起新增 3 张知识图谱运行时表（`kg_claim_state` / `kg_runtime_claim` /
 * `kg_claim_merge`）。注意：**出厂命题仍然只存在于 `assets/kg/graph.json`**，
 * 不进数据库——那是出版物，不是用户数据。进库的只有运行期产生的判断状态、
 * 云端增量命题与同义合并映射。详见 `docs/knowledge-graph-room.md`。
 *
 * v7 新增 `kg_condition_probe`：条件级探测记录（「条件 × 干预点」）。
 * 它比 `kg_claim_state` 细一层，用来回答「用户错在哪条机制上」。
 *
 * ⚠️ **v6 → v7 会清空全部用户数据**：本库用 `fallbackToDestructiveMigration()`，
 * 加表没有补 Migration。仓库内没有真实用户数据，开发期接受这个代价；
 * 队友手机上如果已有真实收藏，升级前先导出。
 *
 * `exportSchema = false`：本阶段不引入数据库迁移，先不落地 schema 目录；
 * 后续加 Migration 时再打开并配置 `room.schemaLocation`。
 *
 * 该类型是 `internal`：模块外只能通过 [ZhiJingData] 拿到 Repository。
 */
@Database(
    entities = [
        ContentEntity::class,
        ChunkEntity::class,
        VectorEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        AssociationEntity::class,
        TopicEntity::class,
        StanceCompareEntity::class,
        WakePlanEntity::class,
        QaRecordEntity::class,
        PetStatusEntity::class,
        ParseTaskEntity::class,
        KgClaimStateEntity::class,
        KgConditionProbeEntity::class,
        KgRuntimeClaimEntity::class,
        KgClaimMergeEntity::class,
        KgGeneratedGraphEntity::class,
        KgGeneratedOutlineEntity::class,
        KgGeneratedCardEntity::class,
        KgGeneratedPrerequisiteEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(EnumConverters::class, ListConverters::class, StringListConverters::class)
internal abstract class ZhijingDatabase : RoomDatabase() {

    abstract fun contentDao(): ContentDao
    abstract fun chunkDao(): ChunkDao
    abstract fun messageDao(): MessageDao
    abstract fun  conversationDao(): ConversationDao
    abstract fun vectorDao(): VectorDao
    abstract fun associationDao(): AssociationDao
    abstract fun topicDao(): TopicDao
    abstract fun stanceCompareDao(): StanceCompareDao
    abstract fun wakePlanDao(): WakePlanDao
    abstract fun qaRecordDao(): QaRecordDao
    abstract fun petStatusDao(): PetStatusDao
    abstract fun parseTaskDao(): ParseTaskDao

    /** 知识图谱：命题探测状态（回写摘要）。 */
    abstract fun kgClaimStateDao(): KgClaimStateDao

    /**
     * 知识图谱：条件级探测记录（「预测 → 揭示」的事实）。
     *
     * 与 `kg_claim_state` 是两个粒度：前者到命题，后者到「条件 × 干预点」。
     */
    abstract fun kgConditionProbeDao(): KgConditionProbeDao

    /** 知识图谱：云端 / 用户产生的增量命题。 */
    abstract fun kgRuntimeClaimDao(): KgRuntimeClaimDao

    /** 知识图谱：同义合并映射（解决云端重新生成导致的 ID 漂移）。 */
    abstract fun kgClaimMergeDao(): KgClaimMergeDao

    /** 知识图谱：用户输入主题后由模型生成的图谱（每个主题一份）。 */
    abstract fun kgGeneratedGraphDao(): KgGeneratedGraphDao

    companion object {
        @Volatile
        private var instance: ZhijingDatabase? = null

        /** 进程内单例，使用 applicationContext 避免持有 Activity。 */
        fun getInstance(context: Context): ZhijingDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ZhijingDatabase::class.java,
                    DbConstants.DATABASE_NAME
                )   .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
