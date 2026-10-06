package com.ledga.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Canary for the screenshot toolchain: Compose renders on Robolectric native graphics and Roborazzi captures it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ScreenshotSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a composable renders on native graphics and can be captured`() {
        compose.setContent {
            Box(
                Modifier.size(120.dp).background(Color(0xFF0A6B4B)).testTag("smoke"),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Ledga", style = TextStyle(color = Color.White, fontSize = 20.sp))
            }
        }
        compose.onNodeWithTag("smoke").assertWidthIsEqualTo(120.dp).assertHeightIsEqualTo(120.dp)
        compose.onNodeWithTag("smoke").captureRoboImage("src/test/screenshots/smoke.png")
    }
}
