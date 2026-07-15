package com.orderly.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Simple status accents — not part of the brand palette. */
val StatusDelivered = Color(0xFF166534)
val StatusInTransit = Color(0xFF1D4ED8)
val StatusDelayed = Color(0xFFB91C1C)
val StatusProcessing = Color(0xFFA16207)

private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)
private val NearBlack = Color(0xFF111111)
private val SoftGray = Color(0xFFF5F5F5)
private val MidGray = Color(0xFF6B6B6B)
private val LineGray = Color(0xFFE5E5E5)

/** Standard white + black Material scheme — no brand tint. */
private val LightColors = lightColorScheme(
    primary = Black,
    onPrimary = White,
    primaryContainer = SoftGray,
    onPrimaryContainer = NearBlack,
    secondary = NearBlack,
    onSecondary = White,
    secondaryContainer = SoftGray,
    onSecondaryContainer = NearBlack,
    tertiary = MidGray,
    onTertiary = White,
    tertiaryContainer = SoftGray,
    onTertiaryContainer = NearBlack,
    background = White,
    onBackground = NearBlack,
    surface = White,
    onSurface = NearBlack,
    surfaceVariant = SoftGray,
    onSurfaceVariant = MidGray,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = White,
    surfaceContainerHigh = White,
    surfaceContainerHighest = SoftGray,
    outline = LineGray,
    outlineVariant = LineGray,
    error = Color(0xFFB91C1C),
    onError = White
)

private val DarkColors = darkColorScheme(
    primary = White,
    onPrimary = Black,
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = White,
    secondary = Color(0xFFE5E5E5),
    onSecondary = Black,
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = White,
    tertiary = Color(0xFFA3A3A3),
    onTertiary = Black,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFA3A3A3),
    surfaceContainerLowest = Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF262626),
    outline = Color(0xFF333333),
    outlineVariant = Color(0xFF262626),
    error = Color(0xFFF87171),
    onError = Black
)

private val OrderlyTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp
    )
)

@Composable
fun OrderlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = OrderlyTypography,
        content = content
    )
}
