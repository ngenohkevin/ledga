package com.ledga.app.ui.you

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class YouBehaviourTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the profile and every row open what they name, and Version opens nothing`() {
        val opened = mutableListOf<String>()
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                YouContent(
                    YouUi(loaded = true, name = "Amani", version = "2.0.0-beta.1"),
                    YouActions(
                        onProfile = { opened += "profile" },
                        onLines = { opened += "lines" },
                        onPeople = { opened += "people" },
                        onNotifications = { opened += "notifications" },
                        onAppearance = { opened += "appearance" },
                        onRescan = { opened += "rescan" },
                        onUnreadable = { opened += "unreadable" },
                        onHistoryCheck = { opened += "history" },
                        onLicences = { opened += "licences" },
                    ),
                )
            }
        }
        listOf(
            "Amani", "M-Pesa lines", "People", "Notifications", "Appearance",
            "Rescan SMS inbox", "Messages Ledga couldn't read", "History check", "Open-source licences",
        ).forEach { compose.onNodeWithText(it).performScrollTo().performClick() }
        assertEquals(listOf("profile", "lines", "people", "notifications", "appearance", "rescan", "unreadable", "history", "licences"), opened)
        compose.onNodeWithText("Version").performScrollTo().assertHasNoClickAction()
    }
}
