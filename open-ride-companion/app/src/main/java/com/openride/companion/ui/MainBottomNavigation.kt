package com.openride.companion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.openride.companion.location.NearbyCategory
import com.openride.companion.location.ParsedDestination
import com.openride.companion.model.ClusterType
import com.openride.companion.model.LiveNavInstruction
import com.openride.companion.model.RideMode
import com.openride.companion.model.ServiceCenter
import com.openride.companion.model.TripRecord
import com.openride.companion.model.VehicleDocument
import com.openride.companion.model.VehicleInfo

enum class NavigationTabItem(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    NAVIGATION("Navigation", Icons.Default.Navigation),
    TRIPS("Trips", Icons.Default.Timeline),
    SERVICE("Service", Icons.Default.Build),
    PROFILE("Profile", Icons.Default.Person)
}

@Composable
fun MainBottomNavigation(
    vehicle: VehicleInfo,
    activeInstruction: LiveNavInstruction,
    isNavigating: Boolean,
    trips: List<TripRecord>,
    documents: List<VehicleDocument>,
    serviceCenters: List<ServiceCenter>,
    currentDestination: ParsedDestination?,
    searchResults: List<ParsedDestination>,
    isSearching: Boolean,
    userLatitude: Double?,
    userLongitude: Double?,
    routeCoordinates: List<Pair<Double, Double>>,
    isAutoSmsEnabled: Boolean,
    customAutoSmsText: String,
    onClusterTypeChange: (ClusterType) -> Unit,
    onSwitchBikeClick: (String) -> Unit,
    onRideModeChange: (RideMode) -> Unit,
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
    onPairBluetoothClick: () -> Unit,
    onBookServiceAppointment: (ServiceCenter) -> Unit,
    onCallServiceCenter: (String) -> Unit,
    onViewDocument: (VehicleDocument) -> Unit,
    onEmergencyRsaClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(NavigationTabItem.HOME) }

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
                NavigationTabItem.HOME -> {
                    DashboardTab(
                        vehicle = vehicle,
                        onClusterTypeChange = onClusterTypeChange,
                        onStartRideClick = { selectedTab = NavigationTabItem.NAVIGATION },
                        onConnectBleClick = onPairBluetoothClick,
                        onSwitchBikeClick = onSwitchBikeClick,
                        onRideModeChange = onRideModeChange,
                        onBookServiceClick = { selectedTab = NavigationTabItem.SERVICE },
                        onDocumentsClick = { selectedTab = NavigationTabItem.PROFILE },
                        onLocateBikeClick = { selectedTab = NavigationTabItem.NAVIGATION },
                        onEmergencyRsaClick = onEmergencyRsaClick
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
                NavigationTabItem.SERVICE -> {
                    ServiceTab(
                        vehicle = vehicle,
                        serviceCenters = serviceCenters,
                        onBookAppointmentClick = onBookServiceAppointment,
                        onCallCenterClick = onCallServiceCenter
                    )
                }
                NavigationTabItem.PROFILE -> {
                    VehicleSettingsTab(
                        vehicle = vehicle,
                        documents = documents,
                        isAutoSmsEnabled = isAutoSmsEnabled,
                        customAutoSmsText = customAutoSmsText,
                        onAutoSmsToggle = onAutoSmsToggle,
                        onCustomSmsChange = onCustomSmsChange,
                        onCheckUpdateClick = onCheckUpdateClick,
                        onPairBluetoothClick = onPairBluetoothClick,
                        onViewDocumentClick = onViewDocument
                    )
                }
            }
        }
    }
}
