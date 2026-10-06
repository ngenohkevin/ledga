package com.ledga.app.ui.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The balance card's promise: a seven-figure balance shrinks onto one line instead of clipping. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AmountTextFitTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a seven-figure balance at font scale 2 shrinks onto one line in a card-width slot`() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) {
                LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                    Box(Modifier.width(296.dp)) { AmountText(123_456_789, Modifier.testTag("balance")) }
                }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("balance").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertEquals(1, layout.lineCount)
        assertTrue(layout.size.width >= ceil(layout.multiParagraph.intrinsics.maxIntrinsicWidth).toInt(), "balance clipped")
    }
    private fun layoutOfTag(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun TextLayoutResult.wholeOrEllipsized() =
        isLineEllipsized(0) || size.width >= ceil(multiParagraph.intrinsics.maxIntrinsicWidth).toInt()

    @Test
    fun `money that cannot fit ends in an ellipsis, never a silently different number`() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) {
                LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                    Column {
                        Box(Modifier.width(120.dp)) { AmountText(999_999_999_999_99L, Modifier.testTag("huge")) }
                        Box(Modifier.width(120.dp)) { InfoChip("Owed 1,250,000.00", Modifier.testTag("chip")) }
                    }
                }
            }
        }
        assertTrue(layoutOfTag("huge").wholeOrEllipsized(), "balance clipped to a different number")
        assertTrue(layoutOfTag("chip").wholeOrEllipsized(), "chip amount clipped to a different number")
    }
}
