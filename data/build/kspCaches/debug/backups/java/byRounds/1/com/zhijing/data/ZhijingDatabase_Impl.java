package com.zhijing.data;

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
import com.zhijing.data.dao.AssociationDao;
import com.zhijing.data.dao.AssociationDao_Impl;
import com.zhijing.data.dao.ChunkDao;
import com.zhijing.data.dao.ChunkDao_Impl;
import com.zhijing.data.dao.ContentDao;
import com.zhijing.data.dao.ContentDao_Impl;
import com.zhijing.data.dao.ParseTaskDao;
import com.zhijing.data.dao.ParseTaskDao_Impl;
import com.zhijing.data.dao.PetStatusDao;
import com.zhijing.data.dao.PetStatusDao_Impl;
import com.zhijing.data.dao.QaRecordDao;
import com.zhijing.data.dao.QaRecordDao_Impl;
import com.zhijing.data.dao.StanceCompareDao;
import com.zhijing.data.dao.StanceCompareDao_Impl;
import com.zhijing.data.dao.TopicDao;
import com.zhijing.data.dao.TopicDao_Impl;
import com.zhijing.data.dao.VectorDao;
import com.zhijing.data.dao.VectorDao_Impl;
import com.zhijing.data.dao.WakePlanDao;
import com.zhijing.data.dao.WakePlanDao_Impl;
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
  private volatile ContentDao _contentDao;

  private volatile ChunkDao _chunkDao;

  private volatile VectorDao _vectorDao;

  private volatile AssociationDao _associationDao;

  private volatile TopicDao _topicDao;

  private volatile StanceCompareDao _stanceCompareDao;

  private volatile WakePlanDao _wakePlanDao;

  private volatile QaRecordDao _qaRecordDao;

  private volatile PetStatusDao _petStatusDao;

  private volatile ParseTaskDao _parseTaskDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `content_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `raw_text` TEXT NOT NULL, `source` TEXT, `import_time` INTEGER NOT NULL, `analysis_json` TEXT, `analysis_status` INTEGER NOT NULL DEFAULT 0, `embedding_vector` BLOB, `embedding_status` INTEGER NOT NULL DEFAULT 0, `remark` TEXT, `import_channel` TEXT NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_content_table_import_time` ON `content_table` (`import_time`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_content_table_analysis_status` ON `content_table` (`analysis_status`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_content_table_embedding_status` ON `content_table` (`embedding_status`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `chunk_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content_id` INTEGER NOT NULL, `chunk_index` INTEGER NOT NULL, `text` TEXT NOT NULL, `start_offset` INTEGER NOT NULL, `end_offset` INTEGER NOT NULL, `char_count` INTEGER NOT NULL, FOREIGN KEY(`content_id`) REFERENCES `content_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chunk_table_content_id` ON `chunk_table` (`content_id`)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chunk_table_content_id_chunk_index` ON `chunk_table` (`content_id`, `chunk_index`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `vector_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `owner_type` TEXT NOT NULL, `owner_id` INTEGER NOT NULL, `dim` INTEGER NOT NULL, `vector` BLOB NOT NULL, `model_id` TEXT NOT NULL, `created_time` INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vector_table_owner_type_owner_id` ON `vector_table` (`owner_type`, `owner_id`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_vector_table_owner_type` ON `vector_table` (`owner_type`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `association_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `source_content_id` INTEGER NOT NULL, `target_content_id` INTEGER NOT NULL, `similarity_score` REAL NOT NULL, `shared_topics` TEXT, `evidence_snippet` TEXT, `feedback` INTEGER NOT NULL DEFAULT 0, `weight` REAL NOT NULL DEFAULT 0, FOREIGN KEY(`source_content_id`) REFERENCES `content_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`target_content_id`) REFERENCES `content_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_association_table_source_content_id` ON `association_table` (`source_content_id`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_association_table_target_content_id` ON `association_table` (`target_content_id`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `topic_table` (`tag_name` TEXT NOT NULL, `parent_tag` TEXT, `content_count` INTEGER NOT NULL DEFAULT 0, `color` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`tag_name`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_topic_table_parent_tag` ON `topic_table` (`parent_tag`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `stance_compare_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `topic_tag` TEXT NOT NULL, `side_a_content_ids` TEXT NOT NULL, `side_b_content_ids` TEXT NOT NULL, `side_a_summary` TEXT, `side_b_summary` TEXT, `status` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stance_compare_table_topic_tag` ON `stance_compare_table` (`topic_tag`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stance_compare_table_status` ON `stance_compare_table` (`status`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `wake_plan_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `enabled` INTEGER NOT NULL DEFAULT 0, `cycle_type` TEXT, `trigger_time` TEXT, `scope` TEXT, `mode` TEXT NOT NULL, `daily_question_num` INTEGER NOT NULL DEFAULT 0, `next_run_time` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_wake_plan_table_enabled` ON `wake_plan_table` (`enabled`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `qa_record_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `question_text` TEXT NOT NULL, `answer_text` TEXT, `source_content_ids` TEXT NOT NULL, `retrieval_ms` INTEGER NOT NULL DEFAULT 0, `generate_ms` INTEGER NOT NULL DEFAULT 0, `answer_status` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qa_record_table_answer_status` ON `qa_record_table` (`answer_status`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `pet_status_table` (`id` INTEGER NOT NULL, `phase` INTEGER NOT NULL DEFAULT 0, `total_contents` INTEGER NOT NULL DEFAULT 0, `active_days` INTEGER NOT NULL DEFAULT 0, `accessories` TEXT, `last_display_time` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `parse_task_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content_id` INTEGER NOT NULL, `task_type` TEXT NOT NULL, `status` INTEGER NOT NULL DEFAULT 0, `retry_count` INTEGER NOT NULL DEFAULT 0, `created_time` INTEGER NOT NULL, FOREIGN KEY(`content_id`) REFERENCES `content_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parse_task_table_content_id` ON `parse_task_table` (`content_id`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parse_task_table_status` ON `parse_task_table` (`status`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '9f7d9e4b96c49a7447faeeab4f200d7b')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `content_table`");
        db.execSQL("DROP TABLE IF EXISTS `chunk_table`");
        db.execSQL("DROP TABLE IF EXISTS `vector_table`");
        db.execSQL("DROP TABLE IF EXISTS `association_table`");
        db.execSQL("DROP TABLE IF EXISTS `topic_table`");
        db.execSQL("DROP TABLE IF EXISTS `stance_compare_table`");
        db.execSQL("DROP TABLE IF EXISTS `wake_plan_table`");
        db.execSQL("DROP TABLE IF EXISTS `qa_record_table`");
        db.execSQL("DROP TABLE IF EXISTS `pet_status_table`");
        db.execSQL("DROP TABLE IF EXISTS `parse_task_table`");
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
        final HashMap<String, TableInfo.Column> _columnsContentTable = new HashMap<String, TableInfo.Column>(10);
        _columnsContentTable.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsContentTable.put("raw_text", new TableInfo.Column("raw_text", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsContentTable.put("source", new TableInfo.Column("source", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsContentTable.put("import_time", new TableInfo.Column("import_time", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsContentTable.put("analysis_json", new TableInfo.Column("analysis_json", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsContentTable.put("analysis_status", new TableInfo.Column("analysis_status", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsContentTable.put("embedding_vector", new TableInfo.Column("embedding_vector", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsContentTable.put("embedding_status", new TableInfo.Column("embedding_status", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsContentTable.put("remark", new TableInfo.Column("remark", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsContentTable.put("import_channel", new TableInfo.Column("import_channel", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysContentTable = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesContentTable = new HashSet<TableInfo.Index>(3);
        _indicesContentTable.add(new TableInfo.Index("index_content_table_import_time", false, Arrays.asList("import_time"), Arrays.asList("ASC")));
        _indicesContentTable.add(new TableInfo.Index("index_content_table_analysis_status", false, Arrays.asList("analysis_status"), Arrays.asList("ASC")));
        _indicesContentTable.add(new TableInfo.Index("index_content_table_embedding_status", false, Arrays.asList("embedding_status"), Arrays.asList("ASC")));
        final TableInfo _infoContentTable = new TableInfo("content_table", _columnsContentTable, _foreignKeysContentTable, _indicesContentTable);
        final TableInfo _existingContentTable = TableInfo.read(db, "content_table");
        if (!_infoContentTable.equals(_existingContentTable)) {
          return new RoomOpenHelper.ValidationResult(false, "content_table(com.zhijing.data.entity.ContentEntity).\n"
                  + " Expected:\n" + _infoContentTable + "\n"
                  + " Found:\n" + _existingContentTable);
        }
        final HashMap<String, TableInfo.Column> _columnsChunkTable = new HashMap<String, TableInfo.Column>(7);
        _columnsChunkTable.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChunkTable.put("content_id", new TableInfo.Column("content_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChunkTable.put("chunk_index", new TableInfo.Column("chunk_index", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChunkTable.put("text", new TableInfo.Column("text", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChunkTable.put("start_offset", new TableInfo.Column("start_offset", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChunkTable.put("end_offset", new TableInfo.Column("end_offset", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChunkTable.put("char_count", new TableInfo.Column("char_count", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysChunkTable = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysChunkTable.add(new TableInfo.ForeignKey("content_table", "CASCADE", "NO ACTION", Arrays.asList("content_id"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesChunkTable = new HashSet<TableInfo.Index>(2);
        _indicesChunkTable.add(new TableInfo.Index("index_chunk_table_content_id", false, Arrays.asList("content_id"), Arrays.asList("ASC")));
        _indicesChunkTable.add(new TableInfo.Index("index_chunk_table_content_id_chunk_index", true, Arrays.asList("content_id", "chunk_index"), Arrays.asList("ASC", "ASC")));
        final TableInfo _infoChunkTable = new TableInfo("chunk_table", _columnsChunkTable, _foreignKeysChunkTable, _indicesChunkTable);
        final TableInfo _existingChunkTable = TableInfo.read(db, "chunk_table");
        if (!_infoChunkTable.equals(_existingChunkTable)) {
          return new RoomOpenHelper.ValidationResult(false, "chunk_table(com.zhijing.data.entity.ChunkEntity).\n"
                  + " Expected:\n" + _infoChunkTable + "\n"
                  + " Found:\n" + _existingChunkTable);
        }
        final HashMap<String, TableInfo.Column> _columnsVectorTable = new HashMap<String, TableInfo.Column>(7);
        _columnsVectorTable.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVectorTable.put("owner_type", new TableInfo.Column("owner_type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVectorTable.put("owner_id", new TableInfo.Column("owner_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVectorTable.put("dim", new TableInfo.Column("dim", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVectorTable.put("vector", new TableInfo.Column("vector", "BLOB", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVectorTable.put("model_id", new TableInfo.Column("model_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVectorTable.put("created_time", new TableInfo.Column("created_time", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysVectorTable = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesVectorTable = new HashSet<TableInfo.Index>(2);
        _indicesVectorTable.add(new TableInfo.Index("index_vector_table_owner_type_owner_id", true, Arrays.asList("owner_type", "owner_id"), Arrays.asList("ASC", "ASC")));
        _indicesVectorTable.add(new TableInfo.Index("index_vector_table_owner_type", false, Arrays.asList("owner_type"), Arrays.asList("ASC")));
        final TableInfo _infoVectorTable = new TableInfo("vector_table", _columnsVectorTable, _foreignKeysVectorTable, _indicesVectorTable);
        final TableInfo _existingVectorTable = TableInfo.read(db, "vector_table");
        if (!_infoVectorTable.equals(_existingVectorTable)) {
          return new RoomOpenHelper.ValidationResult(false, "vector_table(com.zhijing.data.entity.VectorEntity).\n"
                  + " Expected:\n" + _infoVectorTable + "\n"
                  + " Found:\n" + _existingVectorTable);
        }
        final HashMap<String, TableInfo.Column> _columnsAssociationTable = new HashMap<String, TableInfo.Column>(8);
        _columnsAssociationTable.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssociationTable.put("source_content_id", new TableInfo.Column("source_content_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssociationTable.put("target_content_id", new TableInfo.Column("target_content_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssociationTable.put("similarity_score", new TableInfo.Column("similarity_score", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssociationTable.put("shared_topics", new TableInfo.Column("shared_topics", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssociationTable.put("evidence_snippet", new TableInfo.Column("evidence_snippet", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssociationTable.put("feedback", new TableInfo.Column("feedback", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsAssociationTable.put("weight", new TableInfo.Column("weight", "REAL", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAssociationTable = new HashSet<TableInfo.ForeignKey>(2);
        _foreignKeysAssociationTable.add(new TableInfo.ForeignKey("content_table", "CASCADE", "NO ACTION", Arrays.asList("source_content_id"), Arrays.asList("id")));
        _foreignKeysAssociationTable.add(new TableInfo.ForeignKey("content_table", "CASCADE", "NO ACTION", Arrays.asList("target_content_id"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesAssociationTable = new HashSet<TableInfo.Index>(2);
        _indicesAssociationTable.add(new TableInfo.Index("index_association_table_source_content_id", false, Arrays.asList("source_content_id"), Arrays.asList("ASC")));
        _indicesAssociationTable.add(new TableInfo.Index("index_association_table_target_content_id", false, Arrays.asList("target_content_id"), Arrays.asList("ASC")));
        final TableInfo _infoAssociationTable = new TableInfo("association_table", _columnsAssociationTable, _foreignKeysAssociationTable, _indicesAssociationTable);
        final TableInfo _existingAssociationTable = TableInfo.read(db, "association_table");
        if (!_infoAssociationTable.equals(_existingAssociationTable)) {
          return new RoomOpenHelper.ValidationResult(false, "association_table(com.zhijing.data.entity.AssociationEntity).\n"
                  + " Expected:\n" + _infoAssociationTable + "\n"
                  + " Found:\n" + _existingAssociationTable);
        }
        final HashMap<String, TableInfo.Column> _columnsTopicTable = new HashMap<String, TableInfo.Column>(4);
        _columnsTopicTable.put("tag_name", new TableInfo.Column("tag_name", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTopicTable.put("parent_tag", new TableInfo.Column("parent_tag", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTopicTable.put("content_count", new TableInfo.Column("content_count", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsTopicTable.put("color", new TableInfo.Column("color", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTopicTable = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTopicTable = new HashSet<TableInfo.Index>(1);
        _indicesTopicTable.add(new TableInfo.Index("index_topic_table_parent_tag", false, Arrays.asList("parent_tag"), Arrays.asList("ASC")));
        final TableInfo _infoTopicTable = new TableInfo("topic_table", _columnsTopicTable, _foreignKeysTopicTable, _indicesTopicTable);
        final TableInfo _existingTopicTable = TableInfo.read(db, "topic_table");
        if (!_infoTopicTable.equals(_existingTopicTable)) {
          return new RoomOpenHelper.ValidationResult(false, "topic_table(com.zhijing.data.entity.TopicEntity).\n"
                  + " Expected:\n" + _infoTopicTable + "\n"
                  + " Found:\n" + _existingTopicTable);
        }
        final HashMap<String, TableInfo.Column> _columnsStanceCompareTable = new HashMap<String, TableInfo.Column>(7);
        _columnsStanceCompareTable.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStanceCompareTable.put("topic_tag", new TableInfo.Column("topic_tag", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStanceCompareTable.put("side_a_content_ids", new TableInfo.Column("side_a_content_ids", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStanceCompareTable.put("side_b_content_ids", new TableInfo.Column("side_b_content_ids", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStanceCompareTable.put("side_a_summary", new TableInfo.Column("side_a_summary", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStanceCompareTable.put("side_b_summary", new TableInfo.Column("side_b_summary", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStanceCompareTable.put("status", new TableInfo.Column("status", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysStanceCompareTable = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesStanceCompareTable = new HashSet<TableInfo.Index>(2);
        _indicesStanceCompareTable.add(new TableInfo.Index("index_stance_compare_table_topic_tag", false, Arrays.asList("topic_tag"), Arrays.asList("ASC")));
        _indicesStanceCompareTable.add(new TableInfo.Index("index_stance_compare_table_status", false, Arrays.asList("status"), Arrays.asList("ASC")));
        final TableInfo _infoStanceCompareTable = new TableInfo("stance_compare_table", _columnsStanceCompareTable, _foreignKeysStanceCompareTable, _indicesStanceCompareTable);
        final TableInfo _existingStanceCompareTable = TableInfo.read(db, "stance_compare_table");
        if (!_infoStanceCompareTable.equals(_existingStanceCompareTable)) {
          return new RoomOpenHelper.ValidationResult(false, "stance_compare_table(com.zhijing.data.entity.StanceCompareEntity).\n"
                  + " Expected:\n" + _infoStanceCompareTable + "\n"
                  + " Found:\n" + _existingStanceCompareTable);
        }
        final HashMap<String, TableInfo.Column> _columnsWakePlanTable = new HashMap<String, TableInfo.Column>(8);
        _columnsWakePlanTable.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWakePlanTable.put("enabled", new TableInfo.Column("enabled", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsWakePlanTable.put("cycle_type", new TableInfo.Column("cycle_type", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWakePlanTable.put("trigger_time", new TableInfo.Column("trigger_time", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWakePlanTable.put("scope", new TableInfo.Column("scope", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWakePlanTable.put("mode", new TableInfo.Column("mode", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWakePlanTable.put("daily_question_num", new TableInfo.Column("daily_question_num", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsWakePlanTable.put("next_run_time", new TableInfo.Column("next_run_time", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysWakePlanTable = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesWakePlanTable = new HashSet<TableInfo.Index>(1);
        _indicesWakePlanTable.add(new TableInfo.Index("index_wake_plan_table_enabled", false, Arrays.asList("enabled"), Arrays.asList("ASC")));
        final TableInfo _infoWakePlanTable = new TableInfo("wake_plan_table", _columnsWakePlanTable, _foreignKeysWakePlanTable, _indicesWakePlanTable);
        final TableInfo _existingWakePlanTable = TableInfo.read(db, "wake_plan_table");
        if (!_infoWakePlanTable.equals(_existingWakePlanTable)) {
          return new RoomOpenHelper.ValidationResult(false, "wake_plan_table(com.zhijing.data.entity.WakePlanEntity).\n"
                  + " Expected:\n" + _infoWakePlanTable + "\n"
                  + " Found:\n" + _existingWakePlanTable);
        }
        final HashMap<String, TableInfo.Column> _columnsQaRecordTable = new HashMap<String, TableInfo.Column>(7);
        _columnsQaRecordTable.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQaRecordTable.put("question_text", new TableInfo.Column("question_text", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQaRecordTable.put("answer_text", new TableInfo.Column("answer_text", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQaRecordTable.put("source_content_ids", new TableInfo.Column("source_content_ids", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQaRecordTable.put("retrieval_ms", new TableInfo.Column("retrieval_ms", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsQaRecordTable.put("generate_ms", new TableInfo.Column("generate_ms", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsQaRecordTable.put("answer_status", new TableInfo.Column("answer_status", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysQaRecordTable = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesQaRecordTable = new HashSet<TableInfo.Index>(1);
        _indicesQaRecordTable.add(new TableInfo.Index("index_qa_record_table_answer_status", false, Arrays.asList("answer_status"), Arrays.asList("ASC")));
        final TableInfo _infoQaRecordTable = new TableInfo("qa_record_table", _columnsQaRecordTable, _foreignKeysQaRecordTable, _indicesQaRecordTable);
        final TableInfo _existingQaRecordTable = TableInfo.read(db, "qa_record_table");
        if (!_infoQaRecordTable.equals(_existingQaRecordTable)) {
          return new RoomOpenHelper.ValidationResult(false, "qa_record_table(com.zhijing.data.entity.QaRecordEntity).\n"
                  + " Expected:\n" + _infoQaRecordTable + "\n"
                  + " Found:\n" + _existingQaRecordTable);
        }
        final HashMap<String, TableInfo.Column> _columnsPetStatusTable = new HashMap<String, TableInfo.Column>(6);
        _columnsPetStatusTable.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPetStatusTable.put("phase", new TableInfo.Column("phase", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsPetStatusTable.put("total_contents", new TableInfo.Column("total_contents", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsPetStatusTable.put("active_days", new TableInfo.Column("active_days", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsPetStatusTable.put("accessories", new TableInfo.Column("accessories", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPetStatusTable.put("last_display_time", new TableInfo.Column("last_display_time", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPetStatusTable = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPetStatusTable = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPetStatusTable = new TableInfo("pet_status_table", _columnsPetStatusTable, _foreignKeysPetStatusTable, _indicesPetStatusTable);
        final TableInfo _existingPetStatusTable = TableInfo.read(db, "pet_status_table");
        if (!_infoPetStatusTable.equals(_existingPetStatusTable)) {
          return new RoomOpenHelper.ValidationResult(false, "pet_status_table(com.zhijing.data.entity.PetStatusEntity).\n"
                  + " Expected:\n" + _infoPetStatusTable + "\n"
                  + " Found:\n" + _existingPetStatusTable);
        }
        final HashMap<String, TableInfo.Column> _columnsParseTaskTable = new HashMap<String, TableInfo.Column>(6);
        _columnsParseTaskTable.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsParseTaskTable.put("content_id", new TableInfo.Column("content_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsParseTaskTable.put("task_type", new TableInfo.Column("task_type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsParseTaskTable.put("status", new TableInfo.Column("status", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsParseTaskTable.put("retry_count", new TableInfo.Column("retry_count", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsParseTaskTable.put("created_time", new TableInfo.Column("created_time", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysParseTaskTable = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysParseTaskTable.add(new TableInfo.ForeignKey("content_table", "CASCADE", "NO ACTION", Arrays.asList("content_id"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesParseTaskTable = new HashSet<TableInfo.Index>(2);
        _indicesParseTaskTable.add(new TableInfo.Index("index_parse_task_table_content_id", false, Arrays.asList("content_id"), Arrays.asList("ASC")));
        _indicesParseTaskTable.add(new TableInfo.Index("index_parse_task_table_status", false, Arrays.asList("status"), Arrays.asList("ASC")));
        final TableInfo _infoParseTaskTable = new TableInfo("parse_task_table", _columnsParseTaskTable, _foreignKeysParseTaskTable, _indicesParseTaskTable);
        final TableInfo _existingParseTaskTable = TableInfo.read(db, "parse_task_table");
        if (!_infoParseTaskTable.equals(_existingParseTaskTable)) {
          return new RoomOpenHelper.ValidationResult(false, "parse_task_table(com.zhijing.data.entity.ParseTaskEntity).\n"
                  + " Expected:\n" + _infoParseTaskTable + "\n"
                  + " Found:\n" + _existingParseTaskTable);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "9f7d9e4b96c49a7447faeeab4f200d7b", "623e06c9c65e929eb649b3f6a1600885");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "content_table","chunk_table","vector_table","association_table","topic_table","stance_compare_table","wake_plan_table","qa_record_table","pet_status_table","parse_task_table");
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
      _db.execSQL("DELETE FROM `content_table`");
      _db.execSQL("DELETE FROM `chunk_table`");
      _db.execSQL("DELETE FROM `vector_table`");
      _db.execSQL("DELETE FROM `association_table`");
      _db.execSQL("DELETE FROM `topic_table`");
      _db.execSQL("DELETE FROM `stance_compare_table`");
      _db.execSQL("DELETE FROM `wake_plan_table`");
      _db.execSQL("DELETE FROM `qa_record_table`");
      _db.execSQL("DELETE FROM `pet_status_table`");
      _db.execSQL("DELETE FROM `parse_task_table`");
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
    _typeConvertersMap.put(ContentDao.class, ContentDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ChunkDao.class, ChunkDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(VectorDao.class, VectorDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(AssociationDao.class, AssociationDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TopicDao.class, TopicDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(StanceCompareDao.class, StanceCompareDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(WakePlanDao.class, WakePlanDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(QaRecordDao.class, QaRecordDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(PetStatusDao.class, PetStatusDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ParseTaskDao.class, ParseTaskDao_Impl.getRequiredConverters());
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
  public ContentDao contentDao() {
    if (_contentDao != null) {
      return _contentDao;
    } else {
      synchronized(this) {
        if(_contentDao == null) {
          _contentDao = new ContentDao_Impl(this);
        }
        return _contentDao;
      }
    }
  }

  @Override
  public ChunkDao chunkDao() {
    if (_chunkDao != null) {
      return _chunkDao;
    } else {
      synchronized(this) {
        if(_chunkDao == null) {
          _chunkDao = new ChunkDao_Impl(this);
        }
        return _chunkDao;
      }
    }
  }

  @Override
  public VectorDao vectorDao() {
    if (_vectorDao != null) {
      return _vectorDao;
    } else {
      synchronized(this) {
        if(_vectorDao == null) {
          _vectorDao = new VectorDao_Impl(this);
        }
        return _vectorDao;
      }
    }
  }

  @Override
  public AssociationDao associationDao() {
    if (_associationDao != null) {
      return _associationDao;
    } else {
      synchronized(this) {
        if(_associationDao == null) {
          _associationDao = new AssociationDao_Impl(this);
        }
        return _associationDao;
      }
    }
  }

  @Override
  public TopicDao topicDao() {
    if (_topicDao != null) {
      return _topicDao;
    } else {
      synchronized(this) {
        if(_topicDao == null) {
          _topicDao = new TopicDao_Impl(this);
        }
        return _topicDao;
      }
    }
  }

  @Override
  public StanceCompareDao stanceCompareDao() {
    if (_stanceCompareDao != null) {
      return _stanceCompareDao;
    } else {
      synchronized(this) {
        if(_stanceCompareDao == null) {
          _stanceCompareDao = new StanceCompareDao_Impl(this);
        }
        return _stanceCompareDao;
      }
    }
  }

  @Override
  public WakePlanDao wakePlanDao() {
    if (_wakePlanDao != null) {
      return _wakePlanDao;
    } else {
      synchronized(this) {
        if(_wakePlanDao == null) {
          _wakePlanDao = new WakePlanDao_Impl(this);
        }
        return _wakePlanDao;
      }
    }
  }

  @Override
  public QaRecordDao qaRecordDao() {
    if (_qaRecordDao != null) {
      return _qaRecordDao;
    } else {
      synchronized(this) {
        if(_qaRecordDao == null) {
          _qaRecordDao = new QaRecordDao_Impl(this);
        }
        return _qaRecordDao;
      }
    }
  }

  @Override
  public PetStatusDao petStatusDao() {
    if (_petStatusDao != null) {
      return _petStatusDao;
    } else {
      synchronized(this) {
        if(_petStatusDao == null) {
          _petStatusDao = new PetStatusDao_Impl(this);
        }
        return _petStatusDao;
      }
    }
  }

  @Override
  public ParseTaskDao parseTaskDao() {
    if (_parseTaskDao != null) {
      return _parseTaskDao;
    } else {
      synchronized(this) {
        if(_parseTaskDao == null) {
          _parseTaskDao = new ParseTaskDao_Impl(this);
        }
        return _parseTaskDao;
      }
    }
  }
}
