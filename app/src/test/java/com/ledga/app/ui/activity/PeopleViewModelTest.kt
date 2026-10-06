package com.ledga.app.ui.activity

import com.ledga.core.model.TxKind
import com.ledga.core.model.Categories
import com.ledga.app.testing.txRow
import com.ledga.app.testing.twoLines
import com.ledga.app.testing.selectedLine
import androidx.paging.testing.asSnapshot
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.derive.PeopleDirection
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.room.dao.PersonSummary
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.app.time.LiveClock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PeopleViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = Clock.fixed(Instant.parse("2026-03-26T06:00:00Z"), ZoneOffset.UTC)
    private val deriver = Deriver(db, clock)
    private val live = LiveClock(clock) { awaitCancellation() }

    private val vms = TestViewModels()

    private fun vm() = vms.track(PeopleViewModel(LedgerQueries(db), live, selectedLine(db)))

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    private suspend fun ingest(vararg bodies: String) =
        SmsIngestor(db, deriver).ingestAll(bodies.map { RawSms("MPESA", it, clock.instant(), null, null, SmsSource.INBOX) })

    private val john = "TJK4AB12PC Confirmed. Ksh2,000.00 sent to JOHN SAMPLE 0722000999 on 22/3/26 at 9:00 AM. " +
        "New M-PESA balance is Ksh1,200.00. Transaction cost, Ksh13.00."

    @Test
    fun `sent to lists people by total, and received from lists who paid you`() = runTest {
        ingest(
            Sms.SEND,
            Sms.send("TJK4AB12PA", "300.00", "23/3/26 at 9:00 AM"),
            Sms.paybill("TJK4AB12PB", "SAMPLE ACADEMY", "ADM 1024", "5,000.00"),
            Sms.receive("TJK4AB12HC", "SAMPLE EMPLOYER LTD", "20,000.00"),
        )
        val vm = vm()
        val sent = vm.ui.first { it.loaded }
        assertEquals(listOf("Jane Tester"), sent.rows.map { it.name }, "a paybill isn't a person")
        assertEquals(80_000, sent.rows.single().totalCents)
        vm.setDirection(PeopleDirection.RECEIVED)
        assertEquals(
            listOf("Sample Employer LTD"),
            vm.ui.first { it.direction == PeopleDirection.RECEIVED && it.loaded }.rows.map { it.name },
        )
    }

    @Test
    fun `search by name or phone digits and the minimum narrow the list, and switching direction resets the minimum`() = runTest {
        ingest(Sms.SEND, john)
        val vm = vm()
        assertEquals(listOf("John Sample", "Jane Tester"), vm.ui.first { it.loaded && it.rows.size == 2 }.rows.map { it.name })
        vm.setQuery("345")
        assertEquals(listOf("Jane Tester"), vm.ui.first { it.query == "345" }.rows.map { it.name }, "phone digits")
        vm.setQuery("john")
        assertEquals(listOf("John Sample"), vm.ui.first { it.query == "john" }.rows.map { it.name })
        vm.setQuery("")
        vm.setMinimum(100_000)
        val atLeast = vm.ui.first { it.query.isEmpty() && it.minCents == 100_000L }
        assertEquals(listOf("John Sample"), atLeast.rows.map { it.name })
        assertEquals(200_000L, atLeast.maxCents)
        vm.setDirection(PeopleDirection.RECEIVED)
        assertEquals(0L, vm.ui.first { it.direction == PeopleDirection.RECEIVED }.minCents)
    }

    @Test
    fun `the person sheet totals both directions and pages their payments, newest first`() = runTest {
        ingest(Sms.SEND, Sms.send("TJK4AB12PA", "300.00", "23/3/26 at 9:00 AM"), Sms.receive("TJK4AB12PD", "JANE TESTER 0712345111", "900.00"))
        val jane = vm().ui.first { it.loaded }.rows.single()
        val sheet = vms.track(PersonSheetViewModel(LedgerQueries(db), db, live, selectedLine(db)))
        sheet.open(jane)
        assertEquals(PersonSummary(80_000, 2, 90_000, 1), sheet.ui.first { it.person != null && it.summary.sentCount == 2 }.summary)
        assertEquals(listOf("TJK4AB12PD", "TJK4AB12PA", "TJK4AB12FB"), sheet.items.asSnapshot().map { it.code })
    }

    @Test
    fun `with a line chosen, People and their totals count only that line`() = runTest {
        twoLines(db)
        fun send(code: String, cents: Long, line: Long) = txRow(
            code = code, kind = TxKind.SEND, name = "JANE TESTER", phone = "0712345111", account = null,
            categoryKey = Categories.SENT_TO_PEOPLE, amountCents = cents, lineId = line,
        )
        db.transactionsDao().upsertAll(listOf(send("TJK4AB12HC", 50_000, 1), send("TJK4AB12HD", 70_000, 2)))
        val line = selectedLine(db)
        val vm = vms.track(PeopleViewModel(LedgerQueries(db), live, line))
        assertEquals(120_000, vm.ui.first { it.loaded && it.rows.isNotEmpty() }.rows.single().totalCents)
        line.select(2)
        assertEquals(70_000, vm.ui.first { it.line.lineId == 2L }.rows.single().totalCents)
    }
}
