package com.ledga.core.parse

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MpesaDatesTest {

    @Test
    fun `parses two and four digit years in Nairobi time`() {
        // 21 Mar 2026 13:30 EAT = 10:30 UTC
        assertEquals(Instant.parse("2026-03-21T10:30:00Z"), MpesaDates.parseDateTime("21/3/26", "1:30 PM"))
        assertEquals(Instant.parse("2024-05-14T13:41:00Z"), MpesaDates.parseDateTime("14/05/2024", "04:41 PM"))
    }

    @Test
    fun `midnight and noon are handled`() {
        assertEquals(Instant.parse("2026-03-20T21:05:00Z"), MpesaDates.parseDateTime("21/3/26", "12:05 AM"))
        assertEquals(Instant.parse("2026-03-21T09:30:00Z"), MpesaDates.parseDateTime("21/3/26", "12:30 PM"))
        assertEquals(Instant.parse("2026-03-21T08:59:00Z"), MpesaDates.parseDateTime("21/3/26", "11:59am"))
    }

    @Test
    fun `month boundary belongs to the Nairobi month`() {
        // 1 Oct 2026 00:30 EAT is still 30 Sep in UTC.
        assertEquals(Instant.parse("2026-09-30T21:30:00Z"), MpesaDates.parseDateTime("1/10/26", "12:30 AM"))
    }

    @Test
    fun `rejects impossible dates and times instead of guessing`() {
        assertNull(MpesaDates.parseDateTime("31/2/26", "1:00 PM"))
        assertNull(MpesaDates.parseDateTime("21/13/26", "1:00 PM"))
        assertNull(MpesaDates.parseDateTime("21/3/26", "13:00 PM"))
        assertNull(MpesaDates.parseDateTime("21/3/26", "0:30 AM"))
        assertNull(MpesaDates.parseDateTime("21/3/26", "1:60 PM"))
        assertNull(MpesaDates.parseDateTime("21/3/261", "1:00 PM"))
    }

    @Test
    fun `finds the first date-time in a message, tolerating glued and doubled spacing`() {
        assertEquals(
            Instant.parse("2026-03-12T15:42:00Z"),
            MpesaDates.findDateTime("TJK4AB12CD Confirmed.on 12/3/26 at 6:42 PMWithdraw Ksh4,500.00 from 012345 - SAMPLE AGENT"),
        )
        assertEquals(
            Instant.parse("2024-05-14T13:41:00Z"),
            MpesaDates.findDateTime("You have sent Ksh1,000.00 to Hustler Fund on 14/05/2024 at 04:41 PM. New MPESA balance"),
        )
        assertNull(MpesaDates.findDateTime("Fuliza M-PESA amount is Ksh 45.00. Total outstanding due on 09/07/26."))
    }

    @Test
    fun `parses Fuliza due dates`() {
        assertEquals(LocalDate.of(2026, 7, 9), MpesaDates.parseDueDate("09/07/26"))
        assertEquals(LocalDate.of(2025, 8, 18), MpesaDates.parseDueDate("18/08/25"))
        assertNull(MpesaDates.parseDueDate("32/08/25"))
        assertNull(MpesaDates.parseDueDate("soon"))
    }
}
