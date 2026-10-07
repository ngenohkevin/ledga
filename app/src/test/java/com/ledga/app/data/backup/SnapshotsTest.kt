package com.ledga.app.data.backup

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R119–R121: when the snapshot is written, and what is never overwritten. Synthetic SMS. */
@RunWith(RobolectricTestRunner::class)
class SnapshotsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-07T17:00:00Z"))
    private val settings = SettingsStore(FakePrefsStore())
    private val store by lazy { SnapshotStore(File(tmp.root, "backup")) }
    private val snapshots by lazy { Snapshots(store, BackupReader(db, settings, DeviceId { null }, "2.0.0-test", clock), db, settings, clock) }

    @After fun close() = db.close()

    private suspend fun oneMessage() {
        SmsIngestor(db, Deriver(db, clock)).ingest(RawSms("MPESA", Sms.SEND, Sms.at("2026-03-21T10:30:00Z"), null, null, SmsSource.INBOX))
    }

    @Test
    fun `nothing is written before onboarding is done or while there are no messages`() = runTest {
        // A new phone: Android restored the old phone's snapshot (R119). Nothing may overwrite it yet.
        store.write(store.current, BackupData(writtenAt = 1, appVersion = "old", counts = BackupCounts(9, 9)))
        assertFalse(snapshots.write())
        assertFalse(snapshots.writeIfDue())
        oneMessage()
        assertFalse(snapshots.write(), "not onboarded yet")
        settings.setOnboarded()
        assertEquals(1L, store.read(store.current)?.writtenAt, "the found backup is untouched")
        assertTrue(snapshots.write())
        assertEquals(1, store.read(store.current)?.counts?.sms)
    }

    @Test
    fun `an onboarded phone with no messages writes nothing`() = runTest {
        settings.setOnboarded()
        assertFalse(snapshots.write())
        assertNull(snapshots.savedAt())
    }

    @Test
    fun `the background snapshot waits an hour after the last one, a forced one doesn't`() = runTest {
        settings.setOnboarded()
        oneMessage()
        assertTrue(snapshots.writeIfDue())
        assertEquals(clock.instant(), snapshots.savedAt())
        clock.instant = clock.instant().plus(Duration.ofMinutes(59))
        assertFalse(snapshots.writeIfDue())
        assertTrue(snapshots.write(), "after a rebuild or a restore")
        clock.instant = clock.instant().plus(Snapshots.EVERY)
        assertTrue(snapshots.writeIfDue())
    }

    @Test
    fun `a snapshot this install didn't restore becomes the earlier backup`() = runTest {
        store.write(store.current, BackupData(writtenAt = 5, appVersion = "old", counts = BackupCounts(3, 2)))
        assertEquals(2, snapshots.found()?.payments)
        snapshots.keepAsEarlier()
        assertFalse(store.current.exists())
        assertNull(snapshots.found())
        assertEquals(listOf(RestoreSource(RestoreSourceKind.EARLIER, SnapshotInfo(store.earlier, Instant.ofEpochMilli(5), 2))), snapshots.sources())
        snapshots.keepAsEarlier() // nothing to move: no error
    }

    @Test
    fun `the copy before a restore is kept only when there is something to keep`() = runTest {
        assertFalse(snapshots.saveBeforeRestore())
        assertFalse(store.beforeRestore.exists())
        oneMessage()
        assertTrue(snapshots.saveBeforeRestore())
        assertEquals(RestoreSourceKind.BEFORE_RESTORE, snapshots.sources().single().kind)
    }
}
