package com.ledga.app.ui.lines

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.lines.LinePlacements
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.testing.BUSINESS
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.PERSONAL
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.app.testing.twoLines
import com.ledga.app.testing.txRow
import com.ledga.app.time.LiveClock
import com.ledga.core.model.TxKind
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R116, R128: Not on a line. Synthetic payments (rows only: placing them is Task 9's, tested there with real SMS). */
@RunWith(RobolectricTestRunner::class)
class UnassignedViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-03-25T06:00:00Z"))
    private val vms = TestViewModels()

    private fun vm() = vms.track(
        UnassignedViewModel(LinePlacements(db), TransactionEdits(db, Deriver(db, clock), clock), LinesRepository(db.linesDao(), FakeSims(), clock), LiveClock(clock) { awaitCancellation() }),
    )

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    private suspend fun seed() {
        twoLines(db)
        db.transactionsDao().upsertAll(
            listOf(
                txRow(code = "TJK4AB15AA", kind = TxKind.RECEIVE, amountCents = 100_000, balanceCents = 100_000, at = Instant.parse("2026-03-21T03:00:00Z"), lineId = PERSONAL.id),
                txRow(code = "TJK4AB15AB", kind = TxKind.RECEIVE, amountCents = 500_000, balanceCents = 500_000, at = Instant.parse("2026-03-21T03:30:00Z"), lineId = BUSINESS.id),
                txRow(code = "TJK4AB15AC", kind = TxKind.SEND, amountCents = 30_000, balanceCents = 70_000, at = Instant.parse("2026-03-21T04:00:00Z"), lineId = null),
                txRow(code = "TJK4AB15AD", kind = TxKind.RECEIVE, amountCents = 1_000, balanceCents = 999_999, at = Instant.parse("2026-03-22T06:00:00Z"), lineId = null),
            ),
        )
    }

    @Test
    fun `it counts what the balances can place, per line, and what stays unclear`() = runTest {
        seed()
        val ui = vm().ui.first { it.loaded && !it.counting }
        assertEquals(2, ui.unassigned)
        assertEquals(listOf(PlacedShare("Personal ··11", 1)), ui.shares)
        assertEquals(1, ui.placeable)
        assertEquals(1, ui.left)
    }

    @Test
    fun `a date range counts the payments not on a line in it, and starts on the first line`() = runTest {
        seed()
        val vm = vm()
        vm.ui.first { it.loaded }
        assertEquals(PERSONAL.id, vm.ui.value.rangeLine)
        vm.setFrom(LocalDate.parse("2026-03-22"))
        vm.setTo(LocalDate.parse("2026-03-22"))
        assertEquals(1, vm.ui.first { it.inRange == 1 }.inRange)
    }

    @Test
    fun `moving a range puts its payments on the chosen line, and Undo takes them back`() = runTest {
        twoLines(db)
        val ingest = SmsIngestor(db, Deriver(db, clock))
        ingest.ingest(RawSms("MPESA", Sms.SEND, Sms.at("2026-03-21T10:30:00Z"), null, null, SmsSource.INBOX))
        ingest.ingest(RawSms("MPESA", Sms.KPLC, Sms.at("2026-03-21T12:00:00Z"), null, null, SmsSource.INBOX))
        val vm = vm()
        vm.ui.first { it.loaded }
        vm.chooseLine(BUSINESS.id)
        vm.setFrom(LocalDate.parse("2026-03-21"))
        vm.setTo(LocalDate.parse("2026-03-21"))
        vm.ui.first { it.inRange == 2 }
        val done = CompletableDeferred<Moved>()
        vm.moveRange { done.complete(it) }
        val moved = done.await()
        assertEquals(Moved(2, moved.codes, byBalance = false, lineLabel = "Business"), moved)
        assertEquals(listOf<Long?>(BUSINESS.id, BUSINESS.id), listOf("TJK4AB12FB", "TJK4AB12FA").map { db.transactionsDao().get(it)?.lineId })
        vm.ui.first { it.unassigned == 0 }
        vm.undo(moved)
        vm.ui.first { it.unassigned == 2 }
        assertEquals(listOf<Long?>(null, null), listOf("TJK4AB12FB", "TJK4AB12FA").map { db.transactionsDao().get(it)?.lineId })
    }
}
