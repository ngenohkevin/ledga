package com.ledga.app.ui.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import kotlin.math.ceil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real text metrics for the size check
class ControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `segments are tabs with exactly one selected`() {
        var selected by mutableIntStateOf(1)
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                SegmentedControl(listOf("Week", "Month", "Year"), selected, { selected = it })
            }
        }
        compose.onNodeWithText("Month").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        compose.onNodeWithText("Week").assertIsNotSelected().performClick()
        compose.onNodeWithText("Week").assertIsSelected()
        compose.onNodeWithText("Month").assertIsNotSelected()
        assertEquals(0, selected)
    }

    @Test
    fun `a choice chip reports and toggles its selection`() {
        var on by mutableStateOf(false)
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) { ChoiceChip("Money out", on, { on = !on }) }
        }
        compose.onNodeWithText("Money out").assertIsNotSelected().performClick()
        compose.onNodeWithText("Money out").assertIsSelected()
    }

    @Test
    fun `search clears with one tap and shows its placeholder again`() {
        var query by mutableStateOf("naivas")
        val placeholder = "Search name, phone, code, amount"
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) { SearchField(query, { query = it }, placeholder) }
        }
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertEquals("", query)
        compose.onAllNodesWithText(placeholder).onFirst().assertExists()
    }

    @Test
    fun `controls have 48 dp touch targets`() {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                Column {
                    ChoiceChip("All", true, {}, Modifier.testTag("choice"))
                    LineChip("All lines", {}, Modifier.testTag("line"))
                    AddChip("Add rule", {}, Modifier.testTag("add"))
                    SegmentedControl(listOf("Week", "Month"), 0, {}, Modifier.testTag("segments"))
                    SearchField("", {}, "Search", Modifier.testTag("search"))
                }
            }
        }
        // The whole layout node, not the semantics bounds: a chip's merged semantics node is its visible pill, and
        // Compose widens any small hit area anyway. What must hold is 48 dp of reserved space, so targets never overlap.
        listOf("choice", "line", "add", "segments", "search").forEach {
            val height = compose.onNodeWithTag(it).fetchSemanticsNode().layoutInfo.height
            assertTrue(with(compose.density) { height.toDp() } >= 48.dp, "$it reserves only ${height}px")
        }
    }
    @Test
    fun `segment labels shrink to fit at 1_3x and ellipsize rather than clip at 2x`() {
        for ((scale, mustFit) in listOf(1.3f to true, 2f to false)) {
            val label = segmentLabelAt(scale)
            val fits = label.size.width >= ceil(label.multiParagraph.intrinsics.maxIntrinsicWidth).toInt()
            assertEquals(1, label.lineCount, "scale $scale")
            if (mustFit) assertTrue(fits && !label.isLineEllipsized(0), "clipped or ellipsized at $scale")
            else assertTrue(fits || label.isLineEllipsized(0), "cut mid-word at $scale")
        }
    }

    private var scale by mutableStateOf(1f)

    private fun segmentLabelAt(fontScale: Float): TextLayoutResult {
        scale = fontScale
        if (!contentSet) {
            contentSet = true
            compose.setContent {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                    LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                        Box(Modifier.width(360.dp)) { SegmentedControl(listOf("Transactions", "Spending", "People"), 0, {}) }
                    }
                }
            }
        }
        compose.waitForIdle()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Transactions", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private var contentSet = false
}
