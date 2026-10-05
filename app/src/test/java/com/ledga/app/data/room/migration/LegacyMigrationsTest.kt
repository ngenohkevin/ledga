package com.ledga.app.data.room.migration

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.testing.SchemaFixture
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class LegacyMigrationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every shipped v1 schema migrates to exactly schema 5`() {
        val expected = SchemaFixture.create(context, "v5.db", 5).use { shape(it.writableDatabase) }
        for (start in listOf(1, 3, 4)) {
            SchemaFixture.create(context, "from$start.db", start).use { it.writableDatabase }
            val migrated = openAt(5, "from$start.db").use { shape(it.writableDatabase) }
            assertEquals(expected, migrated, "schema $start -> 5")
        }
    }

    private fun openAt(version: Int, name: String): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = error("must exist")
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                        LegacyMigrations.ALL.filter { it.startVersion >= oldVersion && it.endVersion <= newVersion }
                            .forEach { it.migrate(db) }
                })
                .build(),
        )

    /** table -> sorted "column type notnull pk", plus the index names (column order differs after ALTER TABLE). */
    private fun shape(db: SupportSQLiteDatabase): Map<String, List<String>> {
        val tables = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('room_master_table', 'android_metadata') ORDER BY name",
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        val out = tables.associateWith { t ->
            db.query("PRAGMA table_info(`$t`)").use { c ->
                buildList { while (c.moveToNext()) add("${c.getString(1)} ${c.getString(2)} notnull=${c.getInt(3)} pk=${c.getInt(5)}") }
            }.sorted()
        }.toMutableMap()
        out["(indices)"] = db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_%' ORDER BY name")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        return out
    }
}
