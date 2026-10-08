package com.ledga.app.data.update

import com.ledga.app.testing.FakeInstaller
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.FakeUpdateHttp
import com.ledga.app.testing.FakeUpdateNotices
import com.ledga.app.testing.FakeUpdateWork
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.ghList
import com.ledga.app.testing.ghRelease
import com.ledga.app.work.DownloadProgress
import com.ledga.core.update.AppVersion
import com.ledga.core.update.UpdateChannel
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Spec §13.4, R133–R140, R145, R147: checks, offers, quiet downloads, skip, Later, channels, install. Synthetic releases. */
class UpdateServiceTest {
    @get:Rule val tmp = TemporaryFolder()

    private val clock = MutableClock(Instant.parse("2026-10-07T06:00:00Z"))
    private val http = FakeUpdateHttp()
    private val work = FakeUpdateWork()
    private val installer = FakeInstaller()
    private val notices = FakeUpdateNotices()
    private val events = InstallEvents()
    private val store = UpdateStore(FakePrefsStore())
    private var endpoint = UpdateEndpoint("https://example.test/releases", offersUpdates = true)
    private val files by lazy { UpdateFiles(tmp.newFolder("updates")) }

    private fun service(installed: String = "2.0.0-beta.1") = UpdateService(
        store, http, { endpoint }, files, work, installer, notices, events, AppVersion.parse(installed)!!, clock, Dispatchers.Unconfined,
    )

    private fun fresh(vararg releases: String, etag: String? = "\"e1\"") = ReleasesResponse.Fresh(ghList(*releases), etag)

    private fun v(text: String) = AppVersion.parse(text)!!

    @Test
    fun `a first check keeps the list, and a start within 6 hours asks nothing`() = runTest {
        val s = service()
        http.answers += fresh(ghRelease("v2.0.0-beta.2"))
        assertTrue(s.check())
        assertEquals(listOf<Pair<String, String?>>("https://example.test/releases" to null), http.checks)
        clock.instant = clock.instant.plus(Duration.ofHours(5))
        assertFalse(s.check())
        assertEquals(1, http.checks.size)
        assertEquals("v2.0.0-beta.2", s.state.first().newest?.tag)
    }

    @Test
    fun `Check now asks again with the ETag, and a 304 keeps the list`() = runTest {
        val s = service()
        http.answers += fresh(ghRelease("v2.0.0-beta.2"))
        http.answers += ReleasesResponse.NotModified
        s.check()
        clock.instant = clock.instant.plusSeconds(60)
        assertTrue(s.check(force = true))
        assertEquals("\"e1\"", http.checks.last().second)
        val state = s.state.first()
        assertEquals("v2.0.0-beta.2", state.newest?.tag)
        assertEquals(clock.instant, state.checkedAt)
    }

    @Test
    fun `offline keeps the cached list and says so, and the next answer clears it (R147)`() = runTest {
        val s = service()
        http.answers += fresh(ghRelease("v2.0.0-beta.2"))
        http.answers += ReleasesResponse.Failed(CheckFailure.OFFLINE)
        http.answers += fresh(ghRelease("v2.0.0-beta.2"))
        s.check()
        s.check(force = true)
        assertEquals(CheckFailure.OFFLINE, s.state.first().failure)
        assertEquals("v2.0.0-beta.2", s.state.first().newest?.tag)
        s.check(force = true)
        assertNull(s.state.first().failure)
    }

    @Test
    fun `an answer that isn't a release list keeps the cached one`() = runTest {
        val s = service()
        http.answers += fresh(ghRelease("v2.0.0-beta.2"))
        http.answers += ReleasesResponse.Fresh("<html>Bad gateway</html>", null)
        s.check()
        s.check(force = true)
        val state = s.state.first()
        assertEquals(CheckFailure.SERVER, state.failure)
        assertEquals("v2.0.0-beta.2", state.newest?.tag)
    }

    @Test
    fun `a found update downloads quietly, and not again once it is here (R136)`() = runTest {
        val s = service()
        http.answers += fresh(ghRelease("v2.0.0-beta.2"))
        s.check()
        assertEquals(listOf("download 2.0.0-beta.2 quiet"), work.calls)
        files.apk(v("2.0.0-beta.2")).writeText("apk")
        s.check(force = true)
        assertEquals(1, work.calls.count { it.startsWith("download") })
        assertTrue(s.state.first().ready)
    }

    @Test
    fun `a skipped version isn't fetched or offered, and a newer one is (R135)`() = runTest {
        val s = service()
        http.answers += fresh(ghRelease("v2.0.0-beta.2"))
        http.answers += fresh(ghRelease("v2.0.0-beta.3"), ghRelease("v2.0.0-beta.2"))
        s.check()
        work.calls.clear()
        s.skip()
        val skipped = s.state.first()
        assertTrue(skipped.skipped)
        assertFalse(skipped.offered)
        assertEquals("v2.0.0-beta.2", skipped.newest?.tag) // Updates still shows it (R135)
        assertTrue("clearReady" in notices.events)
        s.check(force = true)
        assertTrue(s.state.first().offered)
        assertEquals(listOf("download 2.0.0-beta.3 quiet"), work.calls)
    }

