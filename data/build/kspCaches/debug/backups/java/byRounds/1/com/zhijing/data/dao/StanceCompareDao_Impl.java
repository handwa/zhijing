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
import com.zhijing.data.entity.StanceCompareEntity;
import com.zhijing.data.model.StanceStatus;
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
public final class StanceCompareDao_Impl implements StanceCompareDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<StanceCompareEntity> __insertionAdapterOfStanceCompareEntity;

  private final ListConverters __listConverters = new ListConverters();

  private final EnumConverters __enumConverters = new EnumConverters();

  private final SharedSQLiteStatement __preparedStmtOfUpdateStatus;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByTopic;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public StanceCompareDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfStanceCompareEntity = new EntityInsertionAdapter<StanceCompareEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `stance_compare_table` (`id`,`topic_tag`,`side_a_content_ids`,`side_b_content_ids`,`side_a_summary`,`side_b_summary`,`status`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final StanceCompareEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTopicTag());
        final String _tmp = __listConverters.longListToCsv(entity.getSideAContentIds());
        if (_tmp == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, _tmp);
        }
        final String _tmp_1 = __listConverters.longListToCsv(entity.getSideBContentIds());
        if (_tmp_1 == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, _tmp_1);
        }
        if (entity.getSideASummary() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getSideASummary());
        }
        if (entity.getSideBSummary() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getSideBSummary());
        }
        final int _tmp_2 = __enumConverters.stanceStatusToCode(entity.getStatus());
        statement.bindLong(7, _tmp_2);
      }
    };
    this.__preparedStmtOfUpdateStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE stance_compare_table SET status = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM stance_compare_table WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteByTopic = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM stance_compare_table WHERE topic_tag = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM stance_compare_table";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final StanceCompareEntity entity,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfStanceCompareEntity.insertAndReturnId(entity);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateStatus(final long id, final StanceStatus status,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStatus.acquire();
        int _argIndex = 1;
        final int _tmp = __enumConverters.stanceStatusToCode(status);
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
  public Object deleteByTopic(final String topicTag, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByTopic.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, topicTag);
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
          __preparedStmtOfDeleteByTopic.release(_stmt);
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
  public Object findById(final long id,
      final Continuation<? super StanceCompareEntity> $completion) {
    final String _sql = "SELECT * FROM stance_compare_table WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<StanceCompareEntity>() {
      @Override
      @Nullable
      public StanceCompareEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTopicTag = CursorUtil.getColumnIndexOrThrow(_cursor, "topic_tag");
          final int _cursorIndexOfSideAContentIds = CursorUtil.getColumnIndexOrThrow(_cursor, "side_a_content_ids");
          final int _cursorIndexOfSideBContentIds = CursorUtil.getColumnIndexOrThrow(_cursor, "side_b_content_ids");
          final int _cursorIndexOfSideASummary = CursorUtil.getColumnIndexOrThrow(_cursor, "side_a_summary");
          final int _cursorIndexOfSideBSummary = CursorUtil.getColumnIndexOrThrow(_cursor, "side_b_summary");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final StanceCompareEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTopicTag;
            _tmpTopicTag = _cursor.getString(_cursorIndexOfTopicTag);
            final List<Long> _tmpSideAContentIds;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfSideAContentIds)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfSideAContentIds);
            }
            _tmpSideAContentIds = __listConverters.csvToLongList(_tmp);
            final List<Long> _tmpSideBContentIds;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfSideBContentIds)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfSideBContentIds);
            }
            _tmpSideBContentIds = __listConverters.csvToLongList(_tmp_1);
            final String _tmpSideASummary;
            if (_cursor.isNull(_cursorIndexOfSideASummary)) {
              _tmpSideASummary = null;
            } else {
              _tmpSideASummary = _cursor.getString(_cursorIndexOfSideASummary);
            }
            final String _tmpSideBSummary;
            if (_cursor.isNull(_cursorIndexOfSideBSummary)) {
              _tmpSideBSummary = null;
            } else {
              _tmpSideBSummary = _cursor.getString(_cursorIndexOfSideBSummary);
            }
            final StanceStatus _tmpStatus;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfStatus);
            _tmpStatus = __enumConverters.codeToStanceStatus(_tmp_2);
            _result = new StanceCompareEntity(_tmpId,_tmpTopicTag,_tmpSideAContentIds,_tmpSideBContentIds,_tmpSideASummary,_tmpSideBSummary,_tmpStatus);
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
  public Object findByTopic(final String topicTag,
      final Continuation<? super List<StanceCompareEntity>> $completion) {
    final String _sql = "SELECT * FROM stance_compare_table WHERE topic_tag = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, topicTag);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<StanceCompareEntity>>() {
      @Override
      @NonNull
      public List<StanceCompareEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTopicTag = CursorUtil.getColumnIndexOrThrow(_cursor, "topic_tag");
          final int _cursorIndexOfSideAContentIds = CursorUtil.getColumnIndexOrThrow(_cursor, "side_a_content_ids");
          final int _cursorIndexOfSideBContentIds = CursorUtil.getColumnIndexOrThrow(_cursor, "side_b_content_ids");
          final int _cursorIndexOfSideASummary = CursorUtil.getColumnIndexOrThrow(_cursor, "side_a_summary");
          final int _cursorIndexOfSideBSummary = CursorUtil.getColumnIndexOrThrow(_cursor, "side_b_summary");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final List<StanceCompareEntity> _result = new ArrayList<StanceCompareEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final StanceCompareEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTopicTag;
            _tmpTopicTag = _cursor.getString(_cursorIndexOfTopicTag);
            final List<Long> _tmpSideAContentIds;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfSideAContentIds)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfSideAContentIds);
            }
            _tmpSideAContentIds = __listConverters.csvToLongList(_tmp);
            final List<Long> _tmpSideBContentIds;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfSideBContentIds)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfSideBContentIds);
            }
            _tmpSideBContentIds = __listConverters.csvToLongList(_tmp_1);
            final String _tmpSideASummary;
            if (_cursor.isNull(_cursorIndexOfSideASummary)) {
              _tmpSideASummary = null;
            } else {
              _tmpSideASummary = _cursor.getString(_cursorIndexOfSideASummary);
            }
            final String _tmpSideBSummary;
            if (_cursor.isNull(_cursorIndexOfSideBSummary)) {
              _tmpSideBSummary = null;
            } else {
              _tmpSideBSummary = _cursor.getString(_cursorIndexOfSideBSummary);
            }
            final StanceStatus _tmpStatus;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfStatus);
            _tmpStatus = __enumConverters.codeToStanceStatus(_tmp_2);
            _item = new StanceCompareEntity(_tmpId,_tmpTopicTag,_tmpSideAContentIds,_tmpSideBContentIds,_tmpSideASummary,_tmpSideBSummary,_tmpStatus);
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
  public Flow<List<StanceCompareEntity>> observeByStatus(final StanceStatus status) {
    final String _sql = "SELECT * FROM stance_compare_table WHERE status = ? ORDER BY id DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final int _tmp = __enumConverters.stanceStatusToCode(status);
    _statement.bindLong(_argIndex, _tmp);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"stance_compare_table"}, new Callable<List<StanceCompareEntity>>() {
      @Override
      @NonNull
      public List<StanceCompareEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTopicTag = CursorUtil.getColumnIndexOrThrow(_cursor, "topic_tag");
          final int _cursorIndexOfSideAContentIds = CursorUtil.getColumnIndexOrThrow(_cursor, "side_a_content_ids");
          final int _cursorIndexOfSideBContentIds = CursorUtil.getColumnIndexOrThrow(_cursor, "side_b_content_ids");
          final int _cursorIndexOfSideASummary = CursorUtil.getColumnIndexOrThrow(_cursor, "side_a_summary");
          final int _cursorIndexOfSideBSummary = CursorUtil.getColumnIndexOrThrow(_cursor, "side_b_summary");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final List<StanceCompareEntity> _result = new ArrayList<StanceCompareEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final StanceCompareEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTopicTag;
            _tmpTopicTag = _cursor.getString(_cursorIndexOfTopicTag);
            final List<Long> _tmpSideAContentIds;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfSideAContentIds)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfSideAContentIds);
            }
            _tmpSideAContentIds = __listConverters.csvToLongList(_tmp_1);
            final List<Long> _tmpSideBContentIds;
            final String _tmp_2;
            if (_cursor.isNull(_cursorIndexOfSideBContentIds)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getString(_cursorIndexOfSideBContentIds);
            }
            _tmpSideBContentIds = __listConverters.csvToLongList(_tmp_2);
            final String _tmpSideASummary;
            if (_cursor.isNull(_cursorIndexOfSideASummary)) {
              _tmpSideASummary = null;
            } else {
              _tmpSideASummary = _cursor.getString(_cursorIndexOfSideASummary);
            }
            final String _tmpSideBSummary;
            if (_cursor.isNull(_cursorIndexOfSideBSummary)) {
              _tmpSideBSummary = null;
            } else {
              _tmpSideBSummary = _cursor.getString(_cursorIndexOfSideBSummary);
            }
            final StanceStatus _tmpStatus;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfStatus);
            _tmpStatus = __enumConverters.codeToStanceStatus(_tmp_3);
            _item = new StanceCompareEntity(_tmpId,_tmpTopicTag,_tmpSideAContentIds,_tmpSideBContentIds,_tmpSideASummary,_tmpSideBSummary,_tmpStatus);
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
