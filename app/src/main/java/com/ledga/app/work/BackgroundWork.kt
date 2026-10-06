package com.ledga.app.work

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ledga.app.data.capture.ScanMode
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

    /** True while the post-migration chain has a step queued or running (its own rescan and rebuild are coming). */
    suspend fun migrationChainRunning(): Boolean

    /** The "Updating your history…" banner: null when nothing is running. */
    val history: Flow<HistoryProgress?>

    val inboxImport: Flow<ImportProgress>

    /** The post-migration import gave up: v1's notes, categories and rules aren't in yet (it retries each start). */
    val legacyImportFailed: Flow<Boolean>
}

class WorkManagerBackgroundWork(private val wm: WorkManager) : BackgroundWork {

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

    override suspend fun migrationChainRunning(): Boolean =
        wm.getWorkInfosForUniqueWorkFlow(STARTUP).first().any { !it.state.isFinished }

    override val history: Flow<HistoryProgress?> =
        combine(listOf(STARTUP, RebuildWorker.UNIQUE_NAME, IMPORT).map { wm.getWorkInfosForUniqueWorkFlow(it) }) { lists ->
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
