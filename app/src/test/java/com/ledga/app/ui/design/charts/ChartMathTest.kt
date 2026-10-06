package com.ledga.app.ui.design.charts

import org.junit.Test
import kotlin.test.assertEquals

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
}
