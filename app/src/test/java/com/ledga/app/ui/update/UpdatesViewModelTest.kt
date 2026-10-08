package com.ledga.app.ui.update

import com.ledga.app.data.update.ReleasesResponse
import com.ledga.app.testing.FakeInstaller
import com.ledga.app.testing.FakeUpdateHttp
import com.ledga.app.testing.FakeUpdateWork
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.TestViewModels
import com.ledga.app.testing.ghList
import com.ledga.app.testing.ghRelease
import com.ledga.app.testing.testUpdateService
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Spec §13.4, R134, R138, R145: what the Updates screen asks of the service. Synthetic releases. */
class UpdatesViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val clock = MutableClock(Instant.parse("2026-10-07T06:00:00Z"))
    private val http = FakeUpdateHttp().apply { answers += ReleasesResponse.Fresh(ghList(ghRelease("v2.0.0-beta.2")), "\"e1\"") }
    private val work = FakeUpdateWork()
    private val installer = FakeInstaller()
    private val dir by lazy { tmp.newFolder("updates") }
    private val updates by lazy { testUpdateService(dir, http = http, work = work, clock = clock, installer = installer) }
    private val vms = TestViewModels()

    private fun vm() = vms.track(UpdatesViewModel(updates, clock))

    @After fun stop() = vms.stopAll()

    @Test
    fun `opening the screen checks when one is due, and Check now always asks`() = runTest {
        val vm = vm()
        assertEquals(UpdateStatus.Available("2.0.0-beta.2", "9.0 MB", skipped = false), vm.ui.first { it.status != UpdateStatus.UpToDate }.status)
        assertEquals(1, http.checks.size)
        vm.checkNow().join()
        assertEquals(2, http.checks.size)
    }

    @Test
    fun `Download asks for a person's download, and Skip this version skips it`() = runTest {
        val vm = vm()
        vm.ui.first { it.status is UpdateStatus.Available }
        vm.download().join()
        assertEquals("download 2.0.0-beta.2 user", work.calls.last())
        vm.skip().join()
        assertEquals(UpdateStatus.Available("2.0.0-beta.2", "9.0 MB", skipped = true), vm.ui.first { (it.status as? UpdateStatus.Available)?.skipped == true }.status)
    }

    @Test
    fun `Beta updates off on a beta build stays on it (R145)`() = runTest {
        val vm = vm()
        assertTrue(vm.ui.first { it.loaded }.beta)
        vm.setBeta(false).join()
        val ui = vm.ui.first { it.loaded && !it.beta }
        assertEquals("You'll stay on 2.0.0-beta.1 until the next full release.", ui.betaLine)
        assertEquals(UpdateStatus.UpToDate, ui.status)
    }

    @Test
    fun `Install without Android's permission opens its switch, and with it hands the APK over (R138)`() = runTest {
        val vm = vm()
        vm.ui.first { it.status is UpdateStatus.Available }
        File(dir, "ledga-2.0.0-beta.2.apk").writeText("apk")
        clock.instant = clock.instant.plusSeconds(60) // a check at a new time re-reads the state (as a finished download does in the app)
        vm.checkNow().join()
        installer.allowed = false
        vm.refresh()
        assertFalse(vm.ui.first { it.status is UpdateStatus.Ready && !it.canInstall }.canInstall)
        var asked = 0
        vm.install { asked++ }.join()
        assertEquals(1, asked)
        installer.allowed = true
        vm.install { asked++ }.join()
        assertEquals(1, asked)
        assertEquals(1, installer.installed.size)
    }
}
