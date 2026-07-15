package com.orderly.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Status accents — soft muted emerald / blue / amber / coral */
val StatusDelivered = Color(0xFF059669)
val StatusInTransit = Color(0xFF2563EB)
val StatusDelayed = Color(0xFFE11D48)
val StatusProcessing = Color(0xFFD97706)

private val DeepBlue = Color(0xFF1D4ED8)
private val SoftIndigo = Color(0xFF4338CA)
private val LightBg = Color(0xFFF1F5F9)
private val DarkBg = Color(0xFF0F172A)
private val LightSurface = Color(0xFFE8EEF5)
private val DarkSurface = Color(0xFF1E293B)

private val LightFallback = lightColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE4FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = SoftIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E0FF),
    onSecondaryContainer = Color(0xFF0E0665),
    tertiary = Color(0xFF0F766E),
    background = LightBg,
    onBackground = Color(0xFF0F172A),
    surface = LightBg,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41474D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F7FA),
    surfaceContainer = LightSurface,
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFD8E0EA),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkFallback = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004493),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFC1C1FF),
    onSecondary = Color(0xFF1A1576),
    secondaryContainer = Color(0xFF322E8D),
    onSecondaryContainer = Color(0xFFE0E0FF),
    tertiary = Color(0xFF4DDBC8),
    background = DarkBg,
    onBackground = Color(0xFFE2E8F0),
    surface = DarkBg,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF41474D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceContainerLowest = Color(0xFF0B1220),
    surfaceContainerLow = Color(0xFF151C28),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = Color(0xFF283040),
    surfaceContainerHighest = Color(0xFF333B4B),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41474D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkFallback
        else -> LightFallback
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OrderlyTypography,
        content = content
    )
}
