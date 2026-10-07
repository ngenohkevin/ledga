package com.ledga.app.ui.backup

import android.net.Uri
import com.ledga.app.data.backup.BackupDirs
import com.ledga.app.data.backup.BackupFiles
import com.ledga.app.data.backup.BackupOrigin
import com.ledga.app.data.backup.BackupReader
import com.ledga.app.data.backup.DeviceId
import com.ledga.app.data.backup.Documents
import com.ledga.app.data.backup.Exporter
import com.ledga.app.data.backup.RestoreMode
import com.ledga.app.data.backup.RestoreSourceKind
import com.ledga.app.data.backup.Restorer
import com.ledga.app.data.backup.SnapshotStore
import com.ledga.app.data.backup.BackupCounts
import com.ledga.app.data.backup.BackupData
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.lines.PhoneAccess
import com.ledga.app.data.lines.Sim
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakeBackgroundWork
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.PERSONAL
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.app.testing.testSnapshots
import com.ledga.app.testing.twoLines
import com.ledga.app.work.RestoreProgress
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R124, R125, R115: Export & restore's state and actions. Synthetic data; files in a temporary folder. */
@RunWith(RobolectricTestRunner::class)
class BackupViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private val clock = MutableClock(Instant.parse("2026-10-07T17:00:00Z"))
    private val db = TestDb.inMemory()
    private val source = TestDb.inMemory()
    private val settings = SettingsStore(FakePrefsStore())
    private val sims = FakeSims()
    private val work = FakeBackgroundWork()
    private val vms = TestViewModels()
    private val dirs by lazy { BackupDirs(tmp.newFolder("backup"), tmp.newFolder("exports"), tmp.newFolder("restore")) }
    private val snapshots by lazy { testSnapshots(db, dirs.snapshots, settings, clock) }
    private val reader by lazy { BackupReader(db, settings, DeviceId { "this-phone" }, "2.0.0-test", clock) }

    /** "content://" stand-ins: a Uri's path is a file. */
    private val documents = object : Documents {
        override fun write(uri: Uri): OutputStream = File(uri.path!!).outputStream()
        override fun read(uri: Uri): InputStream = File(uri.path!!).inputStream()
    }

    private fun vm() = vms.track(
        BackupViewModel(
            snapshots, Exporter(reader, db), Restorer(db, Deriver(db, clock), settings, snapshots, sims, DeviceId { "this-phone" }, clock),
            work, dirs, documents, PhoneAccess { true }, clock,
        ),
    )

    @After fun close() {
        vms.stopAll()
        db.close()
        source.close()
    }

    /** A .ledga file from another phone with two lines. */
    private suspend fun otherPhonesFile(): File {
        twoLines(source)
        SmsIngestor(source, Deriver(source, clock)).ingest(RawSms("MPESA", Sms.SEND, Sms.at("2026-03-21T10:30:00Z"), 1, PERSONAL.id, SmsSource.INBOX))
        val file = tmp.newFile("picked.ledga")
        Exporter(BackupReader(source, SettingsStore(FakePrefsStore()), DeviceId { "other-phone" }, "2.0.0-test", clock), source).write(file.outputStream())
        return file
    }

    @Test
    fun `it says when the snapshot was saved and which copies can be restored`() = runTest {
        val store = SnapshotStore(dirs.snapshots)
        store.write(store.current, BackupData(writtenAt = clock.millis(), appVersion = "2.0.0-test", counts = BackupCounts(1, 1)))
        store.write(store.earlier, BackupData(writtenAt = 5, appVersion = "old", counts = BackupCounts(3, 2)))
        val ui = vm().ui.first { it.loaded }
        assertEquals(clock.instant(), ui.savedAt)
        assertEquals(listOf(RestoreSourceKind.EARLIER), ui.sources.map { it.kind })
    }

    @Test
    fun `Save to a file writes the whole export where the person chose`() = runTest {
        SmsIngestor(db, Deriver(db, clock)).ingest(RawSms("MPESA", Sms.KPLC, Sms.at("2026-03-21T12:00:00Z"), null, null, SmsSource.INBOX))
        val target = tmp.newFile("chosen.ledga")
        val vm = vm()
        assertEquals("ledga-2026-10-07.ledga", vm.exportName())
        vm.saveTo(Uri.fromFile(target))
        assertEquals(ExportState.Saved(1), vm.ui.first { it.export is ExportState.Saved }.export)
        assertEquals(BackupOrigin.LEDGA, BackupFiles.read(target).origin)
    }

    @Test
    fun `Share writes one file into exports and hands it over once`() = runTest {
        File(dirs.exports, "ledga-2026-10-01.ledga").writeText("an old share")
        val vm = vm()
        vm.share()
        val shared = assertIs<ExportState.Share>(vm.ui.first { it.export is ExportState.Share }.export)
        assertEquals(listOf("ledga-2026-10-07.ledga"), dirs.exports.list()!!.toList())
        assertEquals(File(dirs.exports, "ledga-2026-10-07.ledga"), shared.file)
        vm.shared()
        assertEquals(ExportState.Idle, vm.ui.value.export)
    }

    @Test
    fun `a chosen file is copied out of reach of backups and read into a draft that asks about unmatched lines`() = runTest {
        sims.add(Sim(5, "SIM 1", null))
        val vm = vm()
        vm.pick(Uri.fromFile(otherPhonesFile()))
        val draft = vm.ui.first { it.draft != null }.draft!!
        assertEquals(dirs.incoming, draft.file.parentFile)
        assertTrue(draft.deleteAfter)
        assertEquals(1, draft.payments)
        assertEquals(listOf(1L, 2L), draft.questions.map { it.line.id })
        assertFalse(draft.ready)
        vm.answer(1L, 5)
        vm.answer(2L, null)
        assertTrue(vm.ui.value.draft!!.ready)
        vm.start()
        val request = work.restores.single()
        assertEquals(RestoreMode.MERGE, request.mode)
        assertEquals(mapOf(1L to 5, 2L to null), request.answers)
        assertFalse(request.applySettings, "a merge keeps this phone's settings (R121)")
        assertNull(vm.ui.value.draft)
    }

    @Test
    fun `Replace carries the backup's settings`() = runTest {
        val vm = vm()
        vm.pick(Uri.fromFile(otherPhonesFile()))
        vm.ui.first { it.draft != null }
        vm.setMode(RestoreMode.REPLACE)
        vm.ui.first { it.draft?.mode == RestoreMode.REPLACE }
        vm.start()
        assertTrue(work.restores.single().applySettings)
    }

    @Test
    fun `a file that isn't a backup says so and leaves no copy behind`() = runTest {
        val png = tmp.newFile("photo.png").apply { writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) }
        val vm = vm()
        vm.pick(Uri.fromFile(png))
        assertEquals("This isn't a Ledga backup file.", vm.ui.first { it.error != null }.error)
        assertEquals(emptyList(), dirs.incoming.list()!!.toList())
    }

    @Test
    fun `closing a draft deletes the copy, never a snapshot`() = runTest {
        val vm = vm()
        vm.pick(Uri.fromFile(otherPhonesFile()))
        val copy = vm.ui.first { it.draft != null }.draft!!.file
        vm.dismiss()
        assertFalse(copy.exists())
        val store = SnapshotStore(dirs.snapshots)
        store.write(store.earlier, BackupData(writtenAt = 5, appVersion = "old", counts = BackupCounts(0, 0)))
        vm.refresh()
        vm.pickSource(vm.ui.first { it.sources.isNotEmpty() }.sources.single())
        vm.ui.first { it.draft != null }
        vm.dismiss()
        assertTrue(store.earlier.exists())
    }

    @Test
    fun `a result shows only for a restore started here`() = runTest {
        work.restoreProgress.value = RestoreProgress.Done(4, 10)
        val vm = vm()
        assertEquals(RestoreProgress.Idle, vm.ui.first { it.loaded }.restore)
        vm.pick(Uri.fromFile(otherPhonesFile()))
        vm.ui.first { it.draft != null }
        vm.start()
        work.restoreProgress.value = RestoreProgress.Running(0, 0)
        vm.ui.first { it.restore is RestoreProgress.Running }
        work.restoreProgress.value = RestoreProgress.Done(1, 1)
        assertEquals(RestoreProgress.Done(1, 1), vm.ui.first { it.restore is RestoreProgress.Done }.restore)
    }
}
