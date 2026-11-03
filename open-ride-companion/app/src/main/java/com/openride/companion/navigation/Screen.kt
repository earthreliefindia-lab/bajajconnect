package com.openride.companion.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Login : Screen("login")
    data class Otp(val dialCode: String, val phoneNumber: String) : Screen("otp/$dialCode/$phoneNumber")
    data object Garage : Screen("garage")
    data object Profile : Screen("profile")
    data object Sos : Screen("sos")
}
