package com.ledga.app.worker

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL

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

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // This worker is a placeholder — the actual check is done in UpdateChecker
        return Result.success()
    }
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

    private fun isNewerVersion(latest: String, current: String): Boolean {
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
