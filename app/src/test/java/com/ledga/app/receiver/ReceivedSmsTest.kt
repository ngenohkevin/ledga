package com.ledga.app.receiver

import android.content.Intent
import android.telephony.SubscriptionManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class ReceivedSmsTest {
    @Test
    fun `parts from one sender are joined in order, and other senders are dropped`() {
        val joined = ReceivedSms.join(
            listOf(
                SmsPart("MPESA", "TJK4AB12FB Confirmed. Ksh500.00 "),
                SmsPart("Safaricom", "Your data bundle is about to expire."),
                SmsPart("MPESA", "sent to JANE TESTER 0712345111 on 21/3/26 at 1:30 PM."),
            ),
        )
        assertEquals(listOf(SmsPart("MPESA", "TJK4AB12FB Confirmed. Ksh500.00 sent to JANE TESTER 0712345111 on 21/3/26 at 1:30 PM.")), joined)
    }

    @Test
    fun `the SIM comes from the modern extra, then the older one, else none`() {
        assertEquals(3, ReceivedSms.subscriptionId(Intent().putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, 3)))
        assertEquals(4, ReceivedSms.subscriptionId(Intent().putExtra("subscription", 4)))
        assertNull(ReceivedSms.subscriptionId(Intent()))
    }
}
