package com.ledga.app.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.ledga.app.data.alerts.AlertType
import com.ledga.app.data.capture.InboxScanner
import com.ledga.app.data.capture.InboxSms
import com.ledga.app.data.capture.InboxSource
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.lines.Sim
import com.ledga.app.data.room.AlertRow
import com.ledga.app.data.room.LineRow
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.notify.Notifier
import com.ledga.app.testing.FakePhone
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec §9.1–9.2, R108: the 6-hourly check. Synthetic SMS. */
@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-03-24T09:00:00Z"))
    private val settings = SettingsStore(FakePrefsStore())
    private val sims = FakeSims()
    private val phone = FakePhone()
    private var smsGranted = true
    private val inbox = InboxSource { listOf(InboxSms("MPESA", Sms.SEND, Instant.parse("2026-03-21T10:30:30Z"), 7)) }
    private val lines = LinesRepository(db.linesDao(), sims, clock)
    private val notifier = Notifier(db, phone, clock)
    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
            if (workerClassName == SyncWorker::class.java.name) {
                val scanner = InboxScanner(inbox, lines, SmsIngestor(db, Deriver(db, clock)), settings)
                SyncWorker(appContext, workerParameters, lines, scanner, notifier, settings) { smsGranted }
            } else {
                null
            }
    }

    @After fun close() = db.close()

    private suspend fun run() = TestListenableWorkerBuilder<SyncWorker>(context).setWorkerFactory(factory).build().doWork()

    /** An alert logged long ago, straight into the table (sending it would post to the fake phone). */
    private suspend fun alertAt(key: String, at: Instant) {
        db.alertsDao().insertIgnore(AlertRow(key, AlertType.LARGE.name, "t", "b", null, at, null))
    }

    @Test
    fun `it reads the SIMs, catches up on the inbox, and prunes old alerts, without alerting`() = runTest {
        settings.setOnboarded()
        // A line Ledga made before Android would say its number: the check fills it in (spec §9.2).
        db.linesDao().insert(LineRow(subscriptionId = 7, phoneNumber = null, displayName = "Line 1", color = "#0E9F6E", isPrimary = true, createdAt = Instant.EPOCH))
        sims.add(Sim(7, "Safaricom", "0712345111"))
        alertAt("large:TJK4AB12OL", clock.instant.minus(Duration.ofDays(61)))
        assertEquals(ListenableWorker.Result.success(), run())
        assertEquals(1, db.transactionsDao().count())
        assertEquals("0712345111", db.linesDao().bySubscription(7)?.phoneNumber)
        assertEquals(emptyList(), db.alertsDao().observeWithTx().first())
        assertEquals(emptyList(), phone.posted, "a scan never alerts (spec §11)")
    }

    @Test
    fun `without SMS access, or before onboarding, it only prunes`() = runTest {
        smsGranted = false
        settings.setOnboarded()
        alertAt("large:TJK4AB12OL", clock.instant.minus(Duration.ofDays(61)))
        run()
        assertEquals(0, db.transactionsDao().count())
        assertEquals(emptyList(), db.alertsDao().observeWithTx().first())
    }
}
