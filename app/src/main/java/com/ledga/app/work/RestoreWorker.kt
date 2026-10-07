package com.ledga.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ledga.app.data.backup.BackupFileError
import com.ledga.app.data.backup.BackupFiles
import com.ledga.app.data.backup.RestoreMode
import com.ledga.app.data.backup.Restorer
import com.ledga.app.data.settings.SettingsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * Spec §12.3, R122: a restore that survives leaving the screen. The "before restore" copy is taken unless this request
 * was already written (final review I1): a retry never copies restored data over it, and never skips it either. A file
 * that isn't a backup fails at once with the screen's message.
 */
@HiltWorker
class RestoreWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val restorer: Restorer,
    private val work: BackgroundWork,
    private val settings: SettingsStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val file = File(inputData.getString(KEY_FILE) ?: return Result.failure(workDataOf(KEY_ERROR to FAILED)))
        val mode = RestoreMode.entries.firstOrNull { it.name == inputData.getString(KEY_MODE) } ?: RestoreMode.MERGE
        val applySettings = inputData.getBoolean(KEY_SETTINGS, false)
        return try {
            val incoming = BackupFiles.read(file)
            val requestId = inputData.getString(KEY_ID) ?: id.toString()
            val report = restorer.restore(incoming, mode, decode(inputData.getString(KEY_ANSWERS)), applySettings, requestId = requestId) { done, total ->
                setProgress(workDataOf(RebuildWorker.KEY_DONE to done, RebuildWorker.KEY_TOTAL to total))
            }
            // R107: restored notification choices take effect now, not at the next start (onboarding's end does its own).
            if (applySettings && settings.current().onboarded) work.scheduleNotifications(settings.current(), replace = true)
            cleanUp(file)
            Result.success(workDataOf(KEY_ADDED to report.paymentsAdded, KEY_PAYMENTS to report.paymentsAfter))
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupFileError) {
            cleanUp(file)
            Result.failure(workDataOf(KEY_ERROR to e.message))
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                cleanUp(file)
                Result.failure(workDataOf(KEY_ERROR to FAILED))
            }
        }
    }

    private fun cleanUp(file: File) {
        if (inputData.getBoolean(KEY_DELETE, false)) file.delete()
    }

    companion object {
        const val UNIQUE_NAME = "ledga-restore"
        const val KEY_FILE = "file"
        const val KEY_MODE = "mode"
        const val KEY_ANSWERS = "answers"
        const val KEY_SETTINGS = "settings"
        const val KEY_DELETE = "delete"
        const val KEY_ID = "id"
        const val KEY_ADDED = "added"
        const val KEY_PAYMENTS = "payments"
        const val KEY_ERROR = "error"
        const val FAILED = "The restore didn't finish. Try again."
        private const val MAX_ATTEMPTS = 2

        fun request(r: RestoreRequest): OneTimeWorkRequest = OneTimeWorkRequestBuilder<RestoreWorker>().setInputData(input(r)).build()

        fun input(r: RestoreRequest): Data = workDataOf(
            KEY_FILE to r.file.path,
            KEY_MODE to r.mode.name,
            KEY_ANSWERS to encode(r.answers),
            KEY_SETTINGS to r.applySettings,
            KEY_DELETE to r.deleteAfter,
            KEY_ID to r.id,
        )

        /** "1=5;2=": backup line id = the chosen SIM's subscription id, or nothing for "its own line". */
        fun encode(answers: Map<Long, Int?>): String = answers.entries.joinToString(";") { (line, sub) -> "$line=${sub ?: ""}" }

        fun decode(text: String?): Map<Long, Int?> = text.orEmpty().split(';').filter { it.isNotBlank() }.mapNotNull { pair ->
            val (line, sub) = pair.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            line.toLongOrNull()?.let { it to sub.toIntOrNull() }
        }.toMap()
    }
}
