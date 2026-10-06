package com.ledga.app.ui.home

import com.ledga.app.data.derive.FulizaStatus
import com.ledga.core.money.Money
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test

/** What Home says (R58, R60): always in Nairobi time, and only what it knows. */
class HomeTextTest {
    private val today = LocalDate.parse("2026-10-06")

    @Test
    fun `the greeting follows Nairobi's clock, not the phone's`() {
        assertEquals("Good evening", HomeText.greeting(Instant.parse("2026-10-06T01:59:00Z"))) // 04:59 in Nairobi
        assertEquals("Good morning", HomeText.greeting(Instant.parse("2026-10-06T02:00:00Z"))) // 05:00
        assertEquals("Good afternoon", HomeText.greeting(Instant.parse("2026-10-06T09:00:00Z"))) // 12:00
        assertEquals("Good evening", HomeText.greeting(Instant.parse("2026-10-06T14:00:00Z"))) // 17:00
    }

    @Test
    fun `updated says when`() {
        assertEquals("Updated 2:15 PM", HomeText.updated(Instant.parse("2026-10-06T11:15:00Z"), today))
        assertEquals("Updated yesterday 2:15 PM", HomeText.updated(Instant.parse("2026-10-05T11:15:00Z"), today))
        assertEquals("Updated 3 Oct, 2:15 PM", HomeText.updated(Instant.parse("2026-10-03T11:15:00Z"), today))
        assertEquals("Updated 3 Oct 2025, 2:15 PM", HomeText.updated(Instant.parse("2025-10-03T11:15:00Z"), today))
    }

    @Test
    fun `under All lines each line says its balance and when M-Pesa stated it`() {
        assertEquals("Personal ··11 · Ksh 3,450.25 · 2:15 PM", HomeText.lineBalance(BalanceLine("Personal ··11", 345_025, Instant.parse("2026-10-06T11:15:00Z")), today))
        assertEquals("Business ··78 · Ksh 1,200 · 2 Sep", HomeText.lineBalance(BalanceLine("Business ··78", 120_000, Instant.parse("2026-09-02T20:37:00Z")), today))
    }

    @Test
    fun `the Fuliza strip says what is owed, when it was due, and only what it knows`() {
        val owed = FulizaStatus(Money(641_836), Money(991_836), Money(350_000), LocalDate.parse("2026-11-02"))
        assertEquals("Fuliza · Ksh 6,418.36 owed" to "Due 2 Nov · Ksh 3,500 still available", HomeText.fuliza(owed, today))
        assertEquals("Fuliza · Ksh 6,418.36 owed" to "Was due 2 Nov · Ksh 3,500 still available", HomeText.fuliza(owed, LocalDate.parse("2026-11-03")))
        assertEquals("Fuliza · Ksh 6,418.36 owed" to null, HomeText.fuliza(owed.copy(available = null, dueDate = null), today))
        assertEquals(
            "Fuliza · nothing owed" to "Ksh 10,000 available to borrow",
            HomeText.fuliza(FulizaStatus(Money.ZERO, Money(1_000_000), Money(1_000_000), null), today),
        )
        assertEquals("Fuliza · nothing owed" to null, HomeText.fuliza(FulizaStatus(Money.ZERO, null, null, null), today))
    }

    @Test
    fun `a Recent row's time is short - the clock today, then the day`() {
        assertEquals("8:40 AM", HomeText.rowTime(Instant.parse("2026-10-06T05:40:00Z"), today))
        assertEquals("Yesterday", HomeText.rowTime(Instant.parse("2026-10-05T11:15:00Z"), today))
        assertEquals("2 Oct", HomeText.rowTime(Instant.parse("2026-10-02T11:15:00Z"), today))
        assertEquals("2 Oct 2025", HomeText.rowTime(Instant.parse("2025-10-02T11:15:00Z"), today))
    }
}
