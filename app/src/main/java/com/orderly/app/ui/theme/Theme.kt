package com.orderly.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val StatusDelivered = Color(0xFF1B873F)
val StatusInTransit = Color(0xFF1565C0)
val StatusDelayed = Color(0xFFC62828)
val StatusProcessing = Color(0xFF6A4C1E)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A4F6E),
    onPrimary = Color.White,
    primaryContainer = Color(0x331A4F6E),
    onPrimaryContainer = Color(0xFF0C2A3C),
    secondary = Color(0xFF5A6B72),
    secondaryContainer = Color(0x225A6B72),
    onSecondaryContainer = Color(0xFF1C252B),
    tertiary = Color(0xFF4A6670),
    background = Color(0xFFF4F7F9),
    surface = Color(0xFFF4F7F9),
    surfaceVariant = Color(0x1A1A4F6E),
    surfaceContainer = Color(0xCCEEF2F5),
    surfaceContainerHigh = Color(0xE6FFFFFF),
    outline = Color(0x331A4F6E),
    outlineVariant = Color(0x221A4F6E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8EC5E0),
    onPrimary = Color(0xFF00344A),
    primaryContainer = Color(0x33448BB0),
    onPrimaryContainer = Color(0xFFC8E8F7),
    secondary = Color(0xFFB6C4CC),
    secondaryContainer = Color(0x33374B54),
    background = Color(0xFF101417),
    surface = Color(0xFF101417),
    surfaceVariant = Color(0x22FFFFFF),
    surfaceContainer = Color(0xCC151A1E),
    surfaceContainerHigh = Color(0xE61C2226),
    outline = Color(0x33FFFFFF),
    outlineVariant = Color(0x22FFFFFF)
)

@Composable
fun OrderlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
