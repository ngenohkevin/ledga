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

/** Spec §13.4: You → Updates in its main states. Synthetic versions and notes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class UpdatesScreensTest {
    private val notes = listOf(
        NotesSection("What's new", listOf("Search finds a payment by its amount", "Trackers show a whole year at a glance")),
        NotesSection("Fixes", listOf("Fuliza's due date no longer shows a day early")),
    )
    private val available = UpdatesUi(
        loaded = true,
        installed = "2.0.0-beta.1",
        status = UpdateStatus.Available("2.0.0-beta.2", "9.0 MB", skipped = false),
        notes = notes,
        pageUrl = "https://example.test/releases/v2.0.0-beta.2",
        checkedLine = "Checked today, 9:12 AM",
        beta = true,
        betaLine = UpdateText.BETA_ON,
    )

    @Test
    fun available() = snapScreen("updates_available") { ShellFrame(null, onSelect = {}) { UpdatesContent(available, UpdatesActions()) } }

    @Test
    fun availableLandscape() = snapScreenLandscape("updates_available") { ShellFrame(null, onSelect = {}) { UpdatesContent(available, UpdatesActions()) } }

    @Test
    fun readyWithoutPermission() = snapScreen("updates_ready") {
        ShellFrame(null, onSelect = {}) {
            UpdatesContent(available.copy(status = UpdateStatus.Ready("2.0.0-beta.2"), canInstall = false), UpdatesActions())
        }
    }

    @Test
    fun upToDate() = snapScreen("updates_up_to_date") {
        ShellFrame(null, onSelect = {}) {
            UpdatesContent(
                available.copy(status = UpdateStatus.UpToDate, notes = emptyList(), pageUrl = null, beta = false, betaLine = UpdateText.BETA_OFF, installed = "2.0.0"),
                UpdatesActions(),
            )
        }
    }
}
