package com.ledga.app.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.notify.SummaryAlerts
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException

/**
 * One scheduled alert (spec §11, R107): it re-reads its switch, writes, then queues its own next run with REPLACE.
 * REPLACE stops this job, so queuing is the last thing it does, and a failure still queues the next one.
 */
@HiltWorker
class ScheduledAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val summaries: SummaryAlerts,
    private val settings: SettingsStore,
    private val work: BackgroundWork,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val kind = Scheduled.entries.firstOrNull { it.name == inputData.getString(KEY_KIND) } ?: return Result.success()
        val at = Instant.ofEpochMilli(inputData.getLong(KEY_AT, 0L))
        try {
            if (kind.isOn(settings.current())) {
                when (kind) {
                    Scheduled.DAILY -> summaries.daily(at)
                    Scheduled.WEEKLY -> summaries.weekly(at)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "a scheduled alert failed; the next one is still queued", e)
        }
        work.schedule(kind, settings.current(), replace = true)
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KEY_AT = "at"
        private const val TAG = "Ledga"

        fun request(kind: Scheduled, at: Instant, now: Instant): OneTimeWorkRequest = OneTimeWorkRequestBuilder<ScheduledAlertWorker>()
            .setInitialDelay(Duration.between(now, at))
            .setInputData(workDataOf(KEY_KIND to kind.name, KEY_AT to at.toEpochMilli()))
            .build()
    }
}
