package com.openride.companion.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Login : Screen("login")
    data class Otp(val dialCode: String, val phoneNumber: String) : Screen("otp/$dialCode/$phoneNumber")
    data object Garage : Screen("garage")
    data object Navigation : Screen("navigation")
    data object Trips : Screen("trips")
    data class TripDetail(val tripId: String) : Screen("trip_detail/$tripId")
    data object DocumentLocker : Screen("document_locker")
    data object Profile : Screen("profile")
    data object Sos : Screen("sos")
}
