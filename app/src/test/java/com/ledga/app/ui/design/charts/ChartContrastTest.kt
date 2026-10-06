package com.ledga.app.ui.design.charts

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.ChartTones
import com.ledga.app.ui.design.tokens.Contrast
import com.ledga.app.ui.design.tokens.LedgaColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertTrue

/** R27 at the pixel: the charts draw unselected bars in the soft tone and the selected/highlighted bar in the strong one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartContrastTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * The pixel at ([fx], [fy]) of the node described by [desc], from a software draw of the window (Roborazzi's way).
     * Compose's captureToImage uses PixelCopy, which never completes under Robolectric's software rendering.
     */
    private fun pixel(desc: String, fx: Float, fy: Float): Color {
        val bounds = compose.onNodeWithContentDescription(desc).fetchSemanticsNode().boundsInWindow
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { root.draw(Canvas(bitmap)) }
        return Color(bitmap.getPixel((bounds.left + bounds.width * fx).toInt(), (bounds.top + bounds.height * fy).toInt()))
    }

    private fun assertClose(expected: Color, actual: Color, what: String) {
        val off = listOf(expected.red - actual.red, expected.green - actual.green, expected.blue - actual.blue).maxOf { abs(it) }
        assertTrue(off <= 2f / 255f, "$what: expected $expected, drew $actual")
    }

    @Test
    fun `a dimmed column chart draws unselected bars soft and the selected bar strong, in both themes`() {
        var appearance by mutableStateOf(Appearance.LIGHT)
        lateinit var colors: LedgaColors
        compose.setContent {
            LedgaTheme(appearance, reducedMotion = true) {
                colors = LedgaTheme.colors
                Box(Modifier.background(colors.surface).padding(16.dp)) {
                    ColumnChart(
                        listOf(Bar("A", listOf(100L), "bar a"), Bar("B", listOf(100L), "bar b")),
                        listOf(colors.chartPrimary),
                        summary = "two bars",
                        selectedIndex = 1,
                        onSelect = {},
                        dimUnselected = true,
                    )
                }
            }
        }
        for (a in listOf(Appearance.LIGHT, Appearance.DARK)) {
            compose.runOnIdle { appearance = a }
            compose.waitForIdle()
            val soft = pixel("bar a", 0.5f, 0.5f)
            val strong = pixel("bar b", 0.5f, 0.5f)
            assertClose(ChartTones.soft(colors.chartPrimary, colors.surface), soft, "$a unselected")
            assertClose(ChartTones.strong(colors.chartPrimary, colors.surface), strong, "$a selected")
            assertTrue(Contrast.ratio(soft, colors.surface) >= 3.0, "$a unselected bar vs card")
        }
    }

    @Test
    fun `mini and spark bars draw normal bars soft and the highlighted bar strong`() {
        lateinit var colors: LedgaColors
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                colors = LedgaTheme.colors
                Column(Modifier.background(colors.surface).padding(16.dp).width(200.dp)) {
                    MiniBars(listOf(100L, 100L), highlightIndex = 1, contentDescription = "mini")
                    Box(Modifier.semantics { contentDescription = "spark" }) { SparkBars(listOf(100L, 100L), colors.chartPrimary) }
                }
            }
        }
        assertClose(ChartTones.soft(colors.chartPrimary, colors.surface), pixel("mini", 0.25f, 0.5f), "mini normal")
        assertClose(ChartTones.strong(colors.chartPrimary, colors.surface), pixel("mini", 0.75f, 0.5f), "mini highlight")
        assertClose(ChartTones.soft(colors.chartPrimary, colors.surface), pixel("spark", 0.25f, 0.5f), "spark normal")
    }
}
