package com.ledga.app.ui.trackers

import java.time.LocalDate
import com.ledga.app.data.room.dao.CategorySpend
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.trackers.TrackerSummary
import com.ledga.core.chart.Bucket
import com.ledga.core.model.CategoryGroup
import com.ledga.core.money.Money
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Test

/** What a tracker says (R52). Synthetic amounts. */
class TrackerTextTest {
    private val now = Instant.parse("2026-10-06T06:00:00Z")
    private val water = CategoryRow("water", "Water", CategoryGroup.BILLS_UTILITIES, "fluent_droplet", null, null, true, 1, CategoryOrigin.SYSTEM, false)

    private fun summary(thisMonth: Long, average: Long?, usual: Int?): TrackerSummary {
        val months = Periods.lastN(PeriodType.MONTH, now, 13).mapIndexed { i, p ->
            val cents = if (i == 12) thisMonth else 61_000L
            Bucket(p, Money(cents), if (cents > 0) 1 else 0)
        }
        return TrackerSummary(water, months, average, usual, null)
    }

    @Test
    fun `a tile says when a monthly bill is usually paid until it is, then the average`() {
        assertEquals("usually by the 12th", TrackerText.tileCaption(summary(0, 61_000, 12)))
        assertEquals("avg 610/mo", TrackerText.tileCaption(summary(64_000, 61_000, 12)))
        assertEquals("this month", TrackerText.tileCaption(summary(0, null, null)))
    }

    @Test
    fun `ordinals read as people say them`() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "31st"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 31).map(TrackerText::ordinal))
    }

    @Test
    fun `a row says who and how many this month, or when it's usually paid, or when it was last paid`() {
        val today = LocalDate.parse("2026-10-06")
        val paid = CategorySpend("TJK4AB12WA", "water", Instant.parse("2026-10-02T07:00:00Z"), 64_000, "SAMPLE WATER CO")
        assertEquals("Sample Water Co · 1 payment this month", TrackerText.rowContext(summary(64_000, 61_000, 12).copy(last = paid), today))
        assertEquals("usually by the 12th", TrackerText.rowContext(summary(0, 61_000, 12), today))
        val old = paid.copy(occurredAt = Instant.parse("2025-07-12T07:00:00Z"), cents = 520_000)
        assertEquals("Last: Jul 2025 · Ksh 5,200", TrackerText.rowContext(summary(0, null, null).copy(last = old), today))
        assertEquals("No payments yet", TrackerText.rowContext(summary(0, null, null), today))
    }

    @Test
    fun `a row's detail is last month while this month is unpaid, else the average`() {
        assertEquals("Sep 610", TrackerText.rowDetail(summary(0, 61_000, 12)))
        assertEquals("avg 610", TrackerText.rowDetail(summary(64_000, 61_000, 12)))
        val empty = summary(0, null, null).let { s -> s.copy(months = s.months.map { it.copy(total = Money.ZERO, count = 0) }) }
        assertEquals("this month", TrackerText.rowDetail(empty))
    }
}
