package com.ledga.app.ui.you

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.ledga.app.ui.app.ShellFrame
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R80, owner ruling M5: every licence can be reached, also on a phone held sideways at large text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h360dp-xhdpi")
class LicencesBehaviourTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `in landscape at 1_3x the last licence can be scrolled to`() {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 1.3f)) {
                LedgaTheme(Appearance.LIGHT, reducedMotion = true) { ShellFrame(null, onSelect = {}) { LicencesContent(onBack = {}, onOpen = {}) } }
            }
        }
        compose.onNodeWithText(Licences.ALL.last().name).performScrollTo().assertIsDisplayed()
    }
}
