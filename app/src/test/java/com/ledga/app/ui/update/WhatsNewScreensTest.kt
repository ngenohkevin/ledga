package com.ledga.app.ui.update

import com.ledga.app.testing.snapScreen
import com.ledga.app.ui.design.components.SheetScaffold
import com.ledga.core.update.NotesSection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R141: What's new, once after an update. Synthetic notes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class WhatsNewScreensTest {
    @Test
    fun whatsNew() = snapScreen("whats_new_sheet") {
        SheetScaffold(title = "What's new in Ledga 2.0.0-beta.2") {
            WhatsNewContent(
                listOf(
                    NotesSection("What's new", listOf("Search finds a payment by its amount", "Trackers show a whole year at a glance")),
                    NotesSection("Fixes", listOf("Fuliza's due date no longer shows a day early")),
                ),
                onDone = {},
            )
        }
    }
}
