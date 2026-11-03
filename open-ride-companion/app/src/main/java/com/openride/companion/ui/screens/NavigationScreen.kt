package com.openride.companion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openride.companion.navigation.ble.TbtManeuvers
import com.openride.companion.navigation.map.ComposeCanvasMapRenderer
import com.openride.companion.navigation.map.MapCameraState
import com.openride.companion.navigation.map.MapThemeMode
import com.openride.companion.navigation.model.GeoPoint
import com.openride.companion.navigation.model.NavigationRoute
import com.openride.companion.navigation.model.NavigationStep
import com.openride.companion.navigation.model.RouteAlternativeType
import com.openride.companion.navigation.state.NavigationManager
import com.openride.companion.navigation.state.NavigationState
import com.openride.companion.safety.MotionSafetyManager
import com.openride.companion.ui.theme.ActionBlue
import com.openride.companion.ui.theme.BrandNavy
import com.openride.companion.ui.theme.CyanAccent
import com.openride.companion.ui.theme.ElevatedNavy
import com.openride.companion.ui.theme.SurfaceDarkBorder
import com.openride.companion.ui.theme.SurfaceDarkCard
import com.openride.companion.ui.theme.SurfaceDarkTextSec

@Composable
fun NavigationScreen(
    navigationManager: NavigationManager,
    safetyManager: MotionSafetyManager? = null,
    onNavigateBack: () -> Unit = {}
) {
    val navState by navigationManager.navigationState.collectAsState()
    val activeRoute by navigationManager.currentRoute.collectAsState()
    val activeStep = navigationManager.activeStep
    val nextStep = navigationManager.nextStep
    val remainingMeters by navigationManager.remainingStepDistanceMeters.collectAsState()
    val voicePrompt by navigationManager.currentVoicePrompt.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }

    val isStationary by (safetyManager?.stationaryFlow ?: remember { kotlinx.coroutines.flow.MutableStateFlow(true) }).collectAsState()
    val isMoving = !isStationary
    val currentSpeedKmh by (safetyManager?.speedFlow ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }).collectAsState()

    val mapRenderer = remember { ComposeCanvasMapRenderer() }
    val cameraState = remember { MapCameraState(centerLat = 18.5204, centerLon = 73.8567, zoomLevel = 14f, isHeadingUp = navState == NavigationState.NAVIGATING) }
    val currentVehiclePos = remember { GeoPoint(18.5204, 73.8567) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Vector Map Canvas
        mapRenderer.RenderMap(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraState,
            route = activeRoute,
            alternateRoutes = emptyList(),
            currentLocation = currentVehiclePos,
            bearingDegrees = 45f,
            traveledPoints = emptyList(),
            waypoints = emptyList(),
            themeMode = MapThemeMode.AUTO
        )

        // 2. Maneuver HUD (Visible during Navigation)
        if (navState == NavigationState.NAVIGATING && activeStep != null) {
            ManeuverHudCard(
                activeStep = activeStep,
                nextStep = nextStep,
                remainingMeters = remainingMeters,
                voicePrompt = voicePrompt,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            )
        }

        // 3. Search Bar / Motion Lockout (Top Header when idle or planning)
        if (navState != NavigationState.NAVIGATING) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                if (isMoving) {
                    // Motion Safety Lockout Banner
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = BrandNavy),
                        border = BorderStroke(1.dp, SurfaceDarkBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color(0xFFFF3B30))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Search locked while motorcycle in motion (${currentSpeedKmh} km/h)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    // Interactive Destination Search Header
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = BrandNavy),
                        border = BorderStroke(1.dp, SurfaceDarkBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = CyanAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search destination, petrol pump, cafes...", color = SurfaceDarkTextSec, fontSize = 14.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = SurfaceDarkTextSec)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Floating Speed Gauge Indicator (Bottom Left)
        Card(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = if (navState == NavigationState.NAVIGATING) 100.dp else 90.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BrandNavy),
            border = BorderStroke(1.dp, SurfaceDarkBorder)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$currentSpeedKmh",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent
                )
                Text(
                    text = "KM/H",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = SurfaceDarkTextSec
                )
            }
        }

        // 5. Bottom Navigation Control Strip
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BrandNavy),
            border = BorderStroke(1.dp, SurfaceDarkBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (navState == NavigationState.NAVIGATING) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeRoute?.name ?: "Active Route",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${activeRoute?.distanceKm ?: 0f} km • ${activeRoute?.etaMinutes ?: 0} min",
                            color = CyanAccent,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = { navigationManager.stopNavigation() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exit", color = Color.White)
                    }
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeRoute?.name ?: "Lonavala Expressway",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "64.2 km • 72 min • Tolls Included",
                            color = CyanAccent,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = { navigationManager.startNavigation() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = Color(0xFF002644))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start", color = Color(0xFF002644), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ManeuverHudCard(
    activeStep: NavigationStep,
    nextStep: NavigationStep?,
    remainingMeters: Int,
    voicePrompt: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BrandNavy),
        border = BorderStroke(1.dp, SurfaceDarkBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Maneuver Direction Icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(ElevatedNavy, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = resolveManeuverIcon(activeStep.maneuverCode)
                    Icon(
                        imageVector = icon,
                        contentDescription = "Turn",
                        tint = CyanAccent,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatDistanceDisplay(remainingMeters),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                    Text(
                        text = activeStep.streetName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!voicePrompt.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = voicePrompt,
                    fontSize = 12.sp,
                    color = Color(0xFF22C55E),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Step Progress Line
            val fraction = (remainingMeters.toFloat() / activeStep.distanceMeters.coerceAtLeast(1).toFloat())
            LinearProgressIndicator(
                progress = { (1f - fraction).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = CyanAccent,
                trackColor = ElevatedNavy
            )

            // Secondary "Then" Next Maneuver Preview
            if (nextStep != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "Then", fontSize = 12.sp, color = SurfaceDarkTextSec)
                    Icon(
                        imageVector = resolveManeuverIcon(nextStep.maneuverCode),
                        contentDescription = null,
                        tint = SurfaceDarkTextSec,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = nextStep.streetName,
                        fontSize = 12.sp,
                        color = SurfaceDarkTextSec,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

fun resolveManeuverIcon(maneuverCode: Int): ImageVector {
    return when (maneuverCode) {
        TbtManeuvers.TURN_LEFT, TbtManeuvers.TURN_SLIGHT_LEFT, TbtManeuvers.TURN_SHARP_LEFT -> Icons.Default.TurnLeft
        TbtManeuvers.TURN_RIGHT, TbtManeuvers.TURN_SLIGHT_RIGHT, TbtManeuvers.TURN_SHARP_RIGHT -> Icons.Default.TurnRight
        TbtManeuvers.DESTINATION_REACHED -> Icons.Default.NearMe
        else -> Icons.Default.Navigation
    }
}

fun formatDistanceDisplay(meters: Int): String {
    return if (meters < 1000) {
        "$meters m"
    } else {
        String.format(java.util.Locale.US, "%.1f km", meters / 1000f)
    }
}
