package com.zhijing.graph;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.zhijing.graph.dao.KnowledgeEntityDao;
import com.zhijing.graph.dao.KnowledgeRelationDao;
import com.zhijing.graph.dao.UserContentDao;
import com.zhijing.graph.entity.KnowledgeEntity;
import com.zhijing.graph.entity.KnowledgeRelation;
import com.zhijing.graph.entity.UserContent;

@Database(
    entities = {KnowledgeEntity.class, KnowledgeRelation.class, UserContent.class},
    version = 1,
    exportSchema = false
)
public abstract class ZhijingDatabase extends RoomDatabase {

    private static volatile ZhijingDatabase INSTANCE;

    public abstract KnowledgeEntityDao entityDao();
    public abstract KnowledgeRelationDao relationDao();
    public abstract UserContentDao contentDao();

    public static ZhijingDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ZhijingDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            ZhijingDatabase.class,
                            "zhijing_db"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
