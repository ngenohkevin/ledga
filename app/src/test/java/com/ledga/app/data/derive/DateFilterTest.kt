package com.ledga.app.data.derive

import com.ledga.core.time.InstantRange
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** R69, R70: a date filter keeps what was chosen and becomes a range against today (Nairobi, UTC+3). */
class DateFilterTest {
    private val march31 = LocalDate.parse("2026-03-31")
    private val april1 = LocalDate.parse("2026-04-01")

    /** 1 March 2026, 00:00 in Nairobi. */
    private val marchStart = Instant.parse("2026-02-28T21:00:00Z")
    private val aprilStart = Instant.parse("2026-03-31T21:00:00Z")

    @Test
    fun `This month is open-ended and moves on with the day`() {
        val f = DateFilter.Preset(DatePreset.THIS_MONTH)
        assertEquals(InstantRange(marchStart, null), f.range(march31))
        assertEquals(InstantRange(aprilStart, null), f.range(april1))
    }

    @Test
    fun `Last month is closed, Last 3 months starts two months back, This year starts in January`() {
        assertEquals(InstantRange(Instant.parse("2026-01-31T21:00:00Z"), marchStart), DatePreset.LAST_MONTH.range(march31))
        assertEquals(InstantRange(Instant.parse("2025-12-31T21:00:00Z"), null), DatePreset.LAST_3_MONTHS.range(march31))
        assertEquals(InstantRange(Instant.parse("2025-12-31T21:00:00Z"), null), DatePreset.THIS_YEAR.range(march31))
    }

    @Test
    fun `a month Spending sent is open while it runs and closed once it has ended`() {
        val march = DateFilter.Month(YearMonth.of(2026, 3))
        assertNull(march.range(march31).endExclusive)
        assertEquals(InstantRange(marchStart, aprilStart), march.range(april1))
    }

    @Test
    fun `a custom range covers whole Nairobi days, both ends included`() {
        val r = DateFilter.Custom(LocalDate.parse("2026-03-02"), LocalDate.parse("2026-03-14")).range(april1)
        assertEquals(Instant.parse("2026-03-01T21:00:00Z"), r.start) // 2 March, 00:00 in Nairobi
        assertEquals(Instant.parse("2026-03-14T21:00:00Z"), r.endExclusive) // 15 March, 00:00 in Nairobi
        // 23:59 on the last day is in; 00:00 the next day is out.
        assert(Instant.parse("2026-03-14T20:59:00Z") in r)
        assert(Instant.parse("2026-03-14T21:00:00Z") !in r)
    }

    @Test
    fun `a custom range chosen backwards is swapped, and one day is a range`() {
        val a = LocalDate.parse("2026-03-14")
        val b = LocalDate.parse("2026-03-02")
        assertEquals(DateFilter.Custom(b, a), DateFilter.Custom.of(a, b))
        val day = DateFilter.Custom.of(a, a).range(april1)
        assertEquals(Instant.parse("2026-03-13T21:00:00Z"), day.start)
        assertEquals(Instant.parse("2026-03-14T21:00:00Z"), day.endExclusive)
    }
}
