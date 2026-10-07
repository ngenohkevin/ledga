package com.ledga.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ledga.app.notify.PaymentAlerts
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException

/**
 * R103: the receiver's new codes, a little after their SMS arrived, so a Fuliza companion is in too. The 2 hours run
 * from that arrival (final review I4).
 */
@HiltWorker
class PaymentAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val alerts: PaymentAlerts,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val received = inputData.getLong(KEY_RECEIVED, 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)
        alerts.check(inputData.getStringArray(KEY_CODES).orEmpty().toList(), received)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        const val KEY_CODES = "codes"
        const val KEY_RECEIVED = "received"
        val DELAY: Duration = Duration.ofSeconds(30)
        private const val MAX_ATTEMPTS = 3

        fun request(codes: Set<String>, receivedAt: Instant): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<PaymentAlertWorker>()
                .setInitialDelay(DELAY)
                .setInputData(workDataOf(KEY_CODES to codes.toTypedArray(), KEY_RECEIVED to receivedAt.toEpochMilli()))
                .build()
    }
}
