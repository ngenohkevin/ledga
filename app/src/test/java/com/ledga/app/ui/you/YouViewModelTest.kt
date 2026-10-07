package com.ledga.app.ui.you

import com.ledga.app.BuildConfig
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.startup.SmsAccess
import com.ledga.app.testing.FakeBackgroundWork
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.app.testing.twoLines
import com.ledga.app.testing.txRow
import com.ledga.app.ui.activity.ActivityLink
import com.ledga.app.ui.activity.ActivityLinks
import com.ledga.app.ui.onboarding.NotificationAccess
import com.ledga.app.work.ImportProgress
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec §10.4 You (R77, R81, R82). Synthetic data. */
@RunWith(RobolectricTestRunner::class)
class YouViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-06T06:00:00Z"))
    private val settings = SettingsStore(FakePrefsStore())
    private val work = FakeBackgroundWork()
    private val links = ActivityLinks()
    private var smsGranted = true
    private val vms = TestViewModels()

    private fun vm() = vms.track(
        YouViewModel(settings, db, LinesRepository(db.linesDao(), FakeSims(), clock), work, SmsAccess { smsGranted }, NotificationAccess { false }, links),
    )

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    @Test
    fun `the profile counts payments and the first day, and the rows sum up their screens`() = runTest {
        twoLines(db)
        db.transactionsDao().upsertAll(
            listOf(
                txRow(code = "TJK4AB12YA", at = Instant.parse("2025-09-12T06:00:00Z")),
                txRow(code = "TJK4AB12YB", at = Instant.parse("2026-10-01T06:00:00Z")),
                txRow(code = "TJK4AB12YC", at = Instant.parse("2026-10-02T06:00:00Z"), hidden = true),
            ),
        )
        settings.setDisplayName("Amani")
        val ui = vm().ui.first { it.loaded && it.name == "Amani" && it.payments == 2 && it.lines.size == 2 }
        assertEquals(LocalDate.parse("2025-09-12"), ui.since)
        assertEquals("Daily 8 PM · Weekly Sun · Fuliza", ui.notifications)
        assertEquals("System theme · Default text", ui.appearance)
        assertEquals(BuildConfig.VERSION_NAME, ui.version)
    }

    @Test
    fun `the name is trimmed, cut to 30 and cleared when blank`() = runTest {
        val vm = vm()
        vm.setName("  Amani    Wanjiru  ")
        assertEquals("Amani Wanjiru", vm.ui.first { it.name == "Amani Wanjiru" }.name)
        vm.setName("A".repeat(40))
        assertEquals(30, vm.ui.first { it.name?.length == 30 }.name!!.length)
        vm.setName("   ")
        assertNull(vm.ui.first { it.loaded && it.name == null }.name)
    }

    @Test
    fun `a rescan started here shows its progress and result, and the onboarding import's result doesn't show (R77)`() = runTest {
        work.inboxImport.value = ImportProgress.Done(found = 10_823, inserted = 10_823)
        val vm = vm()
        assertEquals(RescanState.Idle, vm.ui.first { it.loaded }.rescan)
        assertTrue(vm.rescan())
        assertEquals(listOf("importInbox"), work.calls)
        work.inboxImport.value = ImportProgress.Running(1_200, 10_823)
        assertEquals(RescanState.Running(1_200, 10_823), vm.ui.first { it.rescan is RescanState.Running }.rescan)
        work.inboxImport.value = ImportProgress.Done(found = 10_835, inserted = 12)
        assertEquals(RescanState.Done(12), vm.ui.first { it.rescan is RescanState.Done }.rescan)
    }

    @Test
    fun `without SMS access, Rescan asks for it first`() = runTest {
        smsGranted = false
        val vm = vm()
        assertFalse(vm.rescan())
        assertTrue(work.calls.isEmpty())
    }

    @Test
    fun `People opens Activity's People (R82)`() = runTest {
        vm().openPeople()
        assertEquals(ActivityLink.People, links.requests.value)
    }
}
