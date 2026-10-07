package com.ledga.app.ui.app

import com.ledga.app.data.alerts.AlertType
import com.ledga.app.data.derive.DateFilter
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.notify.Alert
import com.ledga.app.notify.NotificationTap
import com.ledga.app.notify.Notifier
import com.ledga.app.notify.OpenedNotification
import com.ledga.app.testing.FakePhone
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.txRow
import com.ledga.app.ui.activity.ActivityLink
import com.ledga.app.ui.activity.ActivityLinks
import com.ledga.app.ui.home.HomeLinks
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R100: a tapped notification's destination, and the hand-offs it leaves. Synthetic payment. */
@RunWith(RobolectricTestRunner::class)
class NotificationOpensTest {
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-05T17:05:00Z"))
    private val activity = ActivityLinks()
    private val home = HomeLinks()
    private val opens = NotificationOpens(db, clock, activity, home)
    private val tap = NotificationTap.Payment("TJK4AB12FA")

    @After fun close() = db.close()

    @Test
    fun `a payment's notification opens Alerts with the payment, and that alert is read`() = runTest {
        db.transactionsDao().upsertAll(listOf(txRow()))
        Notifier(db, FakePhone(), clock).send(Alert("large:TJK4AB12FA", AlertType.LARGE, "Ksh 1,000 to KPLC Prepaid", "Large payment", "TJK4AB12FA", tap))
        opens.open(OpenedNotification("large:TJK4AB12FA", tap))
        assertEquals(OpenDestination.Alerts("TJK4AB12FA"), opens.destination.value)
        assertEquals(0, db.alertsDao().observeUnread().first())
    }

    @Test
    fun `a hidden payment's notification opens Alerts without it`() = runTest {
        db.transactionsDao().upsertAll(listOf(txRow(hidden = true)))
        opens.open(OpenedNotification("large:TJK4AB12FA", tap))
        assertEquals(OpenDestination.Alerts(null), opens.destination.value)
    }

    @Test
    fun `a Fuliza reminder opens Home and asks for its Fuliza sheet`() = runTest {
        opens.open(OpenedNotification("fuliza-due:1:2026-11-02:3d", NotificationTap.Fuliza))
        assertEquals(OpenDestination.Home, opens.destination.value)
        assertTrue(home.fulizaAsked.value)
    }

    @Test
    fun `a summary opens Activity on its days with every flow, and is taken once`() = runTest {
        val day = LocalDate.parse("2026-10-05")
        opens.open(OpenedNotification("daily:2026-10-05", NotificationTap.Spending(day, day)))
        assertEquals(OpenDestination.Activity, opens.destination.value)
        assertEquals(ActivityLink.Transactions(TransactionFilter(dates = DateFilter.Custom(day, day))), activity.requests.value)
        opens.taken(OpenDestination.Activity)
        assertNull(opens.destination.value)
    }
}
