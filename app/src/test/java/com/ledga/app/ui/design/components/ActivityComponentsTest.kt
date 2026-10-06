package com.ledga.app.ui.design.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.text.input.ImeAction
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** The design-system pieces Activity needs (Phase 3 deferred M10 and M15, and the ChoiceChip ruling). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ActivityComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a transaction row names its tap and its long press for TalkBack`() {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                TxRow(
                    leading = Leading.Icon("fluent_high_voltage"),
                    title = "KPLC Prepaid",
                    subtitle = "Electricity",
                    subtitleTail = "2:15 PM",
                    amountCents = 100_000,
                    inflow = false,
                    speech = "Ksh 1,000 spent at KPLC Prepaid, today 2:15 PM",
                    onClick = {},
                    onClickLabel = "Open payment",
                    onLongClick = {},
                    onLongClickLabel = "Change category",
                )
            }
        }
        val row = compose.onNodeWithContentDescription("Ksh 1,000 spent at KPLC Prepaid, today 2:15 PM").fetchSemanticsNode()
        assertEquals("Open payment", row.config[SemanticsActions.OnClick].label)
        assertEquals("Change category", row.config[SemanticsActions.OnLongClick].label)
    }

    @Test
    fun `the search field's keyboard key is Search, and it runs the search`() {
        var searched = 0
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                SearchField("kplc", onQueryChange = {}, placeholder = "Name, phone, code or amount", onSearch = { searched++ })
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Search))
        field.performImeAction()
        assertEquals(1, searched)
    }

    @Test
    fun `a single-choice filter chip is a radio button for TalkBack`() {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                ChoiceChip("Money out", selected = true, onClick = {}, role = Role.RadioButton)
            }
        }
        compose.onNodeWithText("Money out")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
    }

    @Test
    fun `a delta badge reads as words, not arrow glyphs`() {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                DeltaBadge("${Char(0x25B2)} 9% vs Aug", DeltaTone.Bad, speech = "9 percent more than Aug")
            }
        }
        compose.onNodeWithContentDescription("9 percent more than Aug").assertExists()
    }
}
