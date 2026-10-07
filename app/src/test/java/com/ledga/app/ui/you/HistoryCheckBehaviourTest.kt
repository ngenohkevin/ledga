package com.ledga.app.ui.you

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.ledga.app.testing.txRow
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertFalse
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import androidx.compose.ui.test.performClick
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R79: a break's point is what was expected against what M-Pesa said; it must read in full. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class HistoryCheckBehaviourTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `at 1_3x a break says what was expected and what M-Pesa said, in full`() {
        val b = ChainBreakUi(txRow(code = "TJK4AB12HC", at = Instant.parse("2026-09-03T06:00:00Z"), amountCents = 50_000, balanceCents = 3_000_000), 12_345_675, 12_000_050)
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 1.3f)) {
                LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                    HistoryCheckContent(HistoryCheckUi(loaded = true, checked = 2, breaks = listOf(b), today = LocalDate.parse("2026-10-06")), HistoryCheckActions())
                }
            }
        }
        val line = HistoryText.breakLine(b)
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(line, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        assertFalse(results.single().hasVisualOverflow, "\"$line\" is cut")
    }

    @Test
    fun `payments not on a line read as left out, with why (owner 2026-10-07)`() {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                HistoryCheckContent(
                    HistoryCheckUi(
                        loaded = true,
                        checked = 12,
                        lines = listOf(LineCheckUi("Personal ··11", 12, 0), LineCheckUi("Not on a line", 40, 9, mixed = true)),
                        today = LocalDate.parse("2026-10-06"),
                    ),
                    HistoryCheckActions(),
                )
            }
        }
        compose.onNodeWithText("Your history adds up").assertIsDisplayed()
        compose.onNodeWithText("Left out · could be either line").assertIsDisplayed()
        compose.onNodeWithText("Payments not on a line could be from either line, so their balances can't be compared. They're left out of the check.").assertIsDisplayed()
    }

    @Test
    fun `the left-out note offers to put those payments on a line (R129)`() {
        var opened = 0
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                HistoryCheckContent(
                    HistoryCheckUi(
                        loaded = true,
                        checked = 12,
                        lines = listOf(LineCheckUi("Personal ··11", 12, 0), LineCheckUi("Not on a line", 40, 9, mixed = true)),
                        today = LocalDate.parse("2026-10-06"),
                    ),
                    HistoryCheckActions(onUnassigned = { opened++ }),
                )
            }
        }
        compose.onNodeWithText("Put them on a line").performClick()
        assertEquals(1, opened)
    }
}
