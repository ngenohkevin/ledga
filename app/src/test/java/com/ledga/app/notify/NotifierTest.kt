package com.ledga.app.notify

import com.ledga.app.data.alerts.AlertType
import com.ledga.app.data.room.AlertRow
import com.ledga.app.testing.FakePhone
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.TestDb
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec §7.1, §11: one writer of `alerts`; nothing posts twice. Synthetic alerts. */
@RunWith(RobolectricTestRunner::class)
class NotifierTest {
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-05T17:00:00Z"))
    private val phone = FakePhone()
    private val notifier = Notifier(db, phone, clock)

    private fun alert(key: String = "large:TJK4AB12FA", type: AlertType = AlertType.LARGE) =
        Alert(key, type, "Ksh 1,000 to KPLC Prepaid", "Large payment · Electricity", "TJK4AB12FA", NotificationTap.Payment("TJK4AB12FA"))

    @After fun close() = db.close()

    @Test
    fun `an alert is logged and shown once, however often it is sent`() = runTest {
        assertTrue(notifier.send(alert()))
        assertFalse(notifier.send(alert()))
        val n = phone.posted.single()
        assertEquals(NotifyChannels.LARGE, n.channel)
        assertEquals(OpenedNotification("large:TJK4AB12FA", NotificationTap.Payment("TJK4AB12FA")), n.opened)
        assertEquals(
            AlertRow("large:TJK4AB12FA", "LARGE", "Ksh 1,000 to KPLC Prepaid", "Large payment · Electricity", "TJK4AB12FA", clock.instant, null),
            db.alertsDao().observeWithTx().first().single().alert,
        )
    }

    @Test
    fun `an alert Android won't show still goes into Alerts (R101)`() = runTest {
        phone.allowed = false
        assertTrue(notifier.send(alert()))
        assertEquals(emptyList(), phone.posted)
        assertEquals(1, db.alertsDao().observeUnread().first())
    }

    @Test
    fun `an alert the phone fails to show stays logged, and is not shown later`() = runTest {
        phone.failNext = true
        assertTrue(notifier.send(alert()))
        assertFalse(notifier.send(alert()))
        assertEquals(emptyList(), phone.posted)
        assertEquals(1, db.alertsDao().observeUnread().first())
    }

    @Test
    fun `each kind of alert has its channel (R102)`() {
        assertEquals(NotifyChannels.LARGE, NotifyChannels.of(AlertType.LARGE))
        assertEquals(NotifyChannels.FULIZA, NotifyChannels.of(AlertType.FULIZA_DRAW))
        assertEquals(NotifyChannels.FULIZA, NotifyChannels.of(AlertType.FULIZA_DUE))
        assertEquals(NotifyChannels.SUMMARIES, NotifyChannels.of(AlertType.DAILY))
        assertEquals(NotifyChannels.SUMMARIES, NotifyChannels.of(AlertType.WEEKLY))
    }

    @Test
    fun `alerts older than 60 days are pruned, newer ones kept`() = runTest {
        val now = clock.instant
        clock.instant = now.minus(Duration.ofDays(61))
        notifier.send(alert("large:TJK4AB12OL"))
        clock.instant = now.minus(Duration.ofDays(59))
        notifier.send(alert("large:TJK4AB12NE"))
        clock.instant = now
        assertEquals(1, notifier.prune())
        assertEquals(listOf("large:TJK4AB12NE"), db.alertsDao().observeWithTx().first().map { it.alert.key })
    }
}
