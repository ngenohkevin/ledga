package com.ledga.app.ui.you

import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.data.settings.TextSize
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.TestViewModels
import com.ledga.app.ui.design.theme.Appearance
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppearanceViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val settings = SettingsStore(FakePrefsStore())
    private val vms = TestViewModels()

    @After fun close() = vms.stopAll()

    @Test
    fun `a theme and a text size save at once, and the screen shows them`() = runTest {
        val vm = vms.track(AppearanceViewModel(settings))
        vm.setAppearance(Appearance.DARK)
        vm.setTextSize(TextSize.LARGE)
        val ui = vm.ui.first { it.appearance == Appearance.DARK && it.textSize == TextSize.LARGE }
        assertEquals(Appearance.DARK, settings.current().appearance)
        assertEquals(TextSize.LARGE, ui.textSize)
    }

    @Test
    fun `labels read plainly`() {
        assertEquals("System theme · Default text", YouText.appearanceLine(Appearance.SYSTEM, TextSize.SYSTEM))
        assertEquals("Dark theme · Extra large text", YouText.appearanceLine(Appearance.DARK, TextSize.EXTRA_LARGE))
    }
}
