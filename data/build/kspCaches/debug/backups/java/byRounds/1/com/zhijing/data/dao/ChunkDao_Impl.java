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
import com.zhijing.data.entity.ChunkEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
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
public final class ChunkDao_Impl implements ChunkDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ChunkEntity> __insertionAdapterOfChunkEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByContentId;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public ChunkDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfChunkEntity = new EntityInsertionAdapter<ChunkEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `chunk_table` (`id`,`content_id`,`chunk_index`,`text`,`start_offset`,`end_offset`,`char_count`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ChunkEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getContentId());
        statement.bindLong(3, entity.getChunkIndex());
        statement.bindString(4, entity.getText());
        statement.bindLong(5, entity.getStartOffset());
        statement.bindLong(6, entity.getEndOffset());
        statement.bindLong(7, entity.getCharCount());
      }
    };
    this.__preparedStmtOfDeleteByContentId = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM chunk_table WHERE content_id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM chunk_table";
        return _query;
      }
    };
  }

  @Override
  public Object insertAll(final List<ChunkEntity> chunks,
      final Continuation<? super List<Long>> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        __db.beginTransaction();
        try {
          final List<Long> _result = __insertionAdapterOfChunkEntity.insertAndReturnIdsList(chunks);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
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
  public Object findByContentId(final long contentId,
      final Continuation<? super List<ChunkEntity>> $completion) {
    final String _sql = "SELECT * FROM chunk_table WHERE content_id = ? ORDER BY chunk_index ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, contentId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ChunkEntity>>() {
      @Override
      @NonNull
      public List<ChunkEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "content_id");
          final int _cursorIndexOfChunkIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "chunk_index");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfStartOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "start_offset");
          final int _cursorIndexOfEndOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "end_offset");
          final int _cursorIndexOfCharCount = CursorUtil.getColumnIndexOrThrow(_cursor, "char_count");
          final List<ChunkEntity> _result = new ArrayList<ChunkEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ChunkEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpContentId;
            _tmpContentId = _cursor.getLong(_cursorIndexOfContentId);
            final int _tmpChunkIndex;
            _tmpChunkIndex = _cursor.getInt(_cursorIndexOfChunkIndex);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final int _tmpStartOffset;
            _tmpStartOffset = _cursor.getInt(_cursorIndexOfStartOffset);
            final int _tmpEndOffset;
            _tmpEndOffset = _cursor.getInt(_cursorIndexOfEndOffset);
            final int _tmpCharCount;
            _tmpCharCount = _cursor.getInt(_cursorIndexOfCharCount);
            _item = new ChunkEntity(_tmpId,_tmpContentId,_tmpChunkIndex,_tmpText,_tmpStartOffset,_tmpEndOffset,_tmpCharCount);
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
  public Object findById(final long id, final Continuation<? super ChunkEntity> $completion) {
    final String _sql = "SELECT * FROM chunk_table WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ChunkEntity>() {
      @Override
      @Nullable
      public ChunkEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "content_id");
          final int _cursorIndexOfChunkIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "chunk_index");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfStartOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "start_offset");
          final int _cursorIndexOfEndOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "end_offset");
          final int _cursorIndexOfCharCount = CursorUtil.getColumnIndexOrThrow(_cursor, "char_count");
          final ChunkEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpContentId;
            _tmpContentId = _cursor.getLong(_cursorIndexOfContentId);
            final int _tmpChunkIndex;
            _tmpChunkIndex = _cursor.getInt(_cursorIndexOfChunkIndex);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final int _tmpStartOffset;
            _tmpStartOffset = _cursor.getInt(_cursorIndexOfStartOffset);
            final int _tmpEndOffset;
            _tmpEndOffset = _cursor.getInt(_cursorIndexOfEndOffset);
            final int _tmpCharCount;
            _tmpCharCount = _cursor.getInt(_cursorIndexOfCharCount);
            _result = new ChunkEntity(_tmpId,_tmpContentId,_tmpChunkIndex,_tmpText,_tmpStartOffset,_tmpEndOffset,_tmpCharCount);
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
  public Object findByIds(final List<Long> ids,
      final Continuation<? super List<ChunkEntity>> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT * FROM chunk_table WHERE id IN (");
    final int _inputSize = ids.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (long _item : ids) {
      _statement.bindLong(_argIndex, _item);
      _argIndex++;
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ChunkEntity>>() {
      @Override
      @NonNull
      public List<ChunkEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "content_id");
          final int _cursorIndexOfChunkIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "chunk_index");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfStartOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "start_offset");
          final int _cursorIndexOfEndOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "end_offset");
          final int _cursorIndexOfCharCount = CursorUtil.getColumnIndexOrThrow(_cursor, "char_count");
          final List<ChunkEntity> _result = new ArrayList<ChunkEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ChunkEntity _item_1;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpContentId;
            _tmpContentId = _cursor.getLong(_cursorIndexOfContentId);
            final int _tmpChunkIndex;
            _tmpChunkIndex = _cursor.getInt(_cursorIndexOfChunkIndex);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final int _tmpStartOffset;
            _tmpStartOffset = _cursor.getInt(_cursorIndexOfStartOffset);
            final int _tmpEndOffset;
            _tmpEndOffset = _cursor.getInt(_cursorIndexOfEndOffset);
            final int _tmpCharCount;
            _tmpCharCount = _cursor.getInt(_cursorIndexOfCharCount);
            _item_1 = new ChunkEntity(_tmpId,_tmpContentId,_tmpChunkIndex,_tmpText,_tmpStartOffset,_tmpEndOffset,_tmpCharCount);
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
  public Object findIdsByContentId(final long contentId,
      final Continuation<? super List<Long>> $completion) {
    final String _sql = "SELECT id FROM chunk_table WHERE content_id = ? ORDER BY chunk_index ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, contentId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<Long> _result = new ArrayList<Long>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Long _item;
            _item = _cursor.getLong(0);
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
  public Object findAllIds(final Continuation<? super List<Long>> $completion) {
    final String _sql = "SELECT id FROM chunk_table";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<Long> _result = new ArrayList<Long>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Long _item;
            _item = _cursor.getLong(0);
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
  public Object countByContentId(final long contentId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM chunk_table WHERE content_id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, contentId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
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
