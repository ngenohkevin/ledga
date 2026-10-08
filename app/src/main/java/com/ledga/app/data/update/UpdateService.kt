package com.ledga.app.data.update

import com.ledga.app.work.DownloadProgress
import com.ledga.app.work.UpdateWork
import com.ledga.core.update.AppVersion
import com.ledga.core.update.Release
import com.ledga.core.update.UpdateChannel
import com.ledga.core.update.UpdatePolicy
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Everything the update screens, Home's banner and You's row show (spec §13.4). */
data class UpdateState(
    val installed: AppVersion,
    /** The person's choice, or the installed build's kind until they make one (R145). */
    val channel: UpdateChannel,
    val checking: Boolean = false,
    val checkedAt: Instant? = null,
    val failure: CheckFailure? = null,
    /** Every release on the channel, newest first (Version history, R142). */
    val history: List<Release> = emptyList(),
    /** The newest installable release above the installed one, skipped or not (the Updates screen, R135). */
    val newest: Release? = null,
    val skipped: Boolean = false,
    /** R135: Home's banner may offer [newest]: not skipped, not snoozed. */
    val offered: Boolean = false,
    val download: DownloadProgress = DownloadProgress.Idle,
    /** [newest]'s verified APK is on the phone (R139). */
    val ready: Boolean = false,
    val installFailure: String? = null,
    /** False in Ledga dev without its local source: Version history only (R140). */
    val offersUpdates: Boolean = true,
)

/**
 * Spec §13.4: the one source of update state. It checks GitHub (R134, R147), keeps the answer (R133), offers the newest
 * release on the channel (R135, R145), fetches it quietly on Wi-Fi (R136), and hands a verified APK to the installer (R138).
 */
class UpdateService(
    private val store: UpdateStore,
    private val http: UpdateHttp,
    private val endpoints: UpdateEndpoints,
    private val files: UpdateFiles,
    private val work: UpdateWork,
    private val installer: UpdateInstaller,
    private val notices: UpdateNotices,
    private val events: InstallEvents,
    val installed: AppVersion,
    private val clock: Clock,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Mutex()
    private val checking = MutableStateFlow(false)
    private val releases: Flow<List<Release>> = store.prefs.map { it.releasesJson }.distinctUntilChanged().map(::parse)

    val state: Flow<UpdateState> = combine(store.prefs, releases, checking, work.download, events.failure) { p, all, busy, download, failure ->
        stateOf(p, all, busy, download, failure)
    }.flowOn(io)

    private fun stateOf(p: UpdatePrefs, all: List<Release>, busy: Boolean, download: DownloadProgress, failure: String?): UpdateState {
        val endpoint = endpoints.current()
        val channel = p.channel ?: if (installed.isBeta) UpdateChannel.BETA else UpdateChannel.STABLE
        // A list from another source (Ledga dev's local server came or went) isn't shown until this one answers.
        val current = if (p.releasesUrl == endpoint.releasesUrl) all else emptyList()
        val newest = if (endpoint.offersUpdates) UpdatePolicy.newest(current, installed, channel) else null
        val version = newest?.version
        return UpdateState(
            installed = installed,
            channel = channel,
            checking = busy,
            checkedAt = p.checkedAt,
            failure = p.failure,
            history = UpdatePolicy.history(current, channel),
            newest = newest,
            skipped = version != null && version == p.skipped,
            offered = UpdatePolicy.offered(newest, p.skipped, p.snoozedUntil, clock.instant()),
            download = download,
            ready = version != null && files.ready(version) != null,
            installFailure = failure,
            offersUpdates = endpoint.offersUpdates,
        )
    }

    /** Spec §13.4, R134: at most every 6 hours unless [force] (Check now), or at once from another source. False when it didn't run. */
    suspend fun check(force: Boolean = false): Boolean {
        val ran = lock.withLock {
            val p = store.current()
            val endpoint = endpoints.current()
            val now = clock.instant()
            val sameSource = p.releasesUrl == endpoint.releasesUrl
            if (!force && sameSource && !UpdatePolicy.checkDue(p.checkedAt, now)) return@withLock false
            checking.value = true
            try {
                when (val answer = http.releases(endpoint.releasesUrl, p.etag.takeIf { sameSource })) {
                    is ReleasesResponse.Fresh ->
                        if (runCatching { GitHubJson.releases(answer.body) }.isSuccess) {
                            store.saveReleases(answer.body, endpoint.releasesUrl, answer.etag, now)
                        } else {
                            store.checkFailed(CheckFailure.SERVER)
                        }
                    ReleasesResponse.NotModified -> store.checkedUnchanged(now)
                    is ReleasesResponse.Failed -> store.checkFailed(answer.reason)
                }
            } finally {
                checking.value = false
            }
            true
        }
        if (ran) afterChange()
        return ran
    }

    /** Spec §13.4: You → Updates → Beta updates (R145). */
    suspend fun setChannel(channel: UpdateChannel) {
        store.setChannel(channel)
        afterChange()
    }

    /** R135: the banner, the quiet download and the "ready" notice leave this version alone; a newer one is offered again. */
    suspend fun skip() {
        val version = state.first().newest?.version ?: return
        store.skip(version)
        afterChange()
    }

    /** "Later" (spec §13.4): three days. */
    suspend fun snooze() {
        store.snoozeUntil(clock.instant().plus(UpdatePolicy.SNOOZE))
        notices.clearReady()
    }

    /** A person's download (R136): any network, with its progress shown. */
    suspend fun download() {
        val newest = state.first().newest ?: return
        events.failure.value = null
        work.download(newest, user = true)
    }

    /** R138: hands the ready APK to Android. Null when there's nothing to install. */
    suspend fun install(): InstallStart? {
        val version = state.first().newest?.version ?: return null
        val apk = files.ready(version) ?: return null
        notices.clearReady()
        return withContext(io) { installer.install(apk) }
    }

    /** Android's "Install unknown apps" switch for Ledga (R138). */
    fun canInstall(): Boolean = installer.allowed()

    /**
     * After a check or a change of choice:
     * - downloads of anything but the newest offer are deleted (R139), and stopped while running;
     * - the "ready" notice goes when nothing ready is offered;
     * - an offered release that isn't here yet is fetched quietly (R136).
     */
    private suspend fun afterChange() {
        val s = state.first()
        val newest = s.newest
        val running = s.download as? DownloadProgress.Running
        if (running != null && running.version != newest?.version?.toString()) work.cancelDownload()
        withContext(io) { files.keepOnly(newest?.version) }
        if (!s.offered || !s.ready) notices.clearReady()
        if (newest != null && s.offered && !s.ready && running == null) work.download(newest, user = false)
    }

    private fun parse(json: String?): List<Release> = json?.let { runCatching { GitHubJson.releases(it) }.getOrNull() }.orEmpty()
}
