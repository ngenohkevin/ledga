package com.ledga.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ledga.app.data.capture.InboxScanner
import com.ledga.app.data.capture.ScanMode
import kotlinx.coroutines.CancellationException

/** One inbox scan (spec §9.1) as a job that survives leaving the screen; progress uses the rebuild's keys. Task 8 adds @HiltWorker. */
class InboxScanWorker(
    context: Context,
    params: WorkerParameters,
    private val scanner: InboxScanner,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val mode = ScanMode.entries.firstOrNull { it.name == inputData.getString(KEY_MODE) } ?: ScanMode.CATCH_UP
        val r = scanner.scan(mode) { done, total -> setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total)) }
        Result.success(workDataOf(KEY_FOUND to r.found, KEY_INSERTED to r.inserted))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_DONE = RebuildWorker.KEY_DONE
        const val KEY_TOTAL = RebuildWorker.KEY_TOTAL
        const val KEY_FOUND = "found"
        const val KEY_INSERTED = "inserted"
        private const val MAX_ATTEMPTS = 3

        fun request(mode: ScanMode): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<InboxScanWorker>().setInputData(workDataOf(KEY_MODE to mode.name)).build()
    }
}
