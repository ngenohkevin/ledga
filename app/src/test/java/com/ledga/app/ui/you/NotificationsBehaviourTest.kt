package com.ledga.app.ui.you

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ledga.app.data.settings.Settings
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
class NotificationsBehaviourTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `each notification is a switch, and the time and amount rows show only while theirs is on`() {
        val daily = mutableListOf<Boolean>()
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                NotificationsContent(NotificationsUi(true, Settings(notifyDaily = true, notifyLarge = false), allowed = true), NotificationsActions(onDaily = { daily += it }))
            }
        }
        compose.onNodeWithText("Summary time").assertExists()
        compose.onNodeWithText("Large payment from").assertDoesNotExist()
        compose.onNodeWithText("Daily summary").performClick()
        assertEquals(listOf(false), daily)
    }

    @Test
    fun `Save waits for an amount in range`() {
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { ThresholdContent("50", onText = {}, onSave = {}) } }
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }
}
