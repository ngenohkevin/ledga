package com.ledga.app.ui.design.charts

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartMathTest {

    @Test
    fun `fractions never divide by zero or exceed one`() {
        assertEquals(0f, ChartMath.fraction(0L, 0L))
        assertEquals(0f, ChartMath.fraction(5L, 0L))
        assertEquals(0f, ChartMath.fraction(-5L, 10L))
        assertEquals(0.5f, ChartMath.fraction(5L, 10L))
        assertEquals(1f, ChartMath.fraction(15L, 10L))
    }

    @Test
    fun `the plot top is the tallest bar or the average, whichever is higher`() {
        assertEquals(0L, ChartMath.scaleMax(emptyList(), null))
        assertEquals(300L, ChartMath.scaleMax(listOf(100L, 300L), 200L))
        assertEquals(500L, ChartMath.scaleMax(listOf(100L, 300L), 500L))
        assertEquals(0L, ChartMath.scaleMax(listOf(-5L), -1L))
    }

    @Test
    fun `bar totals skip negatives and saturate instead of overflowing`() {
        assertEquals(300L, Bar("x", listOf(100L, -50L, 200L), "s").total)
        assertEquals(Long.MAX_VALUE, Bar("x", listOf(Long.MAX_VALUE, Long.MAX_VALUE), "s").total)
    }

    @Test
    fun `gridlines step 1-2-5 and stay at or under the top`() {
        assertEquals(listOf(100_000L, 200_000L), ChartMath.gridLines(245_000L))
        assertEquals(listOf(50L), ChartMath.gridLines(90L))
        assertEquals(listOf(1L, 2L, 3L), ChartMath.gridLines(3L))
        assertEquals(listOf(500_000L, 1_000_000L), ChartMath.gridLines(1_000_000L))
        assertEquals(listOf(500_000_000_000L, 1_000_000_000_000L), ChartMath.gridLines(1_200_000_000_000L))
        assertEquals(emptyList(), ChartMath.gridLines(0L))
    }

    @Test
    fun `a tooltip sits above every bar it spans, so a short running month doesn't hide last month`() {
        // Four bars across 400 px under 34 px of headroom; the running month (index 3) is at Ksh 0 so far.
        val fractions = listOf(0.9f, 0.8f, 0.85f, 0f)
        val (x, y) = ChartMath.tooltipPosition(3, fractions, width = 400f, height = 200f, headroom = 34f, gap = 6f, tipWidth = 120, tipHeight = 40, lift = 4f)
        assertEquals(280, x, "kept inside the chart's right edge, so it reaches over bar 2")
        val bar2Top = 200f - 0.85f * (200f - 34f)
        assertTrue(y + 40 <= bar2Top, "the tooltip ($y..${y + 40}) must clear bar 2's top ($bar2Top)")
    }
}
