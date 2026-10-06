package com.ledga.app.ui.lines

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ledga.app.data.lines.LineChoice
import com.ledga.app.testing.BUSINESS
import com.ledga.app.testing.PERSONAL
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
class LineSwitcherBehaviourTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the chosen line is ticked and All lines can be picked`() {
        val picked = mutableListOf<Long?>()
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                LineSwitcherContent(LineChoice(listOf(PERSONAL, BUSINESS), selectedId = 2), onSelect = { picked += it })
            }
        }
        compose.onNodeWithText("Business ··78").assertIsSelected()
        compose.onNodeWithText("All lines").performClick()
        assertEquals(listOf<Long?>(null), picked)
    }

    @Test
    fun `with one line there is no line chip at all`() {
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { LinePicker(LineChoice(listOf(PERSONAL), selectedId = 1), onSelect = {}) } }
        compose.onNodeWithText("Personal ··11").assertDoesNotExist()
        compose.onNodeWithText("All lines").assertDoesNotExist()
    }
}
