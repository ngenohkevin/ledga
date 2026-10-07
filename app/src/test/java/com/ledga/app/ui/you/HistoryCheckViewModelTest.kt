package com.ledga.app.ui.you

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.testing.FakeBackgroundWork
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.app.testing.PERSONAL
import com.ledga.app.testing.twoLines
import com.ledga.app.testing.txRow
import com.ledga.app.time.LiveClock
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.ledga.app.work.ImportProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * R79: History check (spec §15.1). Each line's balances must follow: previous − amount (a paybill, no fee here) = stated.
 * Synthetic payments on one or two lines.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryCheckViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-06T06:00:00Z"))
    private val work = FakeBackgroundWork()
    private val vms = TestViewModels()

    private fun vm() = vms.track(HistoryCheckViewModel(db, LiveClock(clock) { awaitCancellation() }, work, TransactionEdits(db, Deriver(db, clock), clock)))

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    private fun pay(code: String, at: String, amount: Long, balance: Long, line: Long? = 1, hidden: Boolean = false) =
        txRow(code = code, at = Instant.parse(at), amountCents = amount, balanceCents = balance, lineId = line, hidden = hidden)

    @Test
    fun `balances that follow add up`() = runTest {
        db.transactionsDao().upsertAll(listOf(pay("TJK4AB12HA", "2026-10-01T06:00:00Z", 100_000, 500_000), pay("TJK4AB12HB", "2026-10-02T06:00:00Z", 100_000, 400_000)))
        val ui = vm().ui.first { it.loaded }
        assertEquals(1, ui.checked)
        assertTrue(ui.breaks.isEmpty())
    }

    @Test
    fun `a missing message shows as a break, with what was expected and what M-Pesa said, newest first`() = runTest {
        db.transactionsDao().upsertAll(
            listOf(
                pay("TJK4AB12HA", "2026-10-01T06:00:00Z", 100_000, 500_000),
                pay("TJK4AB12HB", "2026-10-02T06:00:00Z", 100_000, 400_000),
                pay("TJK4AB12HC", "2026-10-03T06:00:00Z", 50_000, 300_000), // 400,000 − 50,000 = 350,000 expected
                pay("TJK4AB12HD", "2026-10-04T06:00:00Z", 50_000, 200_000), // 300,000 − 50,000 = 250,000 expected
            ),
        )
        val ui = vm().ui.first { it.loaded }
        assertEquals(listOf("TJK4AB12HD", "TJK4AB12HC"), ui.breaks.map { it.tx.code })
        assertEquals(350_000L to 300_000L, ui.breaks.last().expectedCents to ui.breaks.last().statedCents)
        assertEquals("Expected Ksh 3,500.00 · M-Pesa said Ksh 3,000.00", HistoryText.breakLine(ui.breaks.last()))
    }

    @Test
    fun `a hidden payment still counts, because the wallet moved`() = runTest {
        db.transactionsDao().upsertAll(
            listOf(
                pay("TJK4AB12HA", "2026-10-01T06:00:00Z", 100_000, 500_000),
                pay("TJK4AB12HB", "2026-10-02T06:00:00Z", 100_000, 400_000, hidden = true),
                pay("TJK4AB12HC", "2026-10-03T06:00:00Z", 100_000, 300_000),
            ),
        )
        assertTrue(vm().ui.first { it.loaded }.breaks.isEmpty())
    }

    @Test
    fun `two lines are checked apart, and Rescan asks for the whole inbox`() = runTest {
        twoLines(db)
        db.transactionsDao().upsertAll(
            listOf(
                pay("TJK4AB12HA", "2026-10-01T06:00:00Z", 100_000, 500_000, line = 1),
                pay("TJK4AB12HE", "2026-10-01T07:00:00Z", 100_000, 900_000, line = 2),
                pay("TJK4AB12HB", "2026-10-02T06:00:00Z", 100_000, 400_000, line = 1),
                pay("TJK4AB12HF", "2026-10-02T07:00:00Z", 100_000, 700_000, line = 2), // expected 800,000
            ),
        )
        val vm = vm()
        val ui = vm.ui.first { it.loaded }
        assertEquals(listOf(LineCheckUi("Personal ··11", 1, 0), LineCheckUi("Business ··78", 1, 1)), ui.lines)
        vm.rescan()
        assertEquals(listOf("importInbox"), work.calls)
    }

    @Test
    fun `a rescan started here checks the history again when it finishes`() = runTest {
        db.transactionsDao().upsertAll(listOf(pay("TJK4AB12HA", "2026-10-01T06:00:00Z", 100_000, 500_000), pay("TJK4AB12HC", "2026-10-03T06:00:00Z", 50_000, 300_000)))
        val vm = vm()
        assertEquals(1, vm.ui.first { it.loaded }.breaks.size)
        // Keep the screen's state live while the test waits in real time (Robolectric's coroutine timeouts never fire).
        val watching = CoroutineScope(Dispatchers.Main).launch { vm.ui.collect { } }
        vm.rescan()
        db.transactionsDao().upsertAll(listOf(pay("TJK4AB12HB", "2026-10-02T06:00:00Z", 150_000, 350_000))) // what the rescan found
        work.inboxImport.value = ImportProgress.Running(1, 2)
        work.inboxImport.value = ImportProgress.Done(found = 2, inserted = 1)
        var tries = 0
        while (vm.ui.value.breaks.isNotEmpty() && tries++ < 100) Thread.sleep(20)
        watching.cancel()
        assertTrue(vm.ui.value.breaks.isEmpty(), "the history was checked again once the rescan finished")
    }

    @Test
    fun `with two lines, payments not on a line are left out of the verdict and marked (owner 2026-10-07)`() = runTest {
        twoLines(db)
        db.transactionsDao().upsertAll(
            listOf(
                pay("TJK4AB12HA", "2026-10-01T06:00:00Z", 100_000, 500_000, line = 1),
                pay("TJK4AB12HB", "2026-10-02T06:00:00Z", 100_000, 400_000, line = 1),
                // Not on a line: one from each SIM, so the balances jump and look like a break.
                pay("TJK4AB12HG", "2026-10-01T08:00:00Z", 100_000, 500_000, line = null),
                pay("TJK4AB12HH", "2026-10-02T08:00:00Z", 100_000, 900_000, line = null),
            ),
        )
        val ui = vm().ui.first { it.loaded }
        assertEquals(1, ui.checked)
        assertTrue(ui.breaks.isEmpty(), "a jump between two SIMs is not a break")
        assertEquals(listOf(LineCheckUi("Personal ··11", 1, 0), LineCheckUi("Not on a line", 1, 1, mixed = true)), ui.lines)
    }

    @Test
    fun `with one line, payments not on a line are still checked`() = runTest {
        db.linesDao().insert(PERSONAL)
        db.transactionsDao().upsertAll(
            listOf(
                pay("TJK4AB12HG", "2026-10-01T08:00:00Z", 100_000, 500_000, line = null),
                pay("TJK4AB12HH", "2026-10-02T08:00:00Z", 100_000, 300_000, line = null), // expected 400,000
            ),
        )
        val ui = vm().ui.first { it.loaded }
        assertEquals(listOf("TJK4AB12HH"), ui.breaks.map { it.tx.code })
        assertTrue(ui.lines.none { it.mixed })
    }
}
