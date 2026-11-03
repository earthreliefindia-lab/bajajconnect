package com.openride.companion.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// --- Core Palette Tokens ---
val BrandNavy = Color(0xFF002644)
val ElevatedNavy = Color(0xFF0D3C63)
val HeaderNavy = Color(0xFF0E2436)
val ActionBlue = Color(0xFF1565C0)
val ElectricBlue = Color(0xFF245DC1)
val LightBlueAccent = Color(0xFF439CE0)
val CyanAccent = Color(0xFF00E5FF)

val SuccessGreen = Color(0xFF22C55E)
val SuccessGreenDark = Color(0xFF02B875)
val CriticalRed = Color(0xFFFF3B30)
val CriticalRedAccent = Color(0xFFE53935)
val WarningAmber = Color(0xFFFFA000)
val WarningAmberLight = Color(0xFFFFB300)

val SurfaceDarkBase = Color(0xFF121212)
val SurfaceDarkCard = Color(0xFF1C1C1E)
val SurfaceDarkTile = Color(0xFF212121)
val SurfaceDarkBorder = Color(0xFF383838)
val SurfaceDarkText = Color(0xFFFFFFFF)
val SurfaceDarkTextSec = Color(0xFF8E8E93)
val SurfaceDarkTextTert = Color(0xFF636366)

val SurfaceLightBase = Color(0xFFFFFFFF)
val SurfaceLightCard = Color(0xFFF7F3F9)
val SurfaceLightInput = Color(0xFFF2F2F2)
val SurfaceLightBorder = Color(0xFFE5E5EA)
val SurfaceLightText = Color(0xFF1D262D)
val SurfaceLightTextSec = Color(0xFF6E7A8A)
val SurfaceLightTextTert = Color(0xFF9E9E9E)

// --- Material 3 Standard Schemes ---
val BajajDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8), // Google Blue Dark
    onPrimary = Color(0xFF002B75),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD2E3FC),
    secondary = Color(0xFF00E5FF), // Cyan Accent
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = Color(0xFF80CBC4),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color.Black,
    background = Color(0xFF121316),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF1E1F23),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF2B2D33),
    onSurfaceVariant = Color(0xFF9AA0A6),
    outline = Color(0xFF3C4043),
    outlineVariant = Color(0xFF2D3035),
    error = CriticalRed,
    onError = Color.White
)

val BajajLightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8), // Google Blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E3FC),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF00BFA5), // Fresh Teal
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F0FE),
    onSecondaryContainer = Color(0xFF174EA6),
    tertiary = Color(0xFFFF6D00), // Vibrant Coral Amber
    onTertiary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF202124),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202124),
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFDADCE0),
    outlineVariant = Color(0xFFE8EAED),
    error = CriticalRedAccent,
    onError = Color.White
)

// --- Extended Custom Color Tokens ---
@Immutable
data class BajajCustomColors(
    val brandNavy: Color,
    val cardElevated: Color,
    val headerNavy: Color,
    val actionBlue: Color,
    val electricBlue: Color,
    val cyanAccent: Color,
    val successGreen: Color,
    val criticalRed: Color,
    val warningAmber: Color,
    val metricTile: Color,
    val borderMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val searchHighlightBg: Color,
    val searchHighlightText: Color
)

val DarkCustomColors = BajajCustomColors(
    brandNavy = BrandNavy,
    cardElevated = ElevatedNavy,
    headerNavy = HeaderNavy,
    actionBlue = ActionBlue,
    electricBlue = ElectricBlue,
    cyanAccent = CyanAccent,
    successGreen = SuccessGreen,
    criticalRed = CriticalRed,
    warningAmber = WarningAmber,
    metricTile = SurfaceDarkTile,
    borderMuted = SurfaceDarkBorder,
    textPrimary = SurfaceDarkText,
    textSecondary = SurfaceDarkTextSec,
    textTertiary = SurfaceDarkTextTert,
    searchHighlightBg = Color(0x331565C0),
    searchHighlightText = Color(0xFF64B5F6)
)

val LightCustomColors = BajajCustomColors(
    brandNavy = BrandNavy,
    cardElevated = ElevatedNavy,
    headerNavy = HeaderNavy,
    actionBlue = ActionBlue,
    electricBlue = ElectricBlue,
    cyanAccent = CyanAccent,
    successGreen = SuccessGreen,
    criticalRed = CriticalRed,
    warningAmber = WarningAmber,
    metricTile = SurfaceLightCard,
    borderMuted = SurfaceLightBorder,
    textPrimary = SurfaceLightText,
    textSecondary = SurfaceLightTextSec,
    textTertiary = SurfaceLightTextTert,
    searchHighlightBg = Color(0x261565C0),
    searchHighlightText = ActionBlue
)

val LocalBajajCustomColors = staticCompositionLocalOf { DarkCustomColors }

// --- Gradient Presets ---
object BajajGradients {
    val HeroNavy = Brush.verticalGradient(listOf(BrandNavy, ElevatedNavy))
    val Action = Brush.horizontalGradient(listOf(ActionBlue, ElectricBlue))
    val CardElevated = Brush.linearGradient(listOf(ElevatedNavy, Color(0xFF104A7B)))
    val Alert = Brush.horizontalGradient(listOf(CriticalRed, CriticalRedAccent))
    val EcoSuccess = Brush.horizontalGradient(listOf(SuccessGreen, SuccessGreenDark))
}
