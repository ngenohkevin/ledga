package com.ledga.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ledga.app.data.derive.Deriver
import kotlinx.coroutines.CancellationException

/**
 * Full history rebuild (spec §7.2): after a parser/derivation version change, the migration, a restore,
 * or You -> Data. Progress feeds the "Updating your history…" banner. Task 8 adds @HiltWorker.
 */
class RebuildWorker(
    context: Context,
    params: WorkerParameters,
    private val deriver: Deriver,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        deriver.rebuildAll { done, total -> setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total)) }
        Result.success()
    } catch (e: CancellationException) {
        throw e // stopped by WorkManager: not a failure to retry (Phase 2 deferred M6)
    } catch (e: Exception) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        const val UNIQUE_NAME = "ledga-rebuild"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        private const val MAX_ATTEMPTS = 3
    }
}

object RebuildScheduler {
    /** One rebuild at a time; a request while one is pending or running is dropped (the running one covers it). */
    fun enqueue(workManager: WorkManager) {
        workManager.enqueueUniqueWork(
            RebuildWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RebuildWorker>().build(),
        )
    }
}
