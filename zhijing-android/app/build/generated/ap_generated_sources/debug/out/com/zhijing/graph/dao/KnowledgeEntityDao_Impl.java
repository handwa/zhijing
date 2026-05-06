package com.zhijing.graph.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.zhijing.graph.entity.KnowledgeEntity;
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
public final class KnowledgeEntityDao_Impl implements KnowledgeEntityDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<KnowledgeEntity> __insertionAdapterOfKnowledgeEntity;

  private final EntityDeletionOrUpdateAdapter<KnowledgeEntity> __updateAdapterOfKnowledgeEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateWeight;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public KnowledgeEntityDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfKnowledgeEntity = new EntityInsertionAdapter<KnowledgeEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `knowledge_entity` (`id`,`name`,`type`,`weight`,`createdAt`,`lastSeenAt`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final KnowledgeEntity entity) {
        statement.bindLong(1, entity.id);
        if (entity.name == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.name);
        }
        if (entity.type == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.type);
        }
        statement.bindDouble(4, entity.weight);
        statement.bindLong(5, entity.createdAt);
        statement.bindLong(6, entity.lastSeenAt);
      }
    };
    this.__updateAdapterOfKnowledgeEntity = new EntityDeletionOrUpdateAdapter<KnowledgeEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `knowledge_entity` SET `id` = ?,`name` = ?,`type` = ?,`weight` = ?,`createdAt` = ?,`lastSeenAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final KnowledgeEntity entity) {
        statement.bindLong(1, entity.id);
        if (entity.name == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.name);
        }
        if (entity.type == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.type);
        }
        statement.bindDouble(4, entity.weight);
        statement.bindLong(5, entity.createdAt);
        statement.bindLong(6, entity.lastSeenAt);
        statement.bindLong(7, entity.id);
      }
    };
    this.__preparedStmtOfUpdateWeight = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE knowledge_entity SET weight = weight + ?, lastSeenAt = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM knowledge_entity";
        return _query;
      }
    };
  }

  @Override
  public long insert(final KnowledgeEntity entity) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfKnowledgeEntity.insertAndReturnId(entity);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void update(final KnowledgeEntity entity) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfKnowledgeEntity.handle(entity);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void updateWeight(final long id, final float delta, final long time) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateWeight.acquire();
    int _argIndex = 1;
    _stmt.bindDouble(_argIndex, delta);
    _argIndex = 2;
    _stmt.bindLong(_argIndex, time);
    _argIndex = 3;
    _stmt.bindLong(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfUpdateWeight.release(_stmt);
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
  public KnowledgeEntity findByName(final String name) {
    final String _sql = "SELECT * FROM knowledge_entity WHERE name = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (name == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, name);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfWeight = CursorUtil.getColumnIndexOrThrow(_cursor, "weight");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final int _cursorIndexOfLastSeenAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSeenAt");
      final KnowledgeEntity _result;
      if (_cursor.moveToFirst()) {
        final String _tmpName;
        if (_cursor.isNull(_cursorIndexOfName)) {
          _tmpName = null;
        } else {
          _tmpName = _cursor.getString(_cursorIndexOfName);
        }
        final String _tmpType;
        if (_cursor.isNull(_cursorIndexOfType)) {
          _tmpType = null;
        } else {
          _tmpType = _cursor.getString(_cursorIndexOfType);
        }
        _result = new KnowledgeEntity(_tmpName,_tmpType);
        _result.id = _cursor.getLong(_cursorIndexOfId);
        _result.weight = _cursor.getFloat(_cursorIndexOfWeight);
        _result.createdAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        _result.lastSeenAt = _cursor.getLong(_cursorIndexOfLastSeenAt);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<KnowledgeEntity> getTopEntities(final int limit) {
    final String _sql = "SELECT * FROM knowledge_entity ORDER BY weight DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfWeight = CursorUtil.getColumnIndexOrThrow(_cursor, "weight");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final int _cursorIndexOfLastSeenAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSeenAt");
      final List<KnowledgeEntity> _result = new ArrayList<KnowledgeEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final KnowledgeEntity _item;
        final String _tmpName;
        if (_cursor.isNull(_cursorIndexOfName)) {
          _tmpName = null;
        } else {
          _tmpName = _cursor.getString(_cursorIndexOfName);
        }
        final String _tmpType;
        if (_cursor.isNull(_cursorIndexOfType)) {
          _tmpType = null;
        } else {
          _tmpType = _cursor.getString(_cursorIndexOfType);
        }
        _item = new KnowledgeEntity(_tmpName,_tmpType);
        _item.id = _cursor.getLong(_cursorIndexOfId);
        _item.weight = _cursor.getFloat(_cursorIndexOfWeight);
        _item.createdAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        _item.lastSeenAt = _cursor.getLong(_cursorIndexOfLastSeenAt);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<KnowledgeEntity> getEntitiesByType(final String type) {
    final String _sql = "SELECT * FROM knowledge_entity WHERE type = ? ORDER BY weight DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfWeight = CursorUtil.getColumnIndexOrThrow(_cursor, "weight");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final int _cursorIndexOfLastSeenAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSeenAt");
      final List<KnowledgeEntity> _result = new ArrayList<KnowledgeEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final KnowledgeEntity _item;
        final String _tmpName;
        if (_cursor.isNull(_cursorIndexOfName)) {
          _tmpName = null;
        } else {
          _tmpName = _cursor.getString(_cursorIndexOfName);
        }
        final String _tmpType;
        if (_cursor.isNull(_cursorIndexOfType)) {
          _tmpType = null;
        } else {
          _tmpType = _cursor.getString(_cursorIndexOfType);
        }
        _item = new KnowledgeEntity(_tmpName,_tmpType);
        _item.id = _cursor.getLong(_cursorIndexOfId);
        _item.weight = _cursor.getFloat(_cursorIndexOfWeight);
        _item.createdAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        _item.lastSeenAt = _cursor.getLong(_cursorIndexOfLastSeenAt);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<KnowledgeEntity> getAll() {
    final String _sql = "SELECT * FROM knowledge_entity";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfWeight = CursorUtil.getColumnIndexOrThrow(_cursor, "weight");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final int _cursorIndexOfLastSeenAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSeenAt");
      final List<KnowledgeEntity> _result = new ArrayList<KnowledgeEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final KnowledgeEntity _item;
        final String _tmpName;
        if (_cursor.isNull(_cursorIndexOfName)) {
          _tmpName = null;
        } else {
          _tmpName = _cursor.getString(_cursorIndexOfName);
        }
        final String _tmpType;
        if (_cursor.isNull(_cursorIndexOfType)) {
          _tmpType = null;
        } else {
          _tmpType = _cursor.getString(_cursorIndexOfType);
        }
        _item = new KnowledgeEntity(_tmpName,_tmpType);
        _item.id = _cursor.getLong(_cursorIndexOfId);
        _item.weight = _cursor.getFloat(_cursorIndexOfWeight);
        _item.createdAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        _item.lastSeenAt = _cursor.getLong(_cursorIndexOfLastSeenAt);
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
