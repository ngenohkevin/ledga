package com.ledga.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledga.app.data.legacy.LegacyImporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/** Spec §8 step 3: v1's notes, categories, rules and car tags into v2's overrides and rules, once. All-or-nothing. */
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
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
