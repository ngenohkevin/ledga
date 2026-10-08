package com.ledga.app.ui.onboarding

import com.ledga.app.data.backup.BackupReader
import com.ledga.app.data.backup.DeviceId
import com.ledga.app.data.backup.RestoreMode
import com.ledga.app.data.backup.Restorer
import com.ledga.app.data.backup.SnapshotStore
import com.ledga.app.data.capture.InboxSms
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.lines.PhoneAccess
import com.ledga.app.data.lines.Sim
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.data.update.UpdateStore
import com.ledga.app.data.update.WhatsNew
import com.ledga.app.testing.FakeBackgroundWork
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.FakeUpdateWork
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.PERSONAL
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.testSnapshots
import com.ledga.app.testing.twoLines
import com.ledga.app.work.ImportProgress
import com.ledga.app.work.RestoreProgress
import com.ledga.core.update.AppVersion
import com.ledga.core.update.NotesSection
import java.time.Clock
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private val db = TestDb.inMemory()
    private val source = TestDb.inMemory()
    private val sims = FakeSims()
    private val lines = LinesRepository(db.linesDao(), sims)
    private val settings = SettingsStore(FakePrefsStore())
    private val work = FakeBackgroundWork()
    private val inbox = listOf(
        InboxSms("MPESA", Sms.SEND, Instant.parse("2026-03-21T10:30:30Z"), 1),
        InboxSms("MPESA", Sms.KPLC, Instant.parse("2026-03-23T12:00:30Z"), 1),
    )
    private var askNotifications = true
    private val updates = FakeUpdateWork()
    private val whatsNew = WhatsNew(UpdateStore(FakePrefsStore()), { listOf(NotesSection("What's new", listOf("A new Home"))) }, AppVersion.parse("2.0.0-beta.1")!!)

    private val snapshots by lazy { testSnapshots(db, tmp.root, settings) }
    private val restorer by lazy { Restorer(db, Deriver(db), settings, snapshots, sims, DeviceId { "this-phone" }, Clock.systemUTC()) }

    private fun vm() = OnboardingViewModel(settings, { inbox }, work, lines, { askNotifications }, snapshots, restorer, db, PhoneAccess { true }, updates, whatsNew)

    /** Android restored another phone's snapshot onto this one (two lines, one payment). */
    private suspend fun foundBackup() {
        twoLines(source)
        SmsIngestor(source, Deriver(source)).ingest(RawSms("MPESA", Sms.SEND, Sms.at("2026-03-21T10:30:00Z"), 1, PERSONAL.id, SmsSource.INBOX))
        val store = SnapshotStore(tmp.root)
        store.write(store.current, BackupReader(source, SettingsStore(FakePrefsStore()), DeviceId { "other-phone" }, "2.0.0-test", Clock.systemUTC()).read())
    }

    @After fun close() {
        db.close()
        source.close()
    }

    private suspend fun OnboardingViewModel.atImport() {
        onSmsResult(granted = true)
        state.first { it.step == Step.IMPORT }
    }

    @Test
    fun `welcome saves the name, trimmed, and moves to the SMS step`() = runTest {
        val vm = vm()
        vm.onName("  Jane ")
        vm.next()
        vm.state.first { it.step == Step.SMS }
        assertEquals("Jane", settings.current().displayName)
    }

    @Test
    fun `Not now on SMS finishes onboarding without importing`() = runTest {
        val vm = vm()
        vm.done()
        vm.state.first { it.finished }
        assertTrue(settings.current().onboarded)
        assertEquals(emptyList(), work.calls)
    }

    @Test
    fun `denying SMS in the system dialog is the same as Not now`() = runTest {
        val vm = vm()
        vm.onSmsResult(granted = false)
        vm.state.first { it.finished }
        assertEquals(emptyList(), work.calls)
    }

    @Test
    fun `allowing SMS shows what was found, and from when, before importing`() = runTest {
        val vm = vm()
        vm.atImport()
        assertEquals(
            InboxPreview(2, Instant.parse("2026-03-21T10:30:30Z"), Instant.parse("2026-03-23T12:00:30Z")),
            vm.state.value.preview,
        )
        assertEquals(emptyList(), work.calls, "nothing is imported until the user taps Import")
    }

    @Test
    fun `the import follows its worker, then moves on to notifications`() = runTest {
        val vm = vm()
        vm.atImport()
        vm.startImport()
        assertEquals(listOf("importInbox"), work.calls)
        work.inboxImport.value = ImportProgress.Running(1, 2)
        assertEquals(ImportProgress.Running(1, 2), vm.state.first { it.import is ImportProgress.Running }.import)
        work.inboxImport.value = ImportProgress.Done(2, 2)
        vm.state.first { it.import is ImportProgress.Done }
        vm.next()
        vm.state.first { it.step == Step.NOTIFICATIONS }
    }

    @Test
    fun `two lines are named when the import is done, and the names are saved (R127)`() = runTest {
        val first = lines.lineFor(1)!!
        val second = lines.lineFor(2)!!
        val vm = vm()
        vm.atImport()
        vm.startImport()
        work.inboxImport.value = ImportProgress.Done(2, 2)
        val s = vm.state.first { it.import is ImportProgress.Done && it.lines.size == 2 }
        assertEquals(listOf(first, second), s.lines.map { it.id })
        vm.onLineName(second, "Work")
        vm.next()
        vm.state.first { it.step == Step.NOTIFICATIONS }
        assertEquals("Work", db.linesDao().all().single { it.id == second }.displayName)
    }

    @Test
    fun `the steps are fixed from the start, however many lines turn up (4a D1)`() = runTest {
        lines.lineFor(1)
        lines.lineFor(2)
        val vm = vm()
        val start = vm.state.value.steps
        assertEquals(listOf(Step.WELCOME, Step.SMS, Step.IMPORT, Step.NOTIFICATIONS), start)
        vm.atImport()
        vm.startImport()
        work.inboxImport.value = ImportProgress.Done(2, 2)
        vm.state.first { it.lines.size == 2 }
        assertEquals(start, vm.state.value.steps)
    }

    @Test
    fun `a backup found on a new phone is offered before the import (R126)`() = runTest {
        foundBackup()
        sims.add(Sim(5, "SIM 1", null))
        val vm = vm()
        vm.atImport()
        val offer = vm.state.value.offer!!
        assertEquals(1, offer.payments)
        assertEquals(listOf(1L, 2L), offer.questions.map { it.line.id }, "SIM ids differ and numbers can't be read: asked (R115)")
        assertFalse(offer.ready)
        vm.answer(1L, 5)
        vm.answer(2L, null)
        vm.restore()
        val request = work.restores.single()
        assertEquals(RestoreMode.MERGE, request.mode)
        assertTrue(request.applySettings, "a new phone takes the backup's settings")
        assertFalse(request.deleteAfter, "the snapshot is never deleted")
        assertEquals(mapOf(1L to 5, 2L to null), request.answers)
    }

    @Test
    fun `no backup is offered once the phone has messages`() = runTest {
        foundBackup()
        SmsIngestor(db, Deriver(db)).ingest(RawSms("MPESA", Sms.KPLC, Sms.at("2026-03-21T12:00:00Z"), null, null, SmsSource.INBOX))
        val vm = vm()
        vm.atImport()
        assertNull(vm.state.value.offer)
    }

    @Test
    fun `after the restore the inbox import follows, and finishing keeps the snapshot`() = runTest {
        foundBackup()
        val vm = vm()
        vm.atImport()
        vm.restore()
        work.restoreProgress.value = RestoreProgress.Running(0, 0)
        vm.state.first { it.restore is RestoreProgress.Running }
        work.restoreProgress.value = RestoreProgress.Done(1, 1)
        val s = vm.state.first { it.restored }
        assertNull(s.offer)
        assertEquals(Step.IMPORT, s.step)
        vm.startImport()
        assertEquals(listOf("importInbox"), work.calls)
        vm.done()
        vm.state.first { it.finished }
        assertTrue(SnapshotStore(tmp.root).current.exists(), "restored: the snapshot stays this phone's")
    }

    @Test
    fun `Start fresh keeps the backup as the earlier one (R120)`() = runTest {
        foundBackup()
        val vm = vm()
        vm.atImport()
        vm.startFresh()
        vm.state.first { it.offer == null }
        val store = SnapshotStore(tmp.root)
        assertFalse(store.current.exists())
        assertTrue(store.earlier.exists())
    }

    @Test
    fun `finishing without answering keeps it too`() = runTest {
        foundBackup()
        val vm = vm()
        vm.onSmsResult(granted = false) // never reaches the import step
        vm.state.first { it.finished }
        assertTrue(SnapshotStore(tmp.root).earlier.exists())
    }

    @Test
    fun `with notifications already allowed, onboarding ends after the import`() = runTest {
        askNotifications = false
        val vm = vm()
        assertFalse(Step.NOTIFICATIONS in vm.state.value.steps)
        vm.atImport()
        vm.next()
        vm.state.first { it.finished }
        assertTrue(settings.current().onboarded)
    }

    @Test
    fun `finishing onboarding starts the 6-hourly check and each switched-on notification (R107, R108)`() = runTest {
        val vm = vm()
        vm.done()
        vm.state.first { it.finished }
        assertEquals(listOf("keepSyncing", "DAILY on replace=false", "WEEKLY on replace=false", "FULIZA on replace=false"), work.scheduled)
    }

    @Test
    fun `finishing onboarding starts the update checks and files this version's notes as seen, unshown (R134, R141)`() = runTest {
        val vm = vm()
        vm.done()
        vm.state.first { it.finished }
        assertEquals(listOf("keepChecking", "checkSoon"), updates.calls)
        assertNull(whatsNew.pending.first())
    }
}
