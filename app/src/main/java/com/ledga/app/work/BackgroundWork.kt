package com.ledga.app.work

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ledga.app.data.backup.RestoreMode
import com.ledga.app.data.capture.ScanMode
import com.ledga.app.data.settings.Settings
import java.io.File
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** "Updating your history…" (spec §7.2): [total] is 0 until the running job reports it. */
data class HistoryProgress(val done: Int, val total: Int) {
    val fraction: Float? get() = if (total > 0) done.toFloat() / total else null

    companion object {
        /** Null when every job is finished; else the running job's progress (0/0 while queued or blocked). */
        fun of(infos: List<WorkInfo>): HistoryProgress? {
            val open = infos.filter { !it.state.isFinished }
            if (open.isEmpty()) return null
            val running = open.firstOrNull { it.state == WorkInfo.State.RUNNING }
            return HistoryProgress(
                running?.progress?.getInt(RebuildWorker.KEY_DONE, 0) ?: 0,
                running?.progress?.getInt(RebuildWorker.KEY_TOTAL, 0) ?: 0,
            )
        }
    }
}

/** The onboarding import (spec §10.4 step 3). */
sealed interface ImportProgress {
    data object Idle : ImportProgress

    data class Running(val done: Int, val total: Int) : ImportProgress {
        val fraction: Float? get() = if (total > 0) done.toFloat() / total else null
    }

    data class Done(val found: Int, val inserted: Int) : ImportProgress

    data object Failed : ImportProgress

    companion object {
        fun of(infos: List<WorkInfo>): ImportProgress {
            val info = infos.lastOrNull() ?: return Idle
            return when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING -> Running(
                    info.progress.getInt(InboxScanWorker.KEY_DONE, 0),
                    info.progress.getInt(InboxScanWorker.KEY_TOTAL, 0),
                )
                WorkInfo.State.SUCCEEDED -> Done(
                    info.outputData.getInt(InboxScanWorker.KEY_FOUND, 0),
                    info.outputData.getInt(InboxScanWorker.KEY_INSERTED, 0),
                )
                WorkInfo.State.FAILED -> Failed
                WorkInfo.State.CANCELLED -> Idle
            }
        }
    }
}

/** R122: what to restore. [file] is a snapshot or a copy in no-backup storage; [deleteAfter] removes a copy when done. */
data class RestoreRequest(
    val file: File,
    val mode: RestoreMode,
    val answers: Map<Long, Int?>,
    val applySettings: Boolean,
    val deleteAfter: Boolean,
    /** Final review I1: tells a retry whether this restore was already written (and its copy already taken). */
    val id: String = UUID.randomUUID().toString(),
)

/** A restore's progress (Export & restore, onboarding). [Done.added]: payments the backup added. */
sealed interface RestoreProgress {
    data object Idle : RestoreProgress

    data class Running(val done: Int, val total: Int) : RestoreProgress {
        val fraction: Float? get() = if (total > 0) done.toFloat() / total else null
    }

    data class Done(val added: Int, val payments: Int) : RestoreProgress

    data class Failed(val message: String) : RestoreProgress

    companion object {
        fun of(infos: List<WorkInfo>): RestoreProgress {
            val info = infos.lastOrNull() ?: return Idle
            return when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING ->
                    Running(info.progress.getInt(RebuildWorker.KEY_DONE, 0), info.progress.getInt(RebuildWorker.KEY_TOTAL, 0))
                WorkInfo.State.SUCCEEDED -> Done(info.outputData.getInt(RestoreWorker.KEY_ADDED, 0), info.outputData.getInt(RestoreWorker.KEY_PAYMENTS, 0))
                WorkInfo.State.FAILED -> Failed(info.outputData.getString(RestoreWorker.KEY_ERROR) ?: RestoreWorker.FAILED)
                WorkInfo.State.CANCELLED -> Idle
            }
        }
    }
}

/** Everything the app asks of WorkManager. Screens and Startup depend on this, so tests use a fake. */
interface BackgroundWork {
    /** After MIGRATION_5_6 (spec §8 step 3): import v1's user intent, rescan the whole inbox, then rebuild. */
    fun afterMigration()

    /** A parser or derivation version change (spec §9.3). */
    fun rebuild()

    /** App start: whatever the receiver missed since the last scan (spec §9.1). */
    fun catchUp()

    /** Onboarding's Import and You → Rescan: the whole inbox. */
    fun importInbox()

    /** Spec §7.2 step 4 (R103): the receiver's new codes get their alerts a little later; [receivedAt]: the SMS's arrival. */
    fun alertsFor(codes: Set<String>, receivedAt: Instant)

