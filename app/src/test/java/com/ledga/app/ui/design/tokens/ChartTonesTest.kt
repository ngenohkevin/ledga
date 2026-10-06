package com.ledga.app.ui.design.tokens

import androidx.compose.ui.graphics.Color
import com.ledga.core.model.Categories
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner decision 2026-10-06 (R27): every colour a bar can take meets WCAG 1.4.11 on the card, in both themes. */
class ChartTonesTest {
    private val themes = listOf(LightColors, DarkColors)

    /** v1 custom categories store their own hex: the brightest, darkest and dullest a user could have picked. */
    private val custom = listOf("#FFFF00", "#FFFFFF", "#000000", "#808080", "#4CAF50")

    private fun name(c: LedgaColors) = if (c.isDark) "dark" else "light"

    private fun barColours(c: LedgaColors): List<Pair<String, Color>> =
        listOf("chartPrimary" to c.chartPrimary, "neutral" to CategoryPalette.NEUTRAL.pick(c.isDark)) +
            Categories.SEED.map { it.key to CategoryPalette.seeded(it.key)!!.pick(c.isDark) } +
            custom.map { hex -> hex to CategoryPalette.resolve("legacy_1", hex, null).pick(c.isDark) }

    private fun check(rule: String, min: Double, measure: (LedgaColors, Color) -> Double) {
        val failures = themes.flatMap { c ->
            barColours(c).mapNotNull { (key, base) ->
                val r = measure(c, base)
                if (r < min) "${name(c)} $key ${"%.2f".format(r)}:1" else null
            }
        }
        assertTrue(failures.isEmpty(), "$rule below $min:1 -> $failures")
    }

    @Test
    fun `unselected bars are at least 3 to 1 against the card in both themes`() =
        check("soft vs surface", 3.0) { c, base -> Contrast.ratio(ChartTones.soft(base, c.surface), c.surface) }

    @Test
    fun `selected, current and legend bars are at least 4_5 to 1 against the card`() =
        check("strong vs surface", 4.5) { c, base -> Contrast.ratio(ChartTones.strong(base, c.surface), c.surface) }

    @Test
    fun `the selected bar stands clearly apart from the unselected ones`() =
        check("strong vs soft", 1.4) { c, base ->
            Contrast.ratio(ChartTones.strong(base, c.surface), ChartTones.soft(base, c.surface))
        }

    @Test
    fun `a filled share bar is at least 3 to 1 against its track`() =
        check("strong vs barTrack", 3.0) { c, base -> Contrast.ratio(ChartTones.strong(base, c.surface), c.barTrack) }

    @Test
    fun `tones keep the colour's hue`() {
        for (c in themes) for ((key, base) in barColours(c)) {
            if (saturation(base) < 0.25f) continue
            for (tone in listOf(ChartTones.soft(base, c.surface), ChartTones.strong(base, c.surface))) {
                val drift = abs(((hue(tone) - hue(base) + 540f) % 360f) - 180f)
                assertTrue(drift <= 6f, "${name(c)} $key drifted ${"%.1f".format(drift)} degrees")
            }
        }
    }

    @Test
    fun `a colour that already reaches 4_5 to 1 is its own strong tone`() {
        val internet = CategoryPalette.seeded(Categories.INTERNET)!!.light
        assertEquals(internet, ChartTones.strong(internet, LightColors.surface))
    }

    private fun hue(c: Color): Float {
        val hi = max(c.red, max(c.green, c.blue))
        val lo = min(c.red, min(c.green, c.blue))
        val d = hi - lo
        if (d == 0f) return 0f
        val h = when (hi) {
            c.red -> ((c.green - c.blue) / d) % 6f
            c.green -> (c.blue - c.red) / d + 2f
            else -> (c.red - c.green) / d + 4f
        }
        return (h * 60f + 360f) % 360f
    }

    private fun saturation(c: Color): Float {
        val hi = max(c.red, max(c.green, c.blue))
        val lo = min(c.red, min(c.green, c.blue))
        return if (hi == 0f) 0f else (hi - lo) / hi
    }
}
