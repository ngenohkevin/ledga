package com.ledga.app.notify

import com.ledga.app.testing.FakePhone
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.fulizaTxRow
import com.ledga.app.testing.twoLines
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R105: the 9 AM check. Synthetic payment and lines. */
@RunWith(RobolectricTestRunner::class)
class FulizaCheckTest {
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-30T06:00:30Z")) // 9 AM, Fri 30 Oct 2026 in Nairobi
    private val phone = FakePhone()
    private val check = FulizaCheck(db, Notifier(db, phone, clock), clock)

    @After fun close() = db.close()

    @Test
    fun `on a phone with two lines a reminder names its line, and is written once`() = runTest {
        twoLines(db)
        db.transactionsDao().upsertAll(listOf(fulizaTxRow())) // on Personal: Ksh 6,418.36 owed, due Mon 2 Nov
        assertEquals(1, check.check())
        val n = phone.posted.single()
        assertEquals("Fuliza Ksh 6,418.36 due in 3 days", n.title)
        assertEquals("Personal ··11 · due 2 Nov", n.body)
        assertEquals(NotificationTap.Fuliza(1), n.opened.tap, "the reminder's line goes with its tap (R100)")
        assertEquals(0, check.check(), "once")
    }
}
