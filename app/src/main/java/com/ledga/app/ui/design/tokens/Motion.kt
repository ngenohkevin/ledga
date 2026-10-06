package com.ledga.app.ui.design.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.staticCompositionLocalOf

/** Spec §10.1: 120/240/360 ms emphasized-decelerate; amounts animate old→new over 600 ms. */
object Motion {
    const val SHORT_MS = 120
    const val MEDIUM_MS = 240
    const val LONG_MS = 360
    const val AMOUNT_MS = 600
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}

/** True when Android's "Remove animations" is on. `LedgaTheme` provides it; read it as `LedgaTheme.reducedMotion`. */
val LocalReducedMotion = staticCompositionLocalOf { false }
