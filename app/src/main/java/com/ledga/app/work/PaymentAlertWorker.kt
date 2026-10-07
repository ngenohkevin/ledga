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
import kotlinx.coroutines.CancellationException

/** R103: the receiver's new codes, a little after their SMS arrived, so a Fuliza companion is in too. */
@HiltWorker
class PaymentAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val alerts: PaymentAlerts,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        alerts.check(inputData.getStringArray(KEY_CODES).orEmpty().toList())
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        const val KEY_CODES = "codes"
        val DELAY: Duration = Duration.ofSeconds(30)
        private const val MAX_ATTEMPTS = 3

        fun request(codes: Set<String>): OneTimeWorkRequest = OneTimeWorkRequestBuilder<PaymentAlertWorker>()
            .setInitialDelay(DELAY)
            .setInputData(workDataOf(KEY_CODES to codes.toTypedArray()))
            .build()
    }
}
