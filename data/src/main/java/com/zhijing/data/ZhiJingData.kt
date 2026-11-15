package com.zhijing.data

import android.content.Context
import com.zhijing.data.repository.AssociationRepository
import com.zhijing.data.repository.ConditionProbeRepository
import com.zhijing.data.repository.ContentRepository
import com.zhijing.data.repository.ConversationRepository
import com.zhijing.data.repository.DefaultAssociationRepository
import com.zhijing.data.repository.DefaultConditionProbeRepository
import com.zhijing.data.repository.DefaultContentRepository
import com.zhijing.data.repository.DefaultConversationRepository
import com.zhijing.data.repository.DefaultGeneratedGraphRepository
import com.zhijing.data.repository.DefaultKgClaimRepository
import com.zhijing.data.repository.DefaultKgStateRepository
import com.zhijing.data.repository.DefaultParseTaskRepository
import com.zhijing.data.repository.DefaultPetStatusRepository
import com.zhijing.data.repository.DefaultQaRecordRepository
import com.zhijing.data.repository.DefaultStanceCompareRepository
import com.zhijing.data.repository.DefaultTopicRepository
import com.zhijing.data.repository.DefaultWakePlanRepository
import com.zhijing.data.repository.GeneratedGraphRepository
import com.zhijing.data.repository.KgClaimRepository
import com.zhijing.data.repository.KgStateRepository
import com.zhijing.data.repository.ParseTaskRepository
import com.zhijing.data.repository.PetStatusRepository
import com.zhijing.data.repository.QaRecordRepository
import com.zhijing.data.repository.StanceCompareRepository
import com.zhijing.data.repository.TopicRepository
import com.zhijing.data.repository.WakePlanRepository
import com.zhijing.data.vector.RoomVectorSearchStore

/**
 * `:data` 模块的对外入口。
 *
 * 说明书 7.2 要求「数据入口、内容理解、检索、界面展示分开」，因此模块外**只有**这一条路径：
 *
 * ```kotlin
 * val data = ZhiJingData.get(context)
 * val id = data.contents.saveImportedContent(rawText = "...", source = "com.ss.android.ugc.aweme", importChannel = ImportChannel.SHARE)
 * data.contents.observeRecentContents().collect { ... }
 * ```
 *
 * Room 实体、DAO、数据库类型全部是 `internal`，不会泄漏到调用方。
 */
class ZhiJingData internal constructor(private val db: ZhijingDatabase) {
    /**消息对话记录**/
    val conversations: ConversationRepository= DefaultConversationRepository(db)
    /** 收藏内容、分段与向量。 */
    val contents: ContentRepository = DefaultContentRepository(db, RoomVectorSearchStore(db.vectorDao()))

    /** 关联卡片（说明书 4.2.4）。 */
    val associations: AssociationRepository = DefaultAssociationRepository(db)

    /** 主题树（说明书 3.4.3）。 */
    val topics: TopicRepository = DefaultTopicRepository(db)

    /** 观点对照（说明书 4.2.5）。 */
    val stanceCompares: StanceCompareRepository = DefaultStanceCompareRepository(db)

    /** 记忆唤醒（说明书 4.2.6）。 */
    val wakePlans: WakePlanRepository = DefaultWakePlanRepository(db)

    /** 问答记录（说明书 3.4.6）。 */
    val qaRecords: QaRecordRepository = DefaultQaRecordRepository(db)

    /** 养成系动物（说明书 3.4.7）。 */
    val petStatus: PetStatusRepository = DefaultPetStatusRepository(db)

    /** 解析队列（说明书 4.2.2）。 */
    val parseTasks: ParseTaskRepository = DefaultParseTaskRepository(db)

    /**
     * 知识图谱：命题探测状态（深度侧 → 广度侧的回写）。
     *
     * 出厂命题在 `assets/kg/graph.json`，本仓库只存状态，回写走派生。
     */
    val kgStates: KgStateRepository = DefaultKgStateRepository(db)

    /**
     * 知识图谱：条件级探测记录（哪条机制的哪一问答错、答错几次）。
     *
     * 与 [kgStates] 是两个粒度：本仓库只回答「错在哪」，不参与掌握度收敛；
     * 广度侧按 `claimIds` 现算摘要，不把状态抄进图谱数据。
     */
    val kgConditionProbes: ConditionProbeRepository = DefaultConditionProbeRepository(db)

    /**
     * 知识图谱：云端 / 用户产生的增量命题与同义合并映射。
     *
     * 未确认的候选不会出现在任何广度视图里（见 [KgClaimRepository.observeConfirmed]）。
     */
    val kgClaims: KgClaimRepository = DefaultKgClaimRepository(db)

    /**
     * 知识图谱：用户输入主题后、由模型**生成**的图谱（每个主题一份）。
     *
     * 与出厂图谱（`assets/kg/graph.json`，只读）分开存：
     * 生成图谱是用户数据，可增删、可重建。
     * 渲染时 `GraphAssembler` 把两者合并。
     */
    val kgGenerated: GeneratedGraphRepository = DefaultGeneratedGraphRepository(db)

    /** 关闭数据库并释放单例。之后再次 [get] 会重新打开。 */
    fun close() {
        synchronized(Companion) {
            if (instance === this) instance = null
        }
        db.close()
    }

    companion object {
        @Volatile
        private var instance: ZhiJingData? = null

        /** 进程内单例；数据库文件为应用私有目录下的 zhijing.db。 */
        fun get(context: Context): ZhiJingData =
            instance ?: synchronized(this) {
                instance ?: ZhiJingData(ZhijingDatabase.getInstance(context.applicationContext))
                    .also { instance = it }
            }
    }
}
