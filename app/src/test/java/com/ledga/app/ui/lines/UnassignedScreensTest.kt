package com.ledga.app.ui.lines

import com.ledga.app.testing.BUSINESS
import com.ledga.app.testing.PERSONAL
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.ui.app.ShellFrame
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec §15.2: Not on a line (not mocked; R116, R128). Synthetic values. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class UnassignedScreensTest {
    private val ui = UnassignedUi(
        loaded = true,
        unassigned = 1_231,
        lines = listOf(PERSONAL, BUSINESS),
        counting = false,
        shares = listOf(PlacedShare("Personal ··11", 812), PlacedShare("Business ··78", 378)),
        placeable = 1_190,
        left = 41,
        rangeLine = PERSONAL.id,
        from = LocalDate.parse("2025-01-01"),
        to = LocalDate.parse("2025-06-30"),
        inRange = 37,
        today = LocalDate.parse("2026-10-07"),
    )

    @Test
    fun unassigned() = snapScreen("unassigned") { ShellFrame(null, onSelect = {}) { UnassignedContent(ui, UnassignedActions()) } }

    @Test
    fun unassignedLandscape() = snapScreenLandscape("unassigned") { ShellFrame(null, onSelect = {}) { UnassignedContent(ui, UnassignedActions()) } }
}
