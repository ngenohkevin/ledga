package com.ledga.app.data.derive

import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.room.toDerived
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.core.derive.Ledger
import com.ledga.core.derive.Override
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class LedgerViewTest {
    private val db = TestDb.inMemory()
    private val deriver = Deriver(db)
    private val t0 = Instant.parse("2026-03-21T08:00:00Z")

    @After fun close() = db.close()

    @Test
    fun `every ledger row equals the core Ledger definition`() = runTest {
        val bodies = listOf(
            Sms.receive("TJK4AB12GA", "SAMPLE EMPLOYER LTD", "20,000.00"),
            Sms.SEND, Sms.REVERSAL, Sms.KPLC, Sms.PURCHASE, Sms.COMPANION, Sms.REPAY_FULL, Sms.BANK_APP,
            Sms.buyGoods("TJK4AB12GB", "SAMPLE CAFE", "350.00"),
            Sms.paybill("TJK4AB12GC", "ZUKU", "4400123", "2,999.00"),
        )
        SmsIngestor(db, deriver).ingestAll(bodies.mapIndexed { i, b -> RawSms("MPESA", b, t0.plusSeconds(i * 60L), null, null, SmsSource.INBOX) })
        deriver.saveOverride(Override("TJK4AB12GB", hidden = true))
        deriver.saveOverride(Override("TJK4AB12FC", ownAccount = true))

        val derived = db.transactionsDao().all().map { it.toDerived() }
        val view = db.ledgerDao().all().associateBy { it.code }
        assertEquals(derived.filterNot { it.isHidden }.map { it.code }.toSet(), view.keys, "hidden rows are absent from the view")
        derived.filterNot { it.isHidden }.forEach { tx ->
            val row = view.getValue(tx.code)
            assertEquals(Ledger.spendCents(tx), row.spendCents, "spend ${tx.code}")
            assertEquals(Ledger.inCents(tx), row.inCents, "in ${tx.code}")
            assertEquals(Ledger.feeCents(tx), row.feeCents, "fee ${tx.code}")
        }
        assertEquals(Ledger.spent(derived).cents, view.values.sumOf { it.spendCents + it.feeCents })
        assertEquals(Ledger.moneyIn(derived).cents, view.values.sumOf { it.inCents })
    }
}
