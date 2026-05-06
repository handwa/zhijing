package com.zhijing.graph.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.zhijing.graph.entity.KnowledgeEntity;

import java.util.List;

@Dao
public interface KnowledgeEntityDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(KnowledgeEntity entity);

    @Update
    void update(KnowledgeEntity entity);

    @Query("SELECT * FROM knowledge_entity WHERE name = :name LIMIT 1")
    KnowledgeEntity findByName(String name);

    @Query("SELECT * FROM knowledge_entity ORDER BY weight DESC LIMIT :limit")
    List<KnowledgeEntity> getTopEntities(int limit);

    @Query("SELECT * FROM knowledge_entity WHERE type = :type ORDER BY weight DESC")
    List<KnowledgeEntity> getEntitiesByType(String type);

    @Query("UPDATE knowledge_entity SET weight = weight + :delta, lastSeenAt = :time WHERE id = :id")
    void updateWeight(long id, float delta, long time);

    @Query("SELECT * FROM knowledge_entity")
    List<KnowledgeEntity> getAll();

    @Query("DELETE FROM knowledge_entity")
    void deleteAll();
}
