package com.ledga.app.data.derive

import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.FulizaReading
import com.ledga.app.data.room.SmsSource
import com.ledga.app.testing.TestDb
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class FulizaStatusTest {
    private var t = Instant.parse("2026-04-01T06:00:00Z")
    private fun r(kind: TxKind, amount: Long, outstanding: Long? = null, limit: Long? = null, due: LocalDate? = null, line: Long? = 1): FulizaReading {
        t = t.plusSeconds(3600)
        return FulizaReading("C" + t.epochSecond, line, kind, t, amount, outstanding, limit, due)
    }

    @Test
    fun `draw, partial repay with a limit, second draw, full repay`() {
        val due = LocalDate.of(2026, 4, 20)
        val draw = r(TxKind.FULIZA_ONLY, 60_000, outstanding = 60_000, due = due)
        assertEquals(FulizaStatus(Money(60_000), null, null, due), FulizaStatus.of(listOf(draw)))
        val partial = r(TxKind.FULIZA_REPAY_AUTO, 20_000, limit = 140_000)
        assertEquals(FulizaStatus(Money(40_000), Money(180_000), Money(140_000), due), FulizaStatus.of(listOf(draw, partial)))
        val again = r(TxKind.FULIZA_ONLY, 50_000, outstanding = 90_000, due = due)
        assertEquals(FulizaStatus(Money(90_000), Money(180_000), Money(90_000), due), FulizaStatus.of(listOf(draw, partial, again)))
        val full = r(TxKind.FULIZA_REPAY_AUTO, 90_000, outstanding = 0, limit = 180_000)
        assertEquals(FulizaStatus(Money.ZERO, Money(180_000), Money(180_000), null), FulizaStatus.of(listOf(draw, partial, again, full)))
    }

    @Test
    fun `lines are independent`() {
        val a = r(TxKind.FULIZA_ONLY, 10_000, outstanding = 10_000, line = 1)
        val b = r(TxKind.FULIZA_ONLY, 30_000, outstanding = 30_000, line = 2)
        val status = FulizaStatus.perLine(listOf(a, b))
        assertEquals(Money(10_000), status.getValue(1).outstanding)
        assertEquals(Money(30_000), status.getValue(2).outstanding)
    }

    @Test
    fun `readings come from derived transactions`() = runTest {
        val db = TestDb.inMemory()
        val companion = "TJK4AB12JA Confirmed. Fuliza M-PESA amount is Ksh 600.00. Access Fee charged Ksh 6.00. Total Fuliza M-PESA outstanding amount is Ksh600.00 due on 20/04/26. To check daily charges, Dial *334#OK Select Query Charges"
        val partial = "TJK4AB12JB Confirmed. Ksh 200.00 from your M-PESA has been used to partially pay your outstanding Fuliza M-PESA. Available Fuliza M-PESA limit is Ksh 1400.00. Your M-PESA balance is 0.00."
        SmsIngestor(db, Deriver(db)).ingestAll(
            listOf(
                RawSms("MPESA", companion, Instant.parse("2026-04-01T06:00:00Z"), null, null, SmsSource.INBOX),
                RawSms("MPESA", partial, Instant.parse("2026-04-03T06:00:00Z"), null, null, SmsSource.INBOX),
            ),
        )
        val status = FulizaStatus.perLine(db.transactionsDao().fulizaReadings()).getValue(null)
        assertEquals(FulizaStatus(Money(40_000), Money(180_000), Money(140_000), LocalDate.of(2026, 4, 20)), status)
        db.close()
    }
}
