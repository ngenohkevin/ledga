package com.ledga.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledga.app.data.update.UpdateService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/** Spec §13.4, R134: the start's check and the daily one. The service decides whether one is due. */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updates: UpdateService,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            updates.check(force = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Unit // the next start or day tries again; a failed check is recorded by the service when it can be
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "ledga-update-check"
        const val DAILY_NAME = "ledga-update-daily"
    }
}
