package com.ledga.app.work

import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ledga.app.data.update.ApkInspector
import com.ledga.app.data.update.GitHubJson
import com.ledga.app.data.update.UpdateFiles
import com.ledga.app.data.update.UpdateHttp
import com.ledga.app.data.update.UpdateMessages
import com.ledga.app.data.update.UpdateNotices
import com.ledga.app.data.update.UpdateStore
import com.ledga.core.update.AppVersion
import com.ledga.core.update.ManifestCheck
import com.ledga.core.update.Release
import com.ledga.core.update.Sha256
import com.ledga.core.update.UpdatePolicy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CancellationException

/**
 * Spec §13.4, R136, R138: downloads one release's APK. The manifest comes first and must describe this release. Then
 * the APK, which must match the manifest's SHA-256 and be an update of this very app, before it gets its final name (a
 * partial file is never mistaken for a ready one, R139). A person's download shows its progress in a notification; a
 * quiet one only says when it's ready. Either restarts from zero.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val http: UpdateHttp,
    private val files: UpdateFiles,
    private val notices: UpdateNotices,
    private val inspector: ApkInspector,
    private val store: UpdateStore,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val version = inputData.getString(KEY_VERSION)?.let(AppVersion::parse)
        val apkName = inputData.getString(KEY_APK_NAME)
        val apkUrl = inputData.getString(KEY_APK_URL)
        val manifestUrl = inputData.getString(KEY_MANIFEST_URL)
        val user = inputData.getBoolean(KEY_USER, false)
        if (version == null || apkName == null || apkUrl == null || manifestUrl == null) return failure(version, UpdateMessages.DOWNLOAD_FAILED)
        // R135 (final review I1): a quiet download of a skipped or snoozed version has nothing to do.
        if (!user && held(version)) return Result.success()
        // Final review M2: a run Android stopped (its 10-minute window) comes back here from zero. A person's download
        // gets one more try (R137), a quiet one two more (R136); then it says so instead of starting again forever.
        if (runAttemptCount >= if (user) USER_ATTEMPTS else QUIET_RETRIES + 1) return failure(version, UpdateMessages.TOO_SLOW)
        files.dir()
        val part = files.partial(version)
        part.delete()
        return try {
            val manifest = GitHubJson.manifest(http.text(manifestUrl))
            ManifestCheck.check(manifest, version, apkName, Build.VERSION.SDK_INT)?.let { return failure(version, UpdateMessages.of(it)) }
            var shown = Int.MIN_VALUE
            http.download(apkUrl, part) { done, total ->
                val percent = if (total > 0) (done * 100 / total).toInt() else -1
                if (percent != shown) {
                    shown = percent
                    setProgress(progress(version, done, total, user))
                    if (user) notices.progress(version.toString(), percent.takeIf { it >= 0 })
                }
            }
            if (Sha256.of(part) != manifest.sha256) return failure(version, UpdateMessages.MISMATCH, part)
            val info = inspector.inspect(part)
            if (info == null || info.packageName != applicationContext.packageName || info.versionCode != manifest.versionCode.toLong()) {
                return failure(version, UpdateMessages.NOT_THIS_APP, part)
            }
            if (!part.renameTo(files.apk(version))) throw IOException("couldn't keep the download")
            // Kept either way; announced only when the person asked for it or it is still offered (final review I1).
            if (user || !held(version)) notices.ready(version.toString())
            Result.success(workDataOf(KEY_VERSION to version.toString()))
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (e: IllegalArgumentException) {
            failure(version, UpdateMessages.MISMATCH, part) // the manifest wasn't a manifest
        } catch (e: IOException) {
            part.delete()
            if (!user && runAttemptCount < QUIET_RETRIES) Result.retry() else failure(version, UpdateMessages.NETWORK)
        } finally {
            if (user) notices.clearProgress()
        }
    }

    private suspend fun held(version: AppVersion): Boolean {
        val p = store.current()
        return UpdatePolicy.held(version, p.skipped, p.snoozedUntil, clock.instant())
    }

    private fun failure(version: AppVersion?, message: String, part: File? = null): Result {
        part?.delete()
        return Result.failure(workDataOf(KEY_VERSION to version?.toString(), KEY_ERROR to message))
    }

    companion object {
        const val UNIQUE_NAME = "ledga-update-download"
        const val TAG_USER = "ledga-update-user"
        const val TAG_QUIET = "ledga-update-quiet"

        /** Each request is tagged with its version, so a queued download can say which version it is. */
        const val VERSION_TAG = "ledga-update-version:"
        const val KEY_VERSION = "version"
        const val KEY_USER = "user"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val KEY_APK_NAME = "apkName"
        private const val KEY_APK_URL = "apkUrl"
        private const val KEY_MANIFEST_URL = "manifestUrl"

        /** R136: a quiet download is tried three times in all. */
        private const val QUIET_RETRIES = 2

        /** R137: a person's download is tried twice in all. */
        private const val USER_ATTEMPTS = 2

        fun progress(version: AppVersion, done: Long, total: Long, user: Boolean): Data =
            workDataOf(KEY_VERSION to version.toString(), KEY_DONE to done, KEY_TOTAL to total, KEY_USER to user)

        /** What the worker needs from [release]; null for a release that isn't installable. */
        fun input(release: Release, user: Boolean): Data? {
            val version = release.version ?: return null
            val apk = release.apk ?: return null
            val manifest = release.manifest ?: return null
            return workDataOf(
                KEY_VERSION to version.toString(),
                KEY_APK_NAME to apk.name,
                KEY_APK_URL to apk.url,
                KEY_MANIFEST_URL to manifest.url,
                KEY_USER to user,
            )
        }

        /** The version a download job is for, from its tags. */
        fun versionOf(tags: Set<String>): String? = tags.firstOrNull { it.startsWith(VERSION_TAG) }?.removePrefix(VERSION_TAG)

        /** R136: a person's download runs on any network; a quiet one waits for an unmetered one. */
        fun request(release: Release, user: Boolean): OneTimeWorkRequest? {
            val input = input(release, user) ?: return null
            val network = if (user) NetworkType.CONNECTED else NetworkType.UNMETERED
            return OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(input)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(5))
                .addTag(if (user) TAG_USER else TAG_QUIET)
                .addTag(VERSION_TAG + release.version)
                .build()
        }
    }
}
