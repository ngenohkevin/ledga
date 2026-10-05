package com.ledga.app.data.ingest

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.room.SmsStatus
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.core.model.TxKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SmsIngestorTest {
    private val db = TestDb.inMemory()
    private val ingestor = SmsIngestor(db, Deriver(db))
    private val t0 = Instant.parse("2026-03-21T10:30:05Z")

    @After fun close() = db.close()

    private fun raw(body: String, sender: String = "MPESA", lineId: Long? = null, at: Instant = t0) =
        RawSms(sender, body, at, subscriptionId = null, lineId = lineId, source = SmsSource.RECEIVER)

    @Test
    fun `the same message twice, even with invisible or odd spacing, is stored once`() = runTest {
        val nbsp = String(Character.toChars(0x00A0))
        val lrm = String(Character.toChars(0x200E))
        val first = ingestor.ingest(raw(Sms.SEND))
        val again = ingestor.ingestAll(listOf(raw(Sms.SEND), raw(lrm + Sms.SEND.replace(" sent to", "$nbsp sent  to"))))
        assertEquals(IngestResult(inserted = 1, duplicates = 0, rejected = 0, newCodes = setOf("TJK4AB12FB")), first)
        assertEquals(IngestResult(inserted = 0, duplicates = 2, rejected = 0, newCodes = emptySet()), again)
        assertEquals(1, db.transactionsDao().count())
    }

    @Test
    fun `ignored and unreadable messages are kept with their reason and derive nothing`() = runTest {
        ingestor.ingestAll(listOf(raw(Sms.BALANCE_CHECK), raw(Sms.UNREADABLE)))
        assertEquals(1, db.smsDao().countByStatus(SmsStatus.IGNORED))
        assertEquals("BALANCE_CHECK", db.smsDao().pageAfter(0, 10).first { it.status == SmsStatus.IGNORED }.statusReason)
        assertEquals(Sms.UNREADABLE, db.smsDao().unreadable().single().body)
        assertEquals(0, db.transactionsDao().count())
    }

    @Test
    fun `a failed derive stores nothing, so the next delivery of the same SMS is retried`() = runTest {
        // Simulates the app being killed (or derive failing) between storing an SMS and deriving it.
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("CREATE TRIGGER boom BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'boom'); END")
        runCatching { ingestor.ingest(raw(Sms.SEND)) }
        sqlite.execSQL("DROP TRIGGER boom")
        assertEquals(0, db.smsDao().pageAfter(0, 10).size, "the SMS row must roll back with its failed derive")
        assertEquals(1, ingestor.ingest(raw(Sms.SEND)).inserted)
        assertEquals(50_000L, db.transactionsDao().get("TJK4AB12FB")!!.amountCents)
    }

    @Test
    fun `non M-Pesa senders are rejected`() = runTest {
        assertEquals(1, ingestor.ingest(raw(Sms.SEND, sender = "SAFARICOM")).rejected)
        assertEquals(0, db.smsDao().pageAfter(0, 10).size)
    }

    @Test
    fun `a companion and its payment arriving separately merge, and the line carries over`() = runTest {
        ingestor.ingest(raw(Sms.COMPANION, lineId = null))
        assertEquals(TxKind.FULIZA_ONLY, db.transactionsDao().get("TJK4AB12EA")!!.kind)
        db.openHelper.writableDatabase.execSQL("INSERT INTO lines (id, subscriptionId, phoneNumber, displayName, color, isPrimary, createdAt) VALUES (2, 7, NULL, 'Line 2', '#00A86B', 0, 0)")
        ingestor.ingest(raw(Sms.PURCHASE, lineId = 2, at = t0.plusSeconds(3)))
        val t = db.transactionsDao().get("TJK4AB12EA")!!
        assertEquals(TxKind.BUY_GOODS, t.kind)
        assertEquals(2, t.smsCount)
        assertEquals(2L, t.lineId)
    }
}
