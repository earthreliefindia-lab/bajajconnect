package com.openride.companion.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// High-contrast motorcycle dashboard palette (OLED Black + Neon Cyan / Amber Accents)
private val DarkColorPalette = darkColorScheme(
    primary = Color(0xFF00E5FF),          // High-visibility Cyan for primary navigation actions
    onPrimary = Color(0xFF001F24),
    primaryContainer = Color(0xFF004D56),
    onPrimaryContainer = Color(0xFF80F2FF),
    secondary = Color(0xFFFF9100),        // Amber accent for warnings and alerts
    onSecondary = Color(0xFF3E1C00),
    background = Color(0xFF0A0E12),       // Deep OLED pitch black for S23 Ultra battery conservation
    onBackground = Color(0xFFECEFF1),
    surface = Color(0xFF141A22),          // Elevated dark card surface
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = Color(0xFF1E2632),
    onSurfaceVariant = Color(0xFFB0BEC5),
    outline = Color(0xFF37474F)
)

@Composable
fun OpenRideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorPalette,
        content = content
    )
}
