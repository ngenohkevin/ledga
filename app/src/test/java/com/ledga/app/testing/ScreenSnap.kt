package com.ledga.app.testing

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.size
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme

/**
 * Phase 4 screens (spec §15.2): one file per appearance × font scale in a 360×800 dp window, with animations off.
 * Writes `src/test/screenshots/screens/<name>_<light|dark>_<100|130>.png`. Call it from a
 * `@GraphicsMode(NATIVE)` Robolectric test; it launches its own activity, so don't use a compose rule.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun snapScreen(name: String, content: @Composable () -> Unit) {
    for (appearance in listOf(Appearance.LIGHT, Appearance.DARK)) {
        for ((scale, tag) in listOf(1f to "100", 1.3f to "130")) {
            captureRoboImage(
                filePath = "src/test/screenshots/screens/${name}_${appearance.name.lowercase()}_$tag.png",
                roborazziComposeOptions = RoborazziComposeOptions {
                    size(widthDp = 360, heightDp = 800)
                    fontScale(scale)
                },
            ) {
                LedgaTheme(appearance = appearance, reducedMotion = true) { content() }
            }
        }
    }
}
