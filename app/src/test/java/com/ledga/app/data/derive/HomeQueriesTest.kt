package com.ledga.app.data.derive

import com.ledga.app.data.room.TxRow
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.txRow
import com.ledga.core.model.Categories
import com.ledga.core.money.Money
import com.ledga.core.time.InstantRange
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
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

/** Home's reads (spec §7.5, R57, R58): every amount from the ledger, Nairobi time. Synthetic rows. */
@RunWith(RobolectricTestRunner::class)
class HomeQueriesTest {
    private val db = TestDb.inMemory()
    private val ledger = LedgerQueries(db)

    @After fun close() = db.close()

    private suspend fun put(vararg rows: TxRow) = db.transactionsDao().upsertAll(rows.toList())

    @Test
    fun `all lines adds up each line's latest balance, and a line shows its own`() = runTest {
        put(
            txRow(code = "TJK4AB12KA", lineId = 1, balanceCents = 100_000, at = Instant.parse("2026-10-01T07:00:00Z")),
            txRow(code = "TJK4AB12KB", lineId = 1, balanceCents = 150_000, at = Instant.parse("2026-10-02T07:00:00Z")),
            txRow(code = "TJK4AB12KC", lineId = 2, balanceCents = 40_000, at = Instant.parse("2026-10-03T07:00:00Z")),
            // Unattributed (two SIMs, no subscription id): the lines' own readings win (spec §7.5).
            txRow(code = "TJK4AB12KD", lineId = null, balanceCents = 999_900, at = Instant.parse("2026-10-04T07:00:00Z")),
        )
        val readings = ledger.balances().first()
        assertEquals(
            HomeBalance(
                190_000, Instant.parse("2026-10-03T07:00:00Z"), 2,
                listOf(LineBalance(1, 150_000, Instant.parse("2026-10-02T07:00:00Z")), LineBalance(2, 40_000, Instant.parse("2026-10-03T07:00:00Z"))),
            ),
            HomeBalance.of(readings, null),
            "the total and each line's part of it",
        )
        assertEquals(HomeBalance(150_000, Instant.parse("2026-10-02T07:00:00Z"), 1), HomeBalance.of(readings, 1))
        assertNull(HomeBalance.of(readings, 3))
        assertNull(HomeBalance.of(emptyList(), null))
    }

    @Test
    fun `a hidden payment leaves Recent but not the balance`() = runTest {
        put(
            txRow(code = "TJK4AB12KE", balanceCents = 120_000, at = Instant.parse("2026-10-01T07:00:00Z")),
            txRow(code = "TJK4AB12KF", balanceCents = 80_000, at = Instant.parse("2026-10-02T07:00:00Z"), hidden = true),
        )
        assertEquals(listOf("TJK4AB12KE"), ledger.recent(null).first().map { it.code })
        assertEquals(80_000, HomeBalance.of(ledger.balances().first(), null)?.cents, "the balance is the wallet's, hidden or not")
    }

    @Test
    fun `Recent is the newest five, on the chosen line, or one category's`() = runTest {
        put(
            *(1..7).map { i ->
                txRow(
                    code = "TJK4AB12L$i",
                    lineId = if (i % 2 == 0) 2L else 1L,
                    at = Instant.parse("2026-10-0${i}T07:00:00Z"),
                    categoryKey = if (i == 7) Categories.FUEL else Categories.ELECTRICITY,
                )
            }.toTypedArray(),
        )
        assertEquals(listOf("TJK4AB12L7", "TJK4AB12L6", "TJK4AB12L5", "TJK4AB12L4", "TJK4AB12L3"), ledger.recent(null).first().map { it.code })
        assertEquals(listOf("TJK4AB12L6", "TJK4AB12L4", "TJK4AB12L2"), ledger.recent(2).first().map { it.code })
        assertEquals(listOf("TJK4AB12L5", "TJK4AB12L3", "TJK4AB12L1"), ledger.recent(1, Categories.ELECTRICITY).first().map { it.code })
    }

    @Test
    fun `weeks start on Nairobi's Monday`() = runTest {
        put(
            txRow(code = "TJK4AB12MA", amountCents = 100_000, at = Instant.parse("2026-10-04T20:59:00Z")), // Sun 4 Oct, 23:59 in Nairobi
            txRow(code = "TJK4AB12MB", amountCents = 200_000, at = Instant.parse("2026-10-04T21:00:00Z")), // Mon 5 Oct, 00:00
        )
        val weeks = ledger.spentByPeriod(PeriodType.WEEK, InstantRange(Instant.parse("2026-09-01T00:00:00Z"), null)).first()
        assertEquals(100_000, weeks.getValue("2026-09-28").cents)
        assertEquals(200_000, weeks.getValue("2026-10-05").cents)
        assertEquals("2026-10-05", Periods.of(PeriodType.WEEK, Instant.parse("2026-10-04T21:00:00Z")).key, "the same key as :core's weeks")
    }

    @Test
    fun `years and months are keyed like core's periods, fees included`() = runTest {
        put(
            txRow(code = "TJK4AB12MC", amountCents = 100_000, feeCents = 700, at = Instant.parse("2025-12-31T20:59:00Z")), // 31 Dec 2025, 23:59
            txRow(code = "TJK4AB12MD", amountCents = 200_000, at = Instant.parse("2025-12-31T21:00:00Z")), // 1 Jan 2026, 00:00
        )
        val since = InstantRange(Instant.parse("2025-01-01T00:00:00Z"), null)
        val years = ledger.spentByPeriod(PeriodType.YEAR, since).first()
        assertEquals(100_700, years.getValue("2025").cents)
        assertEquals(200_000, years.getValue("2026").cents)
        assertEquals(setOf("2025-12", "2026-01"), ledger.spentByPeriod(PeriodType.MONTH, since).first().keys)
    }

    @Test
    fun `Fuliza adds up across lines and is honest about what it doesn't know`() = runTest {
        put(
            // Line 1: Ksh 6,418.36 owed, due 2 Nov, with a stated limit of Ksh 10,000.
            txRow(
                code = "TJK4AB12NA", lineId = 1, at = Instant.parse("2026-10-01T07:00:00Z"),
                fulizaOutstandingCents = 641_836, fulizaDueDate = LocalDate.parse("2026-11-02"), fulizaLimitCents = 1_000_000,
            ),
            // Line 2: Ksh 500 owed, due 20 Oct, no limit ever stated.
            txRow(
                code = "TJK4AB12NB", lineId = 2, at = Instant.parse("2026-10-02T07:00:00Z"),
                fulizaOutstandingCents = 50_000, fulizaDueDate = LocalDate.parse("2026-10-20"),
            ),
        )
        val readings = ledger.fulizaReadings().first()
        val one = FulizaStatus.forLine(readings, 1)!!
        assertEquals(Money(641_836), one.outstanding)
        assertEquals(Money(1_000_000), one.available) // ceiling = limit + owed at that reading; available = ceiling − owed now
        val all = FulizaStatus.forLine(readings, null)!!
        assertEquals(Money(691_836), all.outstanding)
        assertNull(all.available, "line 2's limit is unknown, so the total available is too")
        assertEquals(LocalDate.parse("2026-10-20"), all.dueDate, "the earliest due date leads")
        assertNull(FulizaStatus.forLine(readings, 3))
        assertNull(FulizaStatus.forLine(emptyList(), null))
    }
}
