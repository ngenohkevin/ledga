package com.ledga.app.ui.design.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import kotlin.test.assertNotEquals

/**
 * Phase 3 deferred M16: a tappable row's ripple and pressed colour stay inside the day card's rounded corners.
 * Pixels come from a software draw of the window (`app/DESIGN.md`: captureToImage times out on Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class CardSegmentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a card's last row keeps whatever it draws inside the rounded bottom corners`() {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                Box(Modifier.background(LedgaTheme.colors.canvas).padding(16.dp)) {
                    // Red stands in for a pressed row: drawn after the segment, as a clickable's indication is.
                    Box(Modifier.testTag("row").size(200.dp, 60.dp).cardSegment(Segment.Bottom).background(Color.Red))
                }
            }
        }
        val b = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInWindow
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { root.draw(Canvas(bitmap)) }
        val middle = bitmap.getPixel(b.center.x.toInt(), b.center.y.toInt())
        val corner = bitmap.getPixel(b.left.toInt() + 1, b.bottom.toInt() - 2)
        assertEquals(Color.Red.toArgb(), middle, "the row itself still draws")
        assertNotEquals(Color.Red.toArgb(), corner, "the bottom-left corner lies outside the card's 24 dp rounding")
    }
}
