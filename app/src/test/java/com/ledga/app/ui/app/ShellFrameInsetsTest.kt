package com.ledga.app.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** Owner ruling M5 (2026-10-06): landscape is supported, and no tab screen sits under the cutout or a side nav bar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h360dp-xhdpi")
class ShellFrameInsetsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `tab screens stay clear of a landscape cutout and a side navigation bar`() {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                ShellFrame(Tab.ACTIVITY, onSelect = {}, horizontalInsets = WindowInsets(left = 30.dp, right = 12.dp)) {
                    Box(Modifier.fillMaxSize().testTag("screen"))
                }
            }
        }
        val screen = compose.onNodeWithTag("screen").getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertEquals(30.dp, screen.left)
        assertEquals(root.right - 12.dp, screen.right)
    }
}
