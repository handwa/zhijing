package com.zhijing.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.zhijing.data.entity.TopicEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TopicDao_Impl implements TopicDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TopicEntity> __insertionAdapterOfTopicEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateContentCount;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByTag;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public TopicDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTopicEntity = new EntityInsertionAdapter<TopicEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `topic_table` (`tag_name`,`parent_tag`,`content_count`,`color`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TopicEntity entity) {
        statement.bindString(1, entity.getTagName());
        if (entity.getParentTag() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getParentTag());
        }
        statement.bindLong(3, entity.getContentCount());
        statement.bindLong(4, entity.getColor());
      }
    };
    this.__preparedStmtOfUpdateContentCount = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE topic_table SET content_count = ? WHERE tag_name = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteByTag = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM topic_table WHERE tag_name = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM topic_table";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final TopicEntity entity, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTopicEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateContentCount(final String tagName, final int count,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateContentCount.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, count);
        _argIndex = 2;
        _stmt.bindString(_argIndex, tagName);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateContentCount.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteByTag(final String tagName, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByTag.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, tagName);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteByTag.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object findByTag(final String tagName,
      final Continuation<? super TopicEntity> $completion) {
    final String _sql = "SELECT * FROM topic_table WHERE tag_name = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, tagName);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<TopicEntity>() {
      @Override
      @Nullable
      public TopicEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTagName = CursorUtil.getColumnIndexOrThrow(_cursor, "tag_name");
          final int _cursorIndexOfParentTag = CursorUtil.getColumnIndexOrThrow(_cursor, "parent_tag");
          final int _cursorIndexOfContentCount = CursorUtil.getColumnIndexOrThrow(_cursor, "content_count");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final TopicEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpTagName;
            _tmpTagName = _cursor.getString(_cursorIndexOfTagName);
            final String _tmpParentTag;
            if (_cursor.isNull(_cursorIndexOfParentTag)) {
              _tmpParentTag = null;
            } else {
              _tmpParentTag = _cursor.getString(_cursorIndexOfParentTag);
            }
            final int _tmpContentCount;
            _tmpContentCount = _cursor.getInt(_cursorIndexOfContentCount);
            final int _tmpColor;
            _tmpColor = _cursor.getInt(_cursorIndexOfColor);
            _result = new TopicEntity(_tmpTagName,_tmpParentTag,_tmpContentCount,_tmpColor);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findRoots(final Continuation<? super List<TopicEntity>> $completion) {
    final String _sql = "SELECT * FROM topic_table WHERE parent_tag IS NULL ORDER BY tag_name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TopicEntity>>() {
      @Override
      @NonNull
      public List<TopicEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTagName = CursorUtil.getColumnIndexOrThrow(_cursor, "tag_name");
          final int _cursorIndexOfParentTag = CursorUtil.getColumnIndexOrThrow(_cursor, "parent_tag");
          final int _cursorIndexOfContentCount = CursorUtil.getColumnIndexOrThrow(_cursor, "content_count");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final List<TopicEntity> _result = new ArrayList<TopicEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TopicEntity _item;
            final String _tmpTagName;
            _tmpTagName = _cursor.getString(_cursorIndexOfTagName);
            final String _tmpParentTag;
            if (_cursor.isNull(_cursorIndexOfParentTag)) {
              _tmpParentTag = null;
            } else {
              _tmpParentTag = _cursor.getString(_cursorIndexOfParentTag);
            }
            final int _tmpContentCount;
            _tmpContentCount = _cursor.getInt(_cursorIndexOfContentCount);
            final int _tmpColor;
            _tmpColor = _cursor.getInt(_cursorIndexOfColor);
            _item = new TopicEntity(_tmpTagName,_tmpParentTag,_tmpContentCount,_tmpColor);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findChildren(final String parentTag,
      final Continuation<? super List<TopicEntity>> $completion) {
    final String _sql = "SELECT * FROM topic_table WHERE parent_tag = ? ORDER BY tag_name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, parentTag);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TopicEntity>>() {
      @Override
      @NonNull
      public List<TopicEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTagName = CursorUtil.getColumnIndexOrThrow(_cursor, "tag_name");
          final int _cursorIndexOfParentTag = CursorUtil.getColumnIndexOrThrow(_cursor, "parent_tag");
          final int _cursorIndexOfContentCount = CursorUtil.getColumnIndexOrThrow(_cursor, "content_count");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final List<TopicEntity> _result = new ArrayList<TopicEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TopicEntity _item;
            final String _tmpTagName;
            _tmpTagName = _cursor.getString(_cursorIndexOfTagName);
            final String _tmpParentTag;
            if (_cursor.isNull(_cursorIndexOfParentTag)) {
              _tmpParentTag = null;
            } else {
              _tmpParentTag = _cursor.getString(_cursorIndexOfParentTag);
            }
            final int _tmpContentCount;
            _tmpContentCount = _cursor.getInt(_cursorIndexOfContentCount);
            final int _tmpColor;
            _tmpColor = _cursor.getInt(_cursorIndexOfColor);
            _item = new TopicEntity(_tmpTagName,_tmpParentTag,_tmpContentCount,_tmpColor);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<TopicEntity>> observeAll() {
    final String _sql = "SELECT * FROM topic_table ORDER BY content_count DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"topic_table"}, new Callable<List<TopicEntity>>() {
      @Override
      @NonNull
      public List<TopicEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTagName = CursorUtil.getColumnIndexOrThrow(_cursor, "tag_name");
          final int _cursorIndexOfParentTag = CursorUtil.getColumnIndexOrThrow(_cursor, "parent_tag");
          final int _cursorIndexOfContentCount = CursorUtil.getColumnIndexOrThrow(_cursor, "content_count");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final List<TopicEntity> _result = new ArrayList<TopicEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TopicEntity _item;
            final String _tmpTagName;
            _tmpTagName = _cursor.getString(_cursorIndexOfTagName);
            final String _tmpParentTag;
            if (_cursor.isNull(_cursorIndexOfParentTag)) {
              _tmpParentTag = null;
            } else {
              _tmpParentTag = _cursor.getString(_cursorIndexOfParentTag);
            }
            final int _tmpContentCount;
            _tmpContentCount = _cursor.getInt(_cursorIndexOfContentCount);
            final int _tmpColor;
            _tmpColor = _cursor.getInt(_cursorIndexOfColor);
            _item = new TopicEntity(_tmpTagName,_tmpParentTag,_tmpContentCount,_tmpColor);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
