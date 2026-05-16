package com.ledga.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val LedgaLightColorScheme = lightColorScheme(
    primary = LedgaAccent,
    onPrimary = LedgaInkOnAccent,
    primaryContainer = LedgaAccentSoft,
    onPrimaryContainer = LedgaAccentDeep,

    secondary = LedgaInk2,
    onSecondary = LedgaSurface,
    secondaryContainer = LedgaSurface2,
    onSecondaryContainer = LedgaInk,

    tertiary = LedgaWarning,
    onTertiary = LedgaInk,
    tertiaryContainer = LedgaWarningSoft,
    onTertiaryContainer = LedgaInk,

    error = LedgaDanger,
    onError = LedgaInkOnAccent,
    errorContainer = LedgaDangerSoft,
    onErrorContainer = LedgaInk,

    background = LedgaBg,
    onBackground = LedgaInk,
    surface = LedgaSurface,
    onSurface = LedgaInk,
    surfaceVariant = LedgaSurface2,
    onSurfaceVariant = LedgaMuted,
    surfaceContainerLowest = LedgaBg,
    surfaceContainerLow = LedgaSurface,
    surfaceContainer = LedgaSurface,
    surfaceContainerHigh = LedgaSurface2,
    surfaceContainerHighest = LedgaSurface2,

    outline = LedgaLine,
    outlineVariant = LedgaLine,
)

private val LedgaDarkColorScheme = darkColorScheme(
    primary = LedgaAccentDark,
    onPrimary = LedgaInkOnAccentDark,
    primaryContainer = LedgaAccentSoftDark,
    onPrimaryContainer = LedgaAccentDeepDark,

    secondary = LedgaInk2Dark,
    onSecondary = LedgaSurfaceDark,
    secondaryContainer = LedgaSurface2Dark,
    onSecondaryContainer = LedgaInkDark,

    tertiary = LedgaWarningDark,
    onTertiary = LedgaInkDark,
    tertiaryContainer = LedgaWarningSoftDark,
    onTertiaryContainer = LedgaInkDark,

    error = LedgaDangerDark,
    onError = LedgaInkOnAccentDark,
    errorContainer = LedgaDangerSoftDark,
    onErrorContainer = LedgaInkDark,

    background = LedgaBgDark,
    onBackground = LedgaInkDark,
    surface = LedgaSurfaceDark,
    onSurface = LedgaInkDark,
    surfaceVariant = LedgaSurface2Dark,
    onSurfaceVariant = LedgaMutedDark,
    surfaceContainerLowest = LedgaBgDark,
    surfaceContainerLow = LedgaSurfaceDark,
    surfaceContainer = LedgaSurfaceDark,
    surfaceContainerHigh = LedgaSurface2Dark,
    surfaceContainerHighest = LedgaSurface2Dark,

    outline = LedgaLineDark,
    outlineVariant = LedgaLineDark,
)

@Composable
fun LedgaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // v2: brand identity is too specific to defer to dynamic color — always use Ledga palette.
    val colorScheme = if (darkTheme) LedgaDarkColorScheme else LedgaLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LedgaTypography,
        content = content
    )
}
