package com.ledga.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ledga.app.data.legacy.LegacyImporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Spec §8 step 3: v1's notes, categories, rules and car tags into v2's overrides and rules, once. All-or-nothing.
 * After its last attempt it still succeeds, flagged [KEY_FAILED], so the chain's rescan and rebuild run and the user
 * sees their history; the staging tables stay, so the next start queues the import again.
 */
@HiltWorker
class LegacyImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val importer: LegacyImporter,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        importer.run()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success(workDataOf(KEY_FAILED to true))
    }

    companion object {
        const val KEY_FAILED = "failed"
        private const val MAX_ATTEMPTS = 3

        /** The latest post-migration chain's import gave up (see the class note). */
        fun gaveUp(infos: List<WorkInfo>): Boolean = infos.any {
            LegacyImportWorker::class.java.name in it.tags &&
                it.state == WorkInfo.State.SUCCEEDED &&
                it.outputData.getBoolean(KEY_FAILED, false)
        }
    }
}
