package com.openride.companion.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// --- Material 3 Standard Shapes ---
val BajajShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// --- Custom Automotive & Notes Search Shapes ---
@Immutable
data class BajajCustomShapes(
    val squirclePill: Shape = RoundedCornerShape(28.dp),
    val card: Shape = RoundedCornerShape(16.dp),
    val tile: Shape = RoundedCornerShape(12.dp),
    val otpBox: Shape = RoundedCornerShape(12.dp),
    val bottomSheet: Shape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    ),
    val chip: Shape = RoundedCornerShape(8.dp),
    val badge: Shape = RoundedCornerShape(6.dp),
    val circle: Shape = CircleShape
)

val LocalBajajCustomShapes = staticCompositionLocalOf { BajajCustomShapes() }
