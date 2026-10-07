package com.ledga.app.data.lines

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.OverrideRow
import com.ledga.app.data.room.SmsSource
import com.ledga.app.testing.BUSINESS
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.PERSONAL
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.twoLines
import com.ledga.app.testing.txRow
import com.ledga.core.model.TxKind
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R116, R128: proposing, placing and taking back lines for payments not on one. Synthetic payments. */
@RunWith(RobolectricTestRunner::class)
class LinePlacementTest {
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-07T17:00:00Z"))
    private val deriver = Deriver(db, clock)
    private val edits = TransactionEdits(db, deriver, clock)
    private val placements = LinePlacements(db)

    @After fun close() = db.close()

    @Test
    fun `a proposal counts per line what the balances place`() = runTest {
        twoLines(db)
        db.transactionsDao().upsertAll(
            listOf(
                txRow(code = "TJK4AB14AA", kind = TxKind.RECEIVE, amountCents = 100_000, balanceCents = 100_000, at = Instant.parse("2026-03-21T03:00:00Z"), lineId = PERSONAL.id),
                txRow(code = "TJK4AB14AB", kind = TxKind.RECEIVE, amountCents = 500_000, balanceCents = 500_000, at = Instant.parse("2026-03-21T03:30:00Z"), lineId = BUSINESS.id),
                txRow(code = "TJK4AB14AC", kind = TxKind.SEND, amountCents = 30_000, balanceCents = 70_000, at = Instant.parse("2026-03-21T04:00:00Z"), lineId = null),
                txRow(code = "TJK4AB14AD", kind = TxKind.PAYBILL, amountCents = 20_000, balanceCents = 480_000, at = Instant.parse("2026-03-21T05:00:00Z"), lineId = null),
                txRow(code = "TJK4AB14AE", kind = TxKind.RECEIVE, amountCents = 1_000, balanceCents = 999_999, at = Instant.parse("2026-03-21T06:00:00Z"), lineId = null),
            ),
        )
        val p = placements.propose()
        assertEquals(mapOf("TJK4AB14AC" to PERSONAL.id, "TJK4AB14AD" to BUSINESS.id), p.placed)
        assertEquals(mapOf(PERSONAL.id to 1, BUSINESS.id to 1), p.byLine)
        assertEquals(1, p.left, "a balance that continues neither line")
        assertEquals(3, placements.observeUnassigned().first())
    }

    @Test
    fun `placing moves only payments still not on a line`() = runTest {
        twoLines(db)
        val ingest = SmsIngestor(db, deriver)
        ingest.ingest(RawSms("MPESA", Sms.SEND, Sms.at("2026-03-21T10:30:00Z"), null, null, SmsSource.INBOX))
        ingest.ingest(RawSms("MPESA", Sms.KPLC, Sms.at("2026-03-21T12:00:00Z"), null, BUSINESS.id, SmsSource.INBOX))
        val moved = edits.placeOnLines(mapOf("TJK4AB12FB" to PERSONAL.id, "TJK4AB12FA" to PERSONAL.id))
        assertEquals(listOf("TJK4AB12FB"), moved, "the KPLC payment was already on a line")
        assertEquals(PERSONAL.id, db.transactionsDao().get("TJK4AB12FB")?.lineId)
        assertEquals(BUSINESS.id, db.transactionsDao().get("TJK4AB12FA")?.lineId)
        assertEquals(PERSONAL.id, db.overridesDao().get("TJK4AB12FB")?.lineId, "an override: a rebuild keeps it (R128)")
    }

    @Test
    fun `Undo takes back exactly the payments it moved and leaves every other choice alone`() = runTest {
        twoLines(db)
        val ingest = SmsIngestor(db, deriver)
        ingest.ingest(RawSms("MPESA", Sms.SEND, Sms.at("2026-03-21T10:30:00Z"), null, null, SmsSource.INBOX))
        ingest.ingest(RawSms("MPESA", Sms.KPLC, Sms.at("2026-03-21T12:00:00Z"), null, null, SmsSource.INBOX))
        edits.setNote("TJK4AB12FB", "Rent share")
        val moved = edits.placeOnLines(mapOf("TJK4AB12FB" to PERSONAL.id, "TJK4AB12FA" to BUSINESS.id))
        edits.unplace(moved)
        assertNull(db.transactionsDao().get("TJK4AB12FB")?.lineId)
        assertNull(db.transactionsDao().get("TJK4AB12FA")?.lineId)
        assertEquals(OverrideRow("TJK4AB12FB", null, "Rent share", null, null, false, clock.instant()), db.overridesDao().get("TJK4AB12FB"))
        assertNull(db.overridesDao().get("TJK4AB12FA"), "a row made only to place it goes")
    }

    @Test
    fun `a date range is whole Nairobi days, both ends, and only payments not on a line`() = runTest {
        twoLines(db)
        db.transactionsDao().upsertAll(
            listOf(
                txRow(code = "TJK4AB14BA", at = Instant.parse("2026-03-20T20:59:00Z"), lineId = null), // 20 Mar, 11:59 PM
                txRow(code = "TJK4AB14BB", at = Instant.parse("2026-03-20T21:00:00Z"), lineId = null), // 21 Mar, midnight
                txRow(code = "TJK4AB14BC", at = Instant.parse("2026-03-22T20:59:00Z"), lineId = null), // 22 Mar, 11:59 PM
                txRow(code = "TJK4AB14BD", at = Instant.parse("2026-03-22T21:00:00Z"), lineId = null), // 23 Mar
                txRow(code = "TJK4AB14BE", at = Instant.parse("2026-03-21T09:00:00Z"), lineId = PERSONAL.id),
            ),
        )
        assertEquals(setOf("TJK4AB14BB", "TJK4AB14BC"), placements.inDates(LocalDate.parse("2026-03-21"), LocalDate.parse("2026-03-22")).toSet())
    }
}
