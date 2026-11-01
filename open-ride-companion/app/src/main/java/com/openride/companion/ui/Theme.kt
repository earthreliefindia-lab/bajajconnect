package com.openride.companion.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Authentic Bajaj Ride Connect Dark Scheme (Fallback when dynamic wallpaper color is disabled)
val BajajDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4C8DFF),             // Iconic Bajaj Royal Blue Accent
    onPrimary = Color(0xFF002B73),
    primaryContainer = Color(0xFF0A2972),    // Deep Navy Header/Banner
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFF00E5FF),           // Bajaj Electric Cyan
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF004F58),
    onSecondaryContainer = Color(0xFF8CF3FF),
    tertiary = Color(0xFFFFB77C),            // Amber Highlight
    onTertiary = Color(0xFF4D2700),
    background = Color(0xFF0B1019),          // Android 16 Midnight Obsidian
    onBackground = Color(0xFFE2E6EF),
    surface = Color(0xFF111723),             // Elevated Card Canvas
    onSurface = Color(0xFFE2E6EF),
    surfaceVariant = Color(0xFF192233),      // Secondary Tonal Container
    onSurfaceVariant = Color(0xFF909EB4),    // Muted Slate Label
    outline = Color(0xFF28364E),             // Android 16 Sleek Card Border Stroke
    outlineVariant = Color(0xFF1F2B3E),      // Subtle Stroke
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// Authentic Bajaj Ride Connect Light Scheme
val BajajLightColorScheme = lightColorScheme(
    primary = Color(0xFF0056D2),             // Classic Bajaj Blue
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE2FF),
    onPrimaryContainer = Color(0xFF001945),
    secondary = Color(0xFF006874),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF97F0FF),
    onSecondaryContainer = Color(0xFF001F24),
    tertiary = Color(0xFF8B5000),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F9FF),          // Clean Light Canvas
    onBackground = Color(0xFF171B23),
    surface = Color(0xFFFFFFFF),             // Pure White Card Canvas
    onSurface = Color(0xFF171B23),
    surfaceVariant = Color(0xFFE8ECF5),      // Soft Tonal Fill
    onSurfaceVariant = Color(0xFF485160),    // Charcoal Label
    outline = Color(0xFFD0D6E4),             // Android 16 Crisp Light Stroke
    outlineVariant = Color(0xFFE4E8F2),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

/**
 * OpenRideTheme:
 * Implements Android 16 Material You Dynamic Color extraction from device wallpaper,
 * combined with minimalist Google-style tonal surfaces and stroke borders.
 */
@Composable
fun OpenRideTheme(
    useDynamicColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        // Material You Wallpaper Dynamic Color on Android 12, 13, 14, 15, and 16
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        darkTheme -> BajajDarkColorScheme
        else -> BajajLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
