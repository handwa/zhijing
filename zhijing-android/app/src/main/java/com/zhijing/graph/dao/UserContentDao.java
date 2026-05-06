package com.zhijing.graph.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.zhijing.graph.entity.UserContent;

import java.util.List;

@Dao
public interface UserContentDao {

    @Insert
    long insert(UserContent content);

    @Query("SELECT * FROM user_content ORDER BY createdAt DESC LIMIT :limit")
    List<UserContent> getRecent(int limit);

    @Query("SELECT * FROM user_content WHERE createdAt >= :since ORDER BY createdAt DESC")
    List<UserContent> getSince(long since);

    @Query("DELETE FROM user_content")
    void deleteAll();
}
