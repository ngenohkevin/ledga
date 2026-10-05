package com.ledga.core.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmsTextTest {

    @Test
    fun `normalises every whitespace variant to single spaces`() {
        val messy = "  TJK4AB12CD  Confirmed.\r\nKsh500.00\tsent to JANE  TESTER on 21/3/26 at 1:30 PM.\n"
        assertEquals("TJK4AB12CD Confirmed. Ksh500.00 sent to JANE TESTER on 21/3/26 at 1:30 PM.", SmsText.normalize(messy))
    }

    @Test
    fun `hash is stable across whitespace variants and differs for different text`() {
        val a = SmsText.hash("TJK4AB12CD Confirmed. Ksh500.00 sent to JANE TESTER")
        val b = SmsText.hash("TJK4AB12CD  Confirmed. Ksh500.00 sent to\r\nJANE TESTER ")
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
}
