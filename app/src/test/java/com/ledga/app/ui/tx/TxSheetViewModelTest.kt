package com.ledga.app.ui.tx

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.room.SmsSource
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.time.LiveClock
import com.ledga.core.model.Categories
import com.ledga.core.model.FlowKind
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TxSheetViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = Clock.fixed(Instant.parse("2026-06-10T06:00:00Z"), ZoneOffset.UTC)
    private val deriver = Deriver(db, clock)
    private val edits = TransactionEdits(db, deriver, clock)

    private fun vm() = TxSheetViewModel(db, edits, LinesRepository(db.linesDao(), FakeSims(), clock), LiveClock(clock) { awaitCancellation() })

    @After fun close() = db.close()

    private suspend fun ingest(vararg bodies: String) =
        SmsIngestor(db, deriver).ingestAll(bodies.map { RawSms("MPESA", it, clock.instant(), null, null, SmsSource.INBOX) })

    @Test
    fun `opening a payment shows it with its category, today and every SMS behind it`() = runTest {
        ingest(Sms.PURCHASE, Sms.COMPANION)
        val vm = vm()
        vm.open("TJK4AB12EA")
        val s = vm.state.first { it.tx != null && it.sms.size == 2 }
        assertEquals(Categories.OTHER, s.category?.key)
        assertEquals(LocalDate.parse("2026-06-10"), s.today)
        assertTrue(s.sms.any { it.contains("Fuliza M-PESA amount is") }, "the Fuliza companion shows with the payment")
    }

    @Test
    fun `a note saved from the sheet shows at once`() = runTest {
        ingest(Sms.SEND)
        val vm = vm()
        vm.open("TJK4AB12FB")
        vm.state.first { it.tx != null }
        vm.setNote("  lunch ")
        assertEquals("lunch", vm.state.first { it.tx?.note != null }.tx?.note)
    }

    @Test
    fun `my own account asks about all from the name when more than one would change, and changes one at once`() = runTest {
        ingest(Sms.BANK_APP, Sms.receive("TJK4AB12LA", "EXAMPLE BANK LIMITED- APP", "3,000.00"), Sms.SEND)
        val vm = vm()
        vm.open("TJK4AB12FC")
        vm.state.first { it.tx != null }
        vm.setOwnAccount(true)
        assertEquals(PendingOwn(own = true, count = 2, name = "Example Bank Limited"), vm.state.first { it.pendingOwn != null }.pendingOwn)
        vm.confirmOwn(allFromName = true)
        assertNull(vm.state.first { it.isOwnAccount }.pendingOwn)
        assertEquals(FlowKind.OWN_IN, db.transactionsDao().get("TJK4AB12LA")?.flow, "the other transfer too")

        vm.open("TJK4AB12FB")
        vm.state.first { it.tx?.code == "TJK4AB12FB" }
        vm.setOwnAccount(true)
        assertNull(vm.state.first { it.tx?.code == "TJK4AB12FB" && it.isOwnAccount }.pendingOwn, "one payment from that name: no question")
    }

    @Test
    fun `a Fuliza repayment has no own-account switch`() = runTest {
        ingest(Sms.REPAY_FULL)
        val vm = vm()
        vm.open("TJK4AB12FF")
        assertFalse(vm.state.first { it.tx != null }.canBeOwnAccount)
    }

    @Test
    fun `switching own account off where only this payment changes asks nothing`() = runTest {
        ingest(Sms.send("TJK4AB12KA", "500.00"), Sms.send("TJK4AB12KB", "600.00", "22/3/26 at 1:30 PM"), Sms.send("TJK4AB12KC", "700.00", "23/3/26 at 1:30 PM"))
        edits.setOwnAccount("TJK4AB12KA", own = true, allFromName = false)
        val vm = vm()
        vm.open("TJK4AB12KA")
        vm.state.first { it.tx?.code == "TJK4AB12KA" && it.isOwnAccount }
        vm.setOwnAccount(false)
        val s = vm.state.first { it.tx?.code == "TJK4AB12KA" && !it.isOwnAccount }
        assertNull(s.pendingOwn, "one payment changes: no question about all 3")
    }
}
