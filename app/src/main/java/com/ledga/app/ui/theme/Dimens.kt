package com.ledga.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

// =====================================================================
// Spacing scale — LEDGA_REDESIGN.md §1.5
// =====================================================================
object Space {
    val s2 = 4.dp
    val s3 = 8.dp
    val s4 = 12.dp
    val s5 = 16.dp
    val s6 = 20.dp
    val s7 = 24.dp
    val s8 = 32.dp
    val s9 = 48.dp
    val s10 = 64.dp

    val Screen = 20.dp
    val Card = 20.dp
    val CardCompact = 16.dp
    val Section = 24.dp
    val Tight = 12.dp
}

// =====================================================================
// Radius — LEDGA_REDESIGN.md §1.6
// =====================================================================
object Radius {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val pill = 999.dp

    val Card = lg
    val SheetTop = xl
    val IconTile = 14.dp
    val TabBarPill = 36.dp
}

// =====================================================================
// Motion — LEDGA_REDESIGN.md §1.8
// =====================================================================
object Motion {
    val EmphasizedDecelerate = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun fast() = tween<Float>(durationMillis = 120, easing = EmphasizedDecelerate)
    fun base() = tween<Float>(durationMillis = 240, easing = EmphasizedDecelerate)
    fun emphasized() = tween<Float>(durationMillis = 360, easing = EmphasizedDecelerate)
    fun number() = tween<Float>(durationMillis = 600, easing = EmphasizedDecelerate)
}
