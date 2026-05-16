package com.ledga.app.worker

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ledga.app.BuildConfig
import com.ledga.app.data.repository.SettingsRepository
import com.ledga.app.data.repository.UpdateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long = 0
)

/**
 * Daily background pre-fetch of the latest APK.
 *
 * Runs at most once per day on unmetered network with battery not low —
 * so we never spend a user's mobile data without their say-so. When a
 * newer release is detected and the user hasn't dismissed that version,
 * the APK is downloaded to cacheDir/updates/. The Home banner picks it
 * up on next launch and flips to "Ready to install".
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updateRepository: UpdateRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val release = UpdateChecker.checkForUpdate(
            context = applicationContext,
            owner = UpdateRepository.OWNER,
            repo = UpdateRepository.REPO,
            currentVersion = BuildConfig.VERSION_NAME,
        ) ?: run {
            updateRepository.cleanupOldApks(null)
            return@runCatching Result.success()
        }

        val dismissed = settingsRepository.getDismissedUpdateVersion().first()
        if (release.tag_name == dismissed) return@runCatching Result.success()

        updateRepository.prefetchInBackground(release)
        Result.success()
    }.getOrElse { Result.retry() }
}

object UpdateScheduler {

    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

    private const val UNIQUE_NAME = "update_check"
}

object UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }
    private const val PREFS_NAME = "update_prefs"
    private const val LAST_CHECK_KEY = "last_check"
    private const val LAST_VERSION_KEY = "last_version"

    suspend fun checkForUpdate(
        context: Context,
        owner: String,
        repo: String,
        currentVersion: String
    ): GitHubRelease? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(LAST_CHECK_KEY, 0)
        val lastVersion = prefs.getString(LAST_VERSION_KEY, "") ?: ""
        val oneDayMs = 24 * 60 * 60 * 1000L

        // Reset rate limit if app version changed (just updated)
        val versionChanged = lastVersion != currentVersion
        if (versionChanged) {
            prefs.edit().putString(LAST_VERSION_KEY, currentVersion).apply()
        }

        // Rate limit: max once per day (skip if version just changed)
        if (!versionChanged && System.currentTimeMillis() - lastCheck < oneDayMs) {
            return@withContext null
        }

        prefs.edit().putLong(LAST_CHECK_KEY, System.currentTimeMillis()).apply()

        try {
            val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val response = URL(url).readText()
            val release = json.decodeFromString<GitHubRelease>(response)

            val latestVersion = release.tag_name.removePrefix("v")
            if (isNewerVersion(latestVersion, currentVersion)) release else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun downloadApk(context: Context, downloadUrl: String, filename: String): File? =
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, "updates")
                cacheDir.mkdirs()
                val file = File(cacheDir, filename)

                URL(downloadUrl).openStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                file
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Same as [downloadApk] but reports progress.
     *
     * `onProgress(bytesRead, totalBytes)` fires roughly every 64 KB. When
     * [expectedSize] is unknown (0 or missing), `totalBytes` is reported as
     * the asset's `size` (always present on GitHub Releases) or -1 as a
     * last resort.
     */
    suspend fun downloadApkWithProgress(
        context: Context,
        downloadUrl: String,
        filename: String,
        expectedSize: Long,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "updates")
            cacheDir.mkdirs()
            val file = File(cacheDir, filename)

            val connection = URL(downloadUrl).openConnection()
            connection.connect()
            val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize

            connection.getInputStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        total += read
                        onProgress(total, contentLength)
                    }
                }
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    internal fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
