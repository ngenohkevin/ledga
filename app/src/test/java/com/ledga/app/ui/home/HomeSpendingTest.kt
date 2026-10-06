package com.ledga.app.ui.home

import com.ledga.app.data.room.dao.PeriodSum
import com.ledga.app.data.room.dao.PeriodTotals
import com.ledga.core.money.Money
import com.ledga.core.time.PeriodType
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** R57: the spending card's six bars, labels, span and comparison. */
class HomeSpendingTest {
    private val now = Instant.parse("2026-10-06T06:00:00Z") // Tue 6 Oct 2026, 09:00 in Nairobi
    private val dash = Char(0x2013)

    @Test
    fun `the month card has six months, the running one last, compared with the same days of the last`() {
        val card = HomeSpending.card(
            PeriodType.MONTH, now, PeriodTotals(873_500, 6_300, 1_250_000), Money(970_000),
            mapOf("2026-09" to PeriodSum("2026-09", 3_655_000, 30), "2026-10" to PeriodSum("2026-10", 873_500, 9)),
        )
        assertEquals(listOf("May", "Jun", "Jul", "Aug", "Sep", "Oct"), card.labels)
        assertEquals(listOf<Long>(0, 0, 0, 0, 3_655_000, 873_500), card.bars)
        assertEquals("1${dash}6 Oct", card.span)
        assertEquals("same days Sep", card.comparedWith)
        assertEquals(-10, card.deltaPercent) // (8,735 − 9,700) / 9,700 = −9.9 %
        assertEquals(3_655_000, card.averageCents, "completed months from the first with spending: September alone")
        assertEquals("Spent this month", card.title)
        assertEquals("Avg/month", card.averageLabel)
    }

    @Test
    fun `weeks start on Monday and fall back to day numbers, and years read whole`() {
        val weeks = HomeSpending.card(PeriodType.WEEK, now, PeriodTotals(0, 0, 0), Money.ZERO, emptyMap())
        assertEquals(listOf("31 Aug", "7 Sep", "14 Sep", "21 Sep", "28 Sep", "5 Oct"), weeks.labels)
        assertEquals(listOf("31", "7", "14", "21", "28", "5"), weeks.shortLabels)
        assertEquals("5${dash}6 Oct", weeks.span)
        assertEquals("same days last week", weeks.comparedWith)
        assertNull(weeks.deltaPercent, "nothing to compare with")
        val years = HomeSpending.card(PeriodType.YEAR, now, PeriodTotals(0, 0, 0), Money.ZERO, emptyMap())
        assertEquals(listOf("2021", "2022", "2023", "2024", "2025", "2026"), years.labels)
        assertEquals("1 Jan${dash}6 Oct", years.span)
        assertEquals("same days 2025", years.comparedWith)
        assertEquals("Avg/year", years.averageLabel)
    }
}
