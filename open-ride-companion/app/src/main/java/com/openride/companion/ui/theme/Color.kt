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
    primary = ActionBlue,
    onPrimary = Color.White,
    primaryContainer = ElevatedNavy,
    onPrimaryContainer = Color.White,
    secondary = ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = BrandNavy,
    onSecondaryContainer = LightBlueAccent,
    tertiary = CyanAccent,
    onTertiary = Color.Black,
    background = SurfaceDarkBase,
    onBackground = SurfaceDarkText,
    surface = SurfaceDarkCard,
    onSurface = SurfaceDarkText,
    surfaceVariant = ElevatedNavy,
    onSurfaceVariant = SurfaceDarkTextSec,
    outline = SurfaceDarkBorder,
    outlineVariant = Color(0xFF282828),
    error = CriticalRed,
    onError = Color.White
)

val BajajLightColorScheme = lightColorScheme(
    primary = ActionBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = BrandNavy,
    secondary = BrandNavy,
    onSecondary = Color.White,
    secondaryContainer = SurfaceLightCard,
    onSecondaryContainer = BrandNavy,
    tertiary = ElectricBlue,
    onTertiary = Color.White,
    background = SurfaceLightBase,
    onBackground = SurfaceLightText,
    surface = SurfaceLightBase,
    onSurface = SurfaceLightText,
    surfaceVariant = SurfaceLightInput,
    onSurfaceVariant = SurfaceLightTextSec,
    outline = SurfaceLightBorder,
    outlineVariant = Color(0xFFD1D1D6),
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
