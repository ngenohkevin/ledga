package com.ledga.app.ui.design.type

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ledga.app.R

/** Inter 4.1 (OFL), bundled and subset (R18). Never FontFamily.Default: spec §10.2's Samsung script-font bug. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, weight: FontWeight, lineHeight: Int, tracking: Double = 0.0, tabular: Boolean = false) =
    TextStyle(
        fontFamily = Inter,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.em,
        fontFeatureSettings = if (tabular) "tnum" else null,
    )

/**
 * Spec §10.2 type scale (sp / weight / tracking). It respects the system font scale, and layouts are verified at 1.3×.
 * Amount styles use tabular figures so columns of money line up. Colour comes from `LedgaTheme.colors`.
 */
object LedgaType {
    val balance = style(34, FontWeight.ExtraBold, 40, -0.03, tabular = true)
    val screenTitle = style(24, FontWeight.ExtraBold, 30, -0.02)
    val amountL = style(26, FontWeight.ExtraBold, 32, -0.02, tabular = true)
    val amountM = style(16, FontWeight.ExtraBold, 22, -0.01, tabular = true)
    val amount = style(14, FontWeight.Bold, 20, tabular = true)
    val amountCaption = style(12, FontWeight.Medium, 16, tabular = true)
    val section = style(15, FontWeight.ExtraBold, 20, -0.01)
    val cardTitle = style(14, FontWeight.Bold, 20)
    val body = style(14, FontWeight.Medium, 20)
    val bodyStrong = style(14, FontWeight.SemiBold, 20)
    val caption = style(12, FontWeight.Medium, 16)
    val label = style(12, FontWeight.Bold, 16)
    val overline = style(11, FontWeight.Bold, 14, 0.06)

    val all: Map<String, TextStyle> = mapOf(
        "balance" to balance, "screenTitle" to screenTitle, "amountL" to amountL, "amountM" to amountM,
        "amount" to amount, "amountCaption" to amountCaption, "section" to section, "cardTitle" to cardTitle,
        "body" to body, "bodyStrong" to bodyStrong, "caption" to caption, "label" to label, "overline" to overline,
    )

    val tabular: Set<String> = setOf("balance", "amountL", "amountM", "amount", "amountCaption")

    /** M3 components (sheets, snackbars, switches, progress) inherit Inter through this mapping. */
    val material = Typography(
        displayLarge = balance,
        displayMedium = amountL,
        displaySmall = screenTitle,
        headlineLarge = screenTitle,
        headlineMedium = screenTitle,
        headlineSmall = section,
        titleLarge = screenTitle,
        titleMedium = section,
        titleSmall = cardTitle,
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = caption,
        labelLarge = label,
        labelMedium = label,
        labelSmall = overline,
    )
}
