package com.ledga.app.ui.update

import com.ledga.app.data.update.ReleasesResponse
import com.ledga.app.testing.FakeUpdateHttp
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.TestViewModels
import com.ledga.app.testing.ghList
import com.ledga.app.testing.ghRelease
import com.ledga.app.testing.testUpdateService
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** R142: opening Version history fetches the list when due; a tap opens a release's notes. */
class VersionHistoryViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val clock = MutableClock(Instant.parse("2026-10-07T06:00:00Z"))
    private val http = FakeUpdateHttp()
    private val vms = TestViewModels()

    private fun vm() = vms.track(VersionHistoryViewModel(testUpdateService(tmp.newFolder("updates"), http = http, clock = clock)))

    @After fun stop() = vms.stopAll()

    @Test
    fun `opening it fetches the list, and a tap opens and closes a release's notes`() = runTest {
        http.answers += ReleasesResponse.Fresh(ghList(ghRelease("v2.0.0-beta.2"), ghRelease("v2.0.0-beta.1")), null)
        val vm = vm()
        assertEquals(2, vm.ui.first { it.rows.isNotEmpty() }.rows.size)
        vm.toggle("v2.0.0-beta.2")
        assertEquals(setOf("v2.0.0-beta.2"), vm.ui.first { it.expanded.isNotEmpty() }.expanded)
        vm.toggle("v2.0.0-beta.2")
        assertTrue(vm.ui.first { it.expanded.isEmpty() }.expanded.isEmpty())
    }

    @Test
    fun `Try again asks GitHub even within 6 hours`() = runTest {
        val vm = vm()
        vm.ui.first { it.loaded && !it.checking }
        vm.retry().join()
        assertEquals(2, http.checks.size)
    }
}
