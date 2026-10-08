package com.ledga.app.ui.update

import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.ui.app.ShellFrame
import com.ledga.core.update.NotesSection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R142: Version history, with one release's notes open, and with nothing to show. Synthetic releases. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class VersionHistoryScreensTest {
    private val ui = VersionHistoryUi(
        loaded = true,
        rows = listOf(
            HistoryRow(
                "v2.0.0-beta.2", "2.0.0-beta.2", "4 Oct 2026 · Beta", installed = false, beta = true,
                notes = listOf(NotesSection("What's new", listOf("Search finds a payment by its amount")), NotesSection("Fixes", listOf("Fuliza's due date no longer shows a day early"))),
            ),
            HistoryRow("v2.0.0-beta.1", "2.0.0-beta.1", "1 Oct 2026 · Beta", installed = true, beta = true, notes = listOf(NotesSection("What's new", listOf("A new Ledga")))),
            HistoryRow("v1.6.0", "1.6.0", "29 Sep 2026", installed = false, beta = false, notes = emptyList()),
        ),
        expanded = setOf("v2.0.0-beta.2"),
    )

    @Test
    fun history() = snapScreen("version_history") { ShellFrame(null, onSelect = {}) { VersionHistoryContent(ui, VersionHistoryActions()) } }

    @Test
    fun historyLandscape() = snapScreenLandscape("version_history") { ShellFrame(null, onSelect = {}) { VersionHistoryContent(ui, VersionHistoryActions()) } }

    @Test
    fun empty() = snapScreen("version_history_empty") {
        ShellFrame(null, onSelect = {}) {
            VersionHistoryContent(VersionHistoryUi(loaded = true, failureLine = UpdateText.failureLine(com.ledga.app.data.update.CheckFailure.OFFLINE)), VersionHistoryActions())
        }
    }
}
