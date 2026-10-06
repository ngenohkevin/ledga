package com.ledga.app.ui.design.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.tokens.DarkColors
import com.ledga.app.ui.design.tokens.LedgaColors
import com.ledga.app.ui.design.tokens.LightColors
import com.ledga.app.ui.design.tokens.LocalReducedMotion
import com.ledga.app.ui.design.tokens.Radii
import com.ledga.app.ui.design.type.LedgaType

/** You → Appearance (spec §10.4). Phase 4 maps v1's ThemeMode onto it. */
enum class Appearance { SYSTEM, LIGHT, DARK }

val LocalLedgaColors = staticCompositionLocalOf { LightColors }

/** Read the design system from any composable inside [LedgaTheme]. */
object LedgaTheme {
    val colors: LedgaColors
        @Composable @ReadOnlyComposable get() = LocalLedgaColors.current

    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}

/**
 * Ledga's theme: palette C, Inter and the shapes. Always the Ledga palette, never dynamic colour.
 * [reducedMotion] defaults to Android's "Remove animations" setting.
 */
@Composable
fun LedgaTheme(
    appearance: Appearance = Appearance.SYSTEM,
    reducedMotion: Boolean = rememberSystemReducedMotion(),
    content: @Composable () -> Unit,
) {
    val dark = when (appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val scheme = remember(colors) { colors.toMaterial() }
    CompositionLocalProvider(LocalLedgaColors provides colors, LocalReducedMotion provides reducedMotion) {
        MaterialTheme(colorScheme = scheme, typography = LedgaType.material, shapes = LedgaShapes, content = content)
    }
}

/** "Remove animations" (Settings → Accessibility) sets the animator duration scale to 0. */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Spec §10.1 shapes for M3 components: stat tiles 16, cards 24, sheets 28. */
val LedgaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(Radii.stat),
    large = RoundedCornerShape(Radii.card),
    extraLarge = RoundedCornerShape(Radii.sheetTop),
)

/** M3 components (sheets, switches, snackbars, progress) draw from these. Snackbars use `ink` on `canvas`. */
internal fun LedgaColors.toMaterial(): ColorScheme =
    (if (isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primarySoft,
        onPrimaryContainer = onPrimarySoft,
        inversePrimary = inversePrimary, // snackbar actions on inverseSurface (ink)
        secondary = ink2,
        onSecondary = surface,
        secondaryContainer = plate,
        onSecondaryContainer = ink,
        tertiary = warning,
        onTertiary = surface,
        tertiaryContainer = warningSoft,
        onTertiaryContainer = ink,
        background = canvas,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = plate,
        onSurfaceVariant = muted,
        surfaceTint = Color.Transparent,
        inverseSurface = ink,
        inverseOnSurface = canvas,
        error = danger,
        onError = surface,
        errorContainer = dangerSoft,
        onErrorContainer = ink,
        outline = muted, // OutlinedTextField/OutlinedButton borders need >= 3:1
        outlineVariant = line,
        scrim = Color.Black,
        surfaceBright = surface,
        surfaceDim = canvas,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceSheet,
        surfaceContainerHighest = plate,
    )
