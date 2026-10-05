package com.ledga.app.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.ParseStatus
import com.ledga.app.data.room.MetaKeys
import com.ledga.app.data.room.MetaRow
import com.ledga.app.data.room.SmsRow
import com.ledga.app.data.room.SmsSource
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.core.parse.MpesaParser
import com.ledga.core.parse.SmsText
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RebuildWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = TestDb.inMemory()
    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
            if (workerClassName == RebuildWorker::class.java.name) RebuildWorker(appContext, workerParameters, Deriver(db)) else null
    }

    @Test
    fun `the worker rebuilds and records the versions`() = runTest {
        val at = Instant.parse("2026-03-21T10:30:05Z")
        val s = ParseStatus.of(MpesaParser.parse(Sms.KPLC, at))
        db.smsDao().insertIgnore(SmsRow(0, "MPESA", Sms.KPLC, SmsText.hash(Sms.KPLC), at, null, null, s.code, SmsSource.LEGACY, s.status, s.reason, 0))
        db.metaDao().put(MetaRow(MetaKeys.DERIVATION_VERSION, "0"))
        val worker = TestListenableWorkerBuilder<RebuildWorker>(context).setWorkerFactory(factory).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(1, db.transactionsDao().count())
        assertFalse(Deriver(db).needsRebuild())
    }

    @Test
    fun `the scheduler enqueues one unique rebuild`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build(),
        )
        val wm = WorkManager.getInstance(context)
        RebuildScheduler.enqueue(wm)
        val info = wm.getWorkInfosForUniqueWork(RebuildWorker.UNIQUE_NAME).get().single()
        assertTrue(RebuildWorker::class.java.name in info.tags)
    }
}