    /** R107: [kind]'s next run, or none while it is switched off. [replace] moves a queued one; otherwise it stays. */
    fun schedule(kind: Scheduled, settings: Settings, replace: Boolean)

    /** Every scheduled kind: app start and the end of onboarding (KEEP), each following its switch. */
    fun scheduleNotifications(settings: Settings, replace: Boolean) = Scheduled.entries.forEach { schedule(it, settings, replace) }

    /** Spec §9.1, R108: the 6-hourly check (SIMs, catch-up, alert pruning). A queued one is kept. */
    fun keepSyncing()

    /** Spec §12.1, R119: a snapshot soon (the job decides whether one is due). A queued one is kept. */
    fun snapshotSoon()

    /** Spec §12.3, R122: one restore at a time; a request while one runs is dropped. */
    fun restore(request: RestoreRequest)

    val restoreProgress: Flow<RestoreProgress>

    /** True while the post-migration chain has a step queued or running (its own rescan and rebuild are coming). */
    suspend fun migrationChainRunning(): Boolean

    /** The "Updating your history…" banner: null when nothing is running. */
    val history: Flow<HistoryProgress?>

    val inboxImport: Flow<ImportProgress>

    /** The post-migration import gave up: v1's notes, categories and rules aren't in yet (it retries each start). */
    val legacyImportFailed: Flow<Boolean>
}

class WorkManagerBackgroundWork(private val wm: WorkManager, private val clock: Clock = Clock.systemUTC()) : BackgroundWork {

    override fun afterMigration() {
        wm.beginUniqueWork(STARTUP, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<LegacyImportWorker>().build())
            .then(InboxScanWorker.request(ScanMode.FULL))
            .then(OneTimeWorkRequestBuilder<RebuildWorker>().build())
            .enqueue()
    }

    override fun rebuild() = RebuildScheduler.enqueue(wm)

    override fun catchUp() {
        wm.enqueueUniqueWork(CATCH_UP, ExistingWorkPolicy.KEEP, InboxScanWorker.request(ScanMode.CATCH_UP))
    }

    override fun importInbox() {
        wm.enqueueUniqueWork(IMPORT, ExistingWorkPolicy.KEEP, InboxScanWorker.request(ScanMode.FULL))
    }

    override fun alertsFor(codes: Set<String>, receivedAt: Instant) {
        if (codes.isEmpty()) return
        wm.enqueue(PaymentAlertWorker.request(codes, receivedAt))
    }

    override fun schedule(kind: Scheduled, settings: Settings, replace: Boolean) {
        if (!kind.isOn(settings)) {
            wm.cancelUniqueWork(kind.uniqueName)
            return
        }
        val now = clock.instant()
        val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        wm.enqueueUniqueWork(kind.uniqueName, policy, ScheduledAlertWorker.request(kind, kind.next(now, settings), now))
    }

    override fun keepSyncing() {
        // The first run waits a period: start and onboarding have just run their own scan.
        val request = PeriodicWorkRequestBuilder<SyncWorker>(SyncWorker.EVERY).setInitialDelay(SyncWorker.EVERY).build()
        wm.enqueueUniquePeriodicWork(SyncWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun snapshotSoon() {
        wm.enqueueUniqueWork(SnapshotWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<SnapshotWorker>().build())
    }

    override fun restore(request: RestoreRequest) {
        wm.enqueueUniqueWork(RestoreWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, RestoreWorker.request(request))
    }

    override val restoreProgress: Flow<RestoreProgress> = wm.getWorkInfosForUniqueWorkFlow(RestoreWorker.UNIQUE_NAME).map(RestoreProgress::of)

    override suspend fun migrationChainRunning(): Boolean =
        wm.getWorkInfosForUniqueWorkFlow(STARTUP).first().any { !it.state.isFinished }

    override val history: Flow<HistoryProgress?> =
        combine(listOf(STARTUP, RebuildWorker.UNIQUE_NAME, IMPORT, RestoreWorker.UNIQUE_NAME).map { wm.getWorkInfosForUniqueWorkFlow(it) }) { lists ->
            HistoryProgress.of(lists.flatMap { it })
        }

    override val inboxImport: Flow<ImportProgress> = wm.getWorkInfosForUniqueWorkFlow(IMPORT).map(ImportProgress::of)

    override val legacyImportFailed: Flow<Boolean> = wm.getWorkInfosForUniqueWorkFlow(STARTUP).map(LegacyImportWorker::gaveUp)

    companion object {
        const val STARTUP = "ledga-startup"
        const val CATCH_UP = "ledga-catch-up"
        const val IMPORT = "ledga-import"
    }
}
