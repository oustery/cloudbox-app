package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.preferences.AppFont

/**
 * Inter — the default app typeface.
 *
 * Chosen over the platform Roboto for its even rhythm, taller x-height and
 * first-class Cyrillic coverage (the whole UI is Russian). Static weights are
 * bundled in res/font so rendering is deterministic on every device, offline,
 * with no Google Fonts provider round-trip and no variable-font fallback
 * issues on API < 26.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/**
 * Variable-font families (single wght-axis TTF each). Compose instantiates
 * the requested weight through [FontVariation] on API 26+; older devices
 * fall back to the file's default instance, which is visually acceptable.
 */
val Manrope = variableFamily(R.font.manrope)
val Rubik = variableFamily(R.font.rubik)
val Nunito = variableFamily(R.font.nunito)

private fun variableFamily(resId: Int): FontFamily = FontFamily(
    Font(resId, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(resId, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(resId, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(resId, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Resolves the user-chosen typeface; [AppFont.SYSTEM] maps to platform Roboto. */
fun AppFont.toFontFamily(): FontFamily = when (this) {
    AppFont.INTER -> Inter
    AppFont.MANROPE -> Manrope
    AppFont.RUBIK -> Rubik
    AppFont.NUNITO -> Nunito
    AppFont.SYSTEM -> FontFamily.Default
}

/**
 * CloudBox type scale: expressive hierarchy with comfortable reading metrics,
 * parameterised by the app-wide [fontFamily] so the settings font picker
 * re-skins every text style at once.
 */
fun buildTypography(fontFamily: FontFamily): Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    )
)

/** Default scale kept for previews and non-themed call sites. */
val Typography = buildTypography(Inter)
