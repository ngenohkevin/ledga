package com.ledga.app.data.repository

import android.content.Context
import com.ledga.app.BuildConfig
import com.ledga.app.worker.GitHubAsset
import com.ledga.app.worker.GitHubRelease
import com.ledga.app.worker.UpdateChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State machine for downloading + installing a release APK.
 *
 * The Home banner only needs the "is there one?" Flow exposed by
 * [HomeViewModel] directly via [UpdateChecker]. This repository owns the
 * heavier download/install lifecycle so any screen can drive it.
 */
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val bytesRead: Long, val totalBytes: Long) : DownloadState()
    data class Ready(val file: File) : DownloadState()
    data class Failed(val message: String) : DownloadState()
}

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: Flow<DownloadState> = _downloadState.asStateFlow()

    /**
     * Fresh check ignoring the once-per-day rate limit — used by the "Check
     * for updates" tap in You → About.
     */
    suspend fun checkNow(owner: String = OWNER, repo: String = REPO): GitHubRelease? {
        val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_check", 0).apply() // bust rate limit
        return UpdateChecker.checkForUpdate(context, owner, repo, BuildConfig.VERSION_NAME)
    }

    suspend fun download(release: GitHubRelease) {
        val asset = release.apkAsset() ?: run {
            _downloadState.value = DownloadState.Failed("No APK asset on this release.")
            return
        }
        _downloadState.value = DownloadState.Downloading(0, asset.size)
        val file = UpdateChecker.downloadApkWithProgress(
            context = context,
            downloadUrl = asset.browser_download_url,
            filename = asset.name,
            expectedSize = asset.size,
        ) { bytes, total ->
            _downloadState.value = DownloadState.Downloading(bytes, total)
        }
        _downloadState.value = if (file != null) DownloadState.Ready(file)
        else DownloadState.Failed("Download failed. Check your connection and try again.")
    }

    fun install(file: File) {
        UpdateChecker.installApk(context, file)
    }

    fun reset() {
        _downloadState.value = DownloadState.Idle
    }

    /**
     * Force the download-state machine to [DownloadState.Ready] without
     * re-downloading. Used when the background worker has already cached
     * the APK and the screen just opened.
     */
    fun markReady(file: File) {
        _downloadState.value = DownloadState.Ready(file)
    }

    /**
     * Returns the on-disk APK for [release] if it's already been pre-fetched
     * (filename + size match the GitHub asset). Null otherwise.
     *
     * Used by the Home banner to flip to "Ready to install" and by
     * UpdateViewModel to short-circuit the download dance.
     */
    fun findCachedApk(release: GitHubRelease): File? {
        val asset = release.apkAsset() ?: return null
        val file = File(File(context.cacheDir, "updates"), asset.name)
        if (!file.exists() || !file.isFile) return null
        // Size guard — if a download was interrupted, the file may be a
        // partial. Treat anything that doesn't match exactly as missing.
        if (asset.size > 0 && file.length() != asset.size) return null
        return file
    }

    /**
     * Delete any stale APKs in cache so we don't accumulate megabytes per
     * release. Keeps the file matching [release], if present.
     */
    fun cleanupOldApks(release: GitHubRelease?) {
        val dir = File(context.cacheDir, "updates")
        if (!dir.isDirectory) return
        val keep = release?.apkAsset()?.name
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name != keep) f.delete()
        }
    }

    /**
     * Background pre-fetch entry point used by [UpdateCheckWorker]. Idempotent:
     * returns immediately if the APK is already cached for this release.
     */
    suspend fun prefetchInBackground(release: GitHubRelease): Boolean {
        val asset = release.apkAsset() ?: return false
        findCachedApk(release)?.let { return true }
        cleanupOldApks(release) // drop older partials before downloading
        val file = UpdateChecker.downloadApkWithProgress(
            context = context,
            downloadUrl = asset.browser_download_url,
            filename = asset.name,
            expectedSize = asset.size,
        ) { _, _ -> /* no UI to report to here */ }
        return file != null
    }

    /** First .apk asset on the release, by convention. */
    private fun GitHubRelease.apkAsset(): GitHubAsset? =
        assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    companion object {
        const val OWNER = "ngenohkevin"
        const val REPO = "ledga"
    }
}
