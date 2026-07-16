package com.orderly.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.orderly.app.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** Status accents — semantic only (DESIGN.md). */
val StatusDelivered = Color(0xFF166534)
val StatusInTransit = Color(0xFF3B82F6)
val StatusDelayed = Color(0xFFEA580C)
val StatusProcessing = Color(0xFFA16207)

// DESIGN.md color tokens
private val Background = Color(0xFFF8F9FB)
private val OnBackground = Color(0xFF191C1E)
private val Surface = Color(0xFFF8F9FB)
private val SurfaceDim = Color(0xFFD9DADC)
private val SurfaceBright = Color(0xFFF8F9FB)
private val SurfaceLowest = Color(0xFFFFFFFF)
private val SurfaceLow = Color(0xFFF3F4F6)
private val SurfaceContainer = Color(0xFFEDEEF0)
private val SurfaceHigh = Color(0xFFE7E8EA)
private val SurfaceHighest = Color(0xFFE1E2E4)
private val OnSurface = Color(0xFF191C1E)
private val OnSurfaceVariant = Color(0xFF4C4546)
private val InverseSurface = Color(0xFF2E3132)
private val InverseOnSurface = Color(0xFFF0F1F3)
private val Outline = Color(0xFF7E7576)
private val OutlineVariant = Color(0xFFCFC4C5)
private val SurfaceTint = Color(0xFF5E5E5E)
private val Primary = Color(0xFF000000)
private val OnPrimary = Color(0xFFFFFFFF)
private val PrimaryContainer = Color(0xFF1B1B1B)
private val OnPrimaryContainer = Color(0xFF848484)
private val InversePrimary = Color(0xFFC6C6C6)
private val Secondary = Color(0xFF585F6C)
private val OnSecondary = Color(0xFFFFFFFF)
private val SecondaryContainer = Color(0xFFDCE2F3)
private val OnSecondaryContainer = Color(0xFF5E6572)
private val Tertiary = Color(0xFF000000)
private val OnTertiary = Color(0xFFFFFFFF)
private val TertiaryContainer = Color(0xFF1B1B1B)
private val OnTertiaryContainer = Color(0xFF848484)
private val Error = Color(0xFFBA1A1A)
private val OnError = Color(0xFFFFFFFF)
private val ErrorContainer = Color(0xFFFFDAD6)
private val OnErrorContainer = Color(0xFF93000A)

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceHighest,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceDim = SurfaceDim,
    surfaceBright = SurfaceBright,
    surfaceContainerLowest = SurfaceLowest,
    surfaceContainerLow = SurfaceLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    inversePrimary = InversePrimary,
    surfaceTint = SurfaceTint
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFE2E2E2),
    onPrimaryContainer = Color(0xFF1B1B1B),
    secondary = Color(0xFFC0C7D6),
    onSecondary = Color(0xFF151C27),
    secondaryContainer = Color(0xFF404754),
    onSecondaryContainer = Color(0xFFDCE2F3),
    tertiary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFFE2E2E2),
    onTertiaryContainer = Color(0xFF1B1B1B),
    background = Color(0xFF111314),
    onBackground = Color(0xFFE1E2E4),
    surface = Color(0xFF111314),
    onSurface = Color(0xFFE1E2E4),
    surfaceVariant = Color(0xFF2E3132),
    onSurfaceVariant = Color(0xFFCFC4C5),
    surfaceContainerLowest = Color(0xFF0C0E0F),
    surfaceContainerLow = Color(0xFF191C1E),
    surfaceContainer = Color(0xFF1D2022),
    surfaceContainerHigh = Color(0xFF282A2C),
    surfaceContainerHighest = Color(0xFF333537),
    outline = Color(0xFF988E8F),
    outlineVariant = Color(0xFF374151),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE1E2E4),
    inverseOnSurface = Color(0xFF2E3132),
    inversePrimary = Color(0xFF5E5E5E)
)

private fun text(
    size: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
    line: androidx.compose.ui.unit.TextUnit,
    tracking: androidx.compose.ui.unit.TextUnit = 0.sp
) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size,
    lineHeight = line,
    letterSpacing = tracking
)

/** Typography mapped from DESIGN.md (Inter). */
private val OrderlyTypography = Typography(
    displayLarge = text(32.sp, FontWeight.Bold, 40.sp, (-0.64).sp), // display-lg
    displayMedium = text(28.sp, FontWeight.Bold, 36.sp, (-0.56).sp), // display-lg-mobile
    headlineMedium = text(20.sp, FontWeight.SemiBold, 28.sp), // headline-md
    headlineSmall = text(24.sp, FontWeight.Bold, 32.sp, (-0.24).sp), // amount-lg
    titleLarge = text(20.sp, FontWeight.SemiBold, 28.sp), // headline-md
    titleMedium = text(16.sp, FontWeight.SemiBold, 24.sp),
    bodyLarge = text(16.sp, FontWeight.Normal, 24.sp), // body-md
    bodyMedium = text(14.sp, FontWeight.Normal, 20.sp), // body-sm
    labelLarge = text(14.sp, FontWeight.Medium, 20.sp),
    labelMedium = text(12.sp, FontWeight.Medium, 16.sp), // label-sm
    labelSmall = text(12.sp, FontWeight.SemiBold, 16.sp, 0.6.sp) // label-caps
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
