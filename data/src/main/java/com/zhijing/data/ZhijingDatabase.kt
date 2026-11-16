package com.zhijing.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.zhijing.data.entity.KgGeneratedClaimEntity
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
 * v8 新增 `kg_generated_claim`：生成图谱自己的命题，使生成出来的卡片也能下钻。
 *
 * **v6 起补了真实迁移**（`MIGRATION_6_7` / `MIGRATION_7_8`）：从 v6 或 v7 升级
 * 不再清空用户数据。`fallbackToDestructiveMigration()` 只对 v6 以前的历史版本生效，
 * 那些版本没有迁移路径，也没有真实用户。
 *
 * `exportSchema = true` + `room.schemaLocation = data/schemas`：迁移测试要拿历史版本的
 * **真实建表语句**重建旧库，手抄 DDL 迟早会和实体注解漂移。
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
        KgGeneratedPrerequisiteEntity::class,
        KgGeneratedClaimEntity::class
    ],
    version = 8,
    exportSchema = true
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

        /**
         * v6 → v7：补上 `kg_condition_probe`（条件级探测记录）。
         *
         * 建表语句抄自 `data/schemas/com.zhijing.data.ZhijingDatabase/7.json` 的 `createSql`，
         * 不是手写的 —— 手抄 DDL 会和实体注解漂移，而 Room 升级后会校验整个 schema，
         * 差一个 DEFAULT 就直接抛异常。`ZhijingMigrationTest` 会真的从 v6 库升上来验证。
         */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `kg_condition_probe` (" +
                        "`triple_id` TEXT NOT NULL, " +
                        "`intervention_id` TEXT NOT NULL, " +
                        "`claim_id` TEXT NOT NULL, " +
                        "`predicted_option` TEXT NOT NULL, " +
                        "`previous_predicted_option` TEXT, " +
                        "`correct_option` TEXT NOT NULL, " +
                        "`matched` INTEGER NOT NULL DEFAULT 0, " +
                        "`attempts` INTEGER NOT NULL DEFAULT 0, " +
                        "`wrong_attempts` INTEGER NOT NULL DEFAULT 0, " +
                        "`last_probed_at` INTEGER, " +
                        "`misconception` TEXT, " +
                        "PRIMARY KEY(`triple_id`, `intervention_id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_kg_condition_probe_claim_id` " +
                        "ON `kg_condition_probe` (`claim_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_kg_condition_probe_last_probed_at` " +
                        "ON `kg_condition_probe` (`last_probed_at`)"
                )
            }
        }

        /**
         * v7 → v8：补上 `kg_generated_claim`（生成图谱自己的命题）。
         *
         * 为什么单独一张表：生成命题跟着主题一起生、一起删，不进用户的命题审核队列。
         */
        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `kg_generated_claim` (" +
                        "`topic_id` TEXT NOT NULL, " +
                        "`claim_id` TEXT NOT NULL, " +
                        "`card_id` TEXT NOT NULL, " +
                        "`outline_path` TEXT NOT NULL, " +
                        "`statement` TEXT NOT NULL, " +
                        "`expected` INTEGER NOT NULL, " +
                        "`conditions` TEXT NOT NULL, " +
                        "`explanation` TEXT NOT NULL, " +
                        "`misconception` TEXT NOT NULL, " +
                        "`evidence` TEXT NOT NULL, " +
                        "`order_index` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`topic_id`, `claim_id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_kg_generated_claim_topic_id` " +
                        "ON `kg_generated_claim` (`topic_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_kg_generated_claim_topic_id_card_id` " +
                        "ON `kg_generated_claim` (`topic_id`, `card_id`)"
                )
            }
        }

        /** 全部迁移。测试直接引用它跑真实升级。 */
        internal val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_6_7, MIGRATION_7_8)

        /** 进程内单例，使用 applicationContext 避免持有 Activity。 */
        fun getInstance(context: Context): ZhijingDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ZhijingDatabase::class.java,
                    DbConstants.DATABASE_NAME
                )
                    .addMigrations(*MIGRATIONS)
                    // 仅作为兜底：v6 之前的历史版本没有迁移路径。
                    // v6 → v7 → v8 都走真实迁移，不再清库。
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
