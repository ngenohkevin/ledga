package com.ledga.app.testing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing

const val SPECIMEN_TAG = "specimen"

/** A Robolectric window big enough for a 2×2 grid of 344 dp cells. Use as `@Config(qualifiers = SPECIMEN_QUALIFIERS)`. */
const val SPECIMEN_QUALIFIERS = "w720dp-h2400dp-hdpi"

/**
 * One screenshot per component group (spec §15.2, refinement R22): light | dark across, font scale 1.0 over 1.3,
 * animations off. Each cell is a canvas-coloured box with screen padding.
 */
@Composable
fun SpecimenGrid(cellWidth: Dp = 344.dp, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    Column(
        Modifier.testTag(SPECIMEN_TAG).background(Color(0xFF8A8F8D)).padding(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        for (fontScale in listOf(1f, 1.3f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                for (appearance in listOf(Appearance.LIGHT, Appearance.DARK)) {
                    CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                        LedgaTheme(appearance = appearance, reducedMotion = true) {
                            Box(Modifier.width(cellWidth).background(LedgaTheme.colors.canvas).padding(Spacing.l)) {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Renders [content] in a [SpecimenGrid] and captures it to `src/test/screenshots/design/<name>.png`. */
fun ComposeContentTestRule.snap(name: String, cellWidth: Dp = 344.dp, content: @Composable () -> Unit) {
    setContent { SpecimenGrid(cellWidth, content) }
    onNodeWithTag(SPECIMEN_TAG).captureRoboImage("src/test/screenshots/design/$name.png")
}
