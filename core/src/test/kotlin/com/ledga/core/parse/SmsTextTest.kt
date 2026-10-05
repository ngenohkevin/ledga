package com.ledga.core.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmsTextTest {

    @Test
    fun `normalises every whitespace variant to single spaces`() {
        val messy = "  TJK4AB12CD  Confirmed.\r\nKsh500.00\tsent to\u00A0JANE  TESTER on 21/3/26 at 1:30 PM.\n"
        assertEquals("TJK4AB12CD Confirmed. Ksh500.00 sent to JANE TESTER on 21/3/26 at 1:30 PM.", SmsText.normalize(messy))
    }

    @Test
    fun `hash is stable across whitespace variants and differs for different text`() {
        val a = SmsText.hash("TJK4AB12CD Confirmed. Ksh500.00 sent to JANE TESTER")
        val b = SmsText.hash("TJK4AB12CD\u00A0 Confirmed. Ksh500.00 sent to\r\nJANE TESTER ")
        assertEquals(a, b)
        assertEquals(64, a.length)
        assertTrue(a.all { it in '0'..'9' || it in 'a'..'f' })
        assertTrue(a != SmsText.hash("TJK4AB12CD Confirmed. Ksh501.00 sent to JANE TESTER"))
    }

    @Test
    fun `recognises M-Pesa senders only`() {
        listOf("MPESA", "mpesa", " M-PESA ", "m-pesa", "FULIZA", "Fuliza").forEach { assertTrue(SmsText.isMpesaSender(it), it) }
        listOf("", "SAFARICOM", "KCB", "MPESA2", "M PESA", "+254700000000").forEach { assertFalse(SmsText.isMpesaSender(it), it) }
    }

    @Test
    fun `hash is pinned to a known SHA-256 vector`() {
        assertEquals(
            "4f93d22df53508cf1be36ccc6a9e7bd75561b3d5c029a05bc4a66b34f18ea886",
            SmsText.hash("TJK4AB12CD Confirmed. Ksh500.00 sent to JANE TESTER"),
        )
    }

    private val zeroWidth = listOf(0xFEFF, 0x200B, 0x200C, 0x200D, 0x2060).map { String(Character.toChars(it)) }

    @Test
    fun `zero-width characters are removed entirely`() {
        zeroWidth.forEach { z ->
            assertEquals("TJK4AB12CD Confirmed.", SmsText.normalize(z + "TJK4AB12CD Confirmed." + z))
            assertEquals("ABCD", SmsText.normalize("AB" + z + "CD"))
        }
        assertEquals(SmsText.hash("TJK4AB12CD Confirmed."), SmsText.hash(zeroWidth.first() + "TJK4AB12CD Confirmed."))
    }

    @Test
    fun `a body prefixed with a BOM or zero-width space still parses`() {
        val body = "TJK4AB12CD Confirmed. Ksh500.00 sent to JANE TESTER 0712345111 on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh1,200.00. " +
            "Transaction cost, Ksh7.00. Amount you can transact within the day is 499,493.00."
        val at = java.time.Instant.parse("2026-03-21T10:45:12Z")
        zeroWidth.take(2).forEach { z ->
            val outcome = MpesaParser.parse(z + body, at)
            kotlin.test.assertIs<ParseOutcome.Parsed>(outcome)
            assertEquals("TJK4AB12CD", outcome.sms.code)
        }
    }
}
