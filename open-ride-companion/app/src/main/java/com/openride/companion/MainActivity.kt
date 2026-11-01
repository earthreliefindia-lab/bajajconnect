package com.openride.companion

import android.Manifest
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
import com.google.android.gms.location.LocationServices
import com.openride.companion.location.LocalSearchManager
import com.openride.companion.location.LocationIntentParser
import com.openride.companion.location.ParsedDestination
import com.openride.companion.service.ClusterRelayService
import com.openride.companion.ui.NavigationScreen
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
    private var lastKnownLocation: Location? = null

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
            fetchDeviceLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enforce Android 15/16 modern edge-to-edge layout
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestPermissions()
        handleIncomingIntent(intent)
        checkForAppUpdates()

        setContent {
            OpenRideTheme {
                NavigationScreen(
                    currentDestination = currentDestination,
                    searchResults = searchResults,
                    isSearching = isSearching,
                    onSearchQueryChange = { query ->
                        performSearch(query)
                    },
                    onCategorySelected = { category ->
                        performSearch(category.searchQuery)
                    },
                    onDestinationSelected = { dest ->
                        currentDestination = dest
                        searchResults = emptyList()
                    },
                    onStartNavigation = { dest ->
                        startBikeNavigation(dest)
                    },
                    onExternalMapsFallback = { dest ->
                        openInExternalMap(dest)
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
                // Update with distance if current location is known
                val withDistance = lastKnownLocation?.let { loc ->
                    val destLoc = Location("").apply {
                        latitude = parsed.latitude
                        longitude = parsed.longitude
                    }
                    parsed.copy(distanceMeters = loc.distanceTo(destLoc))
                } ?: parsed

                currentDestination = withDistance
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
        ClusterRelayService.startNavigation(this, destination)
        Toast.makeText(
            this,
            "Navigating to ${destination.title}. Relaying turns to bike cluster...",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun openInExternalMap(destination: ParsedDestination) {
        val geoUri = Uri.parse("geo:${destination.latitude},${destination.longitude}?q=${destination.latitude},${destination.longitude}(${Uri.encode(destination.title)})")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            // Avoid looping into this same activity
            setPackage("com.google.android.apps.maps")
        }
        try {
            startActivity(mapIntent)
        } catch (e: Exception) {
            // Fallback to generic chooser
            val genericIntent = Intent(Intent.ACTION_VIEW, geoUri)
            startActivity(Intent.createChooser(genericIntent, "Open with"))
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
            fetchDeviceLocation()
        }
    }

    private fun fetchDeviceLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            fusedClient.lastLocation.addOnSuccessListener { location ->
                lastKnownLocation = location
            }
        }
    }

    private fun checkForAppUpdates() {
        lifecycleScope.launch {
            val update = UpdateChecker.checkForUpdates()
            if (update != null) {
                availableUpdate = update
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
