package com.openride.companion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.openride.companion.location.NearbyCategory
import com.openride.companion.location.ParsedDestination
import com.openride.companion.model.ClusterType
import com.openride.companion.model.LiveNavInstruction
import com.openride.companion.model.TripRecord
import com.openride.companion.model.VehicleInfo

enum class NavigationTabItem(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    NAVIGATION("Navigation", Icons.Default.Navigation),
    TRIPS("Trips", Icons.Default.Timeline),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun MainBottomNavigation(
    vehicle: VehicleInfo,
    activeInstruction: LiveNavInstruction,
    isNavigating: Boolean,
    trips: List<TripRecord>,
    currentDestination: ParsedDestination?,
    searchResults: List<ParsedDestination>,
    isSearching: Boolean,
    userLatitude: Double?,
    userLongitude: Double?,
    routeCoordinates: List<Pair<Double, Double>>,
    isAutoSmsEnabled: Boolean,
    customAutoSmsText: String,
    onClusterTypeChange: (ClusterType) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (NearbyCategory) -> Unit,
    onDestinationSelected: (ParsedDestination) -> Unit,
    onStartNavigation: (ParsedDestination) -> Unit,
    onStopNavigation: () -> Unit,
    onExternalMapsFallback: (ParsedDestination) -> Unit,
    onTripClick: (TripRecord) -> Unit,
    onAutoSmsToggle: (Boolean) -> Unit,
    onCustomSmsChange: (String) -> Unit,
    onCheckUpdateClick: () -> Unit,
    onPairBluetoothClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(NavigationTabItem.DASHBOARD) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = NavigationBarDefaults.Elevation
            ) {
                NavigationTabItem.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavigationTabItem.DASHBOARD -> {
                    DashboardTab(
                        vehicle = vehicle,
                        onClusterTypeChange = onClusterTypeChange,
                        onStartRideClick = { selectedTab = NavigationTabItem.NAVIGATION },
                        onConnectBleClick = onPairBluetoothClick
                    )
                }
                NavigationTabItem.NAVIGATION -> {
                    NavigationTab(
                        activeInstruction = activeInstruction,
                        isNavigating = isNavigating,
                        currentDestination = currentDestination,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        userLatitude = userLatitude,
                        userLongitude = userLongitude,
                        routeCoordinates = routeCoordinates,
                        onSearchQueryChange = onSearchQueryChange,
                        onCategorySelected = onCategorySelected,
                        onDestinationSelected = onDestinationSelected,
                        onStartNavigation = onStartNavigation,
                        onStopNavigation = onStopNavigation,
                        onExternalMapsFallback = onExternalMapsFallback
                    )
                }
                NavigationTabItem.TRIPS -> {
                    TripsTab(
                        trips = trips,
                        onTripClick = onTripClick
                    )
                }
                NavigationTabItem.SETTINGS -> {
                    VehicleSettingsTab(
                        vehicle = vehicle,
                        isAutoSmsEnabled = isAutoSmsEnabled,
                        customAutoSmsText = customAutoSmsText,
                        onAutoSmsToggle = onAutoSmsToggle,
                        onCustomSmsChange = onCustomSmsChange,
                        onCheckUpdateClick = onCheckUpdateClick,
                        onPairBluetoothClick = onPairBluetoothClick
                    )
                }
            }
        }
    }
}
