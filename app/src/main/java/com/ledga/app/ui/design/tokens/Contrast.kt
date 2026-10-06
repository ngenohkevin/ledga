package com.ledga.app.ui.design.tokens

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** WCAG 2.x contrast for opaque sRGB colours (spec §10.1: every text pair ≥ 4.5:1). */
object Contrast {

    fun luminance(c: Color): Double {
        fun channel(v: Float): Double {
            val x = v.toDouble()
            return if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
