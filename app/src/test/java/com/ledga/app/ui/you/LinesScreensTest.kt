package com.ledga.app.ui.you

import com.ledga.app.testing.BUSINESS
import com.ledga.app.testing.PERSONAL
import com.ledga.app.testing.snapScreen
import com.ledga.app.ui.app.ShellFrame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec §15.2: You → M-Pesa lines (not mocked; R65), with the phone-access banner. Synthetic lines. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class LinesScreensTest {
    private val ui = LinesUi(
        loaded = true,
        lines = listOf(LineUi(PERSONAL, "Personal ··11", 1_240), LineUi(BUSINESS, "Business ··78", 312)),
        unattributed = 12,
        phoneAccess = false,
    )

    @Test
    fun lines() = snapScreen("lines") { ShellFrame(null, onSelect = {}) { LinesContent(ui, LinesActions()) } }
}
