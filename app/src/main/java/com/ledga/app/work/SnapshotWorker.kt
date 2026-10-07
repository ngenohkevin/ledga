package com.ledga.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledga.app.data.backup.Snapshots
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/** Spec §12.1: Ledga left the screen; [Snapshots.writeIfDue] decides whether a snapshot is due (R119). */
@HiltWorker
class SnapshotWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val snapshots: Snapshots,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        snapshots.writeIfDue()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.success() // the next time Ledga leaves the screen tries again
    }

    companion object {
        const val UNIQUE_NAME = "ledga-snapshot"
    }
}
