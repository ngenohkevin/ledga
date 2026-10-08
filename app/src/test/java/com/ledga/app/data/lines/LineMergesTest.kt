package com.ledga.app.data.lines

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.LineRow
import com.ledga.app.data.room.OverrideRow
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.TestDb
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFailsWith

/** R176–R179: one number's history split across two lines (a new phone, an eSIM). Synthetic messages. */
@RunWith(RobolectricTestRunner::class)
class LineMergesTest {
    private val db = TestDb.inMemory()
    private val sims = FakeSims()
    private val settings = SettingsStore(FakePrefsStore())
    private val deriver = Deriver(db)
    private val merges = LineMerges(db, sims, settings, deriver)

    private suspend fun line(sub: Int, name: String): Long = db.linesDao().insert(
        LineRow(subscriptionId = sub, phoneNumber = null, displayName = name, color = "#00A86B", isPrimary = sub == 1, createdAt = Instant.parse("2026-09-01T00:00:00Z")),
    )

    private suspend fun sms(lineId: Long, sub: Int, body: String, at: String) =
        SmsIngestor(db, deriver).ingestAll(listOf(RawSms("MPESA", body, Instant.parse(at), sub, lineId, SmsSource.INBOX)))

    private fun sent(code: String, balance: String, whenText: String) =
        "$code Confirmed. Ksh500.00 sent to SAMPLE PERSON 0700000001 on $whenText. New M-PESA balance is Ksh$balance. Transaction cost, Ksh7.00."

    private fun received(code: String, balance: String, whenText: String) =
        "$code Confirmed.You have received Ksh200.00 from SAMPLE CLIENT 0700000002 on $whenText New M-PESA balance is Ksh$balance."

    /** Line 2 (old phone) ends at Ksh 1,000; Line 3 (the eSIM) starts 20 days later at 1,000 + 200. Line 1 runs throughout. */
    private suspend fun splitNumber(continues: Boolean = true): Triple<Long, Long, Long> {
        val one = line(1, "Line 1")
        val two = line(3, "Line 2")
        val three = line(2, "Line 3")
        sms(one, 1, sent("TJK4AB12KA", "5,000.00", "1/9/26 at 9:00 AM"), "2026-09-01T06:00:00Z")
        sms(two, 3, sent("TJK4AB12KB", "1,507.00", "2/9/26 at 9:00 AM"), "2026-09-02T06:00:00Z")
        sms(two, 3, sent("TJK4AB12KC", "1,000.00", "6/9/26 at 9:00 AM"), "2026-09-06T06:00:00Z")
        sms(one, 1, sent("TJK4AB12KD", "4,493.00", "20/9/26 at 9:00 AM"), "2026-09-20T06:00:00Z")
        sms(three, 2, received("TJK4AB12KE", if (continues) "1,200.00" else "1,300.00", "26/9/26 at 6:00 PM"), "2026-09-26T15:00:00Z")
        return Triple(one, two, three)
    }

    @Test
    fun `a line that carries on another's balance is suggested, with no SIM information at all`() = runTest {
        val (_, two, three) = splitNumber()
        val s = merges.find()!!
        assertEquals(two to three, s.from.id to s.into.id)
    }

    @Test
    fun `a balance that doesn't carry on is not suggested`() = runTest {
        splitNumber(continues = false)
        assertNull(merges.find())
    }

    @Test
    fun `lines used at the same time are never suggested`() = runTest {
        val (one, two, _) = splitNumber()
        db.linesDao().delete(db.linesDao().all().single { it.displayName == "Line 3" }.id)
        assertNull(merges.find(), "Line 1 has payments before Line 2's last: two numbers side by side")
        assertEquals(setOf(one, two), db.linesDao().all().map { it.id }.toSet())
    }

    @Test
    fun `a SIM still in the phone isn't suggested as the old half`() = runTest {
        splitNumber()
        sims.add(Sim(3, "SIM 2", null)) // Line 2's SIM is here after all
        sims.add(Sim(2, "eSIM 1", null))
        assertNull(merges.find())
    }

    @Test
    fun `merging moves messages, placements and the chosen line`() = runTest {
        val (_, two, three) = splitNumber()
        db.overridesDao().upsert(OverrideRow("TJK4AB12KB", null, null, lineId = two, ownAccount = null, hidden = false, updatedAt = Instant.parse("2026-09-03T00:00:00Z")))
        settings.setSelectedLine(two)
        merges.merge(two, three)
        assertEquals(listOf("Line 1", "Line 3"), db.linesDao().all().map { it.displayName })
        assertEquals(three, db.transactionsDao().get("TJK4AB12KC")!!.lineId)
        assertEquals(three, db.overridesDao().get("TJK4AB12KB")!!.lineId)
        assertEquals(three, settings.current().selectedLineId)
        assertNull(merges.find())
    }

    @Test
    fun `Not the same is remembered`() = runTest {
        val (_, two, three) = splitNumber()
        merges.dismiss(two, three)
        assertNull(merges.find())
        assertNull(LineMerges(db, sims, settings, deriver).find(), "a new start doesn't ask again")
    }

    @Test
    fun `a merge that fails part-way changes nothing (final review I2)`() = runTest {
        val (_, two, three) = splitNumber()
        val failing = LineMerges(db, sims, settings, deriver) { throw IllegalStateException("stopped") }
        assertFailsWith<IllegalStateException> { failing.merge(two, three) }
        assertEquals(listOf("Line 1", "Line 2", "Line 3"), db.linesDao().all().map { it.displayName })
        assertEquals(two, db.transactionsDao().get("TJK4AB12KC")!!.lineId)
    }

    @Test
    fun `a line that ended at nothing isn't taken for another number's start (final review I3)`() = runTest {
        val two = line(3, "Line 2")
        val three = line(2, "Line 3")
        sms(two, 3, sent("TJK4AB12KC", "0.00", "6/9/26 at 9:00 AM"), "2026-09-06T06:00:00Z")
        sms(three, 2, received("TJK4AB12KE", "200.00", "26/9/26 at 6:00 PM"), "2026-09-26T15:00:00Z")
        assertNull(merges.find())
    }

    @Test
    fun `after a merge the old SIM id still finds the line kept, for messages carried over from v1 too (final review I5)`() = runTest {
        val two = line(3, "Line 2")
        val three = line(2, "Line 3")
        SmsIngestor(db, deriver).ingestAll(
            listOf(RawSms("MPESA", sent("TJK4AB12KC", "1,000.00", "6/9/26 at 9:00 AM"), Instant.parse("2026-09-06T06:00:00Z"), null, two, SmsSource.INBOX)),
        )
        sms(three, 2, received("TJK4AB12KE", "1,200.00", "26/9/26 at 6:00 PM"), "2026-09-26T15:00:00Z")
        merges.merge(two, three)
        assertEquals(three, LinesRepository(db.linesDao(), sims).lineFor(3))
        assertEquals(listOf("Line 3"), db.linesDao().all().map { it.displayName })
    }
}
