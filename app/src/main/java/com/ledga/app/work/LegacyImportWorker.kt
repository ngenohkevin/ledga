package com.ledga.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledga.app.data.legacy.LegacyImporter
import kotlinx.coroutines.CancellationException

/** Spec §8 step 3: v1's notes, categories, rules and car tags into v2's overrides and rules, once. All-or-nothing. Task 8 adds @HiltWorker. */
class LegacyImportWorker(
    context: Context,
    params: WorkerParameters,
    private val importer: LegacyImporter,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        importer.run()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
