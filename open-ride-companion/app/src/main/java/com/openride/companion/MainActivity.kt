package com.openride.companion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.openride.companion.cluster.BajajClusterBleManager
import com.openride.companion.location.LocalSearchManager
import com.openride.companion.location.LocationIntentParser
import com.openride.companion.location.ParsedDestination
import com.openride.companion.location.RouteNavigationManager
import com.openride.companion.location.RouteResult
import com.openride.companion.model.LiveNavInstruction
import com.openride.companion.model.ManeuverType
import com.openride.companion.repository.VehicleRepository
import com.openride.companion.service.ClusterRelayService
import com.openride.companion.telephony.CallSmsManager
import com.openride.companion.ui.MainBottomNavigation
import com.openride.companion.ui.OpenRideTheme
import com.openride.companion.ui.UpdateDialog
import com.openride.companion.update.AppUpdateInfo
import com.openride.companion.update.UpdateChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var currentDestination by mutableStateOf<ParsedDestination?>(null)
    private var searchResults by mutableStateOf<List<ParsedDestination>>(emptyList())
    private var isSearching by mutableStateOf(false)
    private var isNavigating by mutableStateOf(false)
    private var lastKnownLocation by mutableStateOf<Location?>(null)
    private var routeCoordinates by mutableStateOf<List<Pair<Double, Double>>>(emptyList())

    // Location Tracking
    private var fusedClient: FusedLocationProviderClient? = null
    private var isLocationTrackingActive = false

    // Telephony and BLE Managers
    private lateinit var callSmsManager: CallSmsManager
    private lateinit var clusterBleManager: BajajClusterBleManager

    // In-app update state
    private var availableUpdate by mutableStateOf<AppUpdateInfo?>(null)
    private var isDownloadingUpdate by mutableStateOf(false)
    private var downloadProgress by mutableStateOf<Float?>(null)

    private var searchJob: Job? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineLocationGranted) {
            startLocationTracking()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            lastKnownLocation = location

            // Genuine GPS Speed calculation (0 km/h when standing still)
            val rawSpeedKmh = if (location.hasSpeed()) (location.speed * 3.6f).toInt() else 0
            val speedKmh = if (rawSpeedKmh < 2) 0 else rawSpeedKmh
            VehicleRepository.updateSpeed(speedKmh)

            // If navigating, update remaining distance and next maneuver distance
            if (isNavigating) {
                currentDestination?.let { dest ->
                    val destLoc = Location("").apply {
                        latitude = dest.latitude
                        longitude = dest.longitude
                    }
                    val remainingMeters = location.distanceTo(destLoc)
                    val estSpeed = if (speedKmh > 5) speedKmh.toFloat() else 35f
                    val remainingSeconds = ((remainingMeters / 1000f) / estSpeed * 3600f).toLong()

                    val currentInstruction = VehicleRepository.activeInstruction.value
                    VehicleRepository.updateInstruction(
                        currentInstruction.copy(
                            remainingDistanceMeters = remainingMeters,
                            remainingTimeSeconds = remainingSeconds,
                            currentSpeedKmh = speedKmh
                        )
                    )

                    // Relay to BLE cluster
                    clusterBleManager.sendTbtInstruction(
                        instruction = currentInstruction,
                        clusterType = VehicleRepository.vehicle.value.clusterType
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enforce Android 15/16 modern edge-to-edge layout
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        callSmsManager = CallSmsManager(this)
        clusterBleManager = BajajClusterBleManager(this)

        requestPermissions()
        handleIncomingIntent(intent)
        checkForAppUpdates()

        setContent {
            OpenRideTheme {
                val vehicle by VehicleRepository.vehicle.collectAsState()
                val trips by VehicleRepository.trips.collectAsState()
                val activeInstruction by VehicleRepository.activeInstruction.collectAsState()
                val documents by VehicleRepository.documents.collectAsState()
                val serviceCenters by VehicleRepository.serviceCenters.collectAsState()

                MainBottomNavigation(
                    vehicle = vehicle,
                    activeInstruction = activeInstruction,
                    isNavigating = isNavigating,
                    trips = trips,
                    documents = documents,
                    serviceCenters = serviceCenters,
                    currentDestination = currentDestination,
                    searchResults = searchResults,
                    isSearching = isSearching,
                    userLatitude = lastKnownLocation?.latitude,
                    userLongitude = lastKnownLocation?.longitude,
                    routeCoordinates = routeCoordinates,
                    isAutoSmsEnabled = callSmsManager.isAutoSmsEnabled,
                    customAutoSmsText = callSmsManager.customAutoSmsText,
                    onClusterTypeChange = { type ->
                        VehicleRepository.updateClusterType(type)
                        Toast.makeText(this, "Switched to ${type.displayName}", Toast.LENGTH_SHORT).show()
                    },
                    onSwitchBikeClick = { modelName ->
                        VehicleRepository.switchBike(modelName)
                        Toast.makeText(this, "Active bike switched to $modelName", Toast.LENGTH_SHORT).show()
                    },
                    onRideModeChange = { mode ->
                        VehicleRepository.setRideMode(mode)
                        Toast.makeText(this, "ABS Mode: ${mode.title}", Toast.LENGTH_SHORT).show()
                    },
                    onSearchQueryChange = { query ->
                        performSearch(query)
                    },
                    onCategorySelected = { category ->
                        performSearch(category.searchQuery)
                    },
                    onDestinationSelected = { dest ->
                        currentDestination = dest
                        searchResults = emptyList()

                        // Calculate and show route preview on map immediately
                        lifecycleScope.launch {
                            val startLat = lastKnownLocation?.latitude ?: 18.5204
                            val startLng = lastKnownLocation?.longitude ?: 73.8567
                            val route = RouteNavigationManager.calculateRoute(
                                startLat = startLat,
                                startLng = startLng,
                                destLat = dest.latitude,
                                destLng = dest.longitude
                            )
                            if (route != null) {
                                routeCoordinates = route.coordinates
                            }
                        }
                    },
                    onStartNavigation = { dest ->
                        startBikeNavigation(dest)
                    },
                    onStopNavigation = {
                        stopBikeNavigation()
                    },
                    onExternalMapsFallback = { dest ->
                        openInExternalMap(dest)
                    },
                    onTripClick = { trip ->
                        Toast.makeText(this, "${trip.title}: ${trip.formattedDistance} traveled", Toast.LENGTH_SHORT).show()
                    },
                    onAutoSmsToggle = { enabled ->
                        callSmsManager.isAutoSmsEnabled = enabled
                    },
                    onCustomSmsChange = { text ->
                        callSmsManager.customAutoSmsText = text
                    },
                    onCheckUpdateClick = {
                        checkForAppUpdates(manual = true)
                    },
                    onPairBluetoothClick = {
                        clusterBleManager.startScan()
                        VehicleRepository.toggleBleConnection()
                        Toast.makeText(this, "Scanning & syncing with motorcycle cluster...", Toast.LENGTH_SHORT).show()
                    },
                    onBookServiceAppointment = { center ->
                        Toast.makeText(this, "Service booked at ${center.name}", Toast.LENGTH_LONG).show()
                    },
                    onCallServiceCenter = { phone ->
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, "Calling $phone", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onViewDocument = { doc ->
                        Toast.makeText(this, "${doc.title}: ${doc.documentNumber} (${doc.expiryDate})", Toast.LENGTH_LONG).show()
                    },
                    onEmergencyRsaClick = {
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:18002332453"))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, "Toll-Free RSA: 1800 233 2453", Toast.LENGTH_LONG).show()
                        }
                    }
                )

                // In-App Update Dialog
                availableUpdate?.let { updateInfo ->
                    UpdateDialog(
                        updateInfo = updateInfo,
                        downloadProgress = downloadProgress,
                        isDownloading = isDownloadingUpdate,
                        onConfirmUpdate = {
                            performAppUpdate(updateInfo)
                        },
                        onDismiss = {
                            availableUpdate = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return

        lifecycleScope.launch {
            val parsed = LocationIntentParser.parseIntent(this@MainActivity, incomingIntent)
            if (parsed != null) {
                val withDistance = lastKnownLocation?.let { loc ->
                    val destLoc = Location("").apply {
                        latitude = parsed.latitude
                        longitude = parsed.longitude
                    }
                    parsed.copy(distanceMeters = loc.distanceTo(destLoc))
                } ?: parsed

                currentDestination = withDistance

                // Compute route preview
                val startLat = lastKnownLocation?.latitude ?: 18.5204
                val startLng = lastKnownLocation?.longitude ?: 73.8567
                val route = RouteNavigationManager.calculateRoute(
                    startLat = startLat,
                    startLng = startLng,
                    destLat = parsed.latitude,
                    destLng = parsed.longitude
                )
                if (route != null) {
                    routeCoordinates = route.coordinates
                }

                Toast.makeText(
                    this@MainActivity,
                    "Destination received: ${parsed.title}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return
        }

        searchJob = lifecycleScope.launch {
            delay(350) // Debounce typing
            isSearching = true
            searchResults = LocalSearchManager.searchPlaces(
                context = this@MainActivity,
                query = query,
                currentLocation = lastKnownLocation
            )
            isSearching = false
        }
    }

    private fun startBikeNavigation(destination: ParsedDestination) {
        isNavigating = true
        ClusterRelayService.startNavigation(this, destination)

        lifecycleScope.launch {
            val startLat = lastKnownLocation?.latitude ?: 18.5204
            val startLng = lastKnownLocation?.longitude ?: 73.8567

            val route = RouteNavigationManager.calculateRoute(
                startLat = startLat,
                startLng = startLng,
                destLat = destination.latitude,
                destLng = destination.longitude
            )

            if (route != null) {
                routeCoordinates = route.coordinates

                val firstStep = route.steps.firstOrNull()
                VehicleRepository.updateInstruction(
                    LiveNavInstruction(
                        maneuver = firstStep?.maneuverType ?: ManeuverType.STRAIGHT,
                        instructionText = firstStep?.instruction ?: "Proceed towards destination",
                        distanceToManeuverMeters = firstStep?.distanceMeters ?: 0,
                        nextStreet = firstStep?.streetName ?: destination.title,
                        remainingDistanceMeters = route.totalDistanceMeters,
                        remainingTimeSeconds = route.totalDurationSeconds,
                        currentSpeedKmh = 0,
                        speedLimitKmh = 50,
                        laneGuidance = ""
                    )
                )

                Toast.makeText(
                    this@MainActivity,
                    "Navigation started! Relaying turns to bike cluster...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        startLocationTracking()
    }

    private fun stopBikeNavigation() {
        isNavigating = false
        routeCoordinates = emptyList()
        ClusterRelayService.stopNavigation(this)

        VehicleRepository.updateInstruction(
            LiveNavInstruction(
                maneuver = ManeuverType.STRAIGHT,
                instructionText = "Ready for navigation",
                distanceToManeuverMeters = 0,
                nextStreet = "",
                remainingDistanceMeters = 0f,
                remainingTimeSeconds = 0,
                currentSpeedKmh = 0,
                speedLimitKmh = 50,
                laneGuidance = ""
            )
        )

        Toast.makeText(this, "Navigation ended.", Toast.LENGTH_SHORT).show()
    }

    private fun openInExternalMap(destination: ParsedDestination) {
        // Enforce BIKE MODE ALWAYS in Google Maps:
        // 'mode=l' activates the official Google Maps Two-Wheeler / Motorcycle mode in India
        val navUri = Uri.parse("google.navigation:q=${destination.latitude},${destination.longitude}&mode=l")
        val mapIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            startActivity(mapIntent)
        } catch (e: Exception) {
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${destination.latitude},${destination.longitude}&travelmode=two-wheeler")
            val genericIntent = Intent(Intent.ACTION_VIEW, webUri)
            startActivity(Intent.createChooser(genericIntent, "Two-Wheeler Bike Navigation"))
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            startLocationTracking()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val client = fusedClient ?: LocationServices.getFusedLocationProviderClient(this).also { fusedClient = it }

        client.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                lastKnownLocation = location
            }
        }

        if (!isLocationTrackingActive) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .setMinUpdateDistanceMeters(1f)
                .build()

            client.requestLocationUpdates(request, locationCallback, mainLooper)
            isLocationTrackingActive = true
        }
    }

    private fun stopLocationTracking() {
        if (isLocationTrackingActive) {
            fusedClient?.removeLocationUpdates(locationCallback)
            isLocationTrackingActive = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationTracking()
    }

    private fun checkForAppUpdates(manual: Boolean = false) {
        lifecycleScope.launch {
            val update = UpdateChecker.checkForUpdates()
            if (update != null) {
                availableUpdate = update
            } else if (manual) {
                Toast.makeText(this@MainActivity, "You have the latest version installed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performAppUpdate(updateInfo: AppUpdateInfo) {
        lifecycleScope.launch {
            isDownloadingUpdate = true
            val downloadedApk = UpdateChecker.downloadApk(this@MainActivity, updateInfo) { progress ->
                downloadProgress = progress
            }
            isDownloadingUpdate = false
            if (downloadedApk != null) {
                UpdateChecker.installApk(this@MainActivity, downloadedApk)
            } else {
                Toast.makeText(this@MainActivity, "Failed to download update APK", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
