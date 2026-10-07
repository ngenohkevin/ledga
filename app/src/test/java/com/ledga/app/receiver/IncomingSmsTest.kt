package com.ledga.app.receiver

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.lines.Sim
import com.ledga.app.testing.FakeBackgroundWork
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec §9.1, §7.2 step 4, R112: what the receiver does with a delivery. Synthetic SMS. */
@RunWith(RobolectricTestRunner::class)
class IncomingSmsTest {
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-03-21T10:31:00Z"))
    private val sims = FakeSims().apply { add(Sim(7, "Safaricom", null)) }
    private val work = FakeBackgroundWork()
    private val incoming = IncomingSms(LinesRepository(db.linesDao(), sims, clock), SmsIngestor(db, Deriver(db, clock)), work, clock)

    @After fun close() = db.close()

    @Test
    fun `a delivery is stored on its SIM's line, and its new payment gets its alerts`() = runTest {
        val r = incoming.store(listOf(SmsPart("MPESA", Sms.SEND)), subscriptionId = 7)
        assertEquals(setOf("TJK4AB12FB"), r.newCodes)
        assertEquals(db.linesDao().bySubscription(7)?.id, db.transactionsDao().get("TJK4AB12FB")?.lineId)
        assertEquals(listOf("alertsFor TJK4AB12FB"), work.scheduled)
    }

    @Test
    fun `a message already stored queues no alert`() = runTest {
        incoming.store(listOf(SmsPart("MPESA", Sms.SEND)), 7)
        work.scheduled.clear()
        incoming.store(listOf(SmsPart("MPESA", Sms.SEND)), 7)
        assertEquals(emptyList(), work.scheduled)
    }

    @Test
    fun `a message that isn't a payment queues no alert`() = runTest {
        incoming.store(listOf(SmsPart("MPESA", Sms.BALANCE_CHECK)), 7)
        assertEquals(emptyList(), work.scheduled)
    }
}
