package com.ledga.app.ui.design.charts

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil
import kotlin.test.assertTrue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measurement for the label check
class ColumnChartTest {
    @get:Rule val compose = createComposeRule()
    private val amber = Color(0xFFD98A00)

    private fun bars(vararg cents: Long) =
        cents.mapIndexed { i, v -> Bar("M$i", listOf(v), speech = "Month $i: Ksh ${AmountFormat.plain(v)}") }

    @Test
    fun `the chart has a summary and one TalkBack node per bar`() {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                ColumnChart(bars(10_000L, 20_000L, 30_000L), listOf(amber), summary = "Electricity, 3 months")
            }
        }
        compose.onNodeWithContentDescription("Electricity, 3 months").assertExists()
        listOf("Month 0: Ksh 100", "Month 1: Ksh 200", "Month 2: Ksh 300").forEach {
            compose.onNodeWithContentDescription(it).assertExists()
        }
    }

    @Test
    fun `tapping a bar selects it and shows its tooltip`() {
        var selected by mutableStateOf<Int?>(null)
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                ColumnChart(
                    bars(10_000L, 20_000L, 30_000L), listOf(amber), summary = "Electricity",
                    selectedIndex = selected, onSelect = { selected = it }, tooltip = { ChartTooltip("Tip $it") },
                )
            }
        }
        compose.onNodeWithContentDescription("Month 1: Ksh 200").performClick()
        assertEquals(1, selected)
        compose.onNodeWithContentDescription("Month 1: Ksh 200").assertIsSelected()
        compose.onNodeWithText("Tip 1").assertExists()
    }

    @Test
    fun `degenerate data renders without crashing`() {
        val cases = listOf(
            emptyList(), bars(0L, 0L, 0L), bars(50_000L), bars(-10_000L, 20_000L),
            bars(Long.MAX_VALUE / 4, Long.MAX_VALUE / 4),
        )
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                Column {
                    cases.forEachIndexed { i, b ->
                        ColumnChart(
                            b, listOf(amber), summary = "case $i", selectedIndex = 0, average = 1_000_000L,
                            showGrid = true, tooltip = { ChartTooltip("tip") },
                        )
                    }
                    MiniBars(emptyList(), contentDescription = "mini empty")
                    MiniBars(listOf(0L, 0L), contentDescription = "mini zero")
                    SparkBars(listOf(-5L, 0L), amber)
                }
            }
        }
        cases.indices.forEach { compose.onNodeWithContentDescription("case $it").assertExists() }
        compose.onNodeWithContentDescription("mini empty").assertExists()
        compose.onNodeWithContentDescription("mini zero").assertExists()
    }
    @Test
    fun `month labels share one size and never clip - full names when they fit, first letters when not`() {
        val months = listOf("NOV", "DEC", "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT")
        var scale by mutableStateOf(1f)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                    Box(Modifier.width(296.dp)) {
                        ColumnChart(months.map { Bar(it, listOf(100_000L), speech = "$it month") }, listOf(amber), summary = "12 months")
                    }
                }
            }
        }
        for (fontScale in listOf(1f, 1.3f, 2f)) {
            scale = fontScale
            compose.waitForIdle()
            val labels = compose.onNodeWithTag(ChartTags.LABELS, useUnmergedTree = true).onChildren()
            val layouts = (0 until months.size).map { i ->
                val results = mutableListOf<TextLayoutResult>()
                labels[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                results.single()
            }
            layouts.forEachIndexed { i, label ->
                val text = label.layoutInput.text.text
                assertTrue(text == months[i] || text == months[i].take(1), "scale $fontScale: '$text' for ${months[i]}")
                val fits = label.size.width >= ceil(label.multiParagraph.intrinsics.maxIntrinsicWidth).toInt()
                assertTrue(fits && !label.isLineEllipsized(0), "scale $fontScale: ${months[i]} clipped")
            }
            // Baselines, not style.fontSize: autosized text keeps its style size while drawing smaller.
            assertEquals(1, layouts.map { Math.round(it.firstBaseline) }.toSet().size, "scale $fontScale: mixed label sizes")
            assertEquals(1, layouts.map { it.layoutInput.text.text.length > 1 }.toSet().size, "scale $fontScale: mixed full names and initials")
        }
    }
}
