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
import com.zhijing.data.entity.PetStatusEntity;
import com.zhijing.data.model.PetPhase;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class PetStatusDao_Impl implements PetStatusDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<PetStatusEntity> __insertionAdapterOfPetStatusEntity;

  private final EnumConverters __enumConverters = new EnumConverters();

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public PetStatusDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPetStatusEntity = new EntityInsertionAdapter<PetStatusEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `pet_status_table` (`id`,`phase`,`total_contents`,`active_days`,`accessories`,`last_display_time`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PetStatusEntity entity) {
        statement.bindLong(1, entity.getId());
        final int _tmp = __enumConverters.petPhaseToCode(entity.getPhase());
        statement.bindLong(2, _tmp);
        statement.bindLong(3, entity.getTotalContents());
        statement.bindLong(4, entity.getActiveDays());
        if (entity.getAccessories() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getAccessories());
        }
        statement.bindLong(6, entity.getLastDisplayTime());
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM pet_status_table";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final PetStatusEntity entity, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfPetStatusEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
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
  public Object get(final Continuation<? super PetStatusEntity> $completion) {
    final String _sql = "SELECT * FROM pet_status_table WHERE id = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<PetStatusEntity>() {
      @Override
      @Nullable
      public PetStatusEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPhase = CursorUtil.getColumnIndexOrThrow(_cursor, "phase");
          final int _cursorIndexOfTotalContents = CursorUtil.getColumnIndexOrThrow(_cursor, "total_contents");
          final int _cursorIndexOfActiveDays = CursorUtil.getColumnIndexOrThrow(_cursor, "active_days");
          final int _cursorIndexOfAccessories = CursorUtil.getColumnIndexOrThrow(_cursor, "accessories");
          final int _cursorIndexOfLastDisplayTime = CursorUtil.getColumnIndexOrThrow(_cursor, "last_display_time");
          final PetStatusEntity _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final PetPhase _tmpPhase;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfPhase);
            _tmpPhase = __enumConverters.codeToPetPhase(_tmp);
            final int _tmpTotalContents;
            _tmpTotalContents = _cursor.getInt(_cursorIndexOfTotalContents);
            final int _tmpActiveDays;
            _tmpActiveDays = _cursor.getInt(_cursorIndexOfActiveDays);
            final String _tmpAccessories;
            if (_cursor.isNull(_cursorIndexOfAccessories)) {
              _tmpAccessories = null;
            } else {
              _tmpAccessories = _cursor.getString(_cursorIndexOfAccessories);
            }
            final long _tmpLastDisplayTime;
            _tmpLastDisplayTime = _cursor.getLong(_cursorIndexOfLastDisplayTime);
            _result = new PetStatusEntity(_tmpId,_tmpPhase,_tmpTotalContents,_tmpActiveDays,_tmpAccessories,_tmpLastDisplayTime);
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
  public Flow<PetStatusEntity> observe() {
    final String _sql = "SELECT * FROM pet_status_table WHERE id = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"pet_status_table"}, new Callable<PetStatusEntity>() {
      @Override
      @Nullable
      public PetStatusEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPhase = CursorUtil.getColumnIndexOrThrow(_cursor, "phase");
          final int _cursorIndexOfTotalContents = CursorUtil.getColumnIndexOrThrow(_cursor, "total_contents");
          final int _cursorIndexOfActiveDays = CursorUtil.getColumnIndexOrThrow(_cursor, "active_days");
          final int _cursorIndexOfAccessories = CursorUtil.getColumnIndexOrThrow(_cursor, "accessories");
          final int _cursorIndexOfLastDisplayTime = CursorUtil.getColumnIndexOrThrow(_cursor, "last_display_time");
          final PetStatusEntity _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final PetPhase _tmpPhase;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfPhase);
            _tmpPhase = __enumConverters.codeToPetPhase(_tmp);
            final int _tmpTotalContents;
            _tmpTotalContents = _cursor.getInt(_cursorIndexOfTotalContents);
            final int _tmpActiveDays;
            _tmpActiveDays = _cursor.getInt(_cursorIndexOfActiveDays);
            final String _tmpAccessories;
            if (_cursor.isNull(_cursorIndexOfAccessories)) {
              _tmpAccessories = null;
            } else {
              _tmpAccessories = _cursor.getString(_cursorIndexOfAccessories);
            }
            final long _tmpLastDisplayTime;
            _tmpLastDisplayTime = _cursor.getLong(_cursorIndexOfLastDisplayTime);
            _result = new PetStatusEntity(_tmpId,_tmpPhase,_tmpTotalContents,_tmpActiveDays,_tmpAccessories,_tmpLastDisplayTime);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
