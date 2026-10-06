package com.ledga.app.ui.design.format

import com.ledga.core.model.FlowKind
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import kotlin.test.assertEquals

class DateLabelsTest {
    private val today = LocalDate.of(2026, 10, 5)

    @Test
    fun `today and yesterday are words`() {
        assertEquals("TODAY", DateLabels.dayHeader(today, today))
        assertEquals("YESTERDAY", DateLabels.dayHeader(today.minusDays(1), today))
    }

    @Test
    fun `this year's days omit the year`() {
        assertEquals("FRI, 2 OCT", DateLabels.dayHeader(LocalDate.of(2026, 10, 2), today))
    }

    @Test
    fun `other years always show the year`() {
        assertEquals("THU, 2 OCT 2025", DateLabels.dayHeader(LocalDate.of(2025, 10, 2), today))
    }

    @Test
    fun `New Year's Eve seen on New Year's Day is yesterday`() {
        val newYear = LocalDate.of(2026, 1, 1)
        assertEquals("YESTERDAY", DateLabels.dayHeader(LocalDate.of(2025, 12, 31), newYear))
        assertEquals("TUE, 30 DEC 2025", DateLabels.dayHeader(LocalDate.of(2025, 12, 30), newYear))
    }

    @Test
    fun `times and dates are Nairobi's whatever the device zone`() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val at = Instant.parse("2026-10-01T21:30:00Z") // 00:30 on 2 Oct in Nairobi
            assertEquals(LocalDate.of(2026, 10, 2), DateLabels.nairobiDate(at))
            assertEquals("12:30 AM", DateLabels.clock(at))
            assertEquals("FRI, 2 OCT", DateLabels.dayHeader(DateLabels.nairobiDate(at), today))
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun `speech reads amount, direction, counterparty and when`() {
        val at = Instant.parse("2026-10-04T16:12:00Z") // 7:12 PM in Nairobi, yesterday
        assertEquals("Ksh 1,200 spent at Naivas, yesterday 7:12 PM", DateLabels.txSpeech(120_000, FlowKind.SPEND, "Naivas", at, today))
        assertEquals(
            "Ksh 5,000.50 received from Jane Doe, yesterday 7:12 PM",
            DateLabels.txSpeech(500_050, FlowKind.INCOME, "Jane Doe", at, today),
        )
        assertEquals("Fri, 2 Oct", DateLabels.spokenDay(LocalDate.of(2026, 10, 2), today))
    }
    @Test
    fun `speech says what really happened to the money - only SPEND is spent`() {
        val at = Instant.parse("2026-10-04T16:12:00Z")
        val said = FlowKind.entries.associateWith { DateLabels.txSpeech(100_000, it, "X", at, today) }
        assertEquals("Ksh 1,000 spent at X, yesterday 7:12 PM", said[FlowKind.SPEND])
        assertEquals("Ksh 1,000 received from X, yesterday 7:12 PM", said[FlowKind.INCOME])
        assertEquals("Ksh 1,000 moved to savings at X, yesterday 7:12 PM", said[FlowKind.SAVINGS_OUT])
        assertEquals("Ksh 1,000 taken from savings at X, yesterday 7:12 PM", said[FlowKind.SAVINGS_IN])
        assertEquals("Ksh 1,000 moved to your account at X, yesterday 7:12 PM", said[FlowKind.OWN_OUT])
        assertEquals("Ksh 1,000 moved from your account at X, yesterday 7:12 PM", said[FlowKind.OWN_IN])
        assertEquals("Ksh 1,000 repaid to X, yesterday 7:12 PM", said[FlowKind.LOAN_REPAY])
        assertEquals("Ksh 1,000 reversed from X, yesterday 7:12 PM", said[FlowKind.REVERSAL_IN])
        assertEquals(1, said.values.count { "spent" in it })
    }
}
