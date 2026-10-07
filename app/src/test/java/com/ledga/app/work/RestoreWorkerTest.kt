package com.ledga.app.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.ledga.app.data.backup.BackupReader
import com.ledga.app.data.backup.DeviceId
import com.ledga.app.data.backup.Exporter
import com.ledga.app.data.backup.RestoreMode
import com.ledga.app.data.backup.Restorer
import com.ledga.app.data.backup.SnapshotStore
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.MetaKeys
import com.ledga.app.data.room.MetaRow
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakeBackgroundWork
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.testSnapshots
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R122: the restore job — its file, its retries, its result. Synthetic data. */
@RunWith(RobolectricTestRunner::class)
class RestoreWorkerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = MutableClock(Instant.parse("2026-10-07T17:00:00Z"))
    private val db = TestDb.inMemory()
    private val source = TestDb.inMemory()
    private val settings = SettingsStore(FakePrefsStore())
    private val work = FakeBackgroundWork()
    private val backupDir by lazy { tmp.newFolder("backup") }
    private val snapshots by lazy { testSnapshots(db, backupDir, settings, clock) }
    private val restorer by lazy {
        Restorer(db, Deriver(db, clock), settings, snapshots, FakeSims(), DeviceId { "this-phone" }, clock)
    }
    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
            if (workerClassName == RestoreWorker::class.java.name) RestoreWorker(appContext, workerParameters, restorer, work, settings) else null
    }

    @After fun close() {
        db.close()
        source.close()
    }

    private suspend fun exported(): File {
        SmsIngestor(source, Deriver(source, clock)).ingest(RawSms("MPESA", Sms.KPLC, Sms.at("2026-03-21T12:00:00Z"), null, null, SmsSource.INBOX))
        val file = tmp.newFile("picked.ledga")
        Exporter(BackupReader(source, SettingsStore(FakePrefsStore()), DeviceId { "other-phone" }, "2.0.0-test", clock), source).write(file.outputStream())
        return file
    }

    private suspend fun run(request: RestoreRequest, attempt: Int = 0) = TestListenableWorkerBuilder<RestoreWorker>(context)
        .setWorkerFactory(factory)
        .setInputData(RestoreWorker.input(request))
        .setRunAttemptCount(attempt)
        .build()
        .doWork()

    @Test
    fun `a restore runs from its file, reports what it added and removes the copy`() = runTest {
        val file = exported()
        val result = run(RestoreRequest(file, RestoreMode.MERGE, emptyMap(), applySettings = false, deleteAfter = true))
        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, result.outputData.getInt(RestoreWorker.KEY_ADDED, -1))
        assertEquals(1, db.transactionsDao().count())
        assertFalse(file.exists())
        assertEquals(emptyList(), work.scheduled.filter { it.startsWith("alertsFor") }, "a restore never alerts")
    }

    @Test
    fun `a damaged file fails with its message and changes nothing`() = runTest {
        val file = tmp.newFile("broken.ledga").apply { writeText("not a backup") }
        val result = run(RestoreRequest(file, RestoreMode.REPLACE, emptyMap(), applySettings = true, deleteAfter = true))
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals("This isn't a Ledga backup file.", result.outputData.getString(RestoreWorker.KEY_ERROR))
        assertEquals(0, db.smsDao().count())
    }

    private suspend fun beforeRestoreBodies(): List<String>? = SnapshotStore(backupDir).let { it.read(it.beforeRestore) }?.sms?.map { it.body }

    @Test
    fun `a retry before the restore was written takes the copy again (final review I1)`() = runTest {
        // The first attempt died while taking the copy: the phone is untouched, so the retry takes it now.
        SmsIngestor(db, Deriver(db, clock)).ingest(RawSms("MPESA", Sms.SEND, Sms.at("2026-03-21T10:30:00Z"), null, null, SmsSource.INBOX))
        run(RestoreRequest(exported(), RestoreMode.REPLACE, emptyMap(), applySettings = false, deleteAfter = false, id = "r2"), attempt = 1)
        assertEquals(listOf(Sms.SEND), beforeRestoreBodies())
    }

    @Test
    fun `a retry after the restore was written never takes the copy again (final review I1)`() = runTest {
        // The first attempt took the copy and wrote the restore, then died during the rebuild: the copy must stay the
        // phone as it was, not what the restore brought.
        SmsIngestor(db, Deriver(db, clock)).ingest(RawSms("MPESA", Sms.SEND, Sms.at("2026-03-21T10:30:00Z"), null, null, SmsSource.INBOX))
        snapshots.saveBeforeRestore()
        db.metaDao().put(MetaRow(MetaKeys.RESTORE_REQUEST, "r1"))
        SmsIngestor(db, Deriver(db, clock)).ingest(RawSms("MPESA", Sms.BANK_APP, Sms.at("2026-04-02T06:15:00Z"), null, null, SmsSource.IMPORT))
        run(RestoreRequest(exported(), RestoreMode.MERGE, emptyMap(), applySettings = false, deleteAfter = false, id = "r1"), attempt = 1)
        assertEquals(listOf(Sms.SEND), beforeRestoreBodies())
    }

    @Test
    fun `restored settings re-plan the notifications once onboarded`() = runTest {
        settings.setOnboarded()
        run(RestoreRequest(exported(), RestoreMode.MERGE, emptyMap(), applySettings = true, deleteAfter = false))
        assertTrue(work.scheduled.any { it.endsWith("replace=true") })
    }

    @Test
    fun `answers travel to the job and back`() {
        val answers = mapOf(1L to 5, 2L to null)
        assertEquals(answers, RestoreWorker.decode(RestoreWorker.encode(answers)))
        assertEquals(emptyMap(), RestoreWorker.decode(null))
    }
}
