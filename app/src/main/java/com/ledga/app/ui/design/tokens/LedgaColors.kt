package com.ledga.app.ui.design.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Palette C (spec §10.1, refinements R19/R20). Read it through `LedgaTheme.colors`.
 *
 * - Outflow amounts are [ink]; inflows are [inflow] with a "+". Red ([danger]) is only for alerts and Fuliza owed.
 * - [faint] is decoration only (dividers, drag handle, dashed outlines): it fails AA as text.
 * - Secondary text on [dangerSoft]/[warningSoft] is [ink2], not [muted].
 * - Elevation: 1 dp [line] borders plus a 1 dp [shadow] in light; dark uses surface stepping ([shadow] is transparent).
 */
@Immutable
data class LedgaColors(
    val isDark: Boolean,
    val canvas: Color,
    val surface: Color,
    val surfaceSheet: Color,
    val plate: Color,
    val line: Color,
    val lineSubtle: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val faint: Color,
    val primary: Color,
    val onPrimary: Color,
    val chartPrimary: Color,
    val primarySoft: Color,
    val onPrimarySoft: Color,
    val inflow: Color,
    val danger: Color,
    val dangerSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val barTrack: Color,
    val barMuted: Color,
    val navBar: Color,
    val shadow: Color,
)

val LightColors = LedgaColors(
    isDark = false,
    canvas = Color(0xFFEFF2F1),
    surface = Color(0xFFFFFFFF),
    surfaceSheet = Color(0xFFFFFFFF),
    plate = Color(0xFFF1F4F3),
    line = Color(0xFFE8ECEA),
    lineSubtle = Color(0xFFEEF1F0),
    ink = Color(0xFF0B1210),
    ink2 = Color(0xFF2B3532),
    muted = Color(0xFF646F6B), // R19: spec #6B7672 is 4.18:1 on canvas
    faint = Color(0xFFA3ACA8),
    primary = Color(0xFF0A6B4B),
    onPrimary = Color(0xFFFFFFFF),
    chartPrimary = Color(0xFF0E9F6E),
    primarySoft = Color(0xFFE3F2EA),
    onPrimarySoft = Color(0xFF0A5A3F),
    inflow = Color(0xFF0A7E54), // R19: spec #0B8A5C is 3.88:1 on canvas
    danger = Color(0xFFC7333E), // R19: spec #C8333E is 4.497:1 on dangerSoft
    dangerSoft = Color(0xFFFCE9EA),
    warning = Color(0xFFA05F00), // R19: spec #B86E00 is 3.63:1 on warningSoft
    warningSoft = Color(0xFFFFF3DC),
    barTrack = Color(0xFFE3E9E6),
    barMuted = Color(0xFFCFD8D4),
    navBar = Color(0xFFFFFFFF),
    shadow = Color(0x0A101814), // 4 %
)

val DarkColors = LedgaColors(
    isDark = true,
    canvas = Color(0xFF080B0A),
    surface = Color(0xFF121816),
    surfaceSheet = Color(0xFF141A18),
    plate = Color(0xFF1A2220),
    line = Color(0xFF1D2522),
    lineSubtle = Color(0xFF1A211F),
    ink = Color(0xFFEEF4F1),
    ink2 = Color(0xFFC9D2CE),
    muted = Color(0xFF8B9792),
    faint = Color(0xFF5E6964),
    primary = Color(0xFF43E0A0),
    onPrimary = Color(0xFF03160F),
    chartPrimary = Color(0xFF43E0A0),
    primarySoft = Color(0xFF16271F),
    onPrimarySoft = Color(0xFFB9F5D9),
    inflow = Color(0xFF43E0A0),
    danger = Color(0xFFFF7D86),
    dangerSoft = Color(0xFF2A1517),
    warning = Color(0xFFFFC24D),
    warningSoft = Color(0xFF2A2109),
    barTrack = Color(0xFF1D2724),
    barMuted = Color(0xFF26332E),
    navBar = Color(0xFF0D1311),
    shadow = Color.Transparent,
)

/** A foreground/background pair the design system draws text with. */
data class TextPair(val name: String, val fg: Color, val bg: Color)

/**
 * Every text-on-background pair Ledga uses. `ContrastTest` holds each to 4.5:1 in both themes:
 * add a pair here before using a new combination.
 */
fun LedgaColors.textPairs(): List<TextPair> = listOf(
    TextPair("ink/canvas", ink, canvas),
    TextPair("ink/surface", ink, surface),
    TextPair("ink/surfaceSheet", ink, surfaceSheet),
    TextPair("ink/plate", ink, plate),
    TextPair("ink/primarySoft", ink, primarySoft),
    TextPair("ink/dangerSoft", ink, dangerSoft),
    TextPair("ink/warningSoft", ink, warningSoft),
    TextPair("ink2/canvas", ink2, canvas),
    TextPair("ink2/surface", ink2, surface),
    TextPair("ink2/plate", ink2, plate),
    TextPair("ink2/dangerSoft", ink2, dangerSoft),
    TextPair("ink2/warningSoft", ink2, warningSoft),
    TextPair("muted/canvas", muted, canvas),
    TextPair("muted/surface", muted, surface),
    TextPair("muted/surfaceSheet", muted, surfaceSheet),
    TextPair("muted/plate", muted, plate),
    TextPair("muted/primarySoft", muted, primarySoft),
    TextPair("muted/navBar", muted, navBar),
    TextPair("onPrimary/primary", onPrimary, primary),
    TextPair("primary/canvas", primary, canvas),
    TextPair("primary/surface", primary, surface),
    TextPair("primary/surfaceSheet", primary, surfaceSheet),
    TextPair("primary/plate", primary, plate),
    TextPair("primary/navBar", primary, navBar),
    TextPair("onPrimarySoft/primarySoft", onPrimarySoft, primarySoft),
    TextPair("inflow/canvas", inflow, canvas),
    TextPair("inflow/surface", inflow, surface),
    TextPair("danger/canvas", danger, canvas),
    TextPair("danger/surface", danger, surface),
    TextPair("danger/dangerSoft", danger, dangerSoft),
    TextPair("warning/surface", warning, surface),
    TextPair("warning/warningSoft", warning, warningSoft),
    TextPair("canvas/ink", canvas, ink),
)
