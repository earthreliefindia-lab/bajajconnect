package com.openride.companion.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openride.companion.data.DefaultDocumentLockerRepository
import com.openride.companion.data.DefaultTripRepository
import com.openride.companion.data.DocumentLockerRepository
import com.openride.companion.data.TripRepository
import com.openride.companion.data.auth.AuthRepository
import com.openride.companion.data.preferences.SessionPreferences
import com.openride.companion.data.profile.ProfileRepository
import com.openride.companion.data.sos.EmergencySosRepository
import com.openride.companion.data.vehicle.VehicleRepository
import com.openride.companion.navigation.state.NavigationManager
import com.openride.companion.ui.screens.DocumentLockerScreen
import com.openride.companion.ui.screens.NavigationScreen
import com.openride.companion.ui.screens.TripDetailScreen
import com.openride.companion.ui.screens.TripsScreen
import com.openride.companion.ui.screens.auth.LoginScreen
import com.openride.companion.ui.screens.auth.LoginViewModel
import com.openride.companion.ui.screens.auth.OtpVerificationScreen
import com.openride.companion.ui.screens.auth.OtpViewModel
import com.openride.companion.ui.screens.garage.GarageScreen
import com.openride.companion.ui.screens.garage.GarageViewModel
import com.openride.companion.ui.screens.onboarding.OnboardingScreen
import com.openride.companion.ui.screens.onboarding.OnboardingViewModel
import com.openride.companion.ui.screens.profile.ProfileScreen
import com.openride.companion.ui.screens.profile.ProfileViewModel
import com.openride.companion.ui.screens.sos.SosScreen
import com.openride.companion.ui.screens.sos.SosViewModel
import com.openride.companion.ui.theme.ActionBlue
import com.openride.companion.ui.theme.CyanAccent
import com.openride.companion.ui.theme.ElevatedNavy
import com.openride.companion.ui.theme.SurfaceDarkCard
import com.openride.companion.ui.theme.SurfaceDarkTextSec

