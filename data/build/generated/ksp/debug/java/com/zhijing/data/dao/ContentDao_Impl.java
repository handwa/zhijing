package com.zhijing.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.zhijing.data.converter.EnumConverters;
import com.zhijing.data.entity.ContentEntity;
import com.zhijing.data.model.AnalysisStatus;
import com.zhijing.data.model.EmbeddingStatus;
import com.zhijing.data.model.ImportChannel;
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
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ContentDao_Impl implements ContentDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ContentEntity> __insertionAdapterOfContentEntity;

  private final EnumConverters __enumConverters = new EnumConverters();

  private final EntityDeletionOrUpdateAdapter<ContentEntity> __updateAdapterOfContentEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateAnalysis;

  private final SharedSQLiteStatement __preparedStmtOfUpdateAnalysisStatus;

  private final SharedSQLiteStatement __preparedStmtOfUpdateEmbedding;

  private final SharedSQLiteStatement __preparedStmtOfUpdateEmbeddingStatus;

  private final SharedSQLiteStatement __preparedStmtOfUpdateRemark;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public ContentDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfContentEntity = new EntityInsertionAdapter<ContentEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `content_table` (`id`,`raw_text`,`source`,`import_time`,`analysis_json`,`analysis_status`,`embedding_vector`,`embedding_status`,`remark`,`import_channel`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ContentEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getRawText());
        if (entity.getSource() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getSource());
        }
        statement.bindLong(4, entity.getImportTime());
        if (entity.getAnalysisJson() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getAnalysisJson());
        }
        final int _tmp = __enumConverters.analysisStatusToCode(entity.getAnalysisStatus());
        statement.bindLong(6, _tmp);
        if (entity.getEmbeddingVector() == null) {
          statement.bindNull(7);
        } else {
          statement.bindBlob(7, entity.getEmbeddingVector());
        }
        final int _tmp_1 = __enumConverters.embeddingStatusToCode(entity.getEmbeddingStatus());
        statement.bindLong(8, _tmp_1);
        if (entity.getRemark() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getRemark());
        }
        final String _tmp_2 = __enumConverters.importChannelToWire(entity.getImportChannel());
        statement.bindString(10, _tmp_2);
      }
    };
    this.__updateAdapterOfContentEntity = new EntityDeletionOrUpdateAdapter<ContentEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `content_table` SET `id` = ?,`raw_text` = ?,`source` = ?,`import_time` = ?,`analysis_json` = ?,`analysis_status` = ?,`embedding_vector` = ?,`embedding_status` = ?,`remark` = ?,`import_channel` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ContentEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getRawText());
        if (entity.getSource() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getSource());
        }
        statement.bindLong(4, entity.getImportTime());
        if (entity.getAnalysisJson() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getAnalysisJson());
        }
        final int _tmp = __enumConverters.analysisStatusToCode(entity.getAnalysisStatus());
        statement.bindLong(6, _tmp);
        if (entity.getEmbeddingVector() == null) {
          statement.bindNull(7);
        } else {
          statement.bindBlob(7, entity.getEmbeddingVector());
        }
        final int _tmp_1 = __enumConverters.embeddingStatusToCode(entity.getEmbeddingStatus());
        statement.bindLong(8, _tmp_1);
        if (entity.getRemark() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getRemark());
        }
        final String _tmp_2 = __enumConverters.importChannelToWire(entity.getImportChannel());
        statement.bindString(10, _tmp_2);
        statement.bindLong(11, entity.getId());
      }
    };
    this.__preparedStmtOfUpdateAnalysis = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE content_table SET analysis_json = ?, analysis_status = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateAnalysisStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE content_table SET analysis_status = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateEmbedding = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE content_table SET embedding_vector = ?, embedding_status = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateEmbeddingStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE content_table SET embedding_status = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateRemark = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE content_table SET remark = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM content_table WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM content_table";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final ContentEntity entity, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfContentEntity.insertAndReturnId(entity);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<ContentEntity> entities,
      final Continuation<? super List<Long>> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        __db.beginTransaction();
        try {
          final List<Long> _result = __insertionAdapterOfContentEntity.insertAndReturnIdsList(entities);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final ContentEntity entity, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfContentEntity.handle(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateAnalysis(final long id, final String analysisJson,
      final AnalysisStatus status, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateAnalysis.acquire();
        int _argIndex = 1;
        if (analysisJson == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, analysisJson);
        }
        _argIndex = 2;
        final int _tmp = __enumConverters.analysisStatusToCode(status);
        _stmt.bindLong(_argIndex, _tmp);
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
          __preparedStmtOfUpdateAnalysis.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateAnalysisStatus(final long id, final AnalysisStatus status,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateAnalysisStatus.acquire();
        int _argIndex = 1;
        final int _tmp = __enumConverters.analysisStatusToCode(status);
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
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
          __preparedStmtOfUpdateAnalysisStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateEmbedding(final long id, final byte[] vector, final EmbeddingStatus status,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateEmbedding.acquire();
        int _argIndex = 1;
        if (vector == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindBlob(_argIndex, vector);
        }
        _argIndex = 2;
        final int _tmp = __enumConverters.embeddingStatusToCode(status);
        _stmt.bindLong(_argIndex, _tmp);
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
          __preparedStmtOfUpdateEmbedding.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateEmbeddingStatus(final long id, final EmbeddingStatus status,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateEmbeddingStatus.acquire();
        int _argIndex = 1;
        final int _tmp = __enumConverters.embeddingStatusToCode(status);
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
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
          __preparedStmtOfUpdateEmbeddingStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateRemark(final long id, final String remark,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateRemark.acquire();
        int _argIndex = 1;
        if (remark == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, remark);
        }
        _argIndex = 2;
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
          __preparedStmtOfUpdateRemark.release(_stmt);
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
  public Object findById(final long id, final Continuation<? super ContentEntity> $completion) {
    final String _sql = "SELECT * FROM content_table WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ContentEntity>() {
      @Override
      @Nullable
      public ContentEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRawText = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_text");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final int _cursorIndexOfImportTime = CursorUtil.getColumnIndexOrThrow(_cursor, "import_time");
          final int _cursorIndexOfAnalysisJson = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_json");
          final int _cursorIndexOfAnalysisStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_status");
          final int _cursorIndexOfEmbeddingVector = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_vector");
          final int _cursorIndexOfEmbeddingStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_status");
          final int _cursorIndexOfRemark = CursorUtil.getColumnIndexOrThrow(_cursor, "remark");
          final int _cursorIndexOfImportChannel = CursorUtil.getColumnIndexOrThrow(_cursor, "import_channel");
          final ContentEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpRawText;
            _tmpRawText = _cursor.getString(_cursorIndexOfRawText);
            final String _tmpSource;
            if (_cursor.isNull(_cursorIndexOfSource)) {
              _tmpSource = null;
            } else {
              _tmpSource = _cursor.getString(_cursorIndexOfSource);
            }
            final long _tmpImportTime;
            _tmpImportTime = _cursor.getLong(_cursorIndexOfImportTime);
            final String _tmpAnalysisJson;
            if (_cursor.isNull(_cursorIndexOfAnalysisJson)) {
              _tmpAnalysisJson = null;
            } else {
              _tmpAnalysisJson = _cursor.getString(_cursorIndexOfAnalysisJson);
            }
            final AnalysisStatus _tmpAnalysisStatus;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAnalysisStatus);
            _tmpAnalysisStatus = __enumConverters.codeToAnalysisStatus(_tmp);
            final byte[] _tmpEmbeddingVector;
            if (_cursor.isNull(_cursorIndexOfEmbeddingVector)) {
              _tmpEmbeddingVector = null;
            } else {
              _tmpEmbeddingVector = _cursor.getBlob(_cursorIndexOfEmbeddingVector);
            }
            final EmbeddingStatus _tmpEmbeddingStatus;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfEmbeddingStatus);
            _tmpEmbeddingStatus = __enumConverters.codeToEmbeddingStatus(_tmp_1);
            final String _tmpRemark;
            if (_cursor.isNull(_cursorIndexOfRemark)) {
              _tmpRemark = null;
            } else {
              _tmpRemark = _cursor.getString(_cursorIndexOfRemark);
            }
            final ImportChannel _tmpImportChannel;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfImportChannel);
            _tmpImportChannel = __enumConverters.wireToImportChannel(_tmp_2);
            _result = new ContentEntity(_tmpId,_tmpRawText,_tmpSource,_tmpImportTime,_tmpAnalysisJson,_tmpAnalysisStatus,_tmpEmbeddingVector,_tmpEmbeddingStatus,_tmpRemark,_tmpImportChannel);
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
  public Flow<ContentEntity> observeById(final long id) {
    final String _sql = "SELECT * FROM content_table WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"content_table"}, new Callable<ContentEntity>() {
      @Override
      @Nullable
      public ContentEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRawText = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_text");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final int _cursorIndexOfImportTime = CursorUtil.getColumnIndexOrThrow(_cursor, "import_time");
          final int _cursorIndexOfAnalysisJson = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_json");
          final int _cursorIndexOfAnalysisStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_status");
          final int _cursorIndexOfEmbeddingVector = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_vector");
          final int _cursorIndexOfEmbeddingStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_status");
          final int _cursorIndexOfRemark = CursorUtil.getColumnIndexOrThrow(_cursor, "remark");
          final int _cursorIndexOfImportChannel = CursorUtil.getColumnIndexOrThrow(_cursor, "import_channel");
          final ContentEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpRawText;
            _tmpRawText = _cursor.getString(_cursorIndexOfRawText);
            final String _tmpSource;
            if (_cursor.isNull(_cursorIndexOfSource)) {
              _tmpSource = null;
            } else {
              _tmpSource = _cursor.getString(_cursorIndexOfSource);
            }
            final long _tmpImportTime;
            _tmpImportTime = _cursor.getLong(_cursorIndexOfImportTime);
            final String _tmpAnalysisJson;
            if (_cursor.isNull(_cursorIndexOfAnalysisJson)) {
              _tmpAnalysisJson = null;
            } else {
              _tmpAnalysisJson = _cursor.getString(_cursorIndexOfAnalysisJson);
            }
            final AnalysisStatus _tmpAnalysisStatus;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAnalysisStatus);
            _tmpAnalysisStatus = __enumConverters.codeToAnalysisStatus(_tmp);
            final byte[] _tmpEmbeddingVector;
            if (_cursor.isNull(_cursorIndexOfEmbeddingVector)) {
              _tmpEmbeddingVector = null;
            } else {
              _tmpEmbeddingVector = _cursor.getBlob(_cursorIndexOfEmbeddingVector);
            }
            final EmbeddingStatus _tmpEmbeddingStatus;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfEmbeddingStatus);
            _tmpEmbeddingStatus = __enumConverters.codeToEmbeddingStatus(_tmp_1);
            final String _tmpRemark;
            if (_cursor.isNull(_cursorIndexOfRemark)) {
              _tmpRemark = null;
            } else {
              _tmpRemark = _cursor.getString(_cursorIndexOfRemark);
            }
            final ImportChannel _tmpImportChannel;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfImportChannel);
            _tmpImportChannel = __enumConverters.wireToImportChannel(_tmp_2);
            _result = new ContentEntity(_tmpId,_tmpRawText,_tmpSource,_tmpImportTime,_tmpAnalysisJson,_tmpAnalysisStatus,_tmpEmbeddingVector,_tmpEmbeddingStatus,_tmpRemark,_tmpImportChannel);
          } else {
            _result = null;
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
  public Object findByIds(final List<Long> ids,
      final Continuation<? super List<ContentEntity>> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT * FROM content_table WHERE id IN (");
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
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ContentEntity>>() {
      @Override
      @NonNull
      public List<ContentEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRawText = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_text");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final int _cursorIndexOfImportTime = CursorUtil.getColumnIndexOrThrow(_cursor, "import_time");
          final int _cursorIndexOfAnalysisJson = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_json");
          final int _cursorIndexOfAnalysisStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_status");
          final int _cursorIndexOfEmbeddingVector = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_vector");
          final int _cursorIndexOfEmbeddingStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_status");
          final int _cursorIndexOfRemark = CursorUtil.getColumnIndexOrThrow(_cursor, "remark");
          final int _cursorIndexOfImportChannel = CursorUtil.getColumnIndexOrThrow(_cursor, "import_channel");
          final List<ContentEntity> _result = new ArrayList<ContentEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ContentEntity _item_1;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpRawText;
            _tmpRawText = _cursor.getString(_cursorIndexOfRawText);
            final String _tmpSource;
            if (_cursor.isNull(_cursorIndexOfSource)) {
              _tmpSource = null;
            } else {
              _tmpSource = _cursor.getString(_cursorIndexOfSource);
            }
            final long _tmpImportTime;
            _tmpImportTime = _cursor.getLong(_cursorIndexOfImportTime);
            final String _tmpAnalysisJson;
            if (_cursor.isNull(_cursorIndexOfAnalysisJson)) {
              _tmpAnalysisJson = null;
            } else {
              _tmpAnalysisJson = _cursor.getString(_cursorIndexOfAnalysisJson);
            }
            final AnalysisStatus _tmpAnalysisStatus;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAnalysisStatus);
            _tmpAnalysisStatus = __enumConverters.codeToAnalysisStatus(_tmp);
            final byte[] _tmpEmbeddingVector;
            if (_cursor.isNull(_cursorIndexOfEmbeddingVector)) {
              _tmpEmbeddingVector = null;
            } else {
              _tmpEmbeddingVector = _cursor.getBlob(_cursorIndexOfEmbeddingVector);
            }
            final EmbeddingStatus _tmpEmbeddingStatus;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfEmbeddingStatus);
            _tmpEmbeddingStatus = __enumConverters.codeToEmbeddingStatus(_tmp_1);
            final String _tmpRemark;
            if (_cursor.isNull(_cursorIndexOfRemark)) {
              _tmpRemark = null;
            } else {
              _tmpRemark = _cursor.getString(_cursorIndexOfRemark);
            }
            final ImportChannel _tmpImportChannel;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfImportChannel);
            _tmpImportChannel = __enumConverters.wireToImportChannel(_tmp_2);
            _item_1 = new ContentEntity(_tmpId,_tmpRawText,_tmpSource,_tmpImportTime,_tmpAnalysisJson,_tmpAnalysisStatus,_tmpEmbeddingVector,_tmpEmbeddingStatus,_tmpRemark,_tmpImportChannel);
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
  public Flow<List<ContentEntity>> observeAll() {
    final String _sql = "SELECT * FROM content_table ORDER BY import_time DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"content_table"}, new Callable<List<ContentEntity>>() {
      @Override
      @NonNull
      public List<ContentEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRawText = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_text");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final int _cursorIndexOfImportTime = CursorUtil.getColumnIndexOrThrow(_cursor, "import_time");
          final int _cursorIndexOfAnalysisJson = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_json");
          final int _cursorIndexOfAnalysisStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_status");
          final int _cursorIndexOfEmbeddingVector = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_vector");
          final int _cursorIndexOfEmbeddingStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_status");
          final int _cursorIndexOfRemark = CursorUtil.getColumnIndexOrThrow(_cursor, "remark");
          final int _cursorIndexOfImportChannel = CursorUtil.getColumnIndexOrThrow(_cursor, "import_channel");
          final List<ContentEntity> _result = new ArrayList<ContentEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ContentEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpRawText;
            _tmpRawText = _cursor.getString(_cursorIndexOfRawText);
            final String _tmpSource;
            if (_cursor.isNull(_cursorIndexOfSource)) {
              _tmpSource = null;
            } else {
              _tmpSource = _cursor.getString(_cursorIndexOfSource);
            }
            final long _tmpImportTime;
            _tmpImportTime = _cursor.getLong(_cursorIndexOfImportTime);
            final String _tmpAnalysisJson;
            if (_cursor.isNull(_cursorIndexOfAnalysisJson)) {
              _tmpAnalysisJson = null;
            } else {
              _tmpAnalysisJson = _cursor.getString(_cursorIndexOfAnalysisJson);
            }
            final AnalysisStatus _tmpAnalysisStatus;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAnalysisStatus);
            _tmpAnalysisStatus = __enumConverters.codeToAnalysisStatus(_tmp);
            final byte[] _tmpEmbeddingVector;
            if (_cursor.isNull(_cursorIndexOfEmbeddingVector)) {
              _tmpEmbeddingVector = null;
            } else {
              _tmpEmbeddingVector = _cursor.getBlob(_cursorIndexOfEmbeddingVector);
            }
            final EmbeddingStatus _tmpEmbeddingStatus;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfEmbeddingStatus);
            _tmpEmbeddingStatus = __enumConverters.codeToEmbeddingStatus(_tmp_1);
            final String _tmpRemark;
            if (_cursor.isNull(_cursorIndexOfRemark)) {
              _tmpRemark = null;
            } else {
              _tmpRemark = _cursor.getString(_cursorIndexOfRemark);
            }
            final ImportChannel _tmpImportChannel;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfImportChannel);
            _tmpImportChannel = __enumConverters.wireToImportChannel(_tmp_2);
            _item = new ContentEntity(_tmpId,_tmpRawText,_tmpSource,_tmpImportTime,_tmpAnalysisJson,_tmpAnalysisStatus,_tmpEmbeddingVector,_tmpEmbeddingStatus,_tmpRemark,_tmpImportChannel);
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
      final Continuation<? super List<ContentEntity>> $completion) {
    final String _sql = "SELECT * FROM content_table ORDER BY import_time DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ContentEntity>>() {
      @Override
      @NonNull
      public List<ContentEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRawText = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_text");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final int _cursorIndexOfImportTime = CursorUtil.getColumnIndexOrThrow(_cursor, "import_time");
          final int _cursorIndexOfAnalysisJson = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_json");
          final int _cursorIndexOfAnalysisStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_status");
          final int _cursorIndexOfEmbeddingVector = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_vector");
          final int _cursorIndexOfEmbeddingStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_status");
          final int _cursorIndexOfRemark = CursorUtil.getColumnIndexOrThrow(_cursor, "remark");
          final int _cursorIndexOfImportChannel = CursorUtil.getColumnIndexOrThrow(_cursor, "import_channel");
          final List<ContentEntity> _result = new ArrayList<ContentEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ContentEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpRawText;
            _tmpRawText = _cursor.getString(_cursorIndexOfRawText);
            final String _tmpSource;
            if (_cursor.isNull(_cursorIndexOfSource)) {
              _tmpSource = null;
            } else {
              _tmpSource = _cursor.getString(_cursorIndexOfSource);
            }
            final long _tmpImportTime;
            _tmpImportTime = _cursor.getLong(_cursorIndexOfImportTime);
            final String _tmpAnalysisJson;
            if (_cursor.isNull(_cursorIndexOfAnalysisJson)) {
              _tmpAnalysisJson = null;
            } else {
              _tmpAnalysisJson = _cursor.getString(_cursorIndexOfAnalysisJson);
            }
            final AnalysisStatus _tmpAnalysisStatus;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAnalysisStatus);
            _tmpAnalysisStatus = __enumConverters.codeToAnalysisStatus(_tmp);
            final byte[] _tmpEmbeddingVector;
            if (_cursor.isNull(_cursorIndexOfEmbeddingVector)) {
              _tmpEmbeddingVector = null;
            } else {
              _tmpEmbeddingVector = _cursor.getBlob(_cursorIndexOfEmbeddingVector);
            }
            final EmbeddingStatus _tmpEmbeddingStatus;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfEmbeddingStatus);
            _tmpEmbeddingStatus = __enumConverters.codeToEmbeddingStatus(_tmp_1);
            final String _tmpRemark;
            if (_cursor.isNull(_cursorIndexOfRemark)) {
              _tmpRemark = null;
            } else {
              _tmpRemark = _cursor.getString(_cursorIndexOfRemark);
            }
            final ImportChannel _tmpImportChannel;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfImportChannel);
            _tmpImportChannel = __enumConverters.wireToImportChannel(_tmp_2);
            _item = new ContentEntity(_tmpId,_tmpRawText,_tmpSource,_tmpImportTime,_tmpAnalysisJson,_tmpAnalysisStatus,_tmpEmbeddingVector,_tmpEmbeddingStatus,_tmpRemark,_tmpImportChannel);
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
  public Flow<List<ContentEntity>> observeRecent(final int limit) {
    final String _sql = "SELECT * FROM content_table ORDER BY import_time DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"content_table"}, new Callable<List<ContentEntity>>() {
      @Override
      @NonNull
      public List<ContentEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRawText = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_text");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final int _cursorIndexOfImportTime = CursorUtil.getColumnIndexOrThrow(_cursor, "import_time");
          final int _cursorIndexOfAnalysisJson = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_json");
          final int _cursorIndexOfAnalysisStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_status");
          final int _cursorIndexOfEmbeddingVector = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_vector");
          final int _cursorIndexOfEmbeddingStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_status");
          final int _cursorIndexOfRemark = CursorUtil.getColumnIndexOrThrow(_cursor, "remark");
          final int _cursorIndexOfImportChannel = CursorUtil.getColumnIndexOrThrow(_cursor, "import_channel");
          final List<ContentEntity> _result = new ArrayList<ContentEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ContentEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpRawText;
            _tmpRawText = _cursor.getString(_cursorIndexOfRawText);
            final String _tmpSource;
            if (_cursor.isNull(_cursorIndexOfSource)) {
              _tmpSource = null;
            } else {
              _tmpSource = _cursor.getString(_cursorIndexOfSource);
            }
            final long _tmpImportTime;
            _tmpImportTime = _cursor.getLong(_cursorIndexOfImportTime);
            final String _tmpAnalysisJson;
            if (_cursor.isNull(_cursorIndexOfAnalysisJson)) {
              _tmpAnalysisJson = null;
            } else {
              _tmpAnalysisJson = _cursor.getString(_cursorIndexOfAnalysisJson);
            }
            final AnalysisStatus _tmpAnalysisStatus;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAnalysisStatus);
            _tmpAnalysisStatus = __enumConverters.codeToAnalysisStatus(_tmp);
            final byte[] _tmpEmbeddingVector;
            if (_cursor.isNull(_cursorIndexOfEmbeddingVector)) {
              _tmpEmbeddingVector = null;
            } else {
              _tmpEmbeddingVector = _cursor.getBlob(_cursorIndexOfEmbeddingVector);
            }
            final EmbeddingStatus _tmpEmbeddingStatus;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfEmbeddingStatus);
            _tmpEmbeddingStatus = __enumConverters.codeToEmbeddingStatus(_tmp_1);
            final String _tmpRemark;
            if (_cursor.isNull(_cursorIndexOfRemark)) {
              _tmpRemark = null;
            } else {
              _tmpRemark = _cursor.getString(_cursorIndexOfRemark);
            }
            final ImportChannel _tmpImportChannel;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfImportChannel);
            _tmpImportChannel = __enumConverters.wireToImportChannel(_tmp_2);
            _item = new ContentEntity(_tmpId,_tmpRawText,_tmpSource,_tmpImportTime,_tmpAnalysisJson,_tmpAnalysisStatus,_tmpEmbeddingVector,_tmpEmbeddingStatus,_tmpRemark,_tmpImportChannel);
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
  public Object findByAnalysisStatus(final AnalysisStatus status, final int limit,
      final Continuation<? super List<ContentEntity>> $completion) {
    final String _sql = "SELECT * FROM content_table WHERE analysis_status = ? ORDER BY import_time ASC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    final int _tmp = __enumConverters.analysisStatusToCode(status);
    _statement.bindLong(_argIndex, _tmp);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ContentEntity>>() {
      @Override
      @NonNull
      public List<ContentEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRawText = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_text");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final int _cursorIndexOfImportTime = CursorUtil.getColumnIndexOrThrow(_cursor, "import_time");
          final int _cursorIndexOfAnalysisJson = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_json");
          final int _cursorIndexOfAnalysisStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_status");
          final int _cursorIndexOfEmbeddingVector = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_vector");
          final int _cursorIndexOfEmbeddingStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_status");
          final int _cursorIndexOfRemark = CursorUtil.getColumnIndexOrThrow(_cursor, "remark");
          final int _cursorIndexOfImportChannel = CursorUtil.getColumnIndexOrThrow(_cursor, "import_channel");
          final List<ContentEntity> _result = new ArrayList<ContentEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ContentEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpRawText;
            _tmpRawText = _cursor.getString(_cursorIndexOfRawText);
            final String _tmpSource;
            if (_cursor.isNull(_cursorIndexOfSource)) {
              _tmpSource = null;
            } else {
              _tmpSource = _cursor.getString(_cursorIndexOfSource);
            }
            final long _tmpImportTime;
            _tmpImportTime = _cursor.getLong(_cursorIndexOfImportTime);
            final String _tmpAnalysisJson;
            if (_cursor.isNull(_cursorIndexOfAnalysisJson)) {
              _tmpAnalysisJson = null;
            } else {
              _tmpAnalysisJson = _cursor.getString(_cursorIndexOfAnalysisJson);
            }
            final AnalysisStatus _tmpAnalysisStatus;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfAnalysisStatus);
            _tmpAnalysisStatus = __enumConverters.codeToAnalysisStatus(_tmp_1);
            final byte[] _tmpEmbeddingVector;
            if (_cursor.isNull(_cursorIndexOfEmbeddingVector)) {
              _tmpEmbeddingVector = null;
            } else {
              _tmpEmbeddingVector = _cursor.getBlob(_cursorIndexOfEmbeddingVector);
            }
            final EmbeddingStatus _tmpEmbeddingStatus;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfEmbeddingStatus);
            _tmpEmbeddingStatus = __enumConverters.codeToEmbeddingStatus(_tmp_2);
            final String _tmpRemark;
            if (_cursor.isNull(_cursorIndexOfRemark)) {
              _tmpRemark = null;
            } else {
              _tmpRemark = _cursor.getString(_cursorIndexOfRemark);
            }
            final ImportChannel _tmpImportChannel;
            final String _tmp_3;
            _tmp_3 = _cursor.getString(_cursorIndexOfImportChannel);
            _tmpImportChannel = __enumConverters.wireToImportChannel(_tmp_3);
            _item = new ContentEntity(_tmpId,_tmpRawText,_tmpSource,_tmpImportTime,_tmpAnalysisJson,_tmpAnalysisStatus,_tmpEmbeddingVector,_tmpEmbeddingStatus,_tmpRemark,_tmpImportChannel);
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
  public Object findByEmbeddingStatus(final EmbeddingStatus status, final int limit,
      final Continuation<? super List<ContentEntity>> $completion) {
    final String _sql = "SELECT * FROM content_table WHERE embedding_status = ? ORDER BY import_time ASC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    final int _tmp = __enumConverters.embeddingStatusToCode(status);
    _statement.bindLong(_argIndex, _tmp);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ContentEntity>>() {
      @Override
      @NonNull
      public List<ContentEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRawText = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_text");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final int _cursorIndexOfImportTime = CursorUtil.getColumnIndexOrThrow(_cursor, "import_time");
          final int _cursorIndexOfAnalysisJson = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_json");
          final int _cursorIndexOfAnalysisStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis_status");
          final int _cursorIndexOfEmbeddingVector = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_vector");
          final int _cursorIndexOfEmbeddingStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "embedding_status");
          final int _cursorIndexOfRemark = CursorUtil.getColumnIndexOrThrow(_cursor, "remark");
          final int _cursorIndexOfImportChannel = CursorUtil.getColumnIndexOrThrow(_cursor, "import_channel");
          final List<ContentEntity> _result = new ArrayList<ContentEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ContentEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpRawText;
            _tmpRawText = _cursor.getString(_cursorIndexOfRawText);
            final String _tmpSource;
            if (_cursor.isNull(_cursorIndexOfSource)) {
              _tmpSource = null;
            } else {
              _tmpSource = _cursor.getString(_cursorIndexOfSource);
            }
            final long _tmpImportTime;
            _tmpImportTime = _cursor.getLong(_cursorIndexOfImportTime);
            final String _tmpAnalysisJson;
            if (_cursor.isNull(_cursorIndexOfAnalysisJson)) {
              _tmpAnalysisJson = null;
            } else {
              _tmpAnalysisJson = _cursor.getString(_cursorIndexOfAnalysisJson);
            }
            final AnalysisStatus _tmpAnalysisStatus;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfAnalysisStatus);
            _tmpAnalysisStatus = __enumConverters.codeToAnalysisStatus(_tmp_1);
            final byte[] _tmpEmbeddingVector;
            if (_cursor.isNull(_cursorIndexOfEmbeddingVector)) {
              _tmpEmbeddingVector = null;
            } else {
              _tmpEmbeddingVector = _cursor.getBlob(_cursorIndexOfEmbeddingVector);
            }
            final EmbeddingStatus _tmpEmbeddingStatus;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfEmbeddingStatus);
            _tmpEmbeddingStatus = __enumConverters.codeToEmbeddingStatus(_tmp_2);
            final String _tmpRemark;
            if (_cursor.isNull(_cursorIndexOfRemark)) {
              _tmpRemark = null;
            } else {
              _tmpRemark = _cursor.getString(_cursorIndexOfRemark);
            }
            final ImportChannel _tmpImportChannel;
            final String _tmp_3;
            _tmp_3 = _cursor.getString(_cursorIndexOfImportChannel);
            _tmpImportChannel = __enumConverters.wireToImportChannel(_tmp_3);
            _item = new ContentEntity(_tmpId,_tmpRawText,_tmpSource,_tmpImportTime,_tmpAnalysisJson,_tmpAnalysisStatus,_tmpEmbeddingVector,_tmpEmbeddingStatus,_tmpRemark,_tmpImportChannel);
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
  public Object count(final Continuation<? super Long> $completion) {
    final String _sql = "SELECT COUNT(*) FROM content_table";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Long _result;
          if (_cursor.moveToFirst()) {
            final long _tmp;
            _tmp = _cursor.getLong(0);
            _result = _tmp;
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
  public Flow<Long> observeCount() {
    final String _sql = "SELECT COUNT(*) FROM content_table";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"content_table"}, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Long _result;
          if (_cursor.moveToFirst()) {
            final long _tmp;
            _tmp = _cursor.getLong(0);
            _result = _tmp;
          } else {
            _result = 0L;
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
