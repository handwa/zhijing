package com.zhijing.graph.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_content")
public class UserContent {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String content;       // 原始内容
    public String sourceApp;     // 来源应用包名
    public String keywords;      // 提取的关键词（JSON数组）
    public String entities;      // 提取的实体（JSON数组）
    public long createdAt;

    public UserContent(String content, String sourceApp) {
        this.content = content;
        this.sourceApp = sourceApp;
        this.createdAt = System.currentTimeMillis();
    }
}
