package com.ledga.app.ui.design.tokens

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContrastTest {

    @Test
    fun `contrast ratio follows WCAG 2 relative luminance`() {
        assertEquals(21.0, Contrast.ratio(Color.Black, Color.White), 1e-9)
        assertEquals(1.0, Contrast.ratio(Color(0xFF0A6B4B), Color(0xFF0A6B4B)), 1e-9)
        // #767676 on white is the classic 4.54:1 AA boundary grey.
        assertEquals(4.54, Contrast.ratio(Color(0xFF767676), Color.White), 0.01)
    }

    @Test
    fun `every light text pair meets WCAG AA`() = assertAllPass(LightColors)

    @Test
    fun `every dark text pair meets WCAG AA`() = assertAllPass(DarkColors)

    @Test
    fun `faint is decoration only - it fails AA as text in both themes`() {
        assertTrue(Contrast.ratio(LightColors.faint, LightColors.surface) < 4.5)
        assertTrue(Contrast.ratio(DarkColors.faint, DarkColors.surface) < 4.5)
    }

    @Test
    fun `the spec's original light muted, inflow, warning and danger would have failed`() {
        // Why R19 darkened them (spec §10.1: "darkened ... if a pair fails").
        assertTrue(Contrast.ratio(Color(0xFF6B7672), LightColors.canvas) < 4.5)
        assertTrue(Contrast.ratio(Color(0xFF0B8A5C), LightColors.canvas) < 4.5)
        assertTrue(Contrast.ratio(Color(0xFFB86E00), LightColors.warningSoft) < 4.5)
        assertTrue(Contrast.ratio(Color(0xFFC8333E), LightColors.dangerSoft) < 4.5)
    }

    @Test
    fun `themes carry the spec's identity colours`() {
        assertEquals(false, LightColors.isDark)
        assertEquals(true, DarkColors.isDark)
        assertEquals(Color(0xFF0A6B4B), LightColors.primary)
        assertEquals(Color(0xFF43E0A0), DarkColors.primary)
        assertEquals(Color(0xFFEFF2F1), LightColors.canvas)
        assertEquals(Color(0xFF080B0A), DarkColors.canvas)
        assertEquals(Color.Transparent, DarkColors.shadow)
    }

    private fun assertAllPass(colors: LedgaColors) {
        val pairs = colors.textPairs()
        assertTrue(pairs.size >= 30, "only ${pairs.size} pairs listed")
        val failures = pairs.filter { Contrast.ratio(it.fg, it.bg) < 4.5 }
            .map { "${it.name}=${"%.3f".format(Contrast.ratio(it.fg, it.bg))}" }
        assertTrue(failures.isEmpty(), "pairs below 4.5:1: $failures")
    }
}
