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
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.zhijing.data.converter.EnumConverters;
import com.zhijing.data.entity.VectorEntity;
import com.zhijing.data.model.VectorOwnerType;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class VectorDao_Impl implements VectorDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<VectorEntity> __insertionAdapterOfVectorEntity;

  private final EnumConverters __enumConverters = new EnumConverters();

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public VectorDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfVectorEntity = new EntityInsertionAdapter<VectorEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `vector_table` (`id`,`owner_type`,`owner_id`,`dim`,`vector`,`model_id`,`created_time`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final VectorEntity entity) {
        statement.bindLong(1, entity.getId());
        final String _tmp = __enumConverters.vectorOwnerTypeToWire(entity.getOwnerType());
        statement.bindString(2, _tmp);
        statement.bindLong(3, entity.getOwnerId());
        statement.bindLong(4, entity.getDim());
        statement.bindBlob(5, entity.getVector());
        statement.bindString(6, entity.getModelId());
        statement.bindLong(7, entity.getCreatedTime());
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM vector_table WHERE owner_type = ? AND owner_id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM vector_table";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final VectorEntity entity, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfVectorEntity.insertAndReturnId(entity);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final VectorOwnerType ownerType, final long ownerId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
        int _argIndex = 1;
        final String _tmp = __enumConverters.vectorOwnerTypeToWire(ownerType);
        _stmt.bindString(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, ownerId);
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
          __preparedStmtOfDelete.release(_stmt);
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
  public Object find(final VectorOwnerType ownerType, final long ownerId,
      final Continuation<? super VectorEntity> $completion) {
    final String _sql = "SELECT * FROM vector_table WHERE owner_type = ? AND owner_id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    final String _tmp = __enumConverters.vectorOwnerTypeToWire(ownerType);
    _statement.bindString(_argIndex, _tmp);
    _argIndex = 2;
    _statement.bindLong(_argIndex, ownerId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<VectorEntity>() {
      @Override
      @Nullable
      public VectorEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfOwnerType = CursorUtil.getColumnIndexOrThrow(_cursor, "owner_type");
          final int _cursorIndexOfOwnerId = CursorUtil.getColumnIndexOrThrow(_cursor, "owner_id");
          final int _cursorIndexOfDim = CursorUtil.getColumnIndexOrThrow(_cursor, "dim");
          final int _cursorIndexOfVector = CursorUtil.getColumnIndexOrThrow(_cursor, "vector");
          final int _cursorIndexOfModelId = CursorUtil.getColumnIndexOrThrow(_cursor, "model_id");
          final int _cursorIndexOfCreatedTime = CursorUtil.getColumnIndexOrThrow(_cursor, "created_time");
          final VectorEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final VectorOwnerType _tmpOwnerType;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfOwnerType);
            _tmpOwnerType = __enumConverters.wireToVectorOwnerType(_tmp_1);
            final long _tmpOwnerId;
            _tmpOwnerId = _cursor.getLong(_cursorIndexOfOwnerId);
            final int _tmpDim;
            _tmpDim = _cursor.getInt(_cursorIndexOfDim);
            final byte[] _tmpVector;
            _tmpVector = _cursor.getBlob(_cursorIndexOfVector);
            final String _tmpModelId;
            _tmpModelId = _cursor.getString(_cursorIndexOfModelId);
            final long _tmpCreatedTime;
            _tmpCreatedTime = _cursor.getLong(_cursorIndexOfCreatedTime);
            _result = new VectorEntity(_tmpId,_tmpOwnerType,_tmpOwnerId,_tmpDim,_tmpVector,_tmpModelId,_tmpCreatedTime);
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
  public Object findByOwnerIds(final VectorOwnerType ownerType, final List<Long> ownerIds,
      final Continuation<? super List<VectorEntity>> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT * FROM vector_table WHERE owner_type = ");
    _stringBuilder.append("?");
    _stringBuilder.append(" AND owner_id IN (");
    final int _inputSize = ownerIds.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 1 + _inputSize;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    final String _tmp = __enumConverters.vectorOwnerTypeToWire(ownerType);
    _statement.bindString(_argIndex, _tmp);
    _argIndex = 2;
    for (long _item : ownerIds) {
      _statement.bindLong(_argIndex, _item);
      _argIndex++;
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<VectorEntity>>() {
      @Override
      @NonNull
      public List<VectorEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfOwnerType = CursorUtil.getColumnIndexOrThrow(_cursor, "owner_type");
          final int _cursorIndexOfOwnerId = CursorUtil.getColumnIndexOrThrow(_cursor, "owner_id");
          final int _cursorIndexOfDim = CursorUtil.getColumnIndexOrThrow(_cursor, "dim");
          final int _cursorIndexOfVector = CursorUtil.getColumnIndexOrThrow(_cursor, "vector");
          final int _cursorIndexOfModelId = CursorUtil.getColumnIndexOrThrow(_cursor, "model_id");
          final int _cursorIndexOfCreatedTime = CursorUtil.getColumnIndexOrThrow(_cursor, "created_time");
          final List<VectorEntity> _result = new ArrayList<VectorEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final VectorEntity _item_1;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final VectorOwnerType _tmpOwnerType;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfOwnerType);
            _tmpOwnerType = __enumConverters.wireToVectorOwnerType(_tmp_1);
            final long _tmpOwnerId;
            _tmpOwnerId = _cursor.getLong(_cursorIndexOfOwnerId);
            final int _tmpDim;
            _tmpDim = _cursor.getInt(_cursorIndexOfDim);
            final byte[] _tmpVector;
            _tmpVector = _cursor.getBlob(_cursorIndexOfVector);
            final String _tmpModelId;
            _tmpModelId = _cursor.getString(_cursorIndexOfModelId);
            final long _tmpCreatedTime;
            _tmpCreatedTime = _cursor.getLong(_cursorIndexOfCreatedTime);
            _item_1 = new VectorEntity(_tmpId,_tmpOwnerType,_tmpOwnerId,_tmpDim,_tmpVector,_tmpModelId,_tmpCreatedTime);
            _result.add(_item_1);
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
  public Object findAllByType(final VectorOwnerType ownerType,
      final Continuation<? super List<VectorEntity>> $completion) {
    final String _sql = "SELECT * FROM vector_table WHERE owner_type = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final String _tmp = __enumConverters.vectorOwnerTypeToWire(ownerType);
    _statement.bindString(_argIndex, _tmp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<VectorEntity>>() {
      @Override
      @NonNull
      public List<VectorEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfOwnerType = CursorUtil.getColumnIndexOrThrow(_cursor, "owner_type");
          final int _cursorIndexOfOwnerId = CursorUtil.getColumnIndexOrThrow(_cursor, "owner_id");
          final int _cursorIndexOfDim = CursorUtil.getColumnIndexOrThrow(_cursor, "dim");
          final int _cursorIndexOfVector = CursorUtil.getColumnIndexOrThrow(_cursor, "vector");
          final int _cursorIndexOfModelId = CursorUtil.getColumnIndexOrThrow(_cursor, "model_id");
          final int _cursorIndexOfCreatedTime = CursorUtil.getColumnIndexOrThrow(_cursor, "created_time");
          final List<VectorEntity> _result = new ArrayList<VectorEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final VectorEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final VectorOwnerType _tmpOwnerType;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfOwnerType);
            _tmpOwnerType = __enumConverters.wireToVectorOwnerType(_tmp_1);
            final long _tmpOwnerId;
            _tmpOwnerId = _cursor.getLong(_cursorIndexOfOwnerId);
            final int _tmpDim;
            _tmpDim = _cursor.getInt(_cursorIndexOfDim);
            final byte[] _tmpVector;
            _tmpVector = _cursor.getBlob(_cursorIndexOfVector);
            final String _tmpModelId;
            _tmpModelId = _cursor.getString(_cursorIndexOfModelId);
            final long _tmpCreatedTime;
            _tmpCreatedTime = _cursor.getLong(_cursorIndexOfCreatedTime);
            _item = new VectorEntity(_tmpId,_tmpOwnerType,_tmpOwnerId,_tmpDim,_tmpVector,_tmpModelId,_tmpCreatedTime);
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
  public Object countByType(final VectorOwnerType ownerType,
      final Continuation<? super Long> $completion) {
    final String _sql = "SELECT COUNT(*) FROM vector_table WHERE owner_type = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final String _tmp = __enumConverters.vectorOwnerTypeToWire(ownerType);
    _statement.bindString(_argIndex, _tmp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Long _result;
          if (_cursor.moveToFirst()) {
            final long _tmp_1;
            _tmp_1 = _cursor.getLong(0);
            _result = _tmp_1;
          } else {
            _result = 0L;
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
  public Object deleteByOwnerIds(final VectorOwnerType ownerType, final List<Long> ownerIds,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("DELETE FROM vector_table WHERE owner_type = ");
        _stringBuilder.append("?");
        _stringBuilder.append(" AND owner_id IN (");
        final int _inputSize = ownerIds.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        final String _tmp = __enumConverters.vectorOwnerTypeToWire(ownerType);
        _stmt.bindString(_argIndex, _tmp);
        _argIndex = 2;
        for (long _item : ownerIds) {
          _stmt.bindLong(_argIndex, _item);
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
