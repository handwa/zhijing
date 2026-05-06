package com.zhijing.graph.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.zhijing.graph.entity.KnowledgeRelation;

import java.util.List;

@Dao
public interface KnowledgeRelationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(KnowledgeRelation relation);

    @Query("SELECT * FROM knowledge_relation WHERE fromEntityId = :entityId ORDER BY strength DESC")
    List<KnowledgeRelation> getRelationsFrom(long entityId);

    @Query("SELECT * FROM knowledge_relation WHERE fromEntityId = :entityId OR toEntityId = :entityId ORDER BY strength DESC")
    List<KnowledgeRelation> getAllRelations(long entityId);

    @Query("SELECT * FROM knowledge_relation WHERE fromEntityId = :from AND toEntityId = :to LIMIT 1")
    KnowledgeRelation findRelation(long from, long to);

    @Query("UPDATE knowledge_relation SET strength = MIN(1.0, strength + :delta) WHERE fromEntityId = :from AND toEntityId = :to")
    void strengthenRelation(long from, long to, float delta);

    @Query("DELETE FROM knowledge_relation")
    void deleteAll();
}
