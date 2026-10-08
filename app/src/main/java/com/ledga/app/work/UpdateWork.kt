package com.ledga.app.work

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ledga.app.data.update.UpdateMessages
import com.ledga.core.update.Release
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** A download's progress (R136), from its WorkManager job. */
sealed interface DownloadProgress {
    data object Idle : DownloadProgress

    /** [total] is −1 until the size is known. */
    data class Running(val version: String, val done: Long, val total: Long, val user: Boolean) : DownloadProgress {
        val fraction: Float? get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
    }

    data class Done(val version: String) : DownloadProgress

    data class Failed(val version: String?, val message: String, val user: Boolean) : DownloadProgress

    companion object {
        /** The open job, or the last one. A quiet download still waiting for Wi-Fi isn't shown (R136). */
        fun of(infos: List<WorkInfo>): DownloadProgress {
            val info = infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull() ?: return Idle
            val user = DownloadWorker.TAG_USER in info.tags
            val version = DownloadWorker.versionOf(info.tags).orEmpty()
            return when (info.state) {
                WorkInfo.State.RUNNING -> Running(
                    version,
                    info.progress.getLong(DownloadWorker.KEY_DONE, 0L),
                    info.progress.getLong(DownloadWorker.KEY_TOTAL, -1L),
                    user,
                )
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> if (user) Running(version, 0L, -1L, user = true) else Idle
                WorkInfo.State.SUCCEEDED -> Done(version)
                WorkInfo.State.FAILED -> Failed(version, info.outputData.getString(DownloadWorker.KEY_ERROR) ?: UpdateMessages.DOWNLOAD_FAILED, user)
                WorkInfo.State.CANCELLED -> Idle
            }
        }
    }
}

/** The update service's WorkManager jobs (spec §13.4). Tests use a fake. */
interface UpdateWork {
    /** R134: a check soon, at each start once onboarded; the check itself waits 6 hours between runs. */
    fun checkSoon()

    /** R134: the daily check, on any network. A queued one is kept. */
    fun keepChecking()

    /**
     * R136: a quiet download waits for an unmetered network, and a queued one is kept. A person's download runs on any
     * network and replaces one that isn't running, but a download already running for that version is left to finish.
     */
    suspend fun download(release: Release, user: Boolean)

    /** The release it was fetching is no longer the one offered (the channel changed). */
    fun cancelDownload()

    /** R135 (final review I1): Skip or Later stops a quiet download, queued or running; a person's is left alone. */
    suspend fun cancelQuietDownload()

    val download: Flow<DownloadProgress>
}

class WorkManagerUpdateWork(private val wm: WorkManager) : UpdateWork {
    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun checkSoon() {
        wm.enqueueUniqueWork(
            UpdateCheckWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<UpdateCheckWorker>().setConstraints(connected).build(),
        )
    }

    override fun keepChecking() {
        wm.enqueueUniquePeriodicWork(
            UpdateCheckWorker.DAILY_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(Duration.ofDays(1)).setConstraints(connected).build(),
        )
    }

    override suspend fun download(release: Release, user: Boolean) {
        val request = DownloadWorker.request(release, user) ?: return
        val version = release.version.toString()
        val open = wm.getWorkInfosForUniqueWorkFlow(DownloadWorker.UNIQUE_NAME).first().filter { !it.state.isFinished }
        val keep = !user || open.any { DownloadWorker.versionOf(it.tags) == version && (it.state == WorkInfo.State.RUNNING || DownloadWorker.TAG_USER in it.tags) }
        wm.enqueueUniqueWork(DownloadWorker.UNIQUE_NAME, if (keep) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelDownload() {
        wm.cancelUniqueWork(DownloadWorker.UNIQUE_NAME)
    }

    override suspend fun cancelQuietDownload() {
        val open = wm.getWorkInfosForUniqueWorkFlow(DownloadWorker.UNIQUE_NAME).first().filter { !it.state.isFinished }
        if (open.any { DownloadWorker.TAG_QUIET in it.tags }) wm.cancelUniqueWork(DownloadWorker.UNIQUE_NAME)
    }

    override val download: Flow<DownloadProgress> = wm.getWorkInfosForUniqueWorkFlow(DownloadWorker.UNIQUE_NAME).map(DownloadProgress::of)
}
