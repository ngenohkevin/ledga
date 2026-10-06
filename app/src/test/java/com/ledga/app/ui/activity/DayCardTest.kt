package com.ledga.app.ui.activity

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.ledga.app.testing.txRow
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * A day card's rounded bottom (R40) continues the card's edge: its end cap is as tall as the corner, so the corner's
 * arc starts where the last row ends and the border never steps inward. Pixels come from a software draw of the window.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class DayCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val complete = LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(true))

    @Test
    fun `the card's edge runs straight from its last row into the rounded end`() {
        var surface = 0
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                surface = LedgaTheme.colors.surface.toArgb()
                val items = remember { flowOf(PagingData.from(listOf(txRow(at = Instant.parse("2026-10-06T11:15:00Z"))), complete).toActivityItems()) }
                    .collectAsLazyPagingItems()
                TransactionsPane(TransactionsUi(today = LocalDate.parse("2026-10-06")), items, TransactionsActions())
            }
        }
        val row = compose.onNodeWithContentDescription("Ksh 1,000 spent at KPLC Prepaid, today 2:15 PM").fetchSemanticsNode().boundsInWindow
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { root.draw(Canvas(bitmap)) }

        /** Where the card's surface starts, scanning in from the left at [y]. */
        fun surfaceStart(y: Int): Int = (0 until row.center.x.toInt()).first { x ->
            val p = bitmap.getPixel(x, y)
            abs((p shr 16 and 0xFF) - (surface shr 16 and 0xFF)) + abs((p shr 8 and 0xFF) - (surface shr 8 and 0xFF)) + abs((p and 0xFF) - (surface and 0xFF)) < 6
        }
        val aboveJunction = surfaceStart(row.bottom.toInt() - 3)
        val belowJunction = surfaceStart(row.bottom.toInt() + 3)
        assertTrue(belowJunction - aboveJunction <= 1, "the edge steps in by ${belowJunction - aboveJunction} px where the end cap starts")
    }
}
