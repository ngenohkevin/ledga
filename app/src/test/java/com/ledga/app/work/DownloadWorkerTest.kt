package com.ledga.app.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.ledga.app.data.update.ApkInfo
import com.ledga.app.data.update.UpdateFiles
import com.ledga.app.data.update.UpdateMessages
import com.ledga.app.data.update.UpdateStore
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeUpdateHttp
import com.ledga.app.testing.FakeUpdateNotices
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.sha256Hex
import com.ledga.core.update.AppVersion
import com.ledga.core.update.Release
import com.ledga.core.update.ReleaseAsset
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec §13.4, R136, R138: the download, its checks and its notifications. Synthetic bytes and releases. */
@RunWith(RobolectricTestRunner::class)
class DownloadWorkerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val version = AppVersion.parse("2.0.1-beta.2")!!
    private val apkName = "ledga-2.0.1-beta.2.apk"
    private val apkBytes = ByteArray(50_000) { (it * 7 % 256).toByte() }
    private val http = FakeUpdateHttp()
    private val notices = FakeUpdateNotices()
    private val files by lazy { UpdateFiles(tmp.newFolder("updates")) }
    private var apkInfo: ApkInfo? = null
    private val store = UpdateStore(FakePrefsStore())
    private val clock = MutableClock(Instant.parse("2026-10-07T06:00:00Z"))

    private val release = Release(
        tag = "v2.0.1-beta.2", version = version, prerelease = true, draft = false, publishedAt = null, notes = "",
        assets = listOf(
            ReleaseAsset(apkName, "https://example.test/apk", apkBytes.size.toLong()),
            ReleaseAsset(Release.MANIFEST_NAME, "https://example.test/manifest", 200),
        ),
    )

    private fun manifest(sha: String = sha256Hex(apkBytes), code: Int = version.code, minSdk: Int = 26) =
        """{"version":"2.0.1-beta.2","versionCode":$code,"apk":"$apkName","sha256":"$sha","minSdk":$minSdk,"channel":"beta"}"""

    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
            if (workerClassName == DownloadWorker::class.java.name) {
                DownloadWorker(appContext, workerParameters, http, files, notices, { apkInfo }, store, clock)
            } else {
                null
            }
    }

    @Before
    fun serve() {
        http.texts["https://example.test/manifest"] = manifest()
        http.files["https://example.test/apk"] = apkBytes
        apkInfo = ApkInfo(context.packageName, version.code.toLong())
    }

    private suspend fun run(user: Boolean, attempt: Int = 0): ListenableWorker.Result =
        TestListenableWorkerBuilder<DownloadWorker>(context)
            .setInputData(DownloadWorker.input(release, user)!!)
            .setRunAttemptCount(attempt)
            .setWorkerFactory(factory)
            .build()
            .doWork()

    private fun failedWith(result: ListenableWorker.Result): String? = assertIs<ListenableWorker.Result.Failure>(result).outputData.getString(DownloadWorker.KEY_ERROR)

    @Test
    fun `a person's download is checked, kept and announced, with its progress shown`() = runTest {
        assertIs<ListenableWorker.Result.Success>(run(user = true))
        assertContentEquals(apkBytes, files.ready(version)!!.readBytes())
        assertEquals(
            listOf("progress 2.0.1-beta.2 50", "progress 2.0.1-beta.2 100", "ready 2.0.1-beta.2", "clearProgress"),
            notices.events,
        )
    }

    @Test
    fun `a quiet download says only when it is ready`() = runTest {
        assertIs<ListenableWorker.Result.Success>(run(user = false))
        assertEquals(listOf("ready 2.0.1-beta.2"), notices.events)
    }

    @Test
    fun `a tampered APK is refused and deleted`() = runTest {
        http.files["https://example.test/apk"] = apkBytes.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertEquals(UpdateMessages.MISMATCH, failedWith(run(user = true)))
        assertNull(files.ready(version))
        assertEquals(0, files.dir().list()!!.size)
        assertTrue("ready 2.0.1-beta.2" !in notices.events)
    }

    @Test
    fun `an APK for another app, or another version, is refused`() = runTest {
        apkInfo = ApkInfo("com.example.other", version.code.toLong())
        assertEquals(UpdateMessages.NOT_THIS_APP, failedWith(run(user = true)))
        apkInfo = ApkInfo(context.packageName, version.code - 1L)
        assertEquals(UpdateMessages.NOT_THIS_APP, failedWith(run(user = true)))
        apkInfo = null
        assertEquals(UpdateMessages.NOT_THIS_APP, failedWith(run(user = true)))
        assertEquals(0, files.dir().list()!!.size)
    }

    @Test
    fun `a manifest for another release is refused before the APK is fetched`() = runTest {
        http.texts["https://example.test/manifest"] = manifest(code = version.code - 1)
        assertEquals(UpdateMessages.MISMATCH, failedWith(run(user = true)))
        assertTrue(http.downloads.isEmpty())
        http.texts["https://example.test/manifest"] = "<html>not json</html>"
        assertEquals(UpdateMessages.MISMATCH, failedWith(run(user = true)))
    }

    @Test
    fun `an update for a newer Android says which`() = runTest {
        http.texts["https://example.test/manifest"] = manifest(minSdk = 99)
        assertEquals(UpdateMessages.needsAndroid(99), failedWith(run(user = true)))
    }

    @Test
    fun `a broken connection leaves no file, and a quiet download tries twice more`() = runTest {
        http.failDownloads = true
        assertIs<ListenableWorker.Result.Retry>(run(user = false, attempt = 0))
        assertIs<ListenableWorker.Result.Retry>(run(user = false, attempt = 1))
        assertEquals(UpdateMessages.NETWORK, failedWith(run(user = false, attempt = 2)))
        assertEquals(UpdateMessages.NETWORK, failedWith(run(user = true, attempt = 0)))
        assertEquals(0, files.dir().list()!!.size)
    }

    @Test
    fun `a quiet download of a skipped or snoozed version neither downloads nor says it is ready (final review I1)`() = runTest {
        store.skip(version)
        assertIs<ListenableWorker.Result.Success>(run(user = false))
        assertTrue(http.downloads.isEmpty())
        assertTrue(notices.events.isEmpty())
    }

    @Test
    fun `a version put off while it downloaded is kept but not announced, unless the person asked for it (final review I1)`() = runTest {
        http.duringDownload = { store.snoozeUntil(clock.instant.plusSeconds(3_600)) }
        assertIs<ListenableWorker.Result.Success>(run(user = false))
        assertTrue(files.ready(version) != null)
        assertTrue(notices.events.none { it.startsWith("ready") })
        assertIs<ListenableWorker.Result.Success>(run(user = true))
        assertTrue("ready 2.0.1-beta.2" in notices.events)
    }
}
