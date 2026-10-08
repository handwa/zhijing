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
import com.zhijing.data.entity.WakePlanEntity;
import com.zhijing.data.model.WakeMode;
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
public final class WakePlanDao_Impl implements WakePlanDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<WakePlanEntity> __insertionAdapterOfWakePlanEntity;

  private final EnumConverters __enumConverters = new EnumConverters();

  private final SharedSQLiteStatement __preparedStmtOfUpdateSchedule;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public WakePlanDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfWakePlanEntity = new EntityInsertionAdapter<WakePlanEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `wake_plan_table` (`id`,`enabled`,`cycle_type`,`trigger_time`,`scope`,`mode`,`daily_question_num`,`next_run_time`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WakePlanEntity entity) {
        statement.bindLong(1, entity.getId());
        final int _tmp = entity.getEnabled() ? 1 : 0;
        statement.bindLong(2, _tmp);
        if (entity.getCycleType() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getCycleType());
        }
        if (entity.getTriggerTime() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getTriggerTime());
        }
        if (entity.getScope() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getScope());
        }
        final String _tmp_1 = __enumConverters.wakeModeToWire(entity.getMode());
        statement.bindString(6, _tmp_1);
        statement.bindLong(7, entity.getDailyQuestionNum());
        statement.bindLong(8, entity.getNextRunTime());
      }
    };
    this.__preparedStmtOfUpdateSchedule = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE wake_plan_table SET enabled = ?, next_run_time = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM wake_plan_table WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM wake_plan_table";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final WakePlanEntity entity, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfWakePlanEntity.insertAndReturnId(entity);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateSchedule(final long id, final boolean enabled, final long nextRunTime,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateSchedule.acquire();
        int _argIndex = 1;
        final int _tmp = enabled ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, nextRunTime);
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
          __preparedStmtOfUpdateSchedule.release(_stmt);
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
  public Object findById(final long id, final Continuation<? super WakePlanEntity> $completion) {
    final String _sql = "SELECT * FROM wake_plan_table WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<WakePlanEntity>() {
      @Override
      @Nullable
      public WakePlanEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
          final int _cursorIndexOfCycleType = CursorUtil.getColumnIndexOrThrow(_cursor, "cycle_type");
          final int _cursorIndexOfTriggerTime = CursorUtil.getColumnIndexOrThrow(_cursor, "trigger_time");
          final int _cursorIndexOfScope = CursorUtil.getColumnIndexOrThrow(_cursor, "scope");
          final int _cursorIndexOfMode = CursorUtil.getColumnIndexOrThrow(_cursor, "mode");
          final int _cursorIndexOfDailyQuestionNum = CursorUtil.getColumnIndexOrThrow(_cursor, "daily_question_num");
          final int _cursorIndexOfNextRunTime = CursorUtil.getColumnIndexOrThrow(_cursor, "next_run_time");
          final WakePlanEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final boolean _tmpEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfEnabled);
            _tmpEnabled = _tmp != 0;
            final String _tmpCycleType;
            if (_cursor.isNull(_cursorIndexOfCycleType)) {
              _tmpCycleType = null;
            } else {
              _tmpCycleType = _cursor.getString(_cursorIndexOfCycleType);
            }
            final String _tmpTriggerTime;
            if (_cursor.isNull(_cursorIndexOfTriggerTime)) {
              _tmpTriggerTime = null;
            } else {
              _tmpTriggerTime = _cursor.getString(_cursorIndexOfTriggerTime);
            }
            final String _tmpScope;
            if (_cursor.isNull(_cursorIndexOfScope)) {
              _tmpScope = null;
            } else {
              _tmpScope = _cursor.getString(_cursorIndexOfScope);
            }
            final WakeMode _tmpMode;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfMode);
            _tmpMode = __enumConverters.wireToWakeMode(_tmp_1);
            final int _tmpDailyQuestionNum;
            _tmpDailyQuestionNum = _cursor.getInt(_cursorIndexOfDailyQuestionNum);
            final long _tmpNextRunTime;
            _tmpNextRunTime = _cursor.getLong(_cursorIndexOfNextRunTime);
            _result = new WakePlanEntity(_tmpId,_tmpEnabled,_tmpCycleType,_tmpTriggerTime,_tmpScope,_tmpMode,_tmpDailyQuestionNum,_tmpNextRunTime);
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
  public Flow<List<WakePlanEntity>> observeAll() {
    final String _sql = "SELECT * FROM wake_plan_table ORDER BY id ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"wake_plan_table"}, new Callable<List<WakePlanEntity>>() {
      @Override
      @NonNull
      public List<WakePlanEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
          final int _cursorIndexOfCycleType = CursorUtil.getColumnIndexOrThrow(_cursor, "cycle_type");
          final int _cursorIndexOfTriggerTime = CursorUtil.getColumnIndexOrThrow(_cursor, "trigger_time");
          final int _cursorIndexOfScope = CursorUtil.getColumnIndexOrThrow(_cursor, "scope");
          final int _cursorIndexOfMode = CursorUtil.getColumnIndexOrThrow(_cursor, "mode");
          final int _cursorIndexOfDailyQuestionNum = CursorUtil.getColumnIndexOrThrow(_cursor, "daily_question_num");
          final int _cursorIndexOfNextRunTime = CursorUtil.getColumnIndexOrThrow(_cursor, "next_run_time");
          final List<WakePlanEntity> _result = new ArrayList<WakePlanEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final WakePlanEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final boolean _tmpEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfEnabled);
            _tmpEnabled = _tmp != 0;
            final String _tmpCycleType;
            if (_cursor.isNull(_cursorIndexOfCycleType)) {
              _tmpCycleType = null;
            } else {
              _tmpCycleType = _cursor.getString(_cursorIndexOfCycleType);
            }
            final String _tmpTriggerTime;
            if (_cursor.isNull(_cursorIndexOfTriggerTime)) {
              _tmpTriggerTime = null;
            } else {
              _tmpTriggerTime = _cursor.getString(_cursorIndexOfTriggerTime);
            }
            final String _tmpScope;
            if (_cursor.isNull(_cursorIndexOfScope)) {
              _tmpScope = null;
            } else {
              _tmpScope = _cursor.getString(_cursorIndexOfScope);
            }
            final WakeMode _tmpMode;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfMode);
            _tmpMode = __enumConverters.wireToWakeMode(_tmp_1);
            final int _tmpDailyQuestionNum;
            _tmpDailyQuestionNum = _cursor.getInt(_cursorIndexOfDailyQuestionNum);
            final long _tmpNextRunTime;
            _tmpNextRunTime = _cursor.getLong(_cursorIndexOfNextRunTime);
            _item = new WakePlanEntity(_tmpId,_tmpEnabled,_tmpCycleType,_tmpTriggerTime,_tmpScope,_tmpMode,_tmpDailyQuestionNum,_tmpNextRunTime);
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
  public Object findEnabled(final Continuation<? super List<WakePlanEntity>> $completion) {
    final String _sql = "SELECT * FROM wake_plan_table WHERE enabled = 1 ORDER BY next_run_time ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<WakePlanEntity>>() {
      @Override
      @NonNull
      public List<WakePlanEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
          final int _cursorIndexOfCycleType = CursorUtil.getColumnIndexOrThrow(_cursor, "cycle_type");
          final int _cursorIndexOfTriggerTime = CursorUtil.getColumnIndexOrThrow(_cursor, "trigger_time");
          final int _cursorIndexOfScope = CursorUtil.getColumnIndexOrThrow(_cursor, "scope");
          final int _cursorIndexOfMode = CursorUtil.getColumnIndexOrThrow(_cursor, "mode");
          final int _cursorIndexOfDailyQuestionNum = CursorUtil.getColumnIndexOrThrow(_cursor, "daily_question_num");
          final int _cursorIndexOfNextRunTime = CursorUtil.getColumnIndexOrThrow(_cursor, "next_run_time");
          final List<WakePlanEntity> _result = new ArrayList<WakePlanEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final WakePlanEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final boolean _tmpEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfEnabled);
            _tmpEnabled = _tmp != 0;
            final String _tmpCycleType;
            if (_cursor.isNull(_cursorIndexOfCycleType)) {
              _tmpCycleType = null;
            } else {
              _tmpCycleType = _cursor.getString(_cursorIndexOfCycleType);
            }
            final String _tmpTriggerTime;
            if (_cursor.isNull(_cursorIndexOfTriggerTime)) {
              _tmpTriggerTime = null;
            } else {
              _tmpTriggerTime = _cursor.getString(_cursorIndexOfTriggerTime);
            }
            final String _tmpScope;
            if (_cursor.isNull(_cursorIndexOfScope)) {
              _tmpScope = null;
            } else {
              _tmpScope = _cursor.getString(_cursorIndexOfScope);
            }
            final WakeMode _tmpMode;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfMode);
            _tmpMode = __enumConverters.wireToWakeMode(_tmp_1);
            final int _tmpDailyQuestionNum;
            _tmpDailyQuestionNum = _cursor.getInt(_cursorIndexOfDailyQuestionNum);
            final long _tmpNextRunTime;
            _tmpNextRunTime = _cursor.getLong(_cursorIndexOfNextRunTime);
            _item = new WakePlanEntity(_tmpId,_tmpEnabled,_tmpCycleType,_tmpTriggerTime,_tmpScope,_tmpMode,_tmpDailyQuestionNum,_tmpNextRunTime);
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
