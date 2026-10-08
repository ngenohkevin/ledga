package com.ledga.app.testing

import com.ledga.app.data.update.CheckFailure
import com.ledga.app.data.update.InstallEvents
import com.ledga.app.data.update.InstallStart
import com.ledga.app.data.update.ReleasesResponse
import com.ledga.app.data.update.UpdateEndpoint
import com.ledga.app.data.update.UpdateFiles
import com.ledga.app.data.update.UpdateHttp
import com.ledga.app.data.update.UpdateInstaller
import com.ledga.app.data.update.UpdateNotices
import com.ledga.app.data.update.UpdateService
import com.ledga.app.data.update.UpdateStore
import com.ledga.app.work.DownloadProgress
import com.ledga.app.work.UpdateWork
import com.ledga.core.update.AppVersion
import com.ledga.core.update.Release
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Clock
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

/** GitHub as a test sets it: queued answers to checks (the last repeats), manifests by URL, and APK bytes by URL. */
class FakeUpdateHttp : UpdateHttp {
    val answers = ArrayDeque<ReleasesResponse>()

    /** Each check's address and the ETag it sent. */
    val checks = mutableListOf<Pair<String, String?>>()
    val texts = mutableMapOf<String, String>()
    val files = mutableMapOf<String, ByteArray>()
    val downloads = mutableListOf<String>()

    /** Downloads write half the file, then the connection drops. */
    var failDownloads = false

    /** Runs while a download is under way (e.g. the person taps Later meanwhile). */
    var duringDownload: suspend () -> Unit = {}

    override suspend fun releases(url: String, etag: String?): ReleasesResponse {
        checks += url to etag
        return if (answers.size > 1) answers.removeFirst() else answers.firstOrNull() ?: ReleasesResponse.Failed(CheckFailure.OFFLINE)
    }

    override suspend fun text(url: String): String = texts[url] ?: throw IOException("nothing at $url")

    override suspend fun download(url: String, to: File, onProgress: suspend (done: Long, total: Long) -> Unit) {
        downloads += url
        duringDownload()
        val bytes = files[url] ?: throw IOException("nothing at $url")
        if (failDownloads) {
            to.writeBytes(bytes.copyOf(bytes.size / 2))
            throw IOException("connection reset")
        }
        to.writeBytes(bytes)
        onProgress(bytes.size / 2L, bytes.size.toLong())
        onProgress(bytes.size.toLong(), bytes.size.toLong())
    }
}

/** The Updates notifications, as a list of what was asked: "progress 2.0.1 50", "ready 2.0.1", "clearProgress"… */
class FakeUpdateNotices : UpdateNotices {
    val events = mutableListOf<String>()

    override fun progress(version: String, percent: Int?) {
        events += "progress $version $percent"
    }

    override fun clearProgress() {
        events += "clearProgress"
    }

    override fun ready(version: String) {
        events += "ready $version"
    }

    override fun clearReady() {
        events += "clearReady"
    }
}

fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** WorkManager as a test sets it: what was asked for, and the download's progress driven by hand. */
class FakeUpdateWork : UpdateWork {
    val calls = mutableListOf<String>()
    override val download = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)

    override fun checkSoon() {
        calls += "checkSoon"
    }

    override fun keepChecking() {
        calls += "keepChecking"
    }

    override suspend fun download(release: Release, user: Boolean) {
        calls += "download ${release.version} ${if (user) "user" else "quiet"}"
    }

    override fun cancelDownload() {
        calls += "cancelDownload"
    }

    override suspend fun cancelQuietDownload() {
        calls += "cancelQuietDownload"
    }
}

/** Android's installer as a test sets it. */
class FakeInstaller(var allowed: Boolean = true, var start: InstallStart = InstallStart.STARTED) : UpdateInstaller {
    val installed = mutableListOf<File>()

    override fun allowed(): Boolean = allowed

    override fun install(apk: File): InstallStart {
        if (!allowed) return InstallStart.NEEDS_PERMISSION
        installed += apk
        return start
    }
}

/** One release in GitHub's shape: an APK, a manifest unless [withManifest] is false (a v1.x release), notes. Synthetic. */
fun ghRelease(tag: String, prerelease: Boolean = "-beta." in tag, withManifest: Boolean = true): String {
    val name = tag.removePrefix("v")
    val manifest = if (withManifest) """,{"name":"ledga-release.json","browser_download_url":"https://example.test/$tag/m.json","size":200}""" else ""
    return """{"tag_name":"$tag","draft":false,"prerelease":$prerelease,"published_at":"2026-10-01T06:00:00Z",""" +
        """"html_url":"https://example.test/releases/$tag","body":"## What's new\n- Something new in $name",""" +
        """"assets":[{"name":"ledga-$name.apk","browser_download_url":"https://example.test/$tag/a.apk","size":9000000}$manifest]}"""
}

fun ghList(vararg releases: String): String = releases.joinToString(",", "[", "]")

/** A real [UpdateService] over fakes, for ViewModel and screen tests. */
fun testUpdateService(
    dir: File = createTempDirectory("updates").toFile(),
    installed: String = "2.0.0-beta.1",
    http: FakeUpdateHttp = FakeUpdateHttp(),
    work: FakeUpdateWork = FakeUpdateWork(),
    clock: Clock = Clock.systemUTC(),
    store: UpdateStore = UpdateStore(FakePrefsStore()),
    endpoint: UpdateEndpoint = UpdateEndpoint("https://example.test/releases", offersUpdates = true),
    installer: FakeInstaller = FakeInstaller(),
    events: InstallEvents = InstallEvents(),
): UpdateService = UpdateService(
    store, http, { endpoint }, UpdateFiles(dir), work, installer, FakeUpdateNotices(), events, AppVersion.parse(installed)!!, clock, Dispatchers.Unconfined,
)
