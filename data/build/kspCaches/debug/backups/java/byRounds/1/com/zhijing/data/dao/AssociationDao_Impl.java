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
import com.zhijing.data.entity.AssociationEntity;
import com.zhijing.data.model.AssociationFeedback;
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
public final class AssociationDao_Impl implements AssociationDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<AssociationEntity> __insertionAdapterOfAssociationEntity;

  private final EnumConverters __enumConverters = new EnumConverters();

  private final SharedSQLiteStatement __preparedStmtOfUpdateFeedback;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByContentId;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public AssociationDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfAssociationEntity = new EntityInsertionAdapter<AssociationEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `association_table` (`id`,`source_content_id`,`target_content_id`,`similarity_score`,`shared_topics`,`evidence_snippet`,`feedback`,`weight`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AssociationEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getSourceContentId());
        statement.bindLong(3, entity.getTargetContentId());
        statement.bindDouble(4, entity.getSimilarityScore());
        if (entity.getSharedTopics() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getSharedTopics());
        }
        if (entity.getEvidenceSnippet() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getEvidenceSnippet());
        }
        final int _tmp = __enumConverters.associationFeedbackToCode(entity.getFeedback());
        statement.bindLong(7, _tmp);
        statement.bindDouble(8, entity.getWeight());
      }
    };
    this.__preparedStmtOfUpdateFeedback = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE association_table SET feedback = ?, weight = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM association_table WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteByContentId = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM association_table WHERE source_content_id = ? OR target_content_id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM association_table";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final AssociationEntity entity,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfAssociationEntity.insertAndReturnId(entity);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateFeedback(final long id, final AssociationFeedback feedback,
      final double weight, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateFeedback.acquire();
        int _argIndex = 1;
        final int _tmp = __enumConverters.associationFeedbackToCode(feedback);
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindDouble(_argIndex, weight);
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
          __preparedStmtOfUpdateFeedback.release(_stmt);
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
        _argIndex = 2;
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
  public Object findById(final long id, final Continuation<? super AssociationEntity> $completion) {
    final String _sql = "SELECT * FROM association_table WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<AssociationEntity>() {
      @Override
      @Nullable
      public AssociationEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSourceContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "source_content_id");
          final int _cursorIndexOfTargetContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "target_content_id");
          final int _cursorIndexOfSimilarityScore = CursorUtil.getColumnIndexOrThrow(_cursor, "similarity_score");
          final int _cursorIndexOfSharedTopics = CursorUtil.getColumnIndexOrThrow(_cursor, "shared_topics");
          final int _cursorIndexOfEvidenceSnippet = CursorUtil.getColumnIndexOrThrow(_cursor, "evidence_snippet");
          final int _cursorIndexOfFeedback = CursorUtil.getColumnIndexOrThrow(_cursor, "feedback");
          final int _cursorIndexOfWeight = CursorUtil.getColumnIndexOrThrow(_cursor, "weight");
          final AssociationEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpSourceContentId;
            _tmpSourceContentId = _cursor.getLong(_cursorIndexOfSourceContentId);
            final long _tmpTargetContentId;
            _tmpTargetContentId = _cursor.getLong(_cursorIndexOfTargetContentId);
            final float _tmpSimilarityScore;
            _tmpSimilarityScore = _cursor.getFloat(_cursorIndexOfSimilarityScore);
            final String _tmpSharedTopics;
            if (_cursor.isNull(_cursorIndexOfSharedTopics)) {
              _tmpSharedTopics = null;
            } else {
              _tmpSharedTopics = _cursor.getString(_cursorIndexOfSharedTopics);
            }
            final String _tmpEvidenceSnippet;
            if (_cursor.isNull(_cursorIndexOfEvidenceSnippet)) {
              _tmpEvidenceSnippet = null;
            } else {
              _tmpEvidenceSnippet = _cursor.getString(_cursorIndexOfEvidenceSnippet);
            }
            final AssociationFeedback _tmpFeedback;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfFeedback);
            _tmpFeedback = __enumConverters.codeToAssociationFeedback(_tmp);
            final double _tmpWeight;
            _tmpWeight = _cursor.getDouble(_cursorIndexOfWeight);
            _result = new AssociationEntity(_tmpId,_tmpSourceContentId,_tmpTargetContentId,_tmpSimilarityScore,_tmpSharedTopics,_tmpEvidenceSnippet,_tmpFeedback,_tmpWeight);
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
  public Object findRelatedTo(final long contentId,
      final Continuation<? super List<AssociationEntity>> $completion) {
    final String _sql = "SELECT * FROM association_table WHERE source_content_id = ? OR target_content_id = ? ORDER BY similarity_score DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, contentId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, contentId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<AssociationEntity>>() {
      @Override
      @NonNull
      public List<AssociationEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSourceContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "source_content_id");
          final int _cursorIndexOfTargetContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "target_content_id");
          final int _cursorIndexOfSimilarityScore = CursorUtil.getColumnIndexOrThrow(_cursor, "similarity_score");
          final int _cursorIndexOfSharedTopics = CursorUtil.getColumnIndexOrThrow(_cursor, "shared_topics");
          final int _cursorIndexOfEvidenceSnippet = CursorUtil.getColumnIndexOrThrow(_cursor, "evidence_snippet");
          final int _cursorIndexOfFeedback = CursorUtil.getColumnIndexOrThrow(_cursor, "feedback");
          final int _cursorIndexOfWeight = CursorUtil.getColumnIndexOrThrow(_cursor, "weight");
          final List<AssociationEntity> _result = new ArrayList<AssociationEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AssociationEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpSourceContentId;
            _tmpSourceContentId = _cursor.getLong(_cursorIndexOfSourceContentId);
            final long _tmpTargetContentId;
            _tmpTargetContentId = _cursor.getLong(_cursorIndexOfTargetContentId);
            final float _tmpSimilarityScore;
            _tmpSimilarityScore = _cursor.getFloat(_cursorIndexOfSimilarityScore);
            final String _tmpSharedTopics;
            if (_cursor.isNull(_cursorIndexOfSharedTopics)) {
              _tmpSharedTopics = null;
            } else {
              _tmpSharedTopics = _cursor.getString(_cursorIndexOfSharedTopics);
            }
            final String _tmpEvidenceSnippet;
            if (_cursor.isNull(_cursorIndexOfEvidenceSnippet)) {
              _tmpEvidenceSnippet = null;
            } else {
              _tmpEvidenceSnippet = _cursor.getString(_cursorIndexOfEvidenceSnippet);
            }
            final AssociationFeedback _tmpFeedback;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfFeedback);
            _tmpFeedback = __enumConverters.codeToAssociationFeedback(_tmp);
            final double _tmpWeight;
            _tmpWeight = _cursor.getDouble(_cursorIndexOfWeight);
            _item = new AssociationEntity(_tmpId,_tmpSourceContentId,_tmpTargetContentId,_tmpSimilarityScore,_tmpSharedTopics,_tmpEvidenceSnippet,_tmpFeedback,_tmpWeight);
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
  public Flow<List<AssociationEntity>> observeAll() {
    final String _sql = "SELECT * FROM association_table ORDER BY similarity_score DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"association_table"}, new Callable<List<AssociationEntity>>() {
      @Override
      @NonNull
      public List<AssociationEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSourceContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "source_content_id");
          final int _cursorIndexOfTargetContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "target_content_id");
          final int _cursorIndexOfSimilarityScore = CursorUtil.getColumnIndexOrThrow(_cursor, "similarity_score");
          final int _cursorIndexOfSharedTopics = CursorUtil.getColumnIndexOrThrow(_cursor, "shared_topics");
          final int _cursorIndexOfEvidenceSnippet = CursorUtil.getColumnIndexOrThrow(_cursor, "evidence_snippet");
          final int _cursorIndexOfFeedback = CursorUtil.getColumnIndexOrThrow(_cursor, "feedback");
          final int _cursorIndexOfWeight = CursorUtil.getColumnIndexOrThrow(_cursor, "weight");
          final List<AssociationEntity> _result = new ArrayList<AssociationEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AssociationEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpSourceContentId;
            _tmpSourceContentId = _cursor.getLong(_cursorIndexOfSourceContentId);
            final long _tmpTargetContentId;
            _tmpTargetContentId = _cursor.getLong(_cursorIndexOfTargetContentId);
            final float _tmpSimilarityScore;
            _tmpSimilarityScore = _cursor.getFloat(_cursorIndexOfSimilarityScore);
            final String _tmpSharedTopics;
            if (_cursor.isNull(_cursorIndexOfSharedTopics)) {
              _tmpSharedTopics = null;
            } else {
              _tmpSharedTopics = _cursor.getString(_cursorIndexOfSharedTopics);
            }
            final String _tmpEvidenceSnippet;
            if (_cursor.isNull(_cursorIndexOfEvidenceSnippet)) {
              _tmpEvidenceSnippet = null;
            } else {
              _tmpEvidenceSnippet = _cursor.getString(_cursorIndexOfEvidenceSnippet);
            }
            final AssociationFeedback _tmpFeedback;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfFeedback);
            _tmpFeedback = __enumConverters.codeToAssociationFeedback(_tmp);
            final double _tmpWeight;
            _tmpWeight = _cursor.getDouble(_cursorIndexOfWeight);
            _item = new AssociationEntity(_tmpId,_tmpSourceContentId,_tmpTargetContentId,_tmpSimilarityScore,_tmpSharedTopics,_tmpEvidenceSnippet,_tmpFeedback,_tmpWeight);
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
