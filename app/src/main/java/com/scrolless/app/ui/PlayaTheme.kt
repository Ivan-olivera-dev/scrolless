package com.scrolless.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Color tokens directly adapted from playaperello/src/app/globals.css
 */
@Immutable
data class PlayaPalette(
    val bgColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val borderColor: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val hoverBg: Color,
    val isDark: Boolean
)

val LightPlayaPalette = PlayaPalette(
    bgColor = Color(0xFFFAF9F6),
    textPrimary = Color(0xFF2C2C2C),
    textSecondary = Color(0xFF8A8A8A),
    borderColor = Color(0xFFEAEAEA),
    success = Color(0xFF82A884),
    warning = Color(0xFFD6C07E),
    danger = Color(0xFFC47B7B),
    hoverBg = Color(0xFFF0F0F0),
    isDark = false
)

val DarkPlayaPalette = PlayaPalette(
    bgColor = Color(0xFF151515),
    textPrimary = Color(0xFFE6E6E6),
    textSecondary = Color(0xFF707070),
    borderColor = Color(0xFF2A2A2A),
    success = Color(0xFF5D7A5E),
    warning = Color(0xFF9E8E5E),
    danger = Color(0xFF8A5757),
    hoverBg = Color(0xFF222222),
    isDark = true
)

val LocalPlayaPalette = staticCompositionLocalOf { LightPlayaPalette }

val PlayaTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 35.sp,
        letterSpacing = (-1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 24.sp,
        letterSpacing = (-0.5).sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        letterSpacing = 1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        letterSpacing = (-0.5).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 15.sp,
        lineHeight = 24.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 12.5.sp
    )
)

@Composable
fun PlayaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPlayaPalette else LightPlayaPalette
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            background = palette.bgColor,
            surface = palette.bgColor,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            primary = palette.textPrimary
        )
    } else {
        lightColorScheme(
            background = palette.bgColor,
            surface = palette.bgColor,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            primary = palette.textPrimary
        )
    }

    CompositionLocalProvider(LocalPlayaPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PlayaTypography,
            content = content
        )
    }
}
