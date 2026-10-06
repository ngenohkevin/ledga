package com.ledga.app.ui.lines

import com.ledga.app.data.lines.LineChoice
import com.ledga.app.testing.BUSINESS
import com.ledga.app.testing.PERSONAL
import com.ledga.app.testing.snapScreen
import com.ledga.app.ui.design.components.SheetScaffold
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The line switcher (R47, not mocked), light/dark × 1.0/1.3. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class LineSwitcherScreensTest {
    @Test
    fun switcher() = snapScreen("line_switcher") {
        SheetScaffold("Choose a line") { LineSwitcherContent(LineChoice(listOf(PERSONAL, BUSINESS), selectedId = 2), onSelect = {}) }
    }
}
