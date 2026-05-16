package com.ledga.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.ledga.app.R

// =====================================================================
// Ledga v2 type scale — LEDGA_REDESIGN.md §1.4
// Font: Inter (via Google Fonts). Numbers should use tnum where shown.
// =====================================================================

private val GoogleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val InterFont = GoogleFont("Inter")

private val Inter = FontFamily(
    Font(googleFont = InterFont, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = InterFont, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = InterFont, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = InterFont, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold),
)

object LedgaText {
    val DisplayXL = TextStyle(
        fontFamily = Inter,
        fontSize = 56.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 58.8.sp,
        letterSpacing = (-1.5).sp,
    )
    val DisplayL = TextStyle(
        fontFamily = Inter,
        fontSize = 44.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 46.2.sp,
        letterSpacing = (-1).sp,
    )
    val DisplayM = TextStyle(
        fontFamily = Inter,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 35.2.sp,
        letterSpacing = (-0.5).sp,
    )
    val TitleL = TextStyle(
        fontFamily = Inter,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 28.8.sp,
        letterSpacing = (-0.2).sp,
    )
    val TitleM = TextStyle(
        fontFamily = Inter,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 25.sp,
        letterSpacing = (-0.1).sp,
    )
    val TitleS = TextStyle(
        fontFamily = Inter,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.1.sp,
    )
    val BodyL = TextStyle(
        fontFamily = Inter,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.4.sp,
    )
    val BodyM = TextStyle(
        fontFamily = Inter,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 19.6.sp,
    )
    val Caption = TextStyle(
        fontFamily = Inter,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 15.6.sp,
        letterSpacing = 0.1.sp,
    )
    val Overline = TextStyle(
        fontFamily = Inter,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 14.3.sp,
        letterSpacing = 0.5.sp,
    )
    val Mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 18.2.sp,
    )
}

// Material 3 maps onto our scale so existing M3 components inherit reasonably.
val LedgaTypography = Typography(
    displayLarge = LedgaText.DisplayL,
    displayMedium = LedgaText.DisplayM,
    displaySmall = LedgaText.TitleL,
    headlineLarge = LedgaText.TitleL,
    headlineMedium = LedgaText.TitleM,
    headlineSmall = LedgaText.TitleS,
    titleLarge = LedgaText.TitleL,
    titleMedium = LedgaText.TitleM,
    titleSmall = LedgaText.TitleS,
    bodyLarge = LedgaText.BodyL,
    bodyMedium = LedgaText.BodyM,
    bodySmall = LedgaText.Caption,
    labelLarge = LedgaText.BodyM,
    labelMedium = LedgaText.Caption,
    labelSmall = LedgaText.Overline,
)
