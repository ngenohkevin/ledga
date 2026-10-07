package com.ledga.app.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.notify.Notifier
import com.ledga.app.notify.SummaryAlerts
import com.ledga.app.testing.FakeBackgroundWork
import com.ledga.app.testing.FakePhone
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.txRow
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R107: each scheduled run re-reads its switch, writes, and queues its own next run last. */
@RunWith(RobolectricTestRunner::class)
class ScheduledAlertWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-05T17:00:30Z")) // 8:00:30 PM, Mon 5 Oct 2026 in Nairobi
    private val settings = SettingsStore(FakePrefsStore())
    private val phone = FakePhone()
    private val work = FakeBackgroundWork()
    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
            if (workerClassName == ScheduledAlertWorker::class.java.name) {
                val notifier = Notifier(db, phone, clock)
                ScheduledAlertWorker(appContext, workerParameters, SummaryAlerts(db, LedgerQueries(db), notifier, clock), settings, work)
            } else {
                null
            }
    }

    @After fun close() = db.close()

    private suspend fun run(kind: Scheduled, at: Instant): ListenableWorker.Result =
        TestListenableWorkerBuilder<ScheduledAlertWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(ScheduledAlertWorker.KEY_KIND to kind.name, ScheduledAlertWorker.KEY_AT to at.toEpochMilli()))
            .build()
            .doWork()

    @Test
    fun `the daily summary is written at its time, and the next one queued`() = runTest {
        db.transactionsDao().upsertAll(listOf(txRow())) // Ksh 1,000 to KPLC at 2:15 PM on Mon 5 Oct
        assertEquals(ListenableWorker.Result.success(), run(Scheduled.DAILY, Instant.parse("2026-10-05T17:00:00Z")))
        assertEquals("Spent Ksh 1,000 today", phone.posted.single().title)
        assertEquals(listOf("DAILY on replace=true"), work.scheduled)
    }

    @Test
    fun `a run queues the next one whatever it found, and writes nothing once switched off`() = runTest {
        db.transactionsDao().upsertAll(listOf(txRow()))
        settings.setNotifyDaily(false)
        run(Scheduled.DAILY, Instant.parse("2026-10-05T17:00:00Z"))
        assertEquals(emptyList(), phone.posted)
        assertEquals(listOf("DAILY off replace=true"), work.scheduled, "switched off: the schedule cancels it")
        work.scheduled.clear()
        run(Scheduled.WEEKLY, Instant.parse("2026-09-27T16:00:00Z")) // eight days late
        assertEquals(emptyList(), phone.posted)
        assertEquals(listOf("WEEKLY on replace=true"), work.scheduled)
    }
}
