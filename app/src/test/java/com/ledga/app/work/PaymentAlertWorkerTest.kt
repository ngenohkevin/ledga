package com.ledga.app.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.notify.Notifier
import com.ledga.app.notify.PaymentAlerts
import com.ledga.app.testing.FakePhone
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaymentAlertWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-03-21T12:30:00Z"))
    private val settings = SettingsStore(FakePrefsStore())
    private val phone = FakePhone()
    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
            if (workerClassName == PaymentAlertWorker::class.java.name) {
                PaymentAlertWorker(appContext, workerParameters, PaymentAlerts(db, settings, Notifier(db, phone, clock), clock))
            } else {
                null
            }
    }

    @After fun close() = db.close()

    @Test
    fun `the job alerts for the codes the receiver gave it`() = runTest {
        settings.setNotifyLarge(true)
        settings.setLargeThreshold(100_000)
        SmsIngestor(db, Deriver(db, clock)).ingestAll(listOf(RawSms("MPESA", Sms.KPLC, clock.instant(), null, null, SmsSource.RECEIVER)))
        val worker = TestListenableWorkerBuilder<PaymentAlertWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(PaymentAlertWorker.KEY_CODES to arrayOf("TJK4AB12FA")))
            .build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals("Ksh 1,000 to KPLC Prepaid", phone.posted.single().title)
    }
}
