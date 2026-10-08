package com.zhijing.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zhijing.data.repository.DefaultContentRepository
import com.zhijing.data.vector.RoomVectorSearchStore

/** 测试辅助：构建内存数据库，避免污染应用私有目录里的 zhijing.db。 */
internal object TestDatabase {

    fun createInMemory(): ZhijingDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        ZhijingDatabase::class.java
    ).allowMainThreadQueries().build()

    fun contentRepository(db: ZhijingDatabase): DefaultContentRepository =
        DefaultContentRepository(db, RoomVectorSearchStore(db.vectorDao()))

    /** 第 [index] 个分量为 1 的单位向量。 */
    fun unitVector(index: Int): FloatArray = FloatArray(DbConstants.VECTOR_DIM).also { it[index] = 1f }
}
