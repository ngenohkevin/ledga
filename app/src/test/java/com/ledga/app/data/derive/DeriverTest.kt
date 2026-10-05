package com.ledga.app.data.derive

import com.ledga.app.data.ingest.ParseStatus
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.MetaKeys
import com.ledga.app.data.room.MetaRow
import com.ledga.app.data.room.RuleRow
import com.ledga.app.data.room.SmsRow
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.room.SmsStatus
import com.ledga.app.data.room.TxRow
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.core.derive.Override
import com.ledga.core.derive.RuleAction
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.Categories
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import com.ledga.core.parse.MpesaParser
import com.ledga.core.parse.SmsText
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DeriverTest {
    private val db: LedgaDatabase = TestDb.inMemory()
    private val deriver = Deriver(db)
    private var clock = Instant.parse("2026-03-21T10:30:00Z")

    @After fun close() = db.close()

    /** Stores one SMS the way SmsIngestor will (status from the current parser); returns its code. */
    private suspend fun store(body: String, lineId: Long? = null, parserVersion: Int = MpesaParser.VERSION): String? {
        clock = clock.plusSeconds(5)
        val s = ParseStatus.of(MpesaParser.parse(body, clock))
        db.smsDao().insertIgnore(SmsRow(0, "MPESA", body, SmsText.hash(body), clock, null, lineId, s.code, SmsSource.INBOX, s.status, s.reason, parserVersion))
        return s.code
    }

    private suspend fun tx(code: String): TxRow = db.transactionsDao().get(code)!!

    @Test
    fun `payment and its Fuliza companion become one transaction`() = runTest {
        store(Sms.PURCHASE); store(Sms.COMPANION)
        deriver.rederive(listOf("TJK4AB12EA"))
        val t = tx("TJK4AB12EA")
        assertEquals(TxKind.BUY_GOODS, t.kind)
        assertEquals(2, t.smsCount)
        assertEquals(463L, t.feeCents)
        assertEquals(46_300L, t.fulizaDrawnCents)
        assertEquals(1, db.transactionsDao().count())
    }

    @Test
    fun `a resend with a different promo tail counts once`() = runTest {
        store(Sms.SEND); store("${Sms.SEND} Save more with Sample Bank.")
        deriver.rederive(listOf("TJK4AB12FB"))
        assertEquals(2, tx("TJK4AB12FB").smsCount)
        assertEquals(50_000L, tx("TJK4AB12FB").amountCents)
    }

    @Test
    fun `reversal before the original still marks the original reversed`() = runTest {
        store(Sms.REVERSAL)
        deriver.rederive(listOf("TJK4AB12FE"))
        assertEquals("TJK4AB12FB", tx("TJK4AB12FE").reversesCode)
        store(Sms.SEND)
        deriver.rederive(listOf("TJK4AB12FB"))
        assertTrue(tx("TJK4AB12FB").isReversed)
        assertFalse(tx("TJK4AB12FE").isReversed)
    }

    @Test
    fun `removing the reversal un-reverses the original`() = runTest {
        store(Sms.SEND); store(Sms.REVERSAL)
        deriver.rederive(listOf("TJK4AB12FB", "TJK4AB12FE"))
        assertTrue(tx("TJK4AB12FB").isReversed)
        db.openHelper.writableDatabase.execSQL("UPDATE sms SET status = 'UNREADABLE', code = NULL WHERE body = ?", arrayOf(Sms.REVERSAL))
        deriver.rederive(listOf("TJK4AB12FE"))
        assertNull(db.transactionsDao().get("TJK4AB12FE"))
        assertFalse(tx("TJK4AB12FB").isReversed)
    }

    @Test
    fun `saving an override re-derives the code, and a misfit category falls through`() = runTest {
        store(Sms.SEND)
        deriver.rederive(listOf("TJK4AB12FB"))
        deriver.saveOverride(Override("TJK4AB12FB", categoryKey = Categories.SAVINGS, note = "school fees", hidden = true))
        val t = tx("TJK4AB12FB")
        assertEquals(Categories.SENT_TO_PEOPLE, t.categoryKey, "Savings does not fit a SPEND send (owner decision B1)")
        assertEquals("school fees", t.note)
        assertTrue(t.isHidden)
        assertEquals(FlowKind.SPEND, t.flow)
    }

    @Test
    fun `a new user rule reclassifies existing rows without a re-parse`() = runTest {
        store(Sms.SEND)
        deriver.rederive(listOf("TJK4AB12FB"))
        db.rulesDao().insert(RuleRow(field = RuleField.NAME_CONTAINS, pattern = "JANE TESTER", action = RuleAction.SET_CATEGORY, categoryKey = Categories.FOOD, origin = RuleOrigin.USER, priority = 0, createdAt = clock))
        deriver.reclassifyAll()
        assertEquals(Categories.FOOD, tx("TJK4AB12FB").categoryKey)
    }

    @Test
    fun `rebuildAll re-parses stale rows, drops orphans and records the versions`() = runTest {
        store(Sms.KPLC, parserVersion = 0)
        db.openHelper.writableDatabase.execSQL("UPDATE sms SET status = 'UNREADABLE', code = NULL")
        db.transactionsDao().upsertAll(listOf(TxRow("ZZZZZZZZZZ", null, clock, false, TxKind.SEND, FlowKind.SPEND, 1, 0, null, null, null, null, null, null, null, false, null, null, null, null, null, "other", null, false, "", 1)))
        db.metaDao().put(MetaRow(MetaKeys.DERIVATION_VERSION, "0"))
        assertTrue(deriver.needsRebuild())
        val progress = mutableListOf<Pair<Int, Int>>()
        deriver.rebuildAll { done, total -> progress += done to total }
        assertEquals(SmsStatus.PARSED, db.smsDao().pageAfter(0, 10).single().status)
        assertEquals(Categories.ELECTRICITY, tx("TJK4AB12FA").categoryKey)
        assertNull(db.transactionsDao().get("ZZZZZZZZZZ"))
        assertFalse(deriver.needsRebuild())
        assertEquals(1 to 1, progress.last())
    }

    @Test
    fun `1,200 codes derive in chunks below SQLite's variable limit`() = runTest {
        val codes = (1..1200).map { "TJK" + it.toString().padStart(7, '0') }
        codes.forEach { store(Sms.send(it)) }
        deriver.rederive(codes)
        assertEquals(1200, db.transactionsDao().count())
    }
}
