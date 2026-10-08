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
import com.zhijing.data.converter.ListConverters;
import com.zhijing.data.entity.QaRecordEntity;
import com.zhijing.data.model.AnswerStatus;
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
public final class QaRecordDao_Impl implements QaRecordDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<QaRecordEntity> __insertionAdapterOfQaRecordEntity;

  private final ListConverters __listConverters = new ListConverters();

  private final EnumConverters __enumConverters = new EnumConverters();

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public QaRecordDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfQaRecordEntity = new EntityInsertionAdapter<QaRecordEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `qa_record_table` (`id`,`question_text`,`answer_text`,`source_content_ids`,`retrieval_ms`,`generate_ms`,`answer_status`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final QaRecordEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getQuestionText());
        if (entity.getAnswerText() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getAnswerText());
        }
        final String _tmp = __listConverters.longListToCsv(entity.getSourceContentIds());
        if (_tmp == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, _tmp);
        }
        statement.bindLong(5, entity.getRetrievalMs());
        statement.bindLong(6, entity.getGenerateMs());
        final int _tmp_1 = __enumConverters.answerStatusToCode(entity.getAnswerStatus());
        statement.bindLong(7, _tmp_1);
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM qa_record_table WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM qa_record_table";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final QaRecordEntity entity, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfQaRecordEntity.insertAndReturnId(entity);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
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
  public Object findById(final long id, final Continuation<? super QaRecordEntity> $completion) {
    final String _sql = "SELECT * FROM qa_record_table WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<QaRecordEntity>() {
      @Override
      @Nullable
      public QaRecordEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfQuestionText = CursorUtil.getColumnIndexOrThrow(_cursor, "question_text");
          final int _cursorIndexOfAnswerText = CursorUtil.getColumnIndexOrThrow(_cursor, "answer_text");
          final int _cursorIndexOfSourceContentIds = CursorUtil.getColumnIndexOrThrow(_cursor, "source_content_ids");
          final int _cursorIndexOfRetrievalMs = CursorUtil.getColumnIndexOrThrow(_cursor, "retrieval_ms");
          final int _cursorIndexOfGenerateMs = CursorUtil.getColumnIndexOrThrow(_cursor, "generate_ms");
          final int _cursorIndexOfAnswerStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "answer_status");
          final QaRecordEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpQuestionText;
            _tmpQuestionText = _cursor.getString(_cursorIndexOfQuestionText);
            final String _tmpAnswerText;
            if (_cursor.isNull(_cursorIndexOfAnswerText)) {
              _tmpAnswerText = null;
            } else {
              _tmpAnswerText = _cursor.getString(_cursorIndexOfAnswerText);
            }
            final List<Long> _tmpSourceContentIds;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfSourceContentIds)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfSourceContentIds);
            }
            _tmpSourceContentIds = __listConverters.csvToLongList(_tmp);
            final long _tmpRetrievalMs;
            _tmpRetrievalMs = _cursor.getLong(_cursorIndexOfRetrievalMs);
            final long _tmpGenerateMs;
            _tmpGenerateMs = _cursor.getLong(_cursorIndexOfGenerateMs);
            final AnswerStatus _tmpAnswerStatus;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfAnswerStatus);
            _tmpAnswerStatus = __enumConverters.codeToAnswerStatus(_tmp_1);
            _result = new QaRecordEntity(_tmpId,_tmpQuestionText,_tmpAnswerText,_tmpSourceContentIds,_tmpRetrievalMs,_tmpGenerateMs,_tmpAnswerStatus);
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
  public Flow<List<QaRecordEntity>> observeRecent(final int limit) {
    final String _sql = "SELECT * FROM qa_record_table ORDER BY id DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"qa_record_table"}, new Callable<List<QaRecordEntity>>() {
      @Override
      @NonNull
      public List<QaRecordEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfQuestionText = CursorUtil.getColumnIndexOrThrow(_cursor, "question_text");
          final int _cursorIndexOfAnswerText = CursorUtil.getColumnIndexOrThrow(_cursor, "answer_text");
          final int _cursorIndexOfSourceContentIds = CursorUtil.getColumnIndexOrThrow(_cursor, "source_content_ids");
          final int _cursorIndexOfRetrievalMs = CursorUtil.getColumnIndexOrThrow(_cursor, "retrieval_ms");
          final int _cursorIndexOfGenerateMs = CursorUtil.getColumnIndexOrThrow(_cursor, "generate_ms");
          final int _cursorIndexOfAnswerStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "answer_status");
          final List<QaRecordEntity> _result = new ArrayList<QaRecordEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final QaRecordEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpQuestionText;
            _tmpQuestionText = _cursor.getString(_cursorIndexOfQuestionText);
            final String _tmpAnswerText;
            if (_cursor.isNull(_cursorIndexOfAnswerText)) {
              _tmpAnswerText = null;
            } else {
              _tmpAnswerText = _cursor.getString(_cursorIndexOfAnswerText);
            }
            final List<Long> _tmpSourceContentIds;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfSourceContentIds)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfSourceContentIds);
            }
            _tmpSourceContentIds = __listConverters.csvToLongList(_tmp);
            final long _tmpRetrievalMs;
            _tmpRetrievalMs = _cursor.getLong(_cursorIndexOfRetrievalMs);
            final long _tmpGenerateMs;
            _tmpGenerateMs = _cursor.getLong(_cursorIndexOfGenerateMs);
            final AnswerStatus _tmpAnswerStatus;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfAnswerStatus);
            _tmpAnswerStatus = __enumConverters.codeToAnswerStatus(_tmp_1);
            _item = new QaRecordEntity(_tmpId,_tmpQuestionText,_tmpAnswerText,_tmpSourceContentIds,_tmpRetrievalMs,_tmpGenerateMs,_tmpAnswerStatus);
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
  public Object findRecent(final int limit,
      final Continuation<? super List<QaRecordEntity>> $completion) {
    final String _sql = "SELECT * FROM qa_record_table ORDER BY id DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<QaRecordEntity>>() {
      @Override
      @NonNull
      public List<QaRecordEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfQuestionText = CursorUtil.getColumnIndexOrThrow(_cursor, "question_text");
          final int _cursorIndexOfAnswerText = CursorUtil.getColumnIndexOrThrow(_cursor, "answer_text");
          final int _cursorIndexOfSourceContentIds = CursorUtil.getColumnIndexOrThrow(_cursor, "source_content_ids");
          final int _cursorIndexOfRetrievalMs = CursorUtil.getColumnIndexOrThrow(_cursor, "retrieval_ms");
          final int _cursorIndexOfGenerateMs = CursorUtil.getColumnIndexOrThrow(_cursor, "generate_ms");
          final int _cursorIndexOfAnswerStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "answer_status");
          final List<QaRecordEntity> _result = new ArrayList<QaRecordEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final QaRecordEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpQuestionText;
            _tmpQuestionText = _cursor.getString(_cursorIndexOfQuestionText);
            final String _tmpAnswerText;
            if (_cursor.isNull(_cursorIndexOfAnswerText)) {
              _tmpAnswerText = null;
            } else {
              _tmpAnswerText = _cursor.getString(_cursorIndexOfAnswerText);
            }
            final List<Long> _tmpSourceContentIds;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfSourceContentIds)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfSourceContentIds);
            }
            _tmpSourceContentIds = __listConverters.csvToLongList(_tmp);
            final long _tmpRetrievalMs;
            _tmpRetrievalMs = _cursor.getLong(_cursorIndexOfRetrievalMs);
            final long _tmpGenerateMs;
            _tmpGenerateMs = _cursor.getLong(_cursorIndexOfGenerateMs);
            final AnswerStatus _tmpAnswerStatus;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfAnswerStatus);
            _tmpAnswerStatus = __enumConverters.codeToAnswerStatus(_tmp_1);
            _item = new QaRecordEntity(_tmpId,_tmpQuestionText,_tmpAnswerText,_tmpSourceContentIds,_tmpRetrievalMs,_tmpGenerateMs,_tmpAnswerStatus);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
