package com.ledga.app.ui.you

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class LinesBehaviourTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a line opens its rename, and Allow asks for phone access`() {
        val renamed = mutableListOf<Long>()
        var allow = 0
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                LinesContent(LinesUi(true, listOf(LineUi(PERSONAL, "Personal ··11", 3)), phoneAccess = false), LinesActions(onRename = { renamed += it.line.id }, onAllowPhone = { allow++ }))
            }
        }
        compose.onNodeWithText("Personal ··11").performClick()
        compose.onNodeWithText("Allow").performClick()
        assertEquals(listOf(PERSONAL.id), renamed)
        assertEquals(1, allow)
    }

    @Test
    fun `a refused name says why`() {
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { LineNameContent("", refused = true, onName = {}, onSave = {}) } }
        compose.onNodeWithText("A line needs a name.").assertIsDisplayed()
    }
}
