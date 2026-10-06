package com.ledga.app.ui.you

import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.TestViewModels
import com.ledga.app.ui.onboarding.NotificationAccess
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val settings = SettingsStore(FakePrefsStore())
    private var ask = false
    private val vms = TestViewModels()

    private fun vm() = vms.track(NotificationsViewModel(settings, NotificationAccess { ask }))

    @After fun close() = vms.stopAll()

    @Test
    fun `the switches, the time and the threshold save as Phase 5 will read them`() = runTest {
        val vm = vm()
        vm.setDaily(false)
        vm.setLarge(true)
        vm.setThreshold(750_000)
        vm.setDailyMinute(19 * 60)
        val s = vm.ui.first { it.settings.largeThresholdCents == 750_000L && it.settings.dailySummaryMinute == 19 * 60 }.settings
        assertFalse(s.notifyDaily)
        assertTrue(s.notifyLarge)
    }

    @Test
    fun `not allowed shows the banner, a refusal for good sends the next tap to Settings, and coming back allowed clears it`() = runTest {
        ask = true
        val vm = vm()
        assertFalse(vm.ui.first { it.loaded }.allowed)
        vm.onPermissionResult(granted = false, showRationale = false)
        assertTrue(vm.ui.first { it.toSettings }.toSettings)
        ask = false
        vm.refresh()
        val ui = vm.ui.first { it.allowed }
        assertFalse(ui.toSettings)
        assertEquals(true, ui.allowed)
    }
}
