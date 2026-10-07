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
import com.ledga.app.data.backup.SnapshotStore
import com.ledga.app.data.capture.InboxScanner
import com.ledga.app.data.capture.InboxSms
import com.ledga.app.data.capture.InboxSource
import com.ledga.app.data.capture.ScanMode
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.legacy.LegacyImporter
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.settings.Settings
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.testSnapshots
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BackgroundWorkTest {
    @get:Rule val tmp = TemporaryFolder()
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
                RebuildWorker::class.java.name -> RebuildWorker(appContext, workerParameters, deriver, testSnapshots(db, tmp.root))
                SnapshotWorker::class.java.name -> SnapshotWorker(appContext, workerParameters, testSnapshots(db, tmp.root))
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

    @Test
    fun `a legacy import that keeps failing lets the rescan and rebuild still run, and says so`() = runTest {
        db.openHelper.writableDatabase.execSQL("CREATE TABLE legacy_tx (code TEXT)") // staging the importer can't read
        val early = TestListenableWorkerBuilder<LegacyImportWorker>(context).setWorkerFactory(factory).build()
        assertEquals(ListenableWorker.Result.retry(), early.doWork())
        val last = TestListenableWorkerBuilder<LegacyImportWorker>(context).setWorkerFactory(factory).setRunAttemptCount(3).build()
        assertEquals(ListenableWorker.Result.success(workDataOf(LegacyImportWorker.KEY_FAILED to true)), last.doWork())
    }

    @Test
    fun `a legacy import that gave up shows until one succeeds`() {
        fun info(state: WorkInfo.State, failed: Boolean) = WorkInfo(
            UUID.randomUUID(), state, setOf(LegacyImportWorker::class.java.name),
            outputData = workDataOf(LegacyImportWorker.KEY_FAILED to failed),
        )
        assertFalse(LegacyImportWorker.gaveUp(emptyList()))
        assertFalse(LegacyImportWorker.gaveUp(listOf(info(WorkInfo.State.SUCCEEDED, failed = false))))
        assertTrue(LegacyImportWorker.gaveUp(listOf(info(WorkInfo.State.SUCCEEDED, failed = true))))
    }

    @Test
    fun `the receiver's codes get one alert job, 30 seconds later (R103)`() {
        val arrived = Instant.parse("2026-03-21T12:00:30Z")
        WorkManagerBackgroundWork(wm).alertsFor(setOf("TJK4AB12FA", "TJK4AB12FB"), arrived)
        val info = wm.getWorkInfosByTag(PaymentAlertWorker::class.java.name).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(30_000L, info.initialDelayMillis)
        WorkManagerBackgroundWork(wm).alertsFor(emptySet(), arrived)
        assertEquals(1, wm.getWorkInfosByTag(PaymentAlertWorker::class.java.name).get().size, "no codes, no job")
        val input = PaymentAlertWorker.request(setOf("TJK4AB12FA", "TJK4AB12FB"), arrived).workSpec.input
        assertEquals(setOf("TJK4AB12FA", "TJK4AB12FB"), input.getStringArray(PaymentAlertWorker.KEY_CODES)?.toSet())
        assertEquals(arrived.toEpochMilli(), input.getLong(PaymentAlertWorker.KEY_RECEIVED, 0L), "the 2 hours run from the arrival (I4)")
    }

    @Test
    fun `a summary is queued for its next time, kept at start, moved when changed, and cancelled when off (R107)`() {
        val work = WorkManagerBackgroundWork(wm, MutableClock(Instant.parse("2026-10-07T16:30:00Z"))) // 7:30 PM, Wed 7 Oct
        val s = Settings()
        work.schedule(Scheduled.DAILY, s, replace = false)
        val first = wm.getWorkInfosForUniqueWork("ledga-daily").get().single()
        assertEquals(WorkInfo.State.ENQUEUED, first.state)
        assertEquals(30 * 60_000L, first.initialDelayMillis, "8 PM is half an hour away")
        work.schedule(Scheduled.DAILY, s, replace = false)
        assertEquals(listOf(first.id), wm.getWorkInfosForUniqueWork("ledga-daily").get().filter { !it.state.isFinished }.map { it.id }, "KEEP")
        work.schedule(Scheduled.DAILY, s.copy(dailySummaryMinute = 21 * 60), replace = true)
        val moved = wm.getWorkInfosForUniqueWork("ledga-daily").get().single { !it.state.isFinished }
        assertNotEquals(first.id, moved.id)
        assertEquals(90 * 60_000L, moved.initialDelayMillis)
        work.schedule(Scheduled.DAILY, s.copy(notifyDaily = false), replace = true)
        assertTrue(wm.getWorkInfosForUniqueWork("ledga-daily").get().all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `the weekly summary waits for Sunday at 7 PM, and a summary switched off is never queued`() {
        WorkManagerBackgroundWork(wm, MutableClock(Instant.parse("2026-10-07T16:30:00Z")))
            .scheduleNotifications(Settings(notifyDaily = false), replace = false)
        assertEquals((95 * 60 + 30) * 60_000L, wm.getWorkInfosForUniqueWork("ledga-weekly").get().single().initialDelayMillis)
        assertEquals(emptyList(), wm.getWorkInfosForUniqueWork("ledga-daily").get())
    }

    @Test
    fun `a scheduled job carries its kind and its time`() {
        val at = Instant.parse("2026-10-07T17:00:00Z")
        val input = ScheduledAlertWorker.request(Scheduled.DAILY, at, Instant.parse("2026-10-07T16:30:00Z")).workSpec.input
        assertEquals("DAILY", input.getString(ScheduledAlertWorker.KEY_KIND))
        assertEquals(at.toEpochMilli(), input.getLong(ScheduledAlertWorker.KEY_AT, 0L))
    }

    @Test
    fun `the Fuliza check waits for 9 AM`() {
        WorkManagerBackgroundWork(wm, MutableClock(Instant.parse("2026-10-07T16:30:00Z")))
            .schedule(Scheduled.FULIZA, Settings(), replace = false)
        assertEquals((13 * 60 + 30) * 60_000L, wm.getWorkInfosForUniqueWork("ledga-fuliza").get().single().initialDelayMillis)
    }

    @Test
    fun `the 6-hourly check is queued once, and kept (R108)`() {
        WorkManagerBackgroundWork(wm).keepSyncing()
        val first = wm.getWorkInfosForUniqueWork(SyncWorker.UNIQUE_NAME).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, first.state)
        assertEquals(6 * 60 * 60_000L, first.periodicityInfo?.repeatIntervalMillis)
        assertEquals(6 * 60 * 60_000L, first.initialDelayMillis, "start and onboarding run their own scan: the first check waits")
        WorkManagerBackgroundWork(wm).keepSyncing()
        assertEquals(listOf(first.id), wm.getWorkInfosForUniqueWork(SyncWorker.UNIQUE_NAME).get().map { it.id })
    }

    @Test
    fun `leaving the screen queues the snapshot job, which writes only when one is due (R119)`() {
        WorkManagerBackgroundWork(wm).snapshotSoon()
        val info = wm.getWorkInfosForUniqueWork(SnapshotWorker.UNIQUE_NAME).get().single()
        assertEquals(WorkInfo.State.SUCCEEDED, info.state)
        assertFalse(SnapshotStore(tmp.root).current.exists(), "not onboarded: nothing written")
    }
}
