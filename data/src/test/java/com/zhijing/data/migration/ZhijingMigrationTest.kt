package com.zhijing.data.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zhijing.data.ZhijingDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 真实迁移测试：**从旧版本库文件升级**，不是测 SQL 字符串。
 *
 * ## 为什么必须这么做
 *
 * Room 升级完成后会拿结果和编译期 schema 逐表逐列比对（类型、NOT NULL、默认值、索引），
 * 差一个 `DEFAULT 0` 就抛 `IllegalStateException: Migration didn't properly handle`。
 * 只断言「SQL 能执行」等于没测 —— 真正的失败发生在校验阶段。
 *
 * 旧库的建表语句直接读 `data/schemas/.../{7}.json`（Room 编译器导出），
 * 不手抄：手抄的 DDL 会和实体注解漂移，测试就会变成「测我抄得对不对」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZhijingMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val dbNames = mutableListOf<String>()

    @Before
    fun setUp() {
        // 每个用例用独立库名，避免 Robolectric 在同一沙箱里串数据
    }

    @After
    fun tearDown() {
        dbNames.forEach { context.deleteDatabase(it) }
    }

    /* ------------------------------------------------------------ 用例 */

    @Test
    fun `从 v6 升到 v8 保留判断状态并建出条件表与生成命题表`() {
        val name = "migrate-6.db"
        // v6 时还没有 kg_condition_probe 与 kg_generated_claim：从 v7 的 schema 里剔掉前者
        createLegacyDatabase(
            name = name,
            version = 6,
            excludeTables = setOf("kg_condition_probe"),
            seed = { sqlite ->
                sqlite.execSQL(
                    "INSERT INTO kg_claim_state " +
                        "(claim_id, probe_status, misconceptions, confidence, attempts, correct, streak) " +
                        "VALUES ('clm.test.6', 'mastered', '[]', 1.0, 2, 2, 2)"
                )
            }
        )

        val db = openWithMigrations(name)
        try {
            runBlocking {
                // 旧数据必须还在 —— 这正是「补迁移而不是清库」的全部意义
                val state = db.kgClaimStateDao().find("clm.test.6")
                assertNotNull("v6 的命题状态在升级后丢了", state)
                assertEquals("mastered", state!!.probeStatus)
                assertEquals(2, state.streak)

                // 两张新表都要能用
                assertTrue(db.kgConditionProbeDao().findByTriple("tri.none").isEmpty())
                assertTrue(db.kgGeneratedGraphDao().claimsOf("topic.none").isEmpty())
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `从 v7 升到 v8 保留条件级记录`() {
        val name = "migrate-7.db"
        createLegacyDatabase(
            name = name,
            version = 7,
            seed = { sqlite ->
                sqlite.execSQL(
                    "INSERT INTO kg_condition_probe " +
                        "(triple_id, intervention_id, claim_id, predicted_option, correct_option, " +
                        "matched, attempts, wrong_attempts) " +
                        "VALUES ('tri.keep', 'inv.keep', 'clm.keep', 'HOLDS', 'FAILS', 0, 1, 1)"
                )
            }
        )

        val db = openWithMigrations(name)
        try {
            runBlocking {
                val row = db.kgConditionProbeDao().find("tri.keep", "inv.keep")
                assertNotNull("v7 的条件级记录在升级后丢了", row)
                assertEquals("FAILS", row!!.correctOption)
                assertEquals(1, row.wrongAttempts)

                assertTrue(db.kgGeneratedGraphDao().claimsOf("topic.none").isEmpty())
            }
        } finally {
            db.close()
        }
    }

    /* ------------------------------------------------------------ 工具 */

    /**
     * 用导出的 schema 建一个旧版本库。
     *
     * @param excludeTables 该版本还没有的表（从更高版本的 schema 里剔掉来模拟）
     * @param seed 建表后写入旧数据
     */
    private fun createLegacyDatabase(
        name: String,
        version: Int,
        excludeTables: Set<String> = emptySet(),
        seed: (SQLiteDatabase) -> Unit,
    ) {
        dbNames += name
        context.deleteDatabase(name)

        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()

        val schema = JSONObject(schemaFile(7).readText())
            .getJSONObject("database")
            .getJSONArray("entities")

        val sqlite = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            for (index in 0 until schema.length()) {
                val entity = schema.getJSONObject(index)
                val table = entity.getString("tableName")
                if (table in excludeTables) continue

                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (i in 0 until indices.length()) {
                    sqlite.execSQL(
                        indices.getJSONObject(i).getString("createSql")
                            .replace("\${TABLE_NAME}", table)
                    )
                }
            }
            seed(sqlite)
            sqlite.version = version
        } finally {
            sqlite.close()
        }
    }

    /** 不挂 `fallbackToDestructiveMigration`：迁移写错就必须让测试红，而不是悄悄清库。 */
    private fun openWithMigrations(name: String): ZhijingDatabase =
        Room.databaseBuilder(context, ZhijingDatabase::class.java, name)
            .addMigrations(*ZhijingDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    private fun schemaFile(version: Int): File {
        val relative = "schemas/com.zhijing.data.ZhijingDatabase/$version.json"
        val candidates = listOf(File(relative), File("data/$relative"), File("../$relative"))
        return candidates.firstOrNull { it.exists() }
            ?: error("找不到 schema 文件 $relative（工作目录 ${File(".").absolutePath}）")
    }
}
