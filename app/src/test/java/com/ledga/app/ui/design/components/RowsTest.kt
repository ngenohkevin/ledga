package com.ledga.app.ui.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measurement; legacy graphics measure 1 px per char
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class RowsTest {
    @get:Rule val compose = createComposeRule()

    private val longName = "A VERY LONG MERCHANT NAME THAT KEEPS GOING SUPERMARKET LIMITED"
    private val speech = "Ksh 1,234,567.89 spent at A very long merchant, today 7:12 PM"

    private fun showRow(fontScale: Float, events: MutableList<String> = mutableListOf()) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                    Box(Modifier.width(360.dp)) {
                        TxRow(
                            leading = Leading.Icon("fluent_shopping_cart"),
                            title = longName,
                            subtitle = "Groceries · 7:12 PM",
                            amountCents = 123_456_789,
                            inflow = false,
                            speech = speech,
                            balanceText = "Bal 4,231.50",
                            onClick = { events += "click" },
                            onLongClick = { events += "long" },
                        )
                    }
                }
            }
        }
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    @Test
    fun `at font scale 2 the amount never truncates and the name ellipsizes`() {
        showRow(fontScale = 2f)
        val amountText = AmountFormat.signed(123_456_789, inflow = false)
        val amount = layoutOf(amountText)
        // hasVisualOverflow is no oracle here: softWrap = false lays the paragraph out at the slot width.
        assertEquals(1, amount.lineCount)
        assertFalse(amount.isLineEllipsized(0))
        assertTrue(amount.size.width >= ceil(amount.multiParagraph.intrinsics.maxIntrinsicWidth).toInt(), "amount squeezed")
        val right = compose.onNodeWithText(amountText, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.right
        assertTrue(right <= with(compose.density) { 360.dp.toPx() }, "amount runs past the row")
        assertTrue(layoutOf(longName).isLineEllipsized(0))
    }

    @Test
    fun `TalkBack reads one phrase for the whole row, which opens on tap and long-press`() {
        val events = mutableListOf<String>()
        showRow(fontScale = 1f, events)
        compose.onNodeWithContentDescription(speech).assertHasClickAction().performClick()
        compose.onNodeWithContentDescription(speech).performTouchInput { longClick() }
        assertEquals(listOf("click", "long"), events)
    }

    @Test
    fun `a toggle row is a single switch for TalkBack`() {
        var on by mutableStateOf(false)
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                ListRow("Beta updates", subtitle = "Get pre-releases", trailing = RowTrailing.Toggle(on) { on = it })
            }
        }
        compose.onNode(isToggleable() and hasText("Beta updates")).assertIsOff().performClick()
        compose.onNode(isToggleable() and hasText("Beta updates")).assertIsOn()
        assertTrue(on)
    }

    @Test
    fun `a day header is a heading`() {
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { DayHeader("TODAY", 450_000, 500_000) } }
        compose.onNode(isHeading() and hasText("TODAY")).assertExists()
        compose.onNodeWithText("Out 4,500 · In 5,000", useUnmergedTree = true).assertExists()
    }
    @Test
    fun `the time stays whole while the category ellipsizes - 1_3x, 328 dp row with a balance`() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 1.3f)) {
                LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                    Box(Modifier.width(328.dp)) {
                        TxRow(
                            leading = Leading.Icon("fluent_shopping_cart"),
                            title = "Naivas Supermarket Westlands",
                            subtitle = "Fuliza Ksh 300 · Groceries",
                            amountCents = 85_000,
                            inflow = false,
                            speech = "s",
                            balanceText = "Bal 104,231.50",
                            subtitleTail = "9:15 AM",
                        )
                    }
                }
            }
        }
        val tail = layoutOf(" · 9:15 AM")
        assertEquals(1, tail.lineCount)
        assertFalse(tail.isLineEllipsized(0))
        assertTrue(tail.size.width >= ceil(tail.multiParagraph.intrinsics.maxIntrinsicWidth).toInt(), "time squeezed")
    }
}