    @Test
    fun `Later hides the banner until the snooze ends`() = runTest {
        val s = service()
        http.answers += fresh(ghRelease("v2.0.0-beta.2"))
        s.check()
        s.snooze()
        assertFalse(s.state.first().offered)
        clock.instant = clock.instant.plus(Duration.ofDays(3))
        assertTrue(s.state.first().offered)
    }

    @Test
    fun `a beta build follows betas until the person chooses stable (R145)`() = runTest {
        val s = service(installed = "2.0.0-beta.1")
        http.answers += fresh(ghRelease("v2.0.0-beta.2"))
        s.check()
        assertEquals(UpdateChannel.BETA, s.state.first().channel)
        assertEquals("v2.0.0-beta.2", s.state.first().newest?.tag)
    }

    @Test
    fun `a full release follows full releases until the person chooses beta, which then fetches it`() = runTest {
        val s = service(installed = "2.0.0")
        http.answers += fresh(ghRelease("v2.0.1-beta.1"))
        s.check()
        assertEquals(UpdateChannel.STABLE, s.state.first().channel)
        assertNull(s.state.first().newest)
        s.setChannel(UpdateChannel.BETA)
        assertEquals("v2.0.1-beta.1", s.state.first().newest?.tag)
        assertEquals(listOf("download 2.0.1-beta.1 quiet"), work.calls)
    }

    @Test
    fun `switching to stable drops a ready beta`() = runTest {
        val s = service(installed = "2.0.0-beta.1")
        http.answers += fresh(ghRelease("v2.0.0-beta.2"))
        s.check()
        files.apk(v("2.0.0-beta.2")).writeText("apk")
        work.download.value = DownloadProgress.Running("2.0.0-beta.2", 10, 100, user = false)
        s.setChannel(UpdateChannel.STABLE)
        val state = s.state.first()
        assertNull(state.newest)
        assertFalse(state.offered)
        assertEquals(0, files.dir().list()!!.size)
        assertTrue("cancelDownload" in work.calls)
        assertTrue("clearReady" in notices.events)
    }

    @Test
    fun `Ledga dev without its local source keeps the history and offers nothing (R140)`() = runTest {
        endpoint = UpdateEndpoint("https://example.test/releases", offersUpdates = false)
        val s = service()
        http.answers += fresh(ghRelease("v2.0.0-beta.2"), ghRelease("v1.6.0", withManifest = false))
        s.check()
        val state = s.state.first()
        assertEquals(listOf("v2.0.0-beta.2", "v1.6.0"), state.history.map { it.tag })
        assertNull(state.newest)
        assertTrue(work.calls.none { it.startsWith("download") })
    }

    @Test
    fun `another source is checked at once, without the old source's ETag or its releases`() = runTest {
        val s = service()
        http.answers += fresh(ghRelease("v2.0.0-beta.2"))
        s.check()
        endpoint = UpdateEndpoint("http://127.0.0.1:8765/releases.json", offersUpdates = true)
        assertNull(s.state.first().newest)
        assertTrue(s.check())
        assertEquals("http://127.0.0.1:8765/releases.json" to null, http.checks.last())
    }

    @Test
    fun `a person's download clears an earlier install failure`() = runTest {
        val s = service()
        http.answers += fresh(ghRelease("v2.0.0-beta.2"))
        s.check()
        events.failure.value = "an earlier failure"
        s.download()
        assertEquals("download 2.0.0-beta.2 user", work.calls.last())
        assertNull(s.state.first().installFailure)
    }

    @Test
    fun `install hands the ready APK to Android, and asks for the permission when it's off (R138)`() = runTest {
        val s = service()
        http.answers += fresh(ghRelease("v2.0.0-beta.2"))
        s.check()
        assertNull(s.install())
        val apk = files.apk(v("2.0.0-beta.2")).apply { writeText("apk") }
        assertEquals(InstallStart.STARTED, s.install())
        assertEquals(listOf(apk), installer.installed)
        installer.allowed = false
        assertEquals(InstallStart.NEEDS_PERMISSION, s.install())
        assertFalse(s.canInstall())
    }

    @Test
    fun `an answer that lists no Ledga release keeps the cached list (Review Focus 1, final review I3)`() = runTest {
        val s = service()
        http.answers += fresh(ghRelease("v2.0.0-beta.2"))
        http.answers += ReleasesResponse.Fresh("[]", "\"e2\"")
        s.check()
        s.check(force = true)
        val state = s.state.first()
        assertEquals(CheckFailure.SERVER, state.failure)
        assertEquals("v2.0.0-beta.2", state.newest?.tag)
        assertEquals(listOf("v2.0.0-beta.2"), state.history.map { it.tag })
    }
}
