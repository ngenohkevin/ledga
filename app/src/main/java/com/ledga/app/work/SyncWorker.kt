package com.ledga.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledga.app.data.backup.Snapshots
import com.ledga.app.data.capture.InboxScanner
import com.ledga.app.data.capture.ScanMode
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.notify.Notifier
import com.ledga.app.startup.SmsAccess
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import kotlinx.coroutines.CancellationException

/**
 * The 6-hourly check (spec §9.1, R108): re-reads the SIMs (§9.2: a moved SIM keeps its line), catches up on any M-Pesa
 * SMS the receiver missed, prunes alerts older than 60 days (§7.1), and writes a due snapshot (R131). Its scan never
 * alerts (spec §11).
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val lines: LinesRepository,
    private val scanner: InboxScanner,
    private val notifier: Notifier,
    private val settings: SettingsStore,
    private val snapshots: Snapshots,
    private val sms: SmsAccess,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val failed = try {
            if (settings.current().onboarded && sms.granted()) {
                runCatching { lines.syncActive() } // a SIM Android won't describe stays as it was
                scanner.scan(ScanMode.CATCH_UP)
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
        // R131 (5a M8): pruning and the snapshot never wait on a scan that failed.
        quietly { notifier.prune() }
        quietly { snapshots.writeIfDue() }
        return when {
            failed == null -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.success() // R108: after a few tries it waits for its next 6-hour run
        }
    }

    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Unit
        }
    }

    companion object {
        const val UNIQUE_NAME = "ledga-sync"
        val EVERY: Duration = Duration.ofHours(6)
        private const val MAX_ATTEMPTS = 3
    }
}
