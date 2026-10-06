package com.ledga.app.ui.activity

import androidx.paging.testing.asSnapshot
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.FlowFilter
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.room.SmsSource
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.time.LiveClock
import com.ledga.core.model.Categories
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ActivityViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-03-25T09:00:00Z"))
    private val deriver = Deriver(db, clock)
    private val edits = TransactionEdits(db, deriver, clock)
    private val live = LiveClock(clock) { awaitCancellation() }

    private fun vm() = ActivityViewModel(LedgerQueries(db), db, LinesRepository(db.linesDao(), FakeSims(), clock), live, edits)

    @After fun close() = db.close()

    private suspend fun ingest(vararg bodies: String) =
        SmsIngestor(db, deriver).ingestAll(bodies.map { RawSms("MPESA", it, clock.instant(), null, null, SmsSource.INBOX) })

    private fun codes(items: List<ActivityItem>) = items.filterIsInstance<ActivityItem.Tx>().map { it.row.code }

    @Test
    fun `the list groups payments into Nairobi days, newest first`() = runTest {
        ingest(Sms.send("TJK4AB12HA", "500.00", "21/3/26 at 11:59 PM"), Sms.send("TJK4AB12HB", "200.00", "22/3/26 at 12:00 AM"), Sms.KPLC)
        val items = vm().items.asSnapshot()
        assertEquals(ActivityItem.Day(LocalDate.parse("2026-03-22"), closesPrevious = false), items.first())
        assertEquals(listOf("TJK4AB12HB", "TJK4AB12HA", "TJK4AB12FA"), codes(items))
        assertEquals(ActivityItem.End, items.last())
    }

    @Test
    fun `the chips narrow the list and its day totals together`() = runTest {
        ingest(Sms.KPLC, Sms.receive("TJK4AB12HC", "SAMPLE EMPLOYER LTD", "20,000.00"))
        val vm = vm()
        vm.setFlow(FlowFilter.IN)
        assertEquals(listOf("TJK4AB12HC"), codes(vm.items.asSnapshot()))
        val ui = vm.ui.first { it.filter.flow == FlowFilter.IN && it.dayTotals.isNotEmpty() }
        assertEquals(setOf(LocalDate.parse("2026-03-24")), ui.dayTotals.keys)
    }

    @Test
    fun `searching waits for typing to settle, then finds the payment`() = runTest {
        ingest(Sms.KPLC, Sms.SEND)
        val vm = vm()
        vm.setQuery("kplc")
        assertEquals("kplc", vm.ui.first { it.query == "kplc" }.query, "the field shows the text at once")
        assertEquals(listOf("TJK4AB12FA"), codes(vm.items.asSnapshot()))
    }

    @Test
    fun `show hidden lists hidden payments, and undo brings one back`() = runTest {
        ingest(Sms.KPLC, Sms.SEND)
        edits.setHidden("TJK4AB12FA", true)
        val vm = vm()
        assertEquals(listOf("TJK4AB12FB"), codes(vm.items.asSnapshot()))
        vm.applySheet(TransactionFilter(includeHidden = true))
        advanceUntilIdle()
        assertEquals(listOf("TJK4AB12FA", "TJK4AB12FB"), codes(vm.items.asSnapshot()))
        vm.undoHide("TJK4AB12FA").join()
        assertFalse(db.transactionsDao().get("TJK4AB12FA")!!.isHidden)
    }

    @Test
    fun `a category from Spending opens Transactions with its filter`() = runTest {
        ingest(Sms.KPLC, Sms.SEND)
        val vm = vm()
        vm.select(ActivitySegment.SPENDING)
        vm.showTransactions(TransactionFilter(categoryKeys = setOf(Categories.ELECTRICITY)))
        assertEquals(ActivitySegment.TRANSACTIONS, vm.segment.value)
        advanceUntilIdle()
        assertEquals(listOf("TJK4AB12FA"), codes(vm.items.asSnapshot()))
    }

    @Test
    fun `today follows the live clock, so TODAY moves after midnight`() = runTest {
        val vm = vm()
        assertEquals(LocalDate.parse("2026-03-25"), vm.ui.first { it.today != null }.today)
        clock.instant = Instant.parse("2026-03-25T21:00:01Z") // 00:00:01 on 26 March in Nairobi
        live.onResume()
        assertEquals(LocalDate.parse("2026-03-26"), vm.ui.first { it.today == LocalDate.parse("2026-03-26") }.today)
    }
}
