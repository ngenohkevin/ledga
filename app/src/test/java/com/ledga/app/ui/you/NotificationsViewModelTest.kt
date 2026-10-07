package com.ledga.app.ui.you

import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.testing.FakeBackgroundWork
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.OFF_IN_SETTINGS
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
    private val work = FakeBackgroundWork()

    private fun vm() = vms.track(NotificationsViewModel(settings, NotificationAccess { ask }, work))

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

    @Test
    fun `with notifications off in Android's settings the banner shows, and Turn on opens the settings (R109)`() = runTest {
        val vm = vms.track(NotificationsViewModel(settings, OFF_IN_SETTINGS, work))
        val ui = vm.ui.first { it.loaded }
        assertFalse(ui.allowed)
        assertTrue(ui.toSettings)
    }

    @Test
    fun `a changed switch or time moves its own notification and nothing else (R107)`() = runTest {
        val vm = vm()
        vm.setDailyMinute(19 * 60)
        vm.setWeekly(false)
        vm.setFuliza(false)
        vm.setLarge(true)
        vm.setThreshold(750_000)
        vm.ui.first { it.settings.largeThresholdCents == 750_000L }
        assertEquals(listOf("DAILY on replace=true", "WEEKLY off replace=true", "FULIZA off replace=true"), work.scheduled)
    }
}
