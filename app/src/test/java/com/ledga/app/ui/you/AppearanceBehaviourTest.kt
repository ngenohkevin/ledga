package com.ledga.app.ui.you

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ledga.app.data.settings.TextSize
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
class AppearanceBehaviourTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `each choice is a radio button, and a tap picks it`() {
        val themes = mutableListOf<Appearance>()
        val sizes = mutableListOf<TextSize>()
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                AppearanceContent(AppearanceUi(true, Appearance.SYSTEM, TextSize.SYSTEM), AppearanceActions(onTheme = { themes += it }, onTextSize = { sizes += it }))
            }
        }
        compose.onNodeWithText("System").assertIsSelected()
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithText("Extra large").performScrollTo().performClick()
        assertEquals(listOf(Appearance.DARK), themes)
        assertEquals(listOf(TextSize.EXTRA_LARGE), sizes)
    }
}
