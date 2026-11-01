package com.openride.companion.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Authentic Bajaj Ride Connect Theme Palette
private val BajajColorPalette = darkColorScheme(
    primary = Color(0xFF0066FF),             // Iconic Bajaj Royal Blue
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0A2972),    // Deep Navy Header/Banner
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF00E5FF),           // Bajaj Electric Cyan
    onSecondary = Color(0xFF001E24),
    secondaryContainer = Color(0xFF004D56),
    onSecondaryContainer = Color(0xFF80F2FF),
    tertiary = Color(0xFFFF9100),            // Warning / Service Amber
    onTertiary = Color(0xFF3E1C00),
    background = Color(0xFF070C14),          // Midnight Obsidian Canvas
    onBackground = Color(0xFFF0F4F8),
    surface = Color(0xFF0E1726),             // Elevated Bajaj Card Container
    onSurface = Color(0xFFF0F4F8),
    surfaceVariant = Color(0xFF162238),      // Secondary Card Container
    onSurfaceVariant = Color(0xFF94A3B8),    // Muted Label Slate
    outline = Color(0xFF233654),             // Subtle Card Border
    error = Color(0xFFFF3B30),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun OpenRideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BajajColorPalette,
        content = content
    )
}
