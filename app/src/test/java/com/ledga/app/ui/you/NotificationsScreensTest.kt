package com.ledga.app.ui.you

import com.ledga.app.data.settings.Settings
import com.ledga.app.testing.snapScreen
import com.ledga.app.ui.app.ShellFrame
import com.ledga.app.ui.design.components.SheetScaffold
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec §15.2: You → Notifications (not mocked; R75), allowed and not, and the time sheet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class NotificationsScreensTest {
    private val settings = Settings(notifyLarge = true, largeThresholdCents = 750_000)

    @Test
    fun allowed() = snapScreen("notifications") { ShellFrame(null, onSelect = {}) { NotificationsContent(NotificationsUi(true, settings, allowed = true), NotificationsActions()) } }

    @Test
    fun notAllowed() = snapScreen("notifications_off") { ShellFrame(null, onSelect = {}) { NotificationsContent(NotificationsUi(true, Settings(), allowed = false), NotificationsActions()) } }

    @Test
    fun time() = snapScreen("notifications_time") { SheetScaffold("Summary time") { SummaryTimeContent(minute = 20 * 60, onSave = {}) } }
}
