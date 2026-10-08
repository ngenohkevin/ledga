package com.ledga.app.ui.home

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.AlertRow
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.data.trackers.Trackers
import com.ledga.app.data.update.ReleasesResponse
import com.ledga.app.data.update.UpdateStore
import com.ledga.app.data.update.WhatsNew
import com.ledga.app.testing.FakeBackgroundWork
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeUpdateHttp
import com.ledga.app.testing.FakeUpdateWork
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.OFF_IN_SETTINGS
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.app.testing.ghList
import com.ledga.app.testing.ghRelease
import com.ledga.app.testing.selectedLine
import com.ledga.app.testing.testUpdateService
import com.ledga.app.testing.twoLines
import com.ledga.app.testing.txRow
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.activity.ActivityLink
import com.ledga.app.ui.activity.ActivityLinks
import com.ledga.app.ui.onboarding.NotificationAccess
import com.ledga.core.model.Categories
import com.ledga.core.time.PeriodType
import com.ledga.core.update.AppVersion
import com.ledga.core.update.NotesSection
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.ledga.app.data.room.LineRow
import com.ledga.app.data.lines.LineMerges
import com.ledga.app.testing.FakeSims

/** Spec §10.4 Home: every card from the ledger, on the chosen line, live (R47, R57–R61). Synthetic SMS and rows. */
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-03-25T09:00:00Z")) // Wed 25 Mar 2026, 12:00 in Nairobi
    private val deriver = Deriver(db, clock)
    private val work = FakeBackgroundWork()
    private val prefs = FakePrefsStore()
    private val settings = SettingsStore(prefs)
    private val links = ActivityLinks()
    private val homeLinks = HomeLinks()
    private var granted = true
    private var notifyAsk = false
    private var notifyAccess: NotificationAccess = NotificationAccess { notifyAsk }
    private val vms = TestViewModels()
    private val updateHttp = FakeUpdateHttp()
    private val updateWork = FakeUpdateWork()
    private val updateStore = UpdateStore(FakePrefsStore())
    private val updates by lazy { testUpdateService(http = updateHttp, work = updateWork, clock = clock, store = updateStore) }
    private val merges = LineMerges(db, FakeSims(), settings, deriver)
    private val whatsNew = WhatsNew(updateStore, { listOf(NotesSection("What's new", listOf("A new Home"))) }, AppVersion.parse("2.0.0-beta.1")!!)

    private fun vm(live: LiveClock = LiveClock(clock) { awaitCancellation() }) = vms.track(
        HomeViewModel(
            LedgerQueries(db), db, Trackers(db, LedgerQueries(db)), selectedLine(db, prefs), live, work,
            { granted }, notifyAccess, settings, TransactionEdits(db, deriver, clock), links, homeLinks, updates, whatsNew, merges,
        ),
    )

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    private suspend fun ingest(vararg bodies: String) =
        SmsIngestor(db, deriver).ingestAll(bodies.map { RawSms("MPESA", it, clock.instant(), null, null, SmsSource.INBOX) })

    @Test
    fun `Home shows this month's spending, Recent and the newest balance`() = runTest {
        ingest(Sms.SEND, Sms.KPLC) // both on 21 March
        val ui = vm().ui.first { it.loaded && it.recent.size == 2 }
        assertEquals(150_700, ui.spending.spentCents) // 500 + 7 fee + 1,000
        assertEquals(listOf("TJK4AB12FA", "TJK4AB12FB"), ui.recent.map { it.code })
        assertEquals(200_000, ui.balance?.cents, "KPLC's message is the newer one")
        assertTrue(ui.hasHistory)
    }

    @Test
    fun `the greeting follows the hour, re-read when Home resumes`() = runTest {
        val vm = vm()
        assertEquals("Good afternoon", vm.ui.first { it.loaded }.greeting)
        clock.instant = Instant.parse("2026-03-25T15:00:00Z") // 18:00
        vm.refresh()
        assertEquals("Good evening", vm.ui.first { it.greeting == "Good evening" }.greeting)
    }

    @Test
    fun `the greeting moves on with the hour while Home stays open`() = runTest {
        clock.instant = Instant.parse("2026-03-25T08:59:30Z") // 11:59:30 in Nairobi
        val ticks = Channel<Unit>()
        val vm = vm(LiveClock(clock) { ticks.receive() })
        assertEquals("Good morning", vm.ui.first { it.loaded }.greeting)
        clock.instant = Instant.parse("2026-03-25T09:00:00.001Z")
        ticks.send(Unit) // just past noon
        // A later change shows the greeting the tick left, without waiting on one that may never come.
        vm.setPeriod(PeriodType.WEEK)
        assertEquals("Good afternoon", vm.ui.first { it.spending.type == PeriodType.WEEK }.greeting)
    }

    @Test
    fun `the chosen line narrows Home, and each line's balance shows only under All lines`() = runTest {
        twoLines(db)
        db.transactionsDao().upsertAll(
            listOf(
                txRow(code = "TJK4AB12QA", lineId = 1, amountCents = 100_000, balanceCents = 500_000, at = Instant.parse("2026-03-20T07:00:00Z")),
                txRow(code = "TJK4AB12QB", lineId = 2, amountCents = 40_000, balanceCents = 120_000, at = Instant.parse("2026-03-21T07:00:00Z")),
            ),
        )
        val vm = vm()
        val all = vm.ui.first { it.loaded && it.line.showChip && it.balance != null }
        assertEquals(620_000, all.balance?.cents)
        assertEquals(
            listOf(
                BalanceLine("Personal ··11", 500_000, Instant.parse("2026-03-20T07:00:00Z")),
                BalanceLine("Business ··78", 120_000, Instant.parse("2026-03-21T07:00:00Z")),
            ),
            all.balanceLines,
            "the total, then each line's own balance and time (owner, 2026-10-06)",
        )
        assertEquals(140_000, all.spending.spentCents)
        vm.selectLine(2)
        val business = vm.ui.first { it.line.lineId == 2L }
        assertEquals(120_000, business.balance?.cents)
        assertEquals(emptyList(), business.balanceLines, "the chip already says which line")
        assertEquals(40_000, business.spending.spentCents)
        assertEquals(listOf("TJK4AB12QB"), business.recent.map { it.code })
        assertEquals(40_000, business.trackers.first { it.category.key == Categories.ELECTRICITY }.thisMonth.total.cents)
    }

    @Test
    fun `Week and Year change the spending card`() = runTest {
        ingest(Sms.SEND, Sms.KPLC) // Saturday 21 March; today is Wednesday 25 March
        val vm = vm()
        vm.ui.first { it.loaded && it.spending.spentCents == 150_700L }
        vm.setPeriod(PeriodType.WEEK)
        val week = vm.ui.first { it.spending.type == PeriodType.WEEK }
        assertEquals(0, week.spending.spentCents, "21 March was last week")
        assertEquals(150_700, week.spending.bars[4])
        vm.setPeriod(PeriodType.YEAR)
        assertEquals(150_700, vm.ui.first { it.spending.type == PeriodType.YEAR }.spending.spentCents)
    }

    @Test
    fun `the Year card's average counts a first year only for its months`() = runTest {
        db.transactionsDao().upsertAll(
            listOf(
                txRow(code = "TJK4AB12QC", amountCents = 100_000, at = Instant.parse("2024-06-10T07:00:00Z")),
                txRow(code = "TJK4AB12QD", amountCents = 300_000, at = Instant.parse("2025-06-10T07:00:00Z")),
            ),
        )
        val vm = vm()
        vm.ui.first { it.loaded }
        vm.setPeriod(PeriodType.YEAR)
        // History starts in June 2024: Ksh 4,000 over 19 months is Ksh 2,526.32 a year, not Ksh 2,000.
        assertEquals(252_632, vm.ui.first { it.spending.type == PeriodType.YEAR }.spending.averageCents)
    }

    @Test
    fun `when the month ends, the spending card and the trackers move on`() = runTest {
        ingest(Sms.KPLC) // 21 March, Ksh 1,000
        val ticks = Channel<Unit>()
        val vm = vm(LiveClock(clock) { ticks.receive() })
        val march = vm.ui.first { it.loaded && it.spending.spentCents == 100_000L }
        assertEquals(100_000, march.trackers.first { it.category.key == Categories.ELECTRICITY }.thisMonth.total.cents)
        clock.instant = Instant.parse("2026-03-31T21:00:00Z") // 1 April, 00:00 in Nairobi
        ticks.send(Unit)
        val april = vm.ui.first { it.today == LocalDate.parse("2026-04-01") }
        assertEquals(0, april.spending.spentCents)
        assertEquals(listOf("Nov", "Dec", "Jan", "Feb", "Mar", "Apr"), april.spending.labels)
        val electricity = april.trackers.first { it.category.key == Categories.ELECTRICITY }
        assertEquals(0, electricity.thisMonth.total.cents)
        assertEquals(100_000, electricity.lastMonth.total.cents)
    }

    @Test
    fun `the notifications banner shows once onboarded until Not now, and a lasting refusal sends the next tap to Settings`() = runTest {
        notifyAsk = true
        val vm = vm()
        assertFalse(vm.ui.first { it.loaded }.notificationsNudge, "not before onboarding")
        settings.setOnboarded()
        assertTrue(vm.ui.first { it.notificationsNudge }.notificationsNudge)
        vm.onNotificationsResult(granted = false, showRationale = false)
        assertTrue(vm.ui.first { it.notificationsToSettings }.notificationsToSettings)
        vm.dismissNotifications()
        assertFalse(vm.ui.first { !it.notificationsNudge }.notificationsNudge)
    }

    @Test
    fun `allowing SMS imports the whole inbox once, and a lasting refusal sends the next tap to Settings`() = runTest {
        granted = false
        val vm = vm()
        vm.ui.first { it.loaded && !it.smsGranted }
        vm.onSmsDenied(showRationale = false)
        assertTrue(vm.ui.first { it.smsToSettings }.smsToSettings)
        granted = true
        vm.refresh() // back from Settings
        vm.refresh() // and the next resume
        assertFalse(vm.ui.first { it.smsGranted }.smsToSettings)
        assertEquals(listOf("importInbox"), work.calls)
    }

    @Test
    fun `Home says when v1's notes and categories couldn't be moved`() = runTest {
        val vm = vm()
        work.legacyImportFailed.value = true
        vm.ui.first { it.legacyImportFailed }
    }

    @Test
    fun `See all, search and the spending card open Activity through the links, on the chosen line`() = runTest {
        twoLines(db)
        val vm = vm()
        vm.selectLine(2)
        vm.ui.first { it.line.lineId == 2L }
        vm.openRecent()
        assertEquals(ActivityLink.Transactions(TransactionFilter(lineId = 2)), links.requests.value)
        vm.openSearch()
        assertEquals(ActivityLink.Transactions(focusSearch = true), links.requests.value)
        vm.openSpending()
        assertEquals(ActivityLink.Spending, links.requests.value)
    }

    @Test
    fun `the bell counts the alerts not yet read (R71)`() = runTest {
        db.alertsDao().insertIgnore(AlertRow("a", "DAILY", "t", "b", null, Instant.parse("2026-03-24T17:00:00Z"), null))
        db.alertsDao().insertIgnore(AlertRow("b", "DAILY", "t", "b", null, Instant.parse("2026-03-23T17:00:00Z"), null))
        db.alertsDao().insertIgnore(AlertRow("c", "DAILY", "t", "b", null, Instant.parse("2026-03-22T17:00:00Z"), Instant.parse("2026-03-22T18:00:00Z")))
        assertEquals(2, vm().ui.first { it.unreadAlerts == 2 }.unreadAlerts)
    }

    @Test
    fun `with notifications off in Android's settings the banner shows, and Turn on opens the settings (R109)`() = runTest {
        notifyAccess = OFF_IN_SETTINGS
        settings.setOnboarded()
        val ui = vm().ui.first { it.loaded && it.notificationsNudge }
        assertTrue(ui.notificationsToSettings)
    }

    @Test
    fun `a Fuliza reminder's request waits for Home until it has shown the sheet (R100)`() = runTest {
        val vm = vm()
        homeLinks.openFuliza(null)
        assertEquals(FulizaRequest(null), vm.fulizaAsked.value)
        vm.fulizaShown(FulizaRequest(null))
        assertNull(vm.fulizaAsked.value)
    }

    @Test
    fun `a Fuliza reminder for the other line switches Home to that line, and All lines stays (R100, owner 2026-10-07)`() = runTest {
        twoLines(db)
        settings.setSelectedLine(2)
        val vm = vm()
        homeLinks.openFuliza(1)
        vm.fulizaShown(FulizaRequest(1))
        assertEquals(1L, settings.settings.first { it.selectedLineId == 1L }.selectedLineId)
        settings.setSelectedLine(null)
        homeLinks.openFuliza(2)
        vm.fulizaShown(FulizaRequest(2))
        assertNull(vm.ui.first { it.loaded }.line.lineId, "All lines stays All lines")
    }

    @Test
    fun `an offered update shows on Home, Update asks for a person's download, and Later hides it (R148)`() = runTest {
        updateHttp.answers += ReleasesResponse.Fresh(ghList(ghRelease("v2.0.0-beta.2")), null)
        updates.check()
        val vm = vm()
        assertEquals(HomeUpdate.Available("2.0.0-beta.2"), vm.ui.first { it.update != null }.update)
        vm.downloadUpdate().join()
        assertEquals("download 2.0.0-beta.2 user", updateWork.calls.last())
        vm.updateLater().join()
        assertNull(vm.ui.first { it.loaded && it.update == null }.update)
    }

    @Test
    fun `What's new waits on Home until it is seen (R141)`() = runTest {
        val vm = vm()
        assertEquals(listOf(NotesSection("What's new", listOf("A new Home"))), vm.whatsNew.first { it != null })
        assertEquals("2.0.0-beta.1", vm.whatsNewVersion)
        vm.whatsNewSeen().join()
        assertNull(vm.whatsNew.first { it == null })
    }

    @Test
    fun `Home suggests merging a line that carries on another's balance, and Merge or Not the same answers it (R177)`() = runTest {
        val two = db.linesDao().insert(LineRow(subscriptionId = 3, phoneNumber = null, displayName = "Line 2", color = "#00A86B", isPrimary = false, createdAt = clock.instant()))
        val three = db.linesDao().insert(LineRow(subscriptionId = 2, phoneNumber = null, displayName = "Line 3", color = "#00A86B", isPrimary = false, createdAt = clock.instant()))
        SmsIngestor(db, deriver).ingestAll(
            listOf(
                RawSms("MPESA", "TJK4AB12KC Confirmed. Ksh500.00 sent to SAMPLE PERSON 0700000001 on 6/9/26 at 9:00 AM. New M-PESA balance is Ksh1,000.00. Transaction cost, Ksh7.00.", clock.instant(), 3, two, SmsSource.INBOX),
                RawSms("MPESA", "TJK4AB12KE Confirmed.You have received Ksh200.00 from SAMPLE CLIENT 0700000002 on 26/9/26 at 6:00 PM New M-PESA balance is Ksh1,200.00.", clock.instant(), 2, three, SmsSource.INBOX),
            ),
        )
        val vm = vm()
        val shown = vm.ui.first { it.lineMerge != null }.lineMerge!!
        assertEquals("Line 2 and Line 3 look like the same number", shown.text)
        vm.mergeLines().join()
        assertNull(vm.ui.first { it.lineMerge == null }.lineMerge)
        assertEquals(listOf("Line 3"), db.linesDao().all().map { it.displayName })
    }
}