@Composable
fun AppNavGraph(
    sessionPreferences: SessionPreferences,
    authRepository: AuthRepository,
    vehicleRepository: VehicleRepository,
    profileRepository: ProfileRepository,
    tripRepository: TripRepository = remember { DefaultTripRepository() },
    documentRepository: DocumentLockerRepository = remember { DefaultDocumentLockerRepository() },
    navigationManager: NavigationManager = remember { NavigationManager() }
) {
    val initialScreen: Screen = if (!sessionPreferences.isOnboardingCompleted()) {
        Screen.Onboarding
    } else {
        Screen.Login
    }

    val backStack = remember { mutableStateListOf<Screen>(initialScreen) }
    val currentScreen = backStack.lastOrNull() ?: Screen.Login

    fun navigateTo(screen: Screen, clearStack: Boolean = false) {
        if (clearStack) {
            backStack.clear()
        }
        backStack.add(screen)
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    BackHandler(enabled = backStack.size > 1) {
        navigateBack()
    }

    val isMainTabScreen = currentScreen is Screen.Garage ||
            currentScreen is Screen.Navigation ||
            currentScreen is Screen.Trips ||
            currentScreen is Screen.Profile

    Scaffold(
        bottomBar = {
            if (isMainTabScreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 6.dp
                ) {
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )

                    NavigationBarItem(
                        selected = currentScreen is Screen.Garage,
                        onClick = {
                            if (currentScreen !is Screen.Garage) {
                                navigateTo(Screen.Garage, clearStack = true)
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = "Garage") },
                        label = { Text("Garage", fontSize = 11.sp, fontWeight = if (currentScreen is Screen.Garage) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal) },
                        colors = navItemColors
                    )

                    NavigationBarItem(
                        selected = currentScreen is Screen.Navigation,
                        onClick = {
                            if (currentScreen !is Screen.Navigation) {
                                navigateTo(Screen.Navigation)
                            }
                        },
                        icon = { Icon(Icons.Default.Navigation, contentDescription = "Navigate") },
                        label = { Text("Navigate", fontSize = 11.sp, fontWeight = if (currentScreen is Screen.Navigation) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal) },
                        colors = navItemColors
                    )

                    NavigationBarItem(
                        selected = currentScreen is Screen.Trips,
                        onClick = {
                            if (currentScreen !is Screen.Trips) {
                                navigateTo(Screen.Trips)
                            }
                        },
                        icon = { Icon(Icons.Default.Route, contentDescription = "Trips") },
                        label = { Text("Trips", fontSize = 11.sp, fontWeight = if (currentScreen is Screen.Trips) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal) },
                        colors = navItemColors
                    )

                    NavigationBarItem(
                        selected = currentScreen is Screen.Profile,
                        onClick = {
                            if (currentScreen !is Screen.Profile) {
                                navigateTo(Screen.Profile)
                            }
                        },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                        label = { Text("Profile", fontSize = 11.sp, fontWeight = if (currentScreen is Screen.Profile) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal) },
                        colors = navItemColors
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
            when (val screen = currentScreen) {
                is Screen.Onboarding -> {
                    val viewModel = remember { OnboardingViewModel(sessionPreferences) }
                    OnboardingScreen(
                        viewModel = viewModel,
                        onNavigateToAuth = {
                            navigateTo(Screen.Login, clearStack = true)
                        }
                    )
                }

                is Screen.Login -> {
                    val viewModel = remember { LoginViewModel(authRepository) }
                    LoginScreen(
                        viewModel = viewModel,
                        onNavigateToOtp = { dialCode, phoneNumber ->
                            navigateTo(Screen.Otp(dialCode, phoneNumber))
                        }
                    )
                }

                is Screen.Otp -> {
                    val viewModel = remember(screen.dialCode, screen.phoneNumber) {
                        OtpViewModel(screen.dialCode, screen.phoneNumber, authRepository)
                    }
                    OtpVerificationScreen(
                        viewModel = viewModel,
                        onVerificationSuccess = {
                            navigateTo(Screen.Garage, clearStack = true)
                        },
                        onNavigateBackToLogin = {
                            navigateBack()
                        }
                    )
                }

                is Screen.Garage -> {
                    val viewModel = remember { GarageViewModel(vehicleRepository) }
                    GarageScreen(
                        viewModel = viewModel,
                        onNavigateToProfile = { navigateTo(Screen.Profile) },
                        onNavigateToNotes = { navigateTo(Screen.RideNotes) },
                        onNavigateToDocs = { navigateTo(Screen.DocumentLocker) }
                    )
                }

                is Screen.Navigation -> {
                    NavigationScreen(
                        navigationManager = navigationManager,
                        onNavigateBack = { navigateBack() }
                    )
                }

                is Screen.Trips -> {
                    TripsScreen(
                        tripRepository = tripRepository,
                        onTripClick = { tripId ->
                            navigateTo(Screen.TripDetail(tripId))
                        }
                    )
                }

                is Screen.TripDetail -> {
                    TripDetailScreen(
                        tripId = screen.tripId,
                        tripRepository = tripRepository,
                        onNavigateBack = { navigateBack() }
                    )
                }

                is Screen.DocumentLocker -> {
                    DocumentLockerScreen(
                        documentRepository = documentRepository,
                        onNavigateBack = { navigateBack() }
                    )
                }

                is Screen.RideNotes -> {
                    com.openride.companion.notes.ui.RideNotesScreen(
                        onNavigateBack = { navigateBack() }
                    )
                }

                is Screen.Profile -> {
                    val viewModel = remember { ProfileViewModel(profileRepository) }
                    ProfileScreen(
                        viewModel = viewModel,
                        vehicleRepository = vehicleRepository,
                        tripRepository = tripRepository,
                        documentRepository = documentRepository,
                        onNavigateToAuth = {
                            navigateTo(Screen.Login, clearStack = true)
                        }
                    )
                }

                is Screen.Sos -> {
                    // SOS removed per user request: redirect to Garage
                    navigateTo(Screen.Garage, clearStack = true)
                }
            }
        }
    }
}
