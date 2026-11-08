package com.openride.companion.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.openride.companion.location.LocalSearchManager
import com.openride.companion.location.NearbyCategory
import com.openride.companion.location.ParsedDestination
import com.openride.companion.location.RouteNavigationManager
import com.openride.companion.navigation.ble.TbtManeuvers
import com.openride.companion.navigation.model.GeoPoint
import com.openride.companion.navigation.model.NavigationRoute
import com.openride.companion.navigation.model.NavigationStep
import com.openride.companion.navigation.model.RouteAlternativeType
import com.openride.companion.navigation.state.NavigationManager
import com.openride.companion.navigation.state.NavigationState
import com.openride.companion.safety.MotionSafetyManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NavigationScreen(
    navigationManager: NavigationManager,
    safetyManager: MotionSafetyManager? = null,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val navState by navigationManager.navigationState.collectAsState()
    val activeRoute by navigationManager.currentRoute.collectAsState()
    val activeStep = navigationManager.activeStep
    val nextStep = navigationManager.nextStep
    val remainingMeters by navigationManager.remainingStepDistanceMeters.collectAsState()
    val voicePrompt by navigationManager.currentVoicePrompt.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ParsedDestination>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var currentDestination by remember { mutableStateOf<ParsedDestination?>(null) }
    var routeCoordinates by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }

    // Real-time GPS location
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var currentSpeedKmh by remember { mutableIntStateOf(0) }

    val isStationary by (safetyManager?.stationaryFlow ?: remember { kotlinx.coroutines.flow.MutableStateFlow(true) }).collectAsState()
    val isMoving = !isStationary

    var searchJob by remember { mutableStateOf<Job?>(null) }
    val strokeBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    // Start Location Listener for Genuine GPS Coordinates & Speed
    DisposableEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                userLocation = loc
                val rawSpeed = if (loc.hasSpeed()) (loc.speed * 3.6f).toInt() else 0
                currentSpeedKmh = if (rawSpeed < 2) 0 else rawSpeed
                if (navState == NavigationState.NAVIGATING) {
                    navigationManager.onLocationUpdate(GeoPoint(loc.latitude, loc.longitude))
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        if (hasFine || hasCoarse) {
            try {
                locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { userLocation = it }
                    ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { userLocation = it }

                locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener)
            } catch (_: SecurityException) {}
        }

        onDispose {
            try {
                locationManager?.removeUpdates(listener)
            } catch (_: SecurityException) {}
        }
    }

    // Function to fetch fresh GPS/Network location immediately on 1 tap
    fun fetchFreshLocation() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        var freshLoc: Location? = null
        try {
            freshLoc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (_: SecurityException) {}

        if (freshLoc != null) {
            userLocation = freshLoc
            android.widget.Toast.makeText(
                context,
                "📍 Location locked: ${String.format(java.util.Locale.US, "%.4f, %.4f", freshLoc.latitude, freshLoc.longitude)}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            android.widget.Toast.makeText(context, "Fetching latest GPS fix...", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Function to calculate route & set destination
    fun selectDestination(dest: ParsedDestination) {
        currentDestination = dest
        searchResults = emptyList()
        searchQuery = dest.title

        coroutineScope.launch {
            val startLat = userLocation?.latitude ?: 18.5204
            val startLng = userLocation?.longitude ?: 73.8567
            val route = RouteNavigationManager.calculateRoute(startLat, startLng, dest.latitude, dest.longitude)

            if (route != null) {
                routeCoordinates = route.coordinates

                val polyGeo = route.coordinates.map { GeoPoint(it.first, it.second) }
                val steps = route.steps.mapIndexed { idx, s ->
                    NavigationStep(
                        stepIndex = idx,
                        maneuverCode = s.maneuverCode,
                        streetName = s.streetName,
                        startCoordinate = polyGeo.getOrElse(idx) { GeoPoint(startLat, startLng) },
                        endCoordinate = polyGeo.getOrElse(idx + 1) { GeoPoint(dest.latitude, dest.longitude) },
                        distanceMeters = s.distanceMeters,
                        durationSeconds = s.durationSeconds.toInt(),
                        voiceInstruction = s.instruction
                    )
                }

                val navRoute = NavigationRoute(
                    id = "route_${System.currentTimeMillis()}",
                    name = dest.title,
                    alternativeType = RouteAlternativeType.FASTEST,
                    polyline = polyGeo,
                    distanceMeters = route.totalDistanceMeters.toInt(),
                    durationSeconds = route.totalDurationSeconds.toInt(),
                    steps = steps,
                    hasTolls = false
                )
                navigationManager.selectRoute(navRoute)
            }
        }
    }

    // Function to execute search with 250ms debouncing
    fun triggerSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return
        }

        isSearching = true
        searchJob = coroutineScope.launch {
            delay(250) // 250ms debouncer
            val results = LocalSearchManager.searchPlaces(context, query.trim(), userLocation)
            searchResults = results
            isSearching = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- 1. Interactive Lightweight Map with Pan & Drag Gestures ---
        NavigationMapView(
            userLatitude = userLocation?.latitude ?: 18.5204,
            userLongitude = userLocation?.longitude ?: 73.8567,
            destination = currentDestination,
            routeCoordinates = routeCoordinates,
            onFetchCurrentLocation = { fetchFreshLocation() },
            modifier = Modifier.fillMaxSize()
        )

        // --- 2. Live Turn-by-Turn Guidance HUD ---
        if (navState == NavigationState.NAVIGATING && activeStep != null) {
            ManeuverHudCard(
                activeStep = activeStep,
                nextStep = nextStep,
                remainingMeters = remainingMeters,
                voicePrompt = voicePrompt,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(16.dp)
            )
        }

        // --- 3. Google / Android 16 Minimalist Search Bar & Quick Categories ---
        if (navState != NavigationState.NAVIGATING) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (isMoving) {
                    // Motion Safety Lockout Banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = strokeBorder,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color(0xFFFF3B30))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Search locked while bike in motion (${currentSpeedKmh} km/h)",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    // Floating Search Pill Header
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = strokeBorder,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { fetchFreshLocation() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "Current Location",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    triggerSearch(it)
                                },
                                placeholder = {
                                    Text(
                                        "Search destination, petrol pump, cafes...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 13.sp
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        searchResults = emptyList()
                                        currentDestination = null
                                        routeCoordinates = emptyList()
                                        navigationManager.stopNavigation()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Quick Category Chips
                    if (searchResults.isEmpty() && currentDestination == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(LocalSearchManager.QUICK_CATEGORIES) { cat ->
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            searchQuery = cat.name
                                            triggerSearch(cat.searchQuery)
                                        },
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                    border = strokeBorder,
                                    shadowElevation = 3.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val icon = when (cat.id) {
                                            "fuel" -> Icons.Default.LocalGasStation
                                            "ev" -> Icons.Default.ElectricBike
                                            "repair" -> Icons.Default.Build
                                            "food" -> Icons.Default.Restaurant
                                            else -> Icons.Default.LocalHospital
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = cat.name,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = cat.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Interactive Search Results Dropdown List
                if (isSearching) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = strokeBorder,
                        shadowElevation = 6.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                } else if (searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = strokeBorder,
                        shadowElevation = 8.dp
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(searchResults) { result ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { selectDestination(result) },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Place,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = result.title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            result.address?.let { addr ->
                                                Text(
                                                    text = addr,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        result.formattedDistance?.let { dist ->
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = dist,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. Floating Speed Gauge Indicator (Bottom Left) ---
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = if (navState == NavigationState.NAVIGATING) 100.dp else 95.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = strokeBorder,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$currentSpeedKmh",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "KM/H",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- 5. Bottom Navigation Control Strip ---
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = strokeBorder,
            shadowElevation = 8.dp
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
                            text = activeRoute?.name ?: currentDestination?.title ?: "Bike Route",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${activeRoute?.distanceKm ?: 0f} km • ${activeRoute?.etaMinutes ?: 0} min • Bike Mode",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = { navigationManager.stopNavigation() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Exit", tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exit", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentDestination?.title ?: "Select Destination",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (currentDestination != null)
                                "${activeRoute?.distanceKm ?: 0f} km • Ready to navigate"
                            else
                                "Tap search or category above",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            if (currentDestination != null && activeRoute != null) {
                                navigationManager.startNavigation()
                            }
                        },
                        enabled = currentDestination != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start", fontWeight = FontWeight.Bold)
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
    val strokeBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = strokeBorder,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = resolveManeuverIcon(activeStep.maneuverCode)
                    Icon(
                        imageVector = icon,
                        contentDescription = "Turn",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatDistanceDisplay(remainingMeters),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = activeStep.streetName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
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
                    color = Color(0xFF00C853),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val fraction = (remainingMeters.toFloat() / activeStep.distanceMeters.coerceAtLeast(1).toFloat())
            LinearProgressIndicator(
                progress = { (1f - fraction).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            if (nextStep != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "Then", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(
                        imageVector = resolveManeuverIcon(nextStep.maneuverCode),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = nextStep.streetName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

fun resolveManeuverIcon(maneuverCode: Int): androidx.compose.ui.graphics.vector.ImageVector {
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
