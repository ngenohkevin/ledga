package com.ledga.app.ui.design.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Owner report (2026-10-09): scrolling up in the transaction sheet, "the screen jumps up and down repeatedly". An upward
 * flick the content had left over went to M3's sheet, which settled with it although it was already open all the way:
 * the spring carried it past the top and back, once per flick.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h640dp-xxhdpi")
class ModalSheetFlingTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var sheet: SheetState
    private lateinit var scroll: ScrollState

    private fun show(rows: Int) {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = false) {
                sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                LedgaModalSheet(onDismiss = {}, title = null, sheetState = sheet) {
                    scroll = rememberScrollState()
                    Column(Modifier.verticalScroll(scroll)) { repeat(rows) { Text("Row $it", Modifier.height(48.dp)) } }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Drags the content [steps] times by [stepPx] and lets go mid-move (a flick); the sheet's offset over the frames after. */
    private fun flick(stepPx: Float, steps: Int = 25): List<Float> {
        val node = compose.onNode(hasScrollAction(), useUnmergedTree = true)
        compose.mainClock.autoAdvance = false
        node.performTouchInput { down(center) }
        repeat(steps) {
            node.performTouchInput { moveBy(Offset(0f, stepPx)) }
            compose.mainClock.advanceTimeByFrame()
        }
        node.performTouchInput { up() }
        return List(40) {
            compose.mainClock.advanceTimeByFrame()
            sheet.requireOffset()
        }
    }

    @Test
    fun `a flick up to the end of the content leaves an open sheet where it is`() {
        show(rows = 16) // taller than the screen, short enough for one flick to reach the end
        val open = sheet.requireOffset()
        val after = flick(-40f)
        assertEquals(scroll.maxValue, scroll.value, "the content scrolled to its end")
        assertTrue(after.all { it >= open - 0.5f }, "the sheet rose above its open place: least offset ${after.min()} vs $open")
    }

    @Test
    fun `a flick up on content that fits leaves the sheet where it is`() {
        show(rows = 3)
        val open = sheet.requireOffset()
        val after = flick(-40f)
        assertTrue(after.all { it >= open - 0.5f }, "the sheet rose above its open place: least offset ${after.min()} vs $open")
    }

    @Test
    fun `a flick down at the top of the content still closes the sheet`() {
        show(rows = 30)
        flick(40f)
        repeat(40) { compose.mainClock.advanceTimeByFrame() }
        assertEquals(SheetValue.Hidden, sheet.currentValue)
    }
}
