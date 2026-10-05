package com.ledga.core.chart

import com.ledga.core.money.Money
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BucketingTest {
    private fun ksh(s: String) = Money.parse(s)!!
    private fun at(s: String) = Instant.parse(s)
    private val now = at("2026-10-05T07:00:00Z")

    @Test
    fun `points land in their Nairobi month and gaps are zero-filled`() {
        val months = Periods.lastN(PeriodType.MONTH, now, 4)
        val buckets = Bucketing.sumByPeriod(
            listOf(
                AmountPoint(at("2026-07-10T09:00:00Z"), ksh("1000")),
                AmountPoint(at("2026-09-30T21:30:00Z"), ksh("250")), // 1 Oct 00:30 EAT → October
                AmountPoint(at("2026-09-15T09:00:00Z"), ksh("400")),
                AmountPoint(at("2026-03-01T09:00:00Z"), ksh("999")), // outside the window
            ),
            months,
        )
        assertEquals(listOf("2026-07", "2026-08", "2026-09", "2026-10"), buckets.map { it.period.key })
        assertEquals(listOf(ksh("1000"), Money.ZERO, ksh("400"), ksh("250")), buckets.map { it.total })
        assertEquals(listOf(1, 0, 1, 1), buckets.map { it.count })
    }

    @Test
    fun `pre-aggregated sums are zero-filled by period key`() {
        val weeks = Periods.lastN(PeriodType.WEEK, now, 3)
        val b = Bucketing.zeroFill(mapOf("2026-09-28" to ksh("70")), mapOf("2026-09-28" to 2), weeks)
        assertEquals(listOf("2026-09-21", "2026-09-28", "2026-10-05"), b.map { it.period.key })
        assertEquals(listOf(Money.ZERO, ksh("70"), Money.ZERO), b.map { it.total })
        assertEquals(listOf(0, 2, 0), b.map { it.count })
    }

    @Test
    fun `average skips the in-progress period and periods before the first payment`() {
        val months = Periods.lastN(PeriodType.MONTH, now, 6) // May..Oct
        val b = Bucketing.zeroFill(
            mapOf("2026-06" to ksh("1000"), "2026-08" to ksh("2000"), "2026-09" to ksh("3000"), "2026-10" to ksh("500")),
            mapOf("2026-06" to 1, "2026-08" to 2, "2026-09" to 1, "2026-10" to 1),
            months,
        )
        // June..September: (1000 + 0 + 2000 + 3000) / 4. May (before first payment) and October (in progress) excluded.
        assertEquals(ksh("1500"), Bucketing.averageOfCompleted(b, now))
        assertNull(Bucketing.averageOfCompleted(Bucketing.zeroFill(emptyMap(), emptyMap(), months), now))
        assertNull(Bucketing.averageOfCompleted(Bucketing.zeroFill(mapOf("2026-10" to ksh("500")), mapOf("2026-10" to 1), months), now))
        val thirds = Bucketing.zeroFill(mapOf("2026-07" to ksh("1000")), mapOf("2026-07" to 1), months)
        assertEquals(ksh("333.33"), Bucketing.averageOfCompleted(thirds, now))
    }

    @Test
    fun `usual day of month uses the last six payments in Nairobi time`() {
        val times = listOf(
            "2026-03-10T09:00:00Z", // oldest of seven: excluded
            "2026-04-12T09:00:00Z", "2026-05-09T09:00:00Z", "2026-06-11T09:00:00Z",
            "2026-07-30T09:00:00Z", "2026-08-08T09:00:00Z",
            "2026-09-09T21:30:00Z", // 10 Sep 00:30 EAT: day 10, not 9
        ).map(Instant::parse)
        // days [12, 9, 11, 30, 8, 10] → sorted [8, 9, 10, 11, 12, 30] → lower median 10
        assertEquals(10, Bucketing.usualDayOfMonth(times.shuffled(Random(1))))
        assertNull(Bucketing.usualDayOfMonth(emptyList()))
    }

    @Test
    fun `delta percent rounds half away from zero and is undefined against zero`() {
        assertEquals(20, Bucketing.deltaPercent(ksh("1200"), ksh("1000")))
        assertEquals(-10, Bucketing.deltaPercent(ksh("900"), ksh("1000")))
        assertEquals(1, Bucketing.deltaPercent(ksh("1005"), ksh("1000")))
        assertEquals(-1, Bucketing.deltaPercent(ksh("995"), ksh("1000")))
        assertEquals(-100, Bucketing.deltaPercent(Money.ZERO, ksh("1000")))
        assertNull(Bucketing.deltaPercent(ksh("500"), Money.ZERO))
    }
}
