package com.ledga.app.ui.onboarding

import com.ledga.app.data.capture.InboxSms
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakeBackgroundWork
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.work.ImportProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val lines = LinesRepository(db.linesDao(), FakeSims())
    private val settings = SettingsStore(FakePrefsStore())
    private val work = FakeBackgroundWork()
    private val inbox = listOf(
        InboxSms("MPESA", Sms.SEND, Instant.parse("2026-03-21T10:30:30Z"), 1),
        InboxSms("MPESA", Sms.KPLC, Instant.parse("2026-03-23T12:00:30Z"), 1),
    )
    private var askNotifications = true

    private fun vm() = OnboardingViewModel(settings, { inbox }, work, lines, { askNotifications })

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
    fun `two lines found are named, and the names are saved`() = runTest {
        val first = lines.lineFor(1)!!
        val second = lines.lineFor(2)!!
        val vm = vm()
        vm.atImport()
        vm.next()
        val s = vm.state.first { it.step == Step.LINES }
        assertEquals(listOf(first, second), s.lines.map { it.id })
        assertTrue(Step.LINES in s.steps)
        vm.onLineName(second, "Work")
        vm.next()
        vm.state.first { it.step == Step.NOTIFICATIONS }
        assertEquals("Work", db.linesDao().all().single { it.id == second }.displayName)
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
}
