package com.ledga.app.data.legacy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.testing.LegacyDbWriter
import com.ledga.app.testing.SchemaFixture
import com.ledga.app.testing.Sms
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PreV6SnapshotTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a v5 database is copied once before the upgrade and can be deleted`() {
        SchemaFixture.create(context, "snap.db", 5).use { helper ->
            LegacyDbWriter(helper.writableDatabase, 5).apply {
                defaultCategories(); tx(1, "TJK4AB12FB", "SEND", "OUTFLOW", Sms.SEND, Instant.parse("2026-03-21T10:30:05Z"), 6)
            }
        }
        val snapshot = PreV6Snapshot(context, "snap.db")
        assertTrue(snapshot.takeIfNeeded())
        val copy = File(snapshot.dir, "snap.db")
        SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(5, db.version)
            db.rawQuery("SELECT COUNT(*) FROM transactions", null).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        }
        assertFalse(snapshot.takeIfNeeded(), "the first (oldest) snapshot is kept")
        snapshot.delete()
        assertFalse(snapshot.exists())
    }

    @Test
    fun `no database or an already-v6 database needs no snapshot`() {
        assertFalse(PreV6Snapshot(context, "missing.db").takeIfNeeded())
        SchemaFixture.create(context, "six.db", 6).use { it.writableDatabase }
        assertFalse(PreV6Snapshot(context, "six.db").takeIfNeeded())
    }
}
