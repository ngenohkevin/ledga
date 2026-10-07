package com.ledga.app.notify

import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.testing.FakePhone
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.txRow
import com.ledga.core.model.Categories
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec §11 summaries, R106: every number from the ledger, all lines, fees included. Synthetic payments. */
@RunWith(RobolectricTestRunner::class)
class SummaryAlertsTest {
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-05T17:01:00Z")) // 8:01 PM, Mon 5 Oct 2026 in Nairobi
    private val phone = FakePhone()
    private val summaries = SummaryAlerts(db, LedgerQueries(db), Notifier(db, phone, clock), clock)
    private val eightPm = Instant.parse("2026-10-05T17:00:00Z")

    @After fun close() = db.close()

    /** Mon 5 Oct: Ksh 2,500 to Jane, Ksh 1,000 to KPLC, a Ksh 33 fee on a send to your own bank; the rest doesn't count. */
    private suspend fun monday() = db.transactionsDao().upsertAll(
        listOf(
            txRow(
                code = "TJK4AB12SA", kind = TxKind.SEND, amountCents = 250_000, name = "JANE TESTER", phone = "0712345111",
                account = null, categoryKey = Categories.SENT_TO_PEOPLE, at = Instant.parse("2026-10-05T07:00:00Z"),
            ),
            txRow(code = "TJK4AB12SB"),
            txRow(
                code = "TJK4AB12SC", kind = TxKind.SEND, flow = FlowKind.OWN_OUT, amountCents = 1_000_000, feeCents = 3_300,
                name = "EXAMPLE BANK", account = null, categoryKey = Categories.OWN_ACCOUNTS, at = Instant.parse("2026-10-05T12:00:00Z"),
            ),
            txRow(code = "TJK4AB12SD", amountCents = 900_000, hidden = true, at = Instant.parse("2026-10-05T13:00:00Z")),
            txRow(
                code = "TJK4AB12SE", kind = TxKind.SEND, amountCents = 50_000, name = "JANE TESTER", account = null,
                reversed = true, categoryKey = Categories.SENT_TO_PEOPLE, at = Instant.parse("2026-10-05T14:00:00Z"),
            ),
            txRow(
                code = "TJK4AB12SF", kind = TxKind.RECEIVE, amountCents = 500_000, name = "AMANI", account = null,
                categoryKey = Categories.RECEIVED, at = Instant.parse("2026-10-05T15:00:00Z"),
            ),
            txRow(code = "TJK4AB12SG", amountCents = 70_000, at = Instant.parse("2026-10-05T21:30:00Z")), // 12:30 AM on Tue 6 Oct
        ),
    )

    @Test
    fun `the daily summary counts what was spent that Nairobi day, fees included, and names the biggest`() = runTest {
        monday()
        assertTrue(summaries.daily(eightPm))
        val a = db.alertsDao().observeWithTx().first().single().alert
        assertEquals("daily:2026-10-05", a.key)
        assertEquals("Spent Ksh 3,533 today", a.title)
        assertEquals("Across 3 payments · biggest: Jane Tester Ksh 2,500", a.body)
        val day = LocalDate.parse("2026-10-05")
        assertEquals(NotificationTap.Spending(day, day), phone.posted.single().opened.tap)
        assertFalse(summaries.daily(eightPm), "once")
    }

    @Test
    fun `a day with nothing spent has no summary`() = runTest {
        db.transactionsDao().upsertAll(
            listOf(txRow(code = "TJK4AB12SF", kind = TxKind.RECEIVE, amountCents = 500_000, name = "AMANI", account = null, categoryKey = Categories.RECEIVED, at = Instant.parse("2026-10-05T15:00:00Z"))),
        )
        assertFalse(summaries.daily(eightPm))
        assertEquals(emptyList(), phone.posted)
    }

    @Test
    fun `a late summary names its day, and one more than 12 hours late writes nothing`() = runTest {
        monday()
        clock.instant = Instant.parse("2026-10-06T06:00:00Z") // 9 AM on Tue 6 Oct: 13 hours late
        assertFalse(summaries.daily(eightPm))
        clock.instant = Instant.parse("2026-10-06T02:00:00Z") // 5 AM: 9 hours late
        assertTrue(summaries.daily(eightPm))
        assertEquals("Spent Ksh 3,533 on 5 Oct", phone.posted.single().title)
    }

    @Test
    fun `the weekly summary is this week to Sunday 7 PM against last week to the same moment`() = runTest {
        db.transactionsDao().upsertAll(
            listOf(
                txRow(
                    code = "TJK4AB12WA", kind = TxKind.BUY_GOODS, amountCents = 600_000, name = "GREEN GROCER", account = null,
                    categoryKey = Categories.GROCERIES, at = Instant.parse("2026-10-06T09:00:00Z"),
                ),
                txRow(code = "TJK4AB12WB", amountCents = 200_000, at = Instant.parse("2026-10-07T09:00:00Z")),
                txRow(code = "TJK4AB12WC", amountCents = 900_000, at = Instant.parse("2026-10-11T17:00:00Z")), // Sun 8 PM: after
                txRow(code = "TJK4AB12WD", amountCents = 1_000_000, at = Instant.parse("2026-09-29T09:00:00Z")), // last week
                txRow(code = "TJK4AB12WE", amountCents = 500_000, at = Instant.parse("2026-10-04T17:00:00Z")), // last Sun 8 PM
            ),
        )
        val sunday7pm = Instant.parse("2026-10-11T16:00:00Z")
        clock.instant = sunday7pm.plusSeconds(30)
        assertTrue(summaries.weekly(sunday7pm))
        val n = phone.posted.single()
        assertEquals("Spent Ksh 8,000 this week", n.title)
        assertEquals("20% less than last week · most on Groceries (Ksh 6,000)", n.body)
        val monday = LocalDate.parse("2026-10-05")
        assertEquals(OpenedNotification("weekly:2026-10-05", NotificationTap.Spending(monday, monday.plusDays(6))), n.opened)
    }
}
