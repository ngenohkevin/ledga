package com.ledga.app.ui.design.tokens

import androidx.compose.ui.graphics.Color

/**
 * Bar colours that meet WCAG 1.4.11 non-text contrast on the card (owner decision 2026-10-06, refinement R27):
 * - [soft]: a bar that isn't selected, at least 3:1 against the surface;
 * - [strong]: the selected or current bar, a legend swatch, a filled share bar, at least 4.5:1, which also keeps it at
 *   least 1.4:1 apart from [soft].
 * Both keep the colour's hue: it is mixed towards the surface or away from it (towards black on a light card, white on a
 * dark one), never towards another hue. Any colour works, including a v1 custom category's stored hex.
 */
object ChartTones {
    const val SOFT_MIN = 3.0
    const val STRONG_MIN = 4.5

    /** Headroom so an 8-bit pixel of the tone still meets the rule after rounding. */
    private const val MARGIN = 0.05
    private const val STEPS = 64

    fun strong(base: Color, surface: Color): Color =
        if (Contrast.ratio(base, surface) >= STRONG_MIN) base else away(base, surface, STRONG_MIN + MARGIN)

    fun soft(base: Color, surface: Color): Color =
        if (Contrast.ratio(base, surface) >= SOFT_MIN + MARGIN) {
            toward(base, surface, SOFT_MIN + MARGIN)
        } else {
            away(base, surface, SOFT_MIN + MARGIN)
        }

    /** The first mix towards black (light card) or white (dark card) that reaches [target]. */
    private fun away(base: Color, surface: Color, target: Double): Color {
        val pole = if (Contrast.luminance(surface) > 0.5) Color.Black else Color.White
        for (i in 1..STEPS) {
            val c = mix(base, pole, i.toFloat() / STEPS)
            if (Contrast.ratio(c, surface) >= target) return c
        }
        return pole
    }

    /** The last mix towards the surface that still reaches [target]: as quiet as the rule allows. */
    private fun toward(base: Color, surface: Color, target: Double): Color {
        var best = base
        for (i in 1..STEPS) {
            val c = mix(base, surface, i.toFloat() / STEPS)
            if (Contrast.ratio(c, surface) < target) break
            best = c
        }
        return best
    }

    private fun mix(a: Color, b: Color, t: Float): Color = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
    )
}
