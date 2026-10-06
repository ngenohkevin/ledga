package com.ledga.app.ui.you

import com.ledga.app.data.settings.TextSize
import com.ledga.app.testing.snapScreen
import com.ledga.app.ui.app.ShellFrame
import com.ledga.app.ui.design.theme.Appearance
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class AppearanceScreensTest {
    @Test
    fun appearance() = snapScreen("appearance") { ShellFrame(null, onSelect = {}) { AppearanceContent(AppearanceUi(true, Appearance.SYSTEM, TextSize.SYSTEM), AppearanceActions()) } }
}
