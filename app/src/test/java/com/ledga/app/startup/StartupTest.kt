package com.ledga.app.startup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.legacy.LegacyImporter
import com.ledga.app.data.legacy.PreV6Snapshot
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.MetaKeys
import com.ledga.app.data.room.MetaRow
import com.ledga.app.data.room.RuleRow
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakeBackgroundWork
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.FakeUpdateWork
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.core.derive.RuleAction
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.FlowKind
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StartupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val work = FakeBackgroundWork()
    private val updates = FakeUpdateWork()
    private val settings = SettingsStore(FakePrefsStore())
    private var smsGranted = false
    private var leftoversCleared = 0

    private fun startup(db: LedgaDatabase, snapshot: PreV6Snapshot = PreV6Snapshot(context), keepCopy: Boolean = false) = Startup(
        db = db,
        snapshot = snapshot,
        importer = LegacyImporter(db),
        deriver = Deriver(db),
        lines = LinesRepository(db.linesDao(), FakeSims()),
        settings = settings,
        work = work,
        sms = { smsGranted },
        updates = updates,
        leftovers = { leftoversCleared++ },
        keepPreV6Copy = keepCopy,
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
        assertTrue(settings.current().fullRescanOwed, "owed until the chain's full scan completes")
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
    fun `the pre-v6 copy is kept while the migration's work is owed and deleted once it is done`() = runTest {
        val snapshot = PreV6Snapshot(context)
        snapshot.file().apply { parentFile!!.mkdirs(); writeText("copy") }
        val db = withPendingImport(TestDb.inMemory())
        startup(db, snapshot).run()
        assertTrue(snapshot.exists())
        db.openHelper.writableDatabase.execSQL("DROP TABLE legacy_tx") // LegacyImporter finished
        startup(db, snapshot).run()
        assertFalse(snapshot.exists())
    }

    @Test
    fun `a pre-v6 copy is never deleted unless this database went through the migration - recovery instead`() = runTest {
        val snapshot = PreV6Snapshot(context)
        snapshot.file().apply { parentFile!!.mkdirs(); writeText("copy") }
        val state = startup(TestDb.inMemory(), snapshot).run()
        assertIs<StartupState.Failed>(state)
        assertEquals(snapshot.file(), state.snapshot)
        assertTrue(snapshot.exists(), "the copy may be the only one of the user's history")
    }

    @Test
    fun `while the migration chain is still running, startup queues no second rebuild and no catch-up`() = runTest {
        val db = TestDb.inMemory()
        db.metaDao().put(MetaRow(MetaKeys.DERIVATION_VERSION, "0"))
        settings.setOnboarded()
        smsGranted = true
        work.chainRunning = true
        startup(db).run()
        assertEquals(emptyList(), work.calls)
    }

    @Test
    fun `a full rescan the migration left owed runs on the next start instead of a catch-up`() = runTest {
        settings.setOnboarded()
        settings.setFullRescanOwed(true)
        smsGranted = true
        startup(TestDb.inMemory()).run()
        assertEquals(listOf("importInbox"), work.calls)
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

    @Test
    fun `a corrupt v1 database is never wiped - startup shows recovery and both copies survive`() = runTest {
        val name = "corrupt.db"
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val garbage = ByteArray(8192) { (it % 251).toByte() }
        file.writeBytes(garbage)
        val snapshot = PreV6Snapshot(context, name)
        assertTrue(snapshot.takeIfNeeded())
        val db = LedgaDatabase.builder(context, name).build()
        val state = startup(db, snapshot).run()
        assertIs<StartupState.Failed>(state)
        assertTrue(garbage.contentEquals(file.readBytes()), "Room must not delete or replace a database it finds corrupt")
        assertTrue(snapshot.exists(), "the pre-v6 copy is the user's history: never delete it here")
        db.close()
    }

    @Test
    fun `an onboarded start keeps the 6-hourly check and each switched-on notification queued (R107, R108)`() = runTest {
        settings.setOnboarded()
        startup(TestDb.inMemory()).run()
        assertEquals(listOf("keepSyncing", "DAILY on replace=false", "WEEKLY on replace=false", "FULIZA on replace=false"), work.scheduled)
    }

    @Test
    fun `before onboarding nothing is scheduled`() = runTest {
        startup(TestDb.inMemory()).run()
        assertEquals(emptyList(), work.scheduled)
    }

    @Test
    fun `an onboarded start keeps the daily update check and asks for one soon (R134)`() = runTest {
        settings.setOnboarded()
        startup(TestDb.inMemory()).run()
        assertEquals(listOf("keepChecking", "checkSoon"), updates.calls)
    }

    @Test
    fun `before onboarding nothing touches the network (R134)`() = runTest {
        startup(TestDb.inMemory()).run()
        assertEquals(emptyList(), updates.calls)
    }

    @Test
    fun `a beta keeps the pre-v6 copy after the migration's work is done, and a full release deletes it (R159)`() = runTest {
        val snapshot = PreV6Snapshot(context)
        snapshot.file().apply { parentFile!!.mkdirs(); writeText("copy") }
        val db = TestDb.inMemory()
        db.metaDao().put(MetaRow(MetaKeys.MIGRATED_FROM_V1, "1"))
        assertEquals(StartupState.Ready(onboarded = false), startup(db, snapshot, keepCopy = true).run(), "never recovery")
        assertEquals(StartupState.Ready(onboarded = false), startup(db, snapshot, keepCopy = true).run(), "again")
        assertTrue(snapshot.exists())
        startup(db, snapshot).run()
        assertFalse(snapshot.exists())
    }

    private suspend fun withV1PaybillRule(db: LedgaDatabase) {
        db.rulesDao().insert(
            RuleRow(field = RuleField.NAME_CONTAINS, pattern = "SAMPLE SACCO for account", action = RuleAction.MARK_OWN_ACCOUNT,
                categoryKey = null, origin = RuleOrigin.USER, priority = 0, createdAt = Instant.parse("2026-09-01T06:00:00Z")),
        )
        val body = Sms.paybill("TJK4AB12GK", "SAMPLE SACCO", "7788", "2,500.00", "15/9/26 at 9:00 AM")
        SmsIngestor(db, Deriver(db)).ingestAll(listOf(RawSms("MPESA", body, Instant.parse("2026-09-15T06:00:00Z"), null, null, SmsSource.INBOX)))
    }

    @Test
    fun `a phone that imported v1 earlier gets its paybill-form rules repaired once, and its payments re-filed (R175)`() = runTest {
        val db = TestDb.inMemory()
        db.metaDao().put(MetaRow(MetaKeys.MIGRATED_FROM_V1, "1"))
        withV1PaybillRule(db)
        assertEquals(FlowKind.SPEND, db.transactionsDao().get("TJK4AB12GK")!!.flow, "before: the rule never matches")
        startup(db).run()
        assertEquals(FlowKind.OWN_OUT, db.transactionsDao().get("TJK4AB12GK")!!.flow)
        assertEquals("SAMPLE SACCO", db.rulesDao().all().single { it.origin == RuleOrigin.USER }.pattern)
        assertEquals("1", db.metaDao().get(MetaKeys.V1_RULES_REPAIRED))
    }

    @Test
    fun `a phone that never came from v1 keeps its rules (R175)`() = runTest {
        val db = TestDb.inMemory()
        withV1PaybillRule(db)
        startup(db).run()
        assertEquals("SAMPLE SACCO for account", db.rulesDao().all().single { it.origin == RuleOrigin.USER }.pattern)
    }
}
