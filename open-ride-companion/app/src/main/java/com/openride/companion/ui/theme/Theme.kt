package com.openride.companion.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import com.openride.companion.model.ThemeMode

/**
 * Authentic Bajaj Ride Connect Theme.
 *
 * Implements the brand design system derived from the original Bajaj Ride Connect APK.
 * Seamlessly integrates Material You dynamic wallpaper colors, Stock Android 16 styling,
 * and user-selected ThemeMode (System, Light, Dark).
 */
@Composable
fun BajajTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Enabled by default to sample Android 12+ wallpaper colors
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemInDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> BajajDarkColorScheme
        else -> BajajLightColorScheme
    }

    val customColors = if (isDark) DarkCustomColors else LightCustomColors
    val automotiveTypography = BajajAutomotiveTypography()
    val customShapes = BajajCustomShapes()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(
        LocalBajajCustomColors provides customColors,
        LocalBajajAutomotiveTypography provides automotiveTypography,
        LocalBajajCustomShapes provides customShapes
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BajajTypography,
            shapes = BajajShapes,
            content = content
        )
    }
}

/**
 * Backwards compatibility alias for the legacy OpenRideTheme.
 */
@Composable
fun OpenRideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    BajajTheme(
        darkTheme = darkTheme,
        dynamicColor = useDynamicColor,
        content = content
    )
}

/**
 * Global accessor for Bajaj Design System tokens.
 */
object BajajTheme {
    val colors: BajajCustomColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBajajCustomColors.current

    val automotiveTypography: BajajAutomotiveTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalBajajAutomotiveTypography.current

    val customShapes: BajajCustomShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalBajajCustomShapes.current

    val gradients: BajajGradients
        get() = BajajGradients
}
