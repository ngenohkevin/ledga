package com.ledga.app.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.ledga.app.data.capture.InboxScanner
import com.ledga.app.data.capture.InboxSms
import com.ledga.app.data.capture.InboxSource
import com.ledga.app.data.capture.ScanMode
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.legacy.LegacyImporter
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class BackgroundWorkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = TestDb.inMemory()
    private val deriver = Deriver(db)
    private val inbox = InboxSource {
        listOf(
            InboxSms("MPESA", Sms.SEND, Instant.parse("2026-03-21T10:30:30Z"), 1),
            InboxSms("MPESA", Sms.KPLC, Instant.parse("2026-03-21T12:00:30Z"), 1),
        )
    }
    private val scanner = InboxScanner(inbox, LinesRepository(db.linesDao(), FakeSims()), SmsIngestor(db, deriver), SettingsStore(FakePrefsStore()))
    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
            when (workerClassName) {
                LegacyImportWorker::class.java.name -> LegacyImportWorker(appContext, workerParameters, LegacyImporter(db))
                InboxScanWorker::class.java.name -> InboxScanWorker(appContext, workerParameters, scanner)
                RebuildWorker::class.java.name -> RebuildWorker(appContext, workerParameters, deriver)
                else -> null
            }
    }
    private lateinit var wm: WorkManager

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build(),
        )
        wm = WorkManager.getInstance(context)
    }

    @Test
    fun `after a migration the legacy import runs first, then the full rescan, then the rebuild`() {
        WorkManagerBackgroundWork(wm).afterMigration()
        val infos = wm.getWorkInfosForUniqueWork(WorkManagerBackgroundWork.STARTUP).get()
        fun idOf(worker: Class<*>) = infos.single { worker.name in it.tags }.id.toString()
        val deps = (wm as WorkManagerImpl).workDatabase.dependencyDao()
        assertEquals(emptyList(), deps.getPrerequisites(idOf(LegacyImportWorker::class.java)))
        assertEquals(listOf(idOf(LegacyImportWorker::class.java)), deps.getPrerequisites(idOf(InboxScanWorker::class.java)))
        assertEquals(listOf(idOf(InboxScanWorker::class.java)), deps.getPrerequisites(idOf(RebuildWorker::class.java)))
    }

    @Test
    fun `a full scan worker stores the inbox and reports what it found`() = runTest {
        val worker = TestListenableWorkerBuilder<InboxScanWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(InboxScanWorker.KEY_MODE to ScanMode.FULL.name))
            .build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(workDataOf(InboxScanWorker.KEY_FOUND to 2, InboxScanWorker.KEY_INSERTED to 2)), result)
        assertEquals(2, db.transactionsDao().count())
    }

    @Test
    fun `the legacy import worker succeeds, and does nothing when nothing is pending`() = runTest {
        val worker = TestListenableWorkerBuilder<LegacyImportWorker>(context).setWorkerFactory(factory).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }

    @Test
    fun `the history banner shows while any history work is unfinished`() {
        fun info(state: WorkInfo.State, done: Int = 0, total: Int = 0) =
            WorkInfo(UUID.randomUUID(), state, emptySet(), progress = workDataOf("done" to done, "total" to total))
        assertNull(HistoryProgress.of(emptyList()))
        assertNull(HistoryProgress.of(listOf(info(WorkInfo.State.SUCCEEDED), info(WorkInfo.State.FAILED))))
        assertEquals(HistoryProgress(0, 0), HistoryProgress.of(listOf(info(WorkInfo.State.BLOCKED))))
        assertEquals(HistoryProgress(5, 10), HistoryProgress.of(listOf(info(WorkInfo.State.BLOCKED), info(WorkInfo.State.RUNNING, 5, 10))))
        assertEquals(0.5f, HistoryProgress(5, 10).fraction)
        assertNull(HistoryProgress(0, 0).fraction)
    }

    @Test
    fun `the onboarding import's progress follows its worker`() {
        fun info(state: WorkInfo.State, output: Data = Data.EMPTY, progress: Data = Data.EMPTY) =
            WorkInfo(UUID.randomUUID(), state, emptySet(), outputData = output, progress = progress)
        assertEquals(ImportProgress.Idle, ImportProgress.of(emptyList()))
        assertEquals(ImportProgress.Running(0, 0), ImportProgress.of(listOf(info(WorkInfo.State.ENQUEUED))))
        assertEquals(
            ImportProgress.Running(3, 9),
            ImportProgress.of(listOf(info(WorkInfo.State.RUNNING, progress = workDataOf("done" to 3, "total" to 9)))),
        )
        assertEquals(
            ImportProgress.Done(9, 7),
            ImportProgress.of(listOf(info(WorkInfo.State.SUCCEEDED, output = workDataOf("found" to 9, "inserted" to 7)))),
        )
        assertEquals(ImportProgress.Failed, ImportProgress.of(listOf(info(WorkInfo.State.FAILED))))
        assertEquals(ImportProgress.Idle, ImportProgress.of(listOf(info(WorkInfo.State.CANCELLED))))
    }
}
