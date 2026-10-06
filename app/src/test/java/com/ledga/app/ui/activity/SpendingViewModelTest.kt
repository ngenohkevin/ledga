package com.ledga.app.ui.activity

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.room.dao.PeriodTotals
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.app.time.LiveClock
import com.ledga.core.model.Categories
import java.time.Instant
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

@RunWith(RobolectricTestRunner::class)
class SpendingViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-06T06:00:00Z")) // Tue 6 Oct 2026, 09:00 in Nairobi
    private val deriver = Deriver(db, clock)

    private val vms = TestViewModels()

    private fun vm(live: LiveClock = LiveClock(clock) { awaitCancellation() }) = vms.track(SpendingViewModel(LedgerQueries(db), db, live))

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    private suspend fun ingest(vararg bodies: String) =
        SmsIngestor(db, deriver).ingestAll(bodies.map { RawSms("MPESA", it, clock.instant(), null, null, SmsSource.INBOX) })

    // Each send costs Ksh 7: August 1,007; September 2,007 (on the 2nd) + KPLC 3,000 (the 12th); October 507.
    private val august = Sms.send("TJK4AB12SA", "1,000.00", "15/8/26 at 10:00 AM")
    private val septemberEarly = Sms.send("TJK4AB12SB", "2,000.00", "2/9/26 at 10:00 AM")
    private val septemberKplc = Sms.paybill("TJK4AB12SC", "KPLC PREPAID", "37100000001", "3,000.00", "12/9/26 at 9:00 AM")
    private val septemberIncome = Sms.receive("TJK4AB12SD", "SAMPLE EMPLOYER LTD", "20,000.00", "25/9/26 at 9:00 AM")
    private val october = Sms.send("TJK4AB12SE", "500.00", "2/10/26 at 10:00 AM")

    @Test
    fun `the current month shows first, with eight bars ending at it, the current one in progress`() = runTest {
        ingest(august, septemberEarly, septemberKplc, septemberIncome, october)
        val ui = vm().ui.first { it.loaded }
        assertEquals(YearMonth.of(2026, 10), ui.month)
        assertEquals(listOf("MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT"), ui.bars.map { it.label })
        assertEquals(listOf<Long>(0, 0, 0, 0, 0, 100_700, 500_700, 50_700), ui.bars.map { it.total })
        assertTrue(ui.bars.last().inProgress)
        assertEquals(7, ui.selectedIndex)
        assertFalse(ui.canGoForward)
    }

    @Test
    fun `stepping back shows a whole month against the month before it`() = runTest {
        ingest(august, septemberEarly, septemberKplc, septemberIncome, october)
        val vm = vm()
        vm.ui.first { it.loaded }
        vm.previous()
        val sep = vm.ui.first { it.month == YearMonth.of(2026, 9) }
        assertEquals(PeriodTotals(spentCents = 500_700, feeCents = 700, inCents = 2_000_000), sep.totals)
        assertEquals("Aug", sep.comparedWith)
        assertEquals(397, sep.deltaPercent) // (5,007 - 1,007) / 1,007 = 397.2 %
        assertEquals(6, sep.selectedIndex)
        assertTrue(sep.canGoForward)
        vm.next()
        assertEquals(YearMonth.of(2026, 10), vm.ui.first { it.month == YearMonth.of(2026, 10) }.month)
    }

    @Test
    fun `the current month compares with the same days of the last one`() = runTest {
        ingest(august, septemberEarly, septemberKplc, october)
        val ui = vm().ui.first { it.loaded }
        assertEquals("same days Sep", ui.comparedWith)
        // 1 Sep to 6 Sep 09:00 held only the 2 Sep send (2,007); October so far is 507: 74.7 % less.
        assertEquals(-75, ui.deltaPercent)
    }

    @Test
    fun `where it went splits by category and by group, and a tap filters Transactions to that month`() = runTest {
        ingest(august, septemberEarly, septemberKplc, october)
        val vm = vm()
        vm.ui.first { it.loaded }
        vm.previous()
        val sep = vm.ui.first { it.month == YearMonth.of(2026, 9) && it.shares.isNotEmpty() }
        assertEquals(listOf(Categories.ELECTRICITY to 300_000L, Categories.SENT_TO_PEOPLE to 200_700L), sep.shares.map { it.id to it.cents })
        assertEquals(0.599f, sep.shares.first().fraction, 0.001f)
        val filter = vm.transactionsFor(sep.shares.first())
        assertEquals(setOf(Categories.ELECTRICITY), filter.categoryKeys)
        assertEquals("September 2026", filter.dates?.label)
        vm.setByGroup(true)
        assertEquals(listOf("Bills & utilities", "Money"), vm.ui.first { it.byGroup && it.shares.isNotEmpty() }.shares.map { it.name })
    }

    @Test
    fun `when the month ends, the current month moves on`() = runTest {
        clock.instant = Instant.parse("2026-10-31T20:59:30Z") // 23:59:30 on 31 Oct in Nairobi
        ingest(october)
        val ticks = Channel<Unit>()
        val live = LiveClock(clock) { d ->
            ticks.receive()
            clock.instant = clock.instant.plus(d)
        }
        val vm = vm(live)
        assertEquals(YearMonth.of(2026, 10), vm.ui.first { it.loaded }.month)
        ticks.send(Unit) // midnight
        assertEquals(YearMonth.of(2026, 11), vm.ui.first { it.month == YearMonth.of(2026, 11) }.month)
    }
}
