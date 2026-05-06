package com.zhijing.graph;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.zhijing.graph.dao.KnowledgeEntityDao;
import com.zhijing.graph.dao.KnowledgeEntityDao_Impl;
import com.zhijing.graph.dao.KnowledgeRelationDao;
import com.zhijing.graph.dao.KnowledgeRelationDao_Impl;
import com.zhijing.graph.dao.UserContentDao;
import com.zhijing.graph.dao.UserContentDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ZhijingDatabase_Impl extends ZhijingDatabase {
  private volatile KnowledgeEntityDao _knowledgeEntityDao;

  private volatile KnowledgeRelationDao _knowledgeRelationDao;

  private volatile UserContentDao _userContentDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `knowledge_entity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `type` TEXT, `weight` REAL NOT NULL, `createdAt` INTEGER NOT NULL, `lastSeenAt` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `knowledge_relation` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fromEntityId` INTEGER NOT NULL, `toEntityId` INTEGER NOT NULL, `relationType` TEXT, `strength` REAL NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`fromEntityId`) REFERENCES `knowledge_entity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`toEntityId`) REFERENCES `knowledge_entity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_relation_fromEntityId` ON `knowledge_relation` (`fromEntityId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_relation_toEntityId` ON `knowledge_relation` (`toEntityId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_content` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content` TEXT, `sourceApp` TEXT, `keywords` TEXT, `entities` TEXT, `createdAt` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'b3aeeab7a0ebeaeb37c121a586ef57a4')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `knowledge_entity`");
        db.execSQL("DROP TABLE IF EXISTS `knowledge_relation`");
        db.execSQL("DROP TABLE IF EXISTS `user_content`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsKnowledgeEntity = new HashMap<String, TableInfo.Column>(6);
        _columnsKnowledgeEntity.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeEntity.put("name", new TableInfo.Column("name", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeEntity.put("type", new TableInfo.Column("type", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeEntity.put("weight", new TableInfo.Column("weight", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeEntity.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeEntity.put("lastSeenAt", new TableInfo.Column("lastSeenAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysKnowledgeEntity = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesKnowledgeEntity = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoKnowledgeEntity = new TableInfo("knowledge_entity", _columnsKnowledgeEntity, _foreignKeysKnowledgeEntity, _indicesKnowledgeEntity);
        final TableInfo _existingKnowledgeEntity = TableInfo.read(db, "knowledge_entity");
        if (!_infoKnowledgeEntity.equals(_existingKnowledgeEntity)) {
          return new RoomOpenHelper.ValidationResult(false, "knowledge_entity(com.zhijing.graph.entity.KnowledgeEntity).\n"
                  + " Expected:\n" + _infoKnowledgeEntity + "\n"
                  + " Found:\n" + _existingKnowledgeEntity);
        }
        final HashMap<String, TableInfo.Column> _columnsKnowledgeRelation = new HashMap<String, TableInfo.Column>(6);
        _columnsKnowledgeRelation.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeRelation.put("fromEntityId", new TableInfo.Column("fromEntityId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeRelation.put("toEntityId", new TableInfo.Column("toEntityId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeRelation.put("relationType", new TableInfo.Column("relationType", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeRelation.put("strength", new TableInfo.Column("strength", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeRelation.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysKnowledgeRelation = new HashSet<TableInfo.ForeignKey>(2);
        _foreignKeysKnowledgeRelation.add(new TableInfo.ForeignKey("knowledge_entity", "CASCADE", "NO ACTION", Arrays.asList("fromEntityId"), Arrays.asList("id")));
        _foreignKeysKnowledgeRelation.add(new TableInfo.ForeignKey("knowledge_entity", "CASCADE", "NO ACTION", Arrays.asList("toEntityId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesKnowledgeRelation = new HashSet<TableInfo.Index>(2);
        _indicesKnowledgeRelation.add(new TableInfo.Index("index_knowledge_relation_fromEntityId", false, Arrays.asList("fromEntityId"), Arrays.asList("ASC")));
        _indicesKnowledgeRelation.add(new TableInfo.Index("index_knowledge_relation_toEntityId", false, Arrays.asList("toEntityId"), Arrays.asList("ASC")));
        final TableInfo _infoKnowledgeRelation = new TableInfo("knowledge_relation", _columnsKnowledgeRelation, _foreignKeysKnowledgeRelation, _indicesKnowledgeRelation);
        final TableInfo _existingKnowledgeRelation = TableInfo.read(db, "knowledge_relation");
        if (!_infoKnowledgeRelation.equals(_existingKnowledgeRelation)) {
          return new RoomOpenHelper.ValidationResult(false, "knowledge_relation(com.zhijing.graph.entity.KnowledgeRelation).\n"
                  + " Expected:\n" + _infoKnowledgeRelation + "\n"
                  + " Found:\n" + _existingKnowledgeRelation);
        }
        final HashMap<String, TableInfo.Column> _columnsUserContent = new HashMap<String, TableInfo.Column>(6);
        _columnsUserContent.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserContent.put("content", new TableInfo.Column("content", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserContent.put("sourceApp", new TableInfo.Column("sourceApp", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserContent.put("keywords", new TableInfo.Column("keywords", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserContent.put("entities", new TableInfo.Column("entities", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserContent.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUserContent = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUserContent = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoUserContent = new TableInfo("user_content", _columnsUserContent, _foreignKeysUserContent, _indicesUserContent);
        final TableInfo _existingUserContent = TableInfo.read(db, "user_content");
        if (!_infoUserContent.equals(_existingUserContent)) {
          return new RoomOpenHelper.ValidationResult(false, "user_content(com.zhijing.graph.entity.UserContent).\n"
                  + " Expected:\n" + _infoUserContent + "\n"
                  + " Found:\n" + _existingUserContent);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "b3aeeab7a0ebeaeb37c121a586ef57a4", "bd90567cd7f5031a5b68e28413446b52");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "knowledge_entity","knowledge_relation","user_content");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `knowledge_entity`");
      _db.execSQL("DELETE FROM `knowledge_relation`");
      _db.execSQL("DELETE FROM `user_content`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(KnowledgeEntityDao.class, KnowledgeEntityDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(KnowledgeRelationDao.class, KnowledgeRelationDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(UserContentDao.class, UserContentDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public KnowledgeEntityDao entityDao() {
    if (_knowledgeEntityDao != null) {
      return _knowledgeEntityDao;
    } else {
      synchronized(this) {
        if(_knowledgeEntityDao == null) {
          _knowledgeEntityDao = new KnowledgeEntityDao_Impl(this);
        }
        return _knowledgeEntityDao;
      }
    }
  }

  @Override
  public KnowledgeRelationDao relationDao() {
    if (_knowledgeRelationDao != null) {
      return _knowledgeRelationDao;
    } else {
      synchronized(this) {
        if(_knowledgeRelationDao == null) {
          _knowledgeRelationDao = new KnowledgeRelationDao_Impl(this);
        }
        return _knowledgeRelationDao;
      }
    }
  }

  @Override
  public UserContentDao contentDao() {
    if (_userContentDao != null) {
      return _userContentDao;
    } else {
      synchronized(this) {
        if(_userContentDao == null) {
          _userContentDao = new UserContentDao_Impl(this);
        }
        return _userContentDao;
      }
    }
  }
}
