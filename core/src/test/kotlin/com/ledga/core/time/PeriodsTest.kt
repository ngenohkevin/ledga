package com.ledga.core.time

import com.ledga.core.time.PeriodType.DAY
import com.ledga.core.time.PeriodType.MONTH
import com.ledga.core.time.PeriodType.WEEK
import com.ledga.core.time.PeriodType.YEAR
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeriodsTest {
    private fun at(s: String) = Instant.parse(s)

    @Test
    fun `the Nairobi day decides the period, not UTC`() {
        // 1 Oct 2026 00:30 EAT is still 30 Sep in UTC.
        assertEquals(Period(MONTH, LocalDate.of(2026, 10, 1)), Periods.current(MONTH, at("2026-09-30T21:30:00Z")))
        assertEquals(Period(DAY, LocalDate.of(2026, 10, 1)), Periods.current(DAY, at("2026-09-30T21:30:00Z")))
        assertEquals(Period(YEAR, LocalDate.of(2027, 1, 1)), Periods.current(YEAR, at("2026-12-31T21:30:00Z")))
    }

    @Test
    fun `weeks start on Monday and malformed periods are rejected`() {
        // Sun 4 Oct 2026 23:30 EAT → week of Mon 28 Sep; Mon 5 Oct 00:30 EAT → week of 5 Oct.
        assertEquals(LocalDate.of(2026, 9, 28), Periods.current(WEEK, at("2026-10-04T20:30:00Z")).start)
        assertEquals(LocalDate.of(2026, 10, 5), Periods.current(WEEK, at("2026-10-04T21:30:00Z")).start)
        assertFailsWith<IllegalArgumentException> { Period(WEEK, LocalDate.of(2026, 10, 6)) }
        assertFailsWith<IllegalArgumentException> { Period(MONTH, LocalDate.of(2026, 10, 2)) }
        assertFailsWith<IllegalArgumentException> { Period(YEAR, LocalDate.of(2026, 2, 1)) }
    }

    @Test
    fun `keys and lengths`() {
        assertEquals("2026-10-05", Period(DAY, LocalDate.of(2026, 10, 5)).key)
        assertEquals("2026-10-05", Period(WEEK, LocalDate.of(2026, 10, 5)).key)
        assertEquals("2026-10", Period(MONTH, LocalDate.of(2026, 10, 1)).key)
        assertEquals("2026", Period(YEAR, LocalDate.of(2026, 1, 1)).key)
        assertEquals(28, Period(MONTH, LocalDate.of(2026, 2, 1)).lengthInDays)
        assertEquals(366, Period(YEAR, LocalDate.of(2028, 1, 1)).lengthInDays)
        assertEquals(Period(MONTH, LocalDate.of(2027, 1, 1)), Period(MONTH, LocalDate.of(2026, 12, 1)).next())
    }

    @Test
    fun `the current period is open-ended so new transactions always land in it`() {
        val now = at("2026-10-05T07:00:00Z")
        val month = Periods.current(MONTH, now)
        val live = Periods.liveRange(month, now)
        assertTrue(live.isOpen)
        assertNull(live.endExclusive)
        assertTrue(at("2026-10-05T07:00:01Z") in live)
        assertFalse(at("2026-09-30T20:59:59Z") in live, "30 Sep 23:59:59 EAT is September")
        assertTrue(at("2026-09-30T21:00:00Z") in live, "1 Oct 00:00 EAT is October")
        assertEquals(
            InstantRange(at("2026-08-31T21:00:00Z"), at("2026-09-30T21:00:00Z")),
            Periods.liveRange(month.previous(), now),
        )
        assertTrue(at("2026-09-15T00:00:00Z") in month.previous())
    }

    @Test
    fun `lastN ends with the current period`() {
        assertEquals(
            listOf("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10"),
            Periods.lastN(MONTH, at("2026-10-05T07:00:00Z"), 6).map { it.key },
        )
    }

    @Test
    fun `comparison window is the same elapsed time of the previous period`() {
        // Thu 5 Mar 12:00 Nairobi -> 1 Feb 00:00 .. 5 Feb 12:00 Nairobi.
        assertEquals(InstantRange(at("2026-01-31T21:00:00Z"), at("2026-02-05T09:00:00Z")), Periods.comparisonWindow(MONTH, at("2026-03-05T09:00:00Z")))
        // 31 Mar 12:00 -> all of February (clamped to its end).
        assertEquals(InstantRange(at("2026-01-31T21:00:00Z"), at("2026-02-28T21:00:00Z")), Periods.comparisonWindow(MONTH, at("2026-03-31T09:00:00Z")))
        // Wed 7 Oct 12:00 -> Mon 28 Sep 00:00 .. Wed 30 Sep 12:00.
        assertEquals(InstantRange(at("2026-09-27T21:00:00Z"), at("2026-09-30T09:00:00Z")), Periods.comparisonWindow(WEEK, at("2026-10-07T09:00:00Z")))
        // Exactly at the first instant of a period the window is not empty (InstantRange forbids that): 1 ms.
        assertEquals(InstantRange(at("2026-01-31T21:00:00Z"), at("2026-01-31T21:00:00.001Z")), Periods.comparisonWindow(MONTH, at("2026-02-28T21:00:00Z")))
    }

    @Test
    fun `next midnight is Nairobi midnight`() {
        assertEquals(at("2026-10-05T21:00:00Z"), Periods.nextMidnight(at("2026-10-05T20:59:59Z")))
        assertEquals(at("2026-10-06T21:00:00Z"), Periods.nextMidnight(at("2026-10-05T21:00:00Z")))
    }

    @Test
    fun `ranges are half-open and must not be empty`() {
        val r = InstantRange(at("2026-01-01T00:00:00Z"), at("2026-01-02T00:00:00Z"))
        assertTrue(at("2026-01-01T00:00:00Z") in r)
        assertFalse(at("2026-01-02T00:00:00Z") in r)
        assertFailsWith<IllegalArgumentException> { InstantRange(at("2026-01-02T00:00:00Z"), at("2026-01-01T00:00:00Z")) }
    }
}
