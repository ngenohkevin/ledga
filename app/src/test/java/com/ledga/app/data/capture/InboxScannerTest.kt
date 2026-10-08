package com.ledga.app.data.capture

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.lines.Sim
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class InboxScannerTest {
    private val db = TestDb.inMemory()
    private val sims = FakeSims()
    private val lines = LinesRepository(db.linesDao(), sims)
    private val settings = SettingsStore(FakePrefsStore())
    private var inbox = listOf<InboxSms>()
    private val sinces = mutableListOf<Long>()
    private val source = InboxSource { since -> sinces += since; inbox.filter { it.receivedAt.toEpochMilli() > since } }
    private val scanner = InboxScanner(source, lines, SmsIngestor(db, Deriver(db)), settings)
    private val clock = MutableClock(Instant.parse("2026-10-07T06:00:00Z"))
    private val clocked by lazy { InboxScanner(source, lines, SmsIngestor(db, Deriver(db)), settings, clock) }

    private fun sms(body: String, iso: String, sub: Int? = 1) = InboxSms("MPESA", body, Instant.parse(iso), sub)

    @Test
    fun `a full scan stores each message once, on its SIM's line`() = runTest {
        inbox = listOf(sms(Sms.SEND, "2026-03-21T10:30:30Z"), sms(Sms.KPLC, "2026-03-21T12:00:30Z", sub = 2))
        val r = scanner.scan(ScanMode.FULL)
        assertEquals(2, r.found)
        assertEquals(2, r.inserted)
        val lineBySub = db.linesDao().all().associate { it.subscriptionId to it.id }
        assertEquals(lineBySub[1], db.transactionsDao().get("TJK4AB12FB")?.lineId)
        assertEquals(lineBySub[2], db.transactionsDao().get("TJK4AB12FA")?.lineId)
    }

    @Test
    fun `scanning the same inbox again stores nothing new`() = runTest {
        inbox = listOf(sms(Sms.SEND, "2026-03-21T10:30:30Z"))
        scanner.scan(ScanMode.FULL)
        val again = scanner.scan(ScanMode.FULL)
        assertEquals(0, again.inserted)
        assertEquals(1, again.duplicates)
        assertEquals(1, db.transactionsDao().count())
    }

    @Test
    fun `a catch-up reads from six hours before the newest message already scanned`() = runTest {
        inbox = listOf(sms(Sms.SEND, "2026-03-21T10:30:30Z"))
        scanner.scan(ScanMode.CATCH_UP) // never scanned before: everything
        scanner.scan(ScanMode.CATCH_UP)
        val newest = Instant.parse("2026-03-21T10:30:30Z").toEpochMilli()
        assertEquals(listOf(0L, newest - InboxScanner.OVERLAP_MS), sinces)
        assertEquals(newest, settings.current().smsWatermarkMillis)
    }

    @Test
    fun `progress ends at the number of messages found`() = runTest {
        inbox = (1..3).map { sms(Sms.send("TJK4AB12F$it"), "2026-03-21T10:3$it:00Z") }
        val seen = mutableListOf<Pair<Int, Int>>()
        scanner.scan(ScanMode.FULL) { done, total -> seen += done to total }
        assertEquals(0 to 3, seen.first())
        assertEquals(3 to 3, seen.last())
    }

    @Test
    fun `a message with no subscription id on a single-SIM phone goes to that SIM's line`() = runTest {
        sims.add(Sim(4, "Safaricom", null))
        inbox = listOf(sms(Sms.SEND, "2026-03-21T10:30:30Z", sub = null))
        scanner.scan(ScanMode.FULL)
        assertEquals(db.linesDao().all().single().id, db.transactionsDao().get("TJK4AB12FB")?.lineId)
    }

    @Test
    fun `a full scan settles the rescan the migration left owed, and a catch-up doesn't`() = runTest {
        inbox = listOf(sms(Sms.SEND, "2026-03-21T10:30:30Z"))
        settings.setFullRescanOwed(true)
        scanner.scan(ScanMode.CATCH_UP)
        assertTrue(settings.current().fullRescanOwed)
        scanner.scan(ScanMode.FULL)
        assertFalse(settings.current().fullRescanOwed)
    }

    @Test
    fun `a SIM whose id changed keeps one line, even when a later scan meets its old id`() = runTest {
        sims.add(Sim(3, "Safaricom", "0712000001"))
        inbox = listOf(sms(Sms.SEND, "2026-03-21T10:30:30Z", sub = 3))
        scanner.scan(ScanMode.FULL)
        sims.clear()
        sims.add(Sim(7, "Safaricom", "0712000001")) // the same SIM, re-provisioned
        lines.syncActive()
        inbox = inbox + sms(Sms.KPLC, "2026-03-21T12:00:30Z", sub = 3) // an older message, still under the old id
        scanner.scan(ScanMode.FULL)
        val only = db.linesDao().all().single()
        assertEquals(7, only.subscriptionId)
        assertEquals(only.id, db.transactionsDao().get("TJK4AB12FA")?.lineId)
    }

    @Test
    fun `a message dated in the future moves the watermark only as far as now (R157)`() = runTest {
        inbox = listOf(sms(Sms.SEND, "2026-10-30T06:00:00Z"))
        clocked.scan(ScanMode.CATCH_UP)
        assertEquals(clock.instant.toEpochMilli(), settings.current().smsWatermarkMillis)
    }

    @Test
    fun `a watermark already in the future is put right by reading the whole inbox (R157)`() = runTest {
        settings.advanceWatermark(Instant.parse("2026-10-30T06:00:00Z").toEpochMilli())
        inbox = listOf(sms(Sms.SEND, "2026-10-06T06:00:00Z"))
        val r = clocked.scan(ScanMode.CATCH_UP)
        assertEquals(0L, sinces.last())
        assertEquals(1, r.inserted)
        assertEquals(Instant.parse("2026-10-06T06:00:00Z").toEpochMilli(), settings.current().smsWatermarkMillis)
    }
}
