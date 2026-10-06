package com.ledga.app.ui.alerts

import com.ledga.app.data.alerts.AlertType
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.room.AlertRow
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.app.testing.txRow
import com.ledga.app.time.LiveClock
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R71: the alerts log as Phase 5 will write it. Synthetic rows. */
@RunWith(RobolectricTestRunner::class)
class AlertsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-06T06:00:00Z"))
    private val vms = TestViewModels()

    private fun vm() = vms.track(AlertsViewModel(db, LiveClock(clock) { awaitCancellation() }, TransactionEdits(db, Deriver(db, clock), clock)))

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    private fun alert(key: String, type: AlertType, at: String, code: String? = null, read: Boolean = false) =
        AlertRow(key, type.name, "Title $key", "Body $key", code, Instant.parse(at), if (read) Instant.parse(at) else null)

    @Test
    fun `newest first, each with its type`() = runTest {
        db.alertsDao().insertIgnore(alert("daily:2026-10-04", AlertType.DAILY, "2026-10-04T17:00:00Z"))
        db.alertsDao().insertIgnore(alert("weekly:2026-10-04", AlertType.WEEKLY, "2026-10-04T16:00:00Z"))
        db.alertsDao().insertIgnore(alert("daily:2026-10-05", AlertType.DAILY, "2026-10-05T17:00:00Z"))
        val ui = vm().ui.first { it.loaded }
        assertEquals(listOf("daily:2026-10-05", "daily:2026-10-04", "weekly:2026-10-04"), ui.alerts.map { it.key })
        assertEquals(AlertType.WEEKLY, ui.alerts.last().type)
    }

    @Test
    fun `a payment alert opens only a payment that is still there`() = runTest {
        db.transactionsDao().upsertAll(listOf(txRow(code = "TJK4AB12LA"), txRow(code = "TJK4AB12LB", hidden = true)))
        db.alertsDao().insertIgnore(alert("large:TJK4AB12LA", AlertType.LARGE, "2026-10-05T10:00:00Z", code = "TJK4AB12LA"))
        db.alertsDao().insertIgnore(alert("large:TJK4AB12LB", AlertType.LARGE, "2026-10-05T09:00:00Z", code = "TJK4AB12LB"))
        db.alertsDao().insertIgnore(alert("large:TJK4AB12LC", AlertType.LARGE, "2026-10-05T08:00:00Z", code = "TJK4AB12LC"))
        val ui = vm().ui.first { it.loaded }
        assertEquals(listOf("TJK4AB12LA", null, null), ui.alerts.map { it.code }) // visible, hidden, gone
    }

    @Test
    fun `opening Alerts marks every alert read, and what was unread stays new for this visit`() = runTest {
        db.alertsDao().insertIgnore(alert("a", AlertType.DAILY, "2026-10-05T17:00:00Z"))
        db.alertsDao().insertIgnore(alert("b", AlertType.DAILY, "2026-10-04T17:00:00Z"))
        db.alertsDao().insertIgnore(alert("c", AlertType.DAILY, "2026-10-03T17:00:00Z", read = true))
        val vm = vm()
        assertEquals(0, db.alertsDao().observeUnread().first { it == 0 })
        assertEquals(listOf(true, true, false), vm.ui.first { it.loaded }.alerts.map { it.isNew })
    }

    @Test
    fun `an alert that arrives while Alerts is open shows as new and still counts on the bell`() = runTest {
        val vm = vm()
        db.alertsDao().observeUnread().first { it == 0 }
        vm.ui.first { it.loaded }
        db.alertsDao().insertIgnore(alert("large:TJK4AB12LD", AlertType.LARGE, "2026-10-06T05:59:00Z"))
        assertEquals(listOf(true), vm.ui.first { it.alerts.isNotEmpty() }.alerts.map { it.isNew })
        assertEquals(1, db.alertsDao().observeUnread().first())
    }
}
