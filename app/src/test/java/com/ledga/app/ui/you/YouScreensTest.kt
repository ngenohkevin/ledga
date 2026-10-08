package com.ledga.app.ui.you

import com.ledga.app.testing.BUSINESS
import com.ledga.app.testing.PERSONAL
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.ui.app.ShellFrame
import com.ledga.app.ui.app.Tab
import com.ledga.app.ui.design.components.SheetScaffold
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec §15.2: You (mockup `you`; layout and wording only, synthetic values), without a name, the rescan sheet, landscape. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class YouScreensTest {
    private val ui = YouUi(
        loaded = true,
        name = "Amani",
        payments = 6_385,
        since = LocalDate.parse("2024-03-14"),
        lines = listOf(PERSONAL, BUSINESS),
        notifications = "Daily 8 PM · Weekly Sun · Fuliza",
        appearance = "System theme · Default text",
        unreadable = 2,
        version = "2.0.0-beta.1",
        backup = "Snapshot saved today, 8:12 PM",
        updates = "v2.0.0-beta.1 · up to date",
        beta = true,
    )

    @Test
    fun you() = snapScreen("you") { ShellFrame(Tab.YOU, onSelect = {}) { YouContent(ui, YouActions()) } }

    @Test
    fun noName() = snapScreen("you_no_name") { ShellFrame(Tab.YOU, onSelect = {}) { YouContent(ui.copy(name = null, payments = 0, since = null, lines = emptyList(), unreadable = 0), YouActions()) } }

    @Test
    fun rescan() = snapScreen("rescan_sheet") { SheetScaffold("Rescan SMS inbox") { RescanContent(onStart = {}) } }

    @Test
    fun landscape() = snapScreenLandscape("you") { ShellFrame(Tab.YOU, onSelect = {}) { YouContent(ui, YouActions()) } }
}
