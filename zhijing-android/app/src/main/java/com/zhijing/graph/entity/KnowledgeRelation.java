package com.zhijing.graph.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "knowledge_relation",
    foreignKeys = {
        @ForeignKey(entity = KnowledgeEntity.class, parentColumns = "id", childColumns = "fromEntityId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = KnowledgeEntity.class, parentColumns = "id", childColumns = "toEntityId", onDelete = ForeignKey.CASCADE)
    },
    indices = {
        @Index("fromEntityId"),
        @Index("toEntityId")
    }
)
public class KnowledgeRelation {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long fromEntityId;    // 起始实体ID
    public long toEntityId;      // 目标实体ID
    public String relationType;  // 关系类型：RELATED / BELONGS_TO / SIMILAR / CONTAINS
    public float strength;       // 关联强度 0.0~1.0
    public long createdAt;

    public KnowledgeRelation(long fromEntityId, long toEntityId, String relationType, float strength) {
        this.fromEntityId = fromEntityId;
        this.toEntityId = toEntityId;
        this.relationType = relationType;
        this.strength = strength;
        this.createdAt = System.currentTimeMillis();
    }
}
