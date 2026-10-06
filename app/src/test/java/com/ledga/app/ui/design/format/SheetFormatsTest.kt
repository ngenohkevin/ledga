package com.ledga.app.ui.design.format

import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

/** The display names (R44) and the date and amount texts the sheet and Spending use. */
class SheetFormatsTest {
    @Test
    fun `display names title-case words but keep acronyms, numbers and single letters`() {
        assertEquals("KPLC Prepaid", NameFormat.display("KPLC PREPAID"))
        assertEquals("Jane Tester", NameFormat.display("JANE TESTER"))
        assertEquals("Sample Supermarket LTD", NameFormat.display("SAMPLE SUPERMARKET LTD"))
        assertEquals("M-Pesa Globalpay", NameFormat.display("M-PESA GLOBALPAY"))
        assertEquals("NCBA Bank", NameFormat.display("NCBA BANK"))
        assertEquals("0712***678", NameFormat.display("0712***678"))
        assertEquals("Safaricom Home", NameFormat.display("  SAFARICOM   HOME "))
    }

    @Test
    fun `the sheet's amount carries its sign and currency`() {
        assertEquals("${AmountFormat.MINUS}Ksh 1,000.00", AmountFormat.signedKsh(100_000, inflow = false))
        assertEquals("+Ksh 5,000.00", AmountFormat.signedKsh(500_000, inflow = true))
        assertEquals("Ksh 0.00", AmountFormat.signedKsh(0, inflow = false))
    }

    @Test
    fun `dates for the sheet, Fuliza due dates and Spending's month, in Nairobi time`() {
        assertEquals("Mon 5 Oct 2026, 2:15 PM", DateLabels.dateTime(Instant.parse("2026-10-05T11:15:00Z")))
        assertEquals("Tue 6 Oct 2026, 12:30 AM", DateLabels.dateTime(Instant.parse("2026-10-05T21:30:00Z")), "past Nairobi midnight")
        assertEquals("2 Nov", DateLabels.dayMonth(LocalDate.parse("2026-11-02")))
        assertEquals("September 2026", DateLabels.monthYear(YearMonth.of(2026, 9)))
    }
}
