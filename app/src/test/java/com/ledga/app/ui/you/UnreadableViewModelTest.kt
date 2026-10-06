package com.ledga.app.ui.you

import com.ledga.app.data.room.SmsRow
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.room.SmsStatus
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R78: the messages Ledga couldn't read, newest first, and what Share sends. Synthetic messages. */
@RunWith(RobolectricTestRunner::class)
class UnreadableViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val vms = TestViewModels()

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    private fun sms(hash: String, body: String, at: String, status: SmsStatus) =
        SmsRow(sender = "MPESA", body = body, bodyHash = hash, receivedAt = Instant.parse(at), subscriptionId = null, lineId = null, code = null, source = SmsSource.INBOX, status = status, statusReason = null, parserVersion = 1)

    @Test
    fun `only the unreadable ones, newest first`() = runTest {
        db.smsDao().insertIgnore(sms("a", "SAMPLE ODD MESSAGE ONE", "2026-10-01T08:00:00Z", SmsStatus.UNREADABLE))
        db.smsDao().insertIgnore(sms("b", "SAMPLE ODD MESSAGE TWO", "2026-10-05T11:15:00Z", SmsStatus.UNREADABLE))
        db.smsDao().insertIgnore(sms("c", "Your account balance was SAMPLE", "2026-10-04T08:00:00Z", SmsStatus.IGNORED))
        val ui = vms.track(UnreadableViewModel(db)).ui.first { it.loaded }
        assertEquals(listOf("SAMPLE ODD MESSAGE TWO", "SAMPLE ODD MESSAGE ONE"), ui.messages.map { it.body })
        assertEquals(2, db.smsDao().observeUnreadableCount().first())
    }

    @Test
    fun `Share sends when it arrived, then the message as it is`() {
        val m = UnreadableMessage(1, "MPESA", "SAMPLE ODD MESSAGE TWO", Instant.parse("2026-10-05T11:15:00Z"))
        assertEquals("M-Pesa message Ledga couldn't read, received Mon 5 Oct 2026, 2:15 PM:\n\nSAMPLE ODD MESSAGE TWO", UnreadableText.share(m))
    }
}
