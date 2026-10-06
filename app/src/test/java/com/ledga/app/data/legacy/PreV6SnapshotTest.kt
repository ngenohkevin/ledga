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
import kotlin.test.assertNull
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
    fun `a corrupt database is copied as it is and never deleted`() {
        val db = context.getDatabasePath("bad.db")
        db.parentFile!!.mkdirs()
        val garbage = ByteArray(8192) { (it % 251).toByte() }
        db.writeBytes(garbage)
        val snapshot = PreV6Snapshot(context, "bad.db")
        assertTrue(snapshot.takeIfNeeded(), "an unreadable database is exactly what the snapshot is for")
        assertTrue(db.exists(), "the original must survive")
        assertTrue(garbage.contentEquals(db.readBytes()))
        assertTrue(garbage.contentEquals(File(snapshot.dir, "bad.db").readBytes()))
    }

    @Test
    fun `no database or an already-v6 database needs no snapshot`() {
        assertFalse(PreV6Snapshot(context, "missing.db").takeIfNeeded())
        SchemaFixture.create(context, "six.db", 6).use { it.writableDatabase }
        assertFalse(PreV6Snapshot(context, "six.db").takeIfNeeded())
    }

    @Test
    fun `a database already at v6 is recognised from its header and never copied`() {
        SchemaFixture.create(context, "six-fast.db", 6).use { it.writableDatabase }
        val copies = mutableListOf<File>()
        val snapshot = PreV6Snapshot(context, "six-fast.db") { from, to -> copies += from; from.copyTo(to, overwrite = true) }
        assertFalse(snapshot.takeIfNeeded())
        assertEquals(emptyList(), copies, "R29: launches after the upgrade read 100 bytes, not the whole database")
    }

    @Test
    fun `the header version is read without opening the file`() {
        SchemaFixture.create(context, "hdr6.db", 6).use { it.writableDatabase }
        SchemaFixture.create(context, "hdr5.db", 5).use { it.writableDatabase }
        val junk = context.getDatabasePath("hdr-junk.db").apply { parentFile!!.mkdirs(); writeText("not a database at all, but long enough to pass the length check".repeat(4)) }
        assertEquals(6, PreV6Snapshot.headerVersion(context.getDatabasePath("hdr6.db")))
        assertEquals(5, PreV6Snapshot.headerVersion(context.getDatabasePath("hdr5.db")))
        assertNull(PreV6Snapshot.headerVersion(junk))
        assertNull(PreV6Snapshot.headerVersion(context.getDatabasePath("hdr-missing.db")))
    }
}
