package com.zhijing.graph.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.zhijing.graph.entity.UserContent;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class UserContentDao_Impl implements UserContentDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<UserContent> __insertionAdapterOfUserContent;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public UserContentDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfUserContent = new EntityInsertionAdapter<UserContent>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `user_content` (`id`,`content`,`sourceApp`,`keywords`,`entities`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final UserContent entity) {
        statement.bindLong(1, entity.id);
        if (entity.content == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.content);
        }
        if (entity.sourceApp == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.sourceApp);
        }
        if (entity.keywords == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.keywords);
        }
        if (entity.entities == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.entities);
        }
        statement.bindLong(6, entity.createdAt);
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM user_content";
        return _query;
      }
    };
  }

  @Override
  public long insert(final UserContent content) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfUserContent.insertAndReturnId(content);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteAll() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteAll.release(_stmt);
    }
  }

  @Override
  public List<UserContent> getRecent(final int limit) {
    final String _sql = "SELECT * FROM user_content ORDER BY createdAt DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
      final int _cursorIndexOfSourceApp = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceApp");
      final int _cursorIndexOfKeywords = CursorUtil.getColumnIndexOrThrow(_cursor, "keywords");
      final int _cursorIndexOfEntities = CursorUtil.getColumnIndexOrThrow(_cursor, "entities");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final List<UserContent> _result = new ArrayList<UserContent>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final UserContent _item;
        final String _tmpContent;
        if (_cursor.isNull(_cursorIndexOfContent)) {
          _tmpContent = null;
        } else {
          _tmpContent = _cursor.getString(_cursorIndexOfContent);
        }
        final String _tmpSourceApp;
        if (_cursor.isNull(_cursorIndexOfSourceApp)) {
          _tmpSourceApp = null;
        } else {
          _tmpSourceApp = _cursor.getString(_cursorIndexOfSourceApp);
        }
        _item = new UserContent(_tmpContent,_tmpSourceApp);
        _item.id = _cursor.getLong(_cursorIndexOfId);
        if (_cursor.isNull(_cursorIndexOfKeywords)) {
          _item.keywords = null;
        } else {
          _item.keywords = _cursor.getString(_cursorIndexOfKeywords);
        }
        if (_cursor.isNull(_cursorIndexOfEntities)) {
          _item.entities = null;
        } else {
          _item.entities = _cursor.getString(_cursorIndexOfEntities);
        }
        _item.createdAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<UserContent> getSince(final long since) {
    final String _sql = "SELECT * FROM user_content WHERE createdAt >= ? ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
      final int _cursorIndexOfSourceApp = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceApp");
      final int _cursorIndexOfKeywords = CursorUtil.getColumnIndexOrThrow(_cursor, "keywords");
      final int _cursorIndexOfEntities = CursorUtil.getColumnIndexOrThrow(_cursor, "entities");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final List<UserContent> _result = new ArrayList<UserContent>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final UserContent _item;
        final String _tmpContent;
        if (_cursor.isNull(_cursorIndexOfContent)) {
          _tmpContent = null;
        } else {
          _tmpContent = _cursor.getString(_cursorIndexOfContent);
        }
        final String _tmpSourceApp;
        if (_cursor.isNull(_cursorIndexOfSourceApp)) {
          _tmpSourceApp = null;
        } else {
          _tmpSourceApp = _cursor.getString(_cursorIndexOfSourceApp);
        }
        _item = new UserContent(_tmpContent,_tmpSourceApp);
        _item.id = _cursor.getLong(_cursorIndexOfId);
        if (_cursor.isNull(_cursorIndexOfKeywords)) {
          _item.keywords = null;
        } else {
          _item.keywords = _cursor.getString(_cursorIndexOfKeywords);
        }
        if (_cursor.isNull(_cursorIndexOfEntities)) {
          _item.entities = null;
        } else {
          _item.entities = _cursor.getString(_cursorIndexOfEntities);
        }
        _item.createdAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
