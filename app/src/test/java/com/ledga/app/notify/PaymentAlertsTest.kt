package com.ledga.app.notify

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakePhone
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec §7.2 step 4, §11, R103, R104. Synthetic SMS. */
@RunWith(RobolectricTestRunner::class)
class PaymentAlertsTest {
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-03-21T12:30:00Z")) // 3:30 PM, Sat 21 Mar 2026 in Nairobi
    private val settings = SettingsStore(FakePrefsStore())
    private val phone = FakePhone()
    private val alerts = PaymentAlerts(db, settings, Notifier(db, phone, clock), clock)

    @After fun close() = db.close()

    private suspend fun ingest(vararg bodies: String) =
        SmsIngestor(db, Deriver(db, clock)).ingestAll(bodies.map { RawSms("MPESA", it, clock.instant(), null, null, SmsSource.RECEIVER) })

    private suspend fun logged() = db.alertsDao().observeWithTx().first().map { it.alert }

    @Test
    fun `a payment of the amount or more is a large payment, once`() = runTest {
        settings.setNotifyLarge(true)
        settings.setLargeThreshold(100_000) // Ksh 1,000
        ingest(Sms.SEND, Sms.KPLC) // Ksh 500 at 1:30 PM, Ksh 1,000 at 3:00 PM
        assertEquals(1, alerts.check(listOf("TJK4AB12FB", "TJK4AB12FA")))
        val row = logged().single()
        assertEquals("large:TJK4AB12FA", row.key)
        assertEquals("Ksh 1,000 to KPLC Prepaid", row.title)
        assertEquals("Large payment · Electricity", row.body)
        assertEquals(0, alerts.check(listOf("TJK4AB12FA")), "nothing posts twice")
        assertEquals(1, phone.posted.size)
    }

    @Test
    fun `a Fuliza draw says what it covered and what is owed, whichever SMS came first`() = runTest {
        clock.instant = Instant.parse("2026-06-09T17:00:00Z") // 12 minutes after the purchase
        settings.setNotifyLarge(true)
        settings.setLargeThreshold(100_000)
        ingest(Sms.COMPANION) // the companion first, delivered on its own
        ingest(Sms.PURCHASE)
        assertEquals(2, alerts.check(listOf("TJK4AB12EA")))
        val rows = logged().associateBy { it.key }
        assertEquals("Ksh 2,500 to Sample Supermarket", rows.getValue("large:TJK4AB12EA").title, "the whole payment")
        val draw = rows.getValue("fuliza-draw:TJK4AB12EA")
        assertEquals("Fuliza covered Ksh 463", draw.title)
        assertEquals("Of a Ksh 2,500 payment to Sample Supermarket · you owe Ksh 6,418.36, due 2 Nov", draw.body)
    }

    @Test
    fun `a companion whose payment never came still says what Fuliza covered`() = runTest {
        clock.instant = Instant.parse("2026-06-09T17:00:00Z")
        ingest(Sms.COMPANION)
        assertEquals(1, alerts.check(listOf("TJK4AB12EA")))
        val draw = logged().single()
        assertEquals("Fuliza covered Ksh 463", draw.title)
        assertEquals("You owe Ksh 6,418.36, due 2 Nov", draw.body)
    }

    @Test
    fun `old, hidden, reversed, switched-off and incoming payments get no alert`() = runTest {
        settings.setNotifyLarge(true)
        settings.setLargeThreshold(10_000) // Ksh 100
        ingest(Sms.SEND, Sms.BANK_APP) // a Ksh 500 send at 1:30 PM on 21 Mar; Ksh 5,000 in at 9:15 AM on 2 Apr
        clock.instant = Instant.parse("2026-03-21T13:00:00Z") // 4 PM: two and a half hours after the send
        assertEquals(0, alerts.check(listOf("TJK4AB12FB")), "older than 2 hours")
        clock.instant = Instant.parse("2026-04-02T06:30:00Z")
        assertEquals(0, alerts.check(listOf("TJK4AB12FC")), "money in is not a payment")
        clock.instant = Instant.parse("2026-03-21T11:00:00Z")
        val edits = TransactionEdits(db, Deriver(db, clock), clock)
        edits.setHidden("TJK4AB12FB", true)
        assertEquals(0, alerts.check(listOf("TJK4AB12FB")), "hidden")
        edits.setHidden("TJK4AB12FB", false)
        settings.setNotifyLarge(false)
        assertEquals(0, alerts.check(listOf("TJK4AB12FB")), "switched off")
        settings.setNotifyLarge(true)
        ingest(Sms.REVERSAL) // reverses the send
        assertEquals(0, alerts.check(listOf("TJK4AB12FB")), "reversed")
        assertEquals(emptyList(), phone.posted)
    }
}
