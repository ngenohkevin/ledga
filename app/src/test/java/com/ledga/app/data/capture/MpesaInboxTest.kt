package com.ledga.app.data.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.testing.FakeSmsProvider
import com.ledga.app.testing.FakeSmsProvider.Companion.row
import com.ledga.app.testing.Sms
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class MpesaInboxTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val inbox get() = MpesaInbox(context.contentResolver)

    @Before
    fun setUp() {
        FakeSmsProvider.reset()
        Robolectric.setupContentProvider(FakeSmsProvider::class.java, "sms")
    }

    @Test
    fun `reads M-Pesa messages with their SIM, newest first, ignoring other senders`() {
        FakeSmsProvider.rows = listOf(
            row("MPESA", Sms.SEND, 1_000L, sub = 2),
            row("Safaricom", "Your data bundle is about to expire.", 2_000L),
            row("FULIZA", Sms.COMPANION, 3_000L, sub = null),
            row("M-PESA", Sms.KPLC, 4_000L, sub = 3),
        )
        val read = inbox.read(0)
        assertEquals(listOf(4_000L, 3_000L, 1_000L), read.map { it.receivedAt.toEpochMilli() })
        assertEquals(listOf(3, null, 2), read.map { it.subscriptionId })
        assertEquals(listOf("M-PESA", "FULIZA", "MPESA"), read.map { it.sender })
    }

    @Test
    fun `a phone without sub_id is read through sim_id, and one without either still reads`() {
        FakeSmsProvider.rows = listOf(row("MPESA", Sms.SEND, 1_000L, sub = null, sim = 5))
        FakeSmsProvider.columns = setOf("address", "body", "date", "sim_id")
        assertEquals(5, inbox.read(0).single().subscriptionId)
        FakeSmsProvider.columns = setOf("address", "body", "date")
        assertNull(inbox.read(0).single().subscriptionId)
    }

    @Test
    fun `since reads only what arrived after it`() {
        FakeSmsProvider.rows = listOf(row("MPESA", Sms.SEND, 1_000L), row("MPESA", Sms.KPLC, 2_000L))
        assertEquals(listOf(2_000L), inbox.read(1_500L).map { it.receivedAt.toEpochMilli() })
    }

    @Test
    fun `without SMS access nothing is read and nothing crashes`() {
        FakeSmsProvider.rows = listOf(row("MPESA", Sms.SEND, 1_000L))
        FakeSmsProvider.denied = true
        assertEquals(emptyList(), inbox.read(0))
    }
}
