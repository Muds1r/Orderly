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

/** Status accents — green / amber / red / blue */
val StatusDelivered = Color(0xFF22C55E)
val StatusInTransit = Color(0xFF2563EB)
val StatusDelayed = Color(0xFFEF4444)
val StatusProcessing = Color(0xFFF59E0B)

private val RoyalBlue = Color(0xFF2563EB)
private val Indigo = Color(0xFF4F46E5)
private val LightBg = Color(0xFFF8FAFC)
private val DarkBg = Color(0xFF0F172A)
private val LightCard = Color(0xFFFFFFFF)
private val DarkCard = Color(0xFF1E293B)
private val Slate600 = Color(0xFF475569)
private val Slate400 = Color(0xFF94A3B8)
private val Slate200 = Color(0xFFE2E8F0)
private val Slate700 = Color(0xFF334155)

private val LightColors = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Indigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF312E81),
    tertiary = Color(0xFF0EA5E9),
    background = LightBg,
    onBackground = Color(0xFF0F172A),
    surface = LightBg,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Slate200,
    onSurfaceVariant = Slate600,
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = LightCard,
    surfaceContainerHighest = LightCard,
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFEF4444),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = Color(0xFF38BDF8),
    background = DarkBg,
    onBackground = Color(0xFFF8FAFC),
    surface = DarkBg,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate400,
    surfaceContainer = Color(0xFF1E293B),
    surfaceContainerHigh = DarkCard,
    surfaceContainerHighest = DarkCard,
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFF87171),
    onError = Color(0xFF0F172A)
)

private val OrderlyTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
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
