package com.zhijing.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zhijing.data.converter.EnumConverters
import com.zhijing.data.converter.ListConverters
import com.zhijing.data.entity.AssociationEntity
import com.zhijing.data.entity.ChunkEntity
import com.zhijing.data.entity.ContentEntity
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
        ParseTaskEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(EnumConverters::class, ListConverters::class)
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
