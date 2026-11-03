package com.openride.companion.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.sp
import com.openride.companion.data.auth.AuthRepository
import com.openride.companion.data.preferences.SessionPreferences
import com.openride.companion.data.profile.ProfileRepository
import com.openride.companion.data.sos.EmergencySosRepository
import com.openride.companion.data.vehicle.VehicleRepository
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
    sosRepository: EmergencySosRepository
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
            currentScreen is Screen.Profile ||
            currentScreen is Screen.Sos

    Scaffold(
        bottomBar = {
            if (isMainTabScreen) {
                NavigationBar(
                    containerColor = SurfaceDarkCard,
                    contentColor = Color.White
                ) {
                    NavigationBarItem(
                        selected = currentScreen is Screen.Garage,
                        onClick = {
                            if (currentScreen !is Screen.Garage) {
                                navigateTo(Screen.Garage, clearStack = true)
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = "Garage") },
                        label = { Text("Garage", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanAccent,
                            selectedTextColor = CyanAccent,
                            unselectedIconColor = SurfaceDarkTextSec,
                            unselectedTextColor = SurfaceDarkTextSec,
                            indicatorColor = ElevatedNavy
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen is Screen.Sos,
                        onClick = {
                            if (currentScreen !is Screen.Sos) {
                                navigateTo(Screen.Sos)
                            }
                        },
                        icon = { Icon(Icons.Default.Security, contentDescription = "Emergency SOS") },
                        label = { Text("SOS", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanAccent,
                            selectedTextColor = CyanAccent,
                            unselectedIconColor = SurfaceDarkTextSec,
                            unselectedTextColor = SurfaceDarkTextSec,
                            indicatorColor = ElevatedNavy
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen is Screen.Profile,
                        onClick = {
                            if (currentScreen !is Screen.Profile) {
                                navigateTo(Screen.Profile)
                            }
                        },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                        label = { Text("Profile", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanAccent,
                            selectedTextColor = CyanAccent,
                            unselectedIconColor = SurfaceDarkTextSec,
                            unselectedTextColor = SurfaceDarkTextSec,
                            indicatorColor = ElevatedNavy
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
                        onNavigateToSos = { navigateTo(Screen.Sos) }
                    )
                }

                is Screen.Profile -> {
                    val viewModel = remember { ProfileViewModel(profileRepository) }
                    ProfileScreen(
                        viewModel = viewModel,
                        onNavigateToSos = { navigateTo(Screen.Sos) },
                        onNavigateToAuth = {
                            navigateTo(Screen.Login, clearStack = true)
                        }
                    )
                }

                is Screen.Sos -> {
                    val viewModel = remember { SosViewModel(sosRepository, profileRepository) }
                    SosScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navigateBack() }
                    )
                }
            }
        }
    }
}
