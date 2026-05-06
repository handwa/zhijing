package com.zhijing.graph.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "knowledge_entity")
public class KnowledgeEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;       // 实体名称
    public String type;       // 类型：PERSON / WORK / EVENT / CONCEPT
    public float weight;      // 权重/重要度
    public long createdAt;    // 创建时间戳
    public long lastSeenAt;   // 最近出现时间戳

    public KnowledgeEntity(String name, String type) {
        this.name = name;
        this.type = type;
        this.weight = 1.0f;
        this.createdAt = System.currentTimeMillis();
        this.lastSeenAt = System.currentTimeMillis();
    }
}
