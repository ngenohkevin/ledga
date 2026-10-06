package com.ledga.app.startup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.legacy.LegacyImporter
import com.ledga.app.data.legacy.PreV6Snapshot
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.MetaKeys
import com.ledga.app.data.room.MetaRow
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakeBackgroundWork
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.TestDb
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class StartupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val work = FakeBackgroundWork()
    private val settings = SettingsStore(FakePrefsStore())
    private var smsGranted = false
    private var leftoversCleared = 0

    private fun startup(db: LedgaDatabase, snapshot: PreV6Snapshot = PreV6Snapshot(context)) = Startup(
        db = db,
        snapshot = snapshot,
        importer = LegacyImporter(db),
        deriver = Deriver(db),
        lines = LinesRepository(db.linesDao(), FakeSims()),
        settings = settings,
        work = work,
        sms = { smsGranted },
        leftovers = { leftoversCleared++ },
    )

    /** What MIGRATION_5_6 leaves behind until LegacyImporter has run. */
    private fun withPendingImport(db: LedgaDatabase) = db.also { it.openHelper.writableDatabase.execSQL("CREATE TABLE legacy_tx (code TEXT)") }

    @Test
    fun `a fresh install opens ready for onboarding and queues nothing`() = runTest {
        assertEquals(StartupState.Ready(onboarded = false), startup(TestDb.inMemory()).run())
        assertEquals(emptyList(), work.calls)
        assertEquals(0, leftoversCleared)
    }

    @Test
    fun `a parser or derivation change queues one rebuild`() = runTest {
        val db = TestDb.inMemory()
        db.metaDao().put(MetaRow(MetaKeys.DERIVATION_VERSION, "0"))
        startup(db).run()
        assertEquals(listOf("rebuild"), work.calls)
    }

    @Test
    fun `after the v1 migration the import chain is queued and v1's jobs are cleared`() = runTest {
        settings.setOnboarded()
        smsGranted = true
        assertEquals(StartupState.Ready(onboarded = true), startup(withPendingImport(TestDb.inMemory())).run())
        assertEquals(listOf("afterMigration"), work.calls, "the chain's full rescan replaces the catch-up")
        assertEquals(1, leftoversCleared)
    }

    @Test
    fun `an onboarded phone with SMS access catches up on start, and one without doesn't`() = runTest {
        settings.setOnboarded()
        startup(TestDb.inMemory()).run()
        assertEquals(emptyList(), work.calls)
        smsGranted = true
        startup(TestDb.inMemory()).run()
        assertEquals(listOf("catchUp"), work.calls)
    }

    @Test
    fun `the pre-v6 copy is kept while history work is owed and deleted once it is done`() = runTest {
        val snapshot = PreV6Snapshot(context)
        snapshot.file().apply { parentFile!!.mkdirs(); writeText("copy") }
        startup(withPendingImport(TestDb.inMemory()), snapshot).run()
        assertTrue(snapshot.exists())
        startup(TestDb.inMemory(), snapshot).run()
        assertFalse(snapshot.exists())
    }

    @Test
    fun `a database that can't be migrated shows recovery, with the copy to share`() = runTest {
        val name = "broken.db"
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        // A v5 file with none of v5's tables: MIGRATION_5_6 can't read what it expects and throws.
        SQLiteDatabase.openOrCreateDatabase(file, null).use { it.version = 5 }
        val snapshot = PreV6Snapshot(context, name)
        assertTrue(snapshot.takeIfNeeded())
        val db = LedgaDatabase.builder(context, name).build()
        val state = startup(db, snapshot).run()
        assertIs<StartupState.Failed>(state)
        assertEquals(snapshot.file(), state.snapshot)
        assertTrue(snapshot.file().exists(), "the copy is what the recovery screen shares")
        assertEquals(emptyList(), work.calls)
        db.close()
    }
}
