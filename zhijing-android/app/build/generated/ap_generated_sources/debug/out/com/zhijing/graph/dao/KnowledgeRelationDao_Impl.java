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
import com.zhijing.graph.entity.KnowledgeRelation;
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
public final class KnowledgeRelationDao_Impl implements KnowledgeRelationDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<KnowledgeRelation> __insertionAdapterOfKnowledgeRelation;

  private final SharedSQLiteStatement __preparedStmtOfStrengthenRelation;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public KnowledgeRelationDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfKnowledgeRelation = new EntityInsertionAdapter<KnowledgeRelation>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `knowledge_relation` (`id`,`fromEntityId`,`toEntityId`,`relationType`,`strength`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final KnowledgeRelation entity) {
        statement.bindLong(1, entity.id);
        statement.bindLong(2, entity.fromEntityId);
        statement.bindLong(3, entity.toEntityId);
        if (entity.relationType == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.relationType);
        }
        statement.bindDouble(5, entity.strength);
        statement.bindLong(6, entity.createdAt);
      }
    };
    this.__preparedStmtOfStrengthenRelation = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE knowledge_relation SET strength = MIN(1.0, strength + ?) WHERE fromEntityId = ? AND toEntityId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM knowledge_relation";
        return _query;
      }
    };
  }

  @Override
  public long insert(final KnowledgeRelation relation) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfKnowledgeRelation.insertAndReturnId(relation);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void strengthenRelation(final long from, final long to, final float delta) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfStrengthenRelation.acquire();
    int _argIndex = 1;
    _stmt.bindDouble(_argIndex, delta);
    _argIndex = 2;
    _stmt.bindLong(_argIndex, from);
    _argIndex = 3;
    _stmt.bindLong(_argIndex, to);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfStrengthenRelation.release(_stmt);
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
  public List<KnowledgeRelation> getRelationsFrom(final long entityId) {
    final String _sql = "SELECT * FROM knowledge_relation WHERE fromEntityId = ? ORDER BY strength DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, entityId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfFromEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "fromEntityId");
      final int _cursorIndexOfToEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "toEntityId");
      final int _cursorIndexOfRelationType = CursorUtil.getColumnIndexOrThrow(_cursor, "relationType");
      final int _cursorIndexOfStrength = CursorUtil.getColumnIndexOrThrow(_cursor, "strength");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final List<KnowledgeRelation> _result = new ArrayList<KnowledgeRelation>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final KnowledgeRelation _item;
        final long _tmpFromEntityId;
        _tmpFromEntityId = _cursor.getLong(_cursorIndexOfFromEntityId);
        final long _tmpToEntityId;
        _tmpToEntityId = _cursor.getLong(_cursorIndexOfToEntityId);
        final String _tmpRelationType;
        if (_cursor.isNull(_cursorIndexOfRelationType)) {
          _tmpRelationType = null;
        } else {
          _tmpRelationType = _cursor.getString(_cursorIndexOfRelationType);
        }
        final float _tmpStrength;
        _tmpStrength = _cursor.getFloat(_cursorIndexOfStrength);
        _item = new KnowledgeRelation(_tmpFromEntityId,_tmpToEntityId,_tmpRelationType,_tmpStrength);
        _item.id = _cursor.getLong(_cursorIndexOfId);
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
  public List<KnowledgeRelation> getAllRelations(final long entityId) {
    final String _sql = "SELECT * FROM knowledge_relation WHERE fromEntityId = ? OR toEntityId = ? ORDER BY strength DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, entityId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, entityId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfFromEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "fromEntityId");
      final int _cursorIndexOfToEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "toEntityId");
      final int _cursorIndexOfRelationType = CursorUtil.getColumnIndexOrThrow(_cursor, "relationType");
      final int _cursorIndexOfStrength = CursorUtil.getColumnIndexOrThrow(_cursor, "strength");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final List<KnowledgeRelation> _result = new ArrayList<KnowledgeRelation>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final KnowledgeRelation _item;
        final long _tmpFromEntityId;
        _tmpFromEntityId = _cursor.getLong(_cursorIndexOfFromEntityId);
        final long _tmpToEntityId;
        _tmpToEntityId = _cursor.getLong(_cursorIndexOfToEntityId);
        final String _tmpRelationType;
        if (_cursor.isNull(_cursorIndexOfRelationType)) {
          _tmpRelationType = null;
        } else {
          _tmpRelationType = _cursor.getString(_cursorIndexOfRelationType);
        }
        final float _tmpStrength;
        _tmpStrength = _cursor.getFloat(_cursorIndexOfStrength);
        _item = new KnowledgeRelation(_tmpFromEntityId,_tmpToEntityId,_tmpRelationType,_tmpStrength);
        _item.id = _cursor.getLong(_cursorIndexOfId);
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
  public KnowledgeRelation findRelation(final long from, final long to) {
    final String _sql = "SELECT * FROM knowledge_relation WHERE fromEntityId = ? AND toEntityId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, from);
    _argIndex = 2;
    _statement.bindLong(_argIndex, to);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfFromEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "fromEntityId");
      final int _cursorIndexOfToEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "toEntityId");
      final int _cursorIndexOfRelationType = CursorUtil.getColumnIndexOrThrow(_cursor, "relationType");
      final int _cursorIndexOfStrength = CursorUtil.getColumnIndexOrThrow(_cursor, "strength");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final KnowledgeRelation _result;
      if (_cursor.moveToFirst()) {
        final long _tmpFromEntityId;
        _tmpFromEntityId = _cursor.getLong(_cursorIndexOfFromEntityId);
        final long _tmpToEntityId;
        _tmpToEntityId = _cursor.getLong(_cursorIndexOfToEntityId);
        final String _tmpRelationType;
        if (_cursor.isNull(_cursorIndexOfRelationType)) {
          _tmpRelationType = null;
        } else {
          _tmpRelationType = _cursor.getString(_cursorIndexOfRelationType);
        }
        final float _tmpStrength;
        _tmpStrength = _cursor.getFloat(_cursorIndexOfStrength);
        _result = new KnowledgeRelation(_tmpFromEntityId,_tmpToEntityId,_tmpRelationType,_tmpStrength);
        _result.id = _cursor.getLong(_cursorIndexOfId);
        _result.createdAt = _cursor.getLong(_cursorIndexOfCreatedAt);
      } else {
        _result = null;
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
