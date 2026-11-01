package com.openride.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    selectedTab: NavigationTabItem = NavigationTabItem.HOME,
    onTabChange: (NavigationTabItem) -> Unit = {},
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
    mapplsApiKey: String = "",
    onSaveMapplsApiKey: (String) -> Unit = {},
    useDynamicColor: Boolean = true,
    themeMode: String = "system",
    onToggleDynamicColor: (Boolean) -> Unit = {},
    onChangeThemeMode: (String) -> Unit = {},
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
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        // Android 16 stroke hairline divider
                        drawLine(
                            color = outlineColor,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
            ) {
                NavigationTabItem.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { onTabChange(tab) },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                        onStartRideClick = { onTabChange(NavigationTabItem.NAVIGATION) },
                        onConnectBleClick = onPairBluetoothClick,
                        onSwitchBikeClick = onSwitchBikeClick,
                        onRideModeChange = onRideModeChange,
                        onBookServiceClick = { onTabChange(NavigationTabItem.SERVICE) },
                        onDocumentsClick = { onTabChange(NavigationTabItem.PROFILE) },
                        onLocateBikeClick = { onTabChange(NavigationTabItem.NAVIGATION) },
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
                        mapplsApiKey = mapplsApiKey,
                        onSaveMapplsApiKey = onSaveMapplsApiKey,
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
                        mapplsApiKey = mapplsApiKey,
                        onSaveMapplsApiKey = onSaveMapplsApiKey,
                        useDynamicColor = useDynamicColor,
                        themeMode = themeMode,
                        onToggleDynamicColor = onToggleDynamicColor,
                        onChangeThemeMode = onChangeThemeMode,
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
