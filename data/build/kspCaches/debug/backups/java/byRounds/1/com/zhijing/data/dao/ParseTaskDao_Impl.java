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
import com.zhijing.data.converter.EnumConverters;
import com.zhijing.data.entity.ParseTaskEntity;
import com.zhijing.data.model.ParseTaskStatus;
import com.zhijing.data.model.ParseTaskType;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
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
public final class ParseTaskDao_Impl implements ParseTaskDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ParseTaskEntity> __insertionAdapterOfParseTaskEntity;

  private final EnumConverters __enumConverters = new EnumConverters();

  private final SharedSQLiteStatement __preparedStmtOfUpdateStatus;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByContentId;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public ParseTaskDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfParseTaskEntity = new EntityInsertionAdapter<ParseTaskEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `parse_task_table` (`id`,`content_id`,`task_type`,`status`,`retry_count`,`created_time`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ParseTaskEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getContentId());
        final String _tmp = __enumConverters.parseTaskTypeToWire(entity.getTaskType());
        statement.bindString(3, _tmp);
        final int _tmp_1 = __enumConverters.parseTaskStatusToCode(entity.getStatus());
        statement.bindLong(4, _tmp_1);
        statement.bindLong(5, entity.getRetryCount());
        statement.bindLong(6, entity.getCreatedTime());
      }
    };
    this.__preparedStmtOfUpdateStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE parse_task_table SET status = ?, retry_count = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM parse_task_table WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteByContentId = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM parse_task_table WHERE content_id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM parse_task_table";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final ParseTaskEntity entity, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfParseTaskEntity.insertAndReturnId(entity);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<ParseTaskEntity> entities,
      final Continuation<? super List<Long>> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        __db.beginTransaction();
        try {
          final List<Long> _result = __insertionAdapterOfParseTaskEntity.insertAndReturnIdsList(entities);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateStatus(final long id, final ParseTaskStatus status, final int retryCount,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStatus.acquire();
        int _argIndex = 1;
        final int _tmp = __enumConverters.parseTaskStatusToCode(status);
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, retryCount);
        _argIndex = 3;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfUpdateStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteByContentId(final long contentId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByContentId.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, contentId);
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
          __preparedStmtOfDeleteByContentId.release(_stmt);
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
  public Object findById(final long id, final Continuation<? super ParseTaskEntity> $completion) {
    final String _sql = "SELECT * FROM parse_task_table WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ParseTaskEntity>() {
      @Override
      @Nullable
      public ParseTaskEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "content_id");
          final int _cursorIndexOfTaskType = CursorUtil.getColumnIndexOrThrow(_cursor, "task_type");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfRetryCount = CursorUtil.getColumnIndexOrThrow(_cursor, "retry_count");
          final int _cursorIndexOfCreatedTime = CursorUtil.getColumnIndexOrThrow(_cursor, "created_time");
          final ParseTaskEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpContentId;
            _tmpContentId = _cursor.getLong(_cursorIndexOfContentId);
            final ParseTaskType _tmpTaskType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfTaskType);
            _tmpTaskType = __enumConverters.wireToParseTaskType(_tmp);
            final ParseTaskStatus _tmpStatus;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfStatus);
            _tmpStatus = __enumConverters.codeToParseTaskStatus(_tmp_1);
            final int _tmpRetryCount;
            _tmpRetryCount = _cursor.getInt(_cursorIndexOfRetryCount);
            final long _tmpCreatedTime;
            _tmpCreatedTime = _cursor.getLong(_cursorIndexOfCreatedTime);
            _result = new ParseTaskEntity(_tmpId,_tmpContentId,_tmpTaskType,_tmpStatus,_tmpRetryCount,_tmpCreatedTime);
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
  public Object findOldest(final Continuation<? super ParseTaskEntity> $completion) {
    final String _sql = "SELECT * FROM parse_task_table ORDER BY created_time ASC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ParseTaskEntity>() {
      @Override
      @Nullable
      public ParseTaskEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "content_id");
          final int _cursorIndexOfTaskType = CursorUtil.getColumnIndexOrThrow(_cursor, "task_type");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfRetryCount = CursorUtil.getColumnIndexOrThrow(_cursor, "retry_count");
          final int _cursorIndexOfCreatedTime = CursorUtil.getColumnIndexOrThrow(_cursor, "created_time");
          final ParseTaskEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpContentId;
            _tmpContentId = _cursor.getLong(_cursorIndexOfContentId);
            final ParseTaskType _tmpTaskType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfTaskType);
            _tmpTaskType = __enumConverters.wireToParseTaskType(_tmp);
            final ParseTaskStatus _tmpStatus;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfStatus);
            _tmpStatus = __enumConverters.codeToParseTaskStatus(_tmp_1);
            final int _tmpRetryCount;
            _tmpRetryCount = _cursor.getInt(_cursorIndexOfRetryCount);
            final long _tmpCreatedTime;
            _tmpCreatedTime = _cursor.getLong(_cursorIndexOfCreatedTime);
            _result = new ParseTaskEntity(_tmpId,_tmpContentId,_tmpTaskType,_tmpStatus,_tmpRetryCount,_tmpCreatedTime);
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
  public Object findByStatus(final ParseTaskStatus status, final int limit,
      final Continuation<? super List<ParseTaskEntity>> $completion) {
    final String _sql = "SELECT * FROM parse_task_table WHERE status = ? ORDER BY created_time ASC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    final int _tmp = __enumConverters.parseTaskStatusToCode(status);
    _statement.bindLong(_argIndex, _tmp);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ParseTaskEntity>>() {
      @Override
      @NonNull
      public List<ParseTaskEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "content_id");
          final int _cursorIndexOfTaskType = CursorUtil.getColumnIndexOrThrow(_cursor, "task_type");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfRetryCount = CursorUtil.getColumnIndexOrThrow(_cursor, "retry_count");
          final int _cursorIndexOfCreatedTime = CursorUtil.getColumnIndexOrThrow(_cursor, "created_time");
          final List<ParseTaskEntity> _result = new ArrayList<ParseTaskEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ParseTaskEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpContentId;
            _tmpContentId = _cursor.getLong(_cursorIndexOfContentId);
            final ParseTaskType _tmpTaskType;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfTaskType);
            _tmpTaskType = __enumConverters.wireToParseTaskType(_tmp_1);
            final ParseTaskStatus _tmpStatus;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfStatus);
            _tmpStatus = __enumConverters.codeToParseTaskStatus(_tmp_2);
            final int _tmpRetryCount;
            _tmpRetryCount = _cursor.getInt(_cursorIndexOfRetryCount);
            final long _tmpCreatedTime;
            _tmpCreatedTime = _cursor.getLong(_cursorIndexOfCreatedTime);
            _item = new ParseTaskEntity(_tmpId,_tmpContentId,_tmpTaskType,_tmpStatus,_tmpRetryCount,_tmpCreatedTime);
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
  public Object findByContentId(final long contentId,
      final Continuation<? super List<ParseTaskEntity>> $completion) {
    final String _sql = "SELECT * FROM parse_task_table WHERE content_id = ? ORDER BY created_time ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, contentId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ParseTaskEntity>>() {
      @Override
      @NonNull
      public List<ParseTaskEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "content_id");
          final int _cursorIndexOfTaskType = CursorUtil.getColumnIndexOrThrow(_cursor, "task_type");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfRetryCount = CursorUtil.getColumnIndexOrThrow(_cursor, "retry_count");
          final int _cursorIndexOfCreatedTime = CursorUtil.getColumnIndexOrThrow(_cursor, "created_time");
          final List<ParseTaskEntity> _result = new ArrayList<ParseTaskEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ParseTaskEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpContentId;
            _tmpContentId = _cursor.getLong(_cursorIndexOfContentId);
            final ParseTaskType _tmpTaskType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfTaskType);
            _tmpTaskType = __enumConverters.wireToParseTaskType(_tmp);
            final ParseTaskStatus _tmpStatus;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfStatus);
            _tmpStatus = __enumConverters.codeToParseTaskStatus(_tmp_1);
            final int _tmpRetryCount;
            _tmpRetryCount = _cursor.getInt(_cursorIndexOfRetryCount);
            final long _tmpCreatedTime;
            _tmpCreatedTime = _cursor.getLong(_cursorIndexOfCreatedTime);
            _item = new ParseTaskEntity(_tmpId,_tmpContentId,_tmpTaskType,_tmpStatus,_tmpRetryCount,_tmpCreatedTime);
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
  public Flow<List<ParseTaskEntity>> observeAll() {
    final String _sql = "SELECT * FROM parse_task_table ORDER BY created_time ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"parse_task_table"}, new Callable<List<ParseTaskEntity>>() {
      @Override
      @NonNull
      public List<ParseTaskEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "content_id");
          final int _cursorIndexOfTaskType = CursorUtil.getColumnIndexOrThrow(_cursor, "task_type");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfRetryCount = CursorUtil.getColumnIndexOrThrow(_cursor, "retry_count");
          final int _cursorIndexOfCreatedTime = CursorUtil.getColumnIndexOrThrow(_cursor, "created_time");
          final List<ParseTaskEntity> _result = new ArrayList<ParseTaskEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ParseTaskEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpContentId;
            _tmpContentId = _cursor.getLong(_cursorIndexOfContentId);
            final ParseTaskType _tmpTaskType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfTaskType);
            _tmpTaskType = __enumConverters.wireToParseTaskType(_tmp);
            final ParseTaskStatus _tmpStatus;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfStatus);
            _tmpStatus = __enumConverters.codeToParseTaskStatus(_tmp_1);
            final int _tmpRetryCount;
            _tmpRetryCount = _cursor.getInt(_cursorIndexOfRetryCount);
            final long _tmpCreatedTime;
            _tmpCreatedTime = _cursor.getLong(_cursorIndexOfCreatedTime);
            _item = new ParseTaskEntity(_tmpId,_tmpContentId,_tmpTaskType,_tmpStatus,_tmpRetryCount,_tmpCreatedTime);
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

  @Override
  public Object countByStatus(final ParseTaskStatus status,
      final Continuation<? super Long> $completion) {
    final String _sql = "SELECT COUNT(*) FROM parse_task_table WHERE status = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final int _tmp = __enumConverters.parseTaskStatusToCode(status);
    _statement.bindLong(_argIndex, _tmp);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
