package com.openride.companion.data.auth

import com.openride.companion.model.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton/Shared manager for rider authentication session state across the app lifecycle.
 */
class AuthSessionManager {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    fun setSession(token: String, dialCode: String, phoneNumber: String, activeVehicleId: String? = null) {
        _sessionState.value = SessionState.Authenticated(
            token = token,
            dialCode = dialCode,
            phoneNumber = phoneNumber,
            activeVehicleId = activeVehicleId
        )
    }

    fun setActiveVehicle(vehicleId: String) {
        val current = _sessionState.value
        if (current is SessionState.Authenticated) {
            _sessionState.value = current.copy(activeVehicleId = vehicleId)
        }
    }

    fun clearSession() {
        _sessionState.value = SessionState.Unauthenticated
    }

    val isAuthenticated: Boolean
        get() = _sessionState.value is SessionState.Authenticated

    companion object {
        val instance by lazy { AuthSessionManager() }
    }
}
