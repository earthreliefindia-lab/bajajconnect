package com.openride.companion.safety

import com.openride.companion.wifi.TouchPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Motion Safety Interlock Subsystem.
 * Enforces strict motorcycle safety policy:
 * - isBikeStationary = (currentSpeedKmh == 0)
 * - Input speeds clamped to >= 0
 * - Moving bike (speed > 0) immediately dismisses search keyboard, locks deep screens,
 *   displays lockout warning banner, suppresses cluster touch events, and pauses video mirror.
 * - Stationary bike (speed == 0) restores interactive control and releases lockouts.
 */
class MotionSafetyManager {

    var currentSpeedKmh: Int = 0
        private set

    val isBikeStationary: Boolean
        get() = currentSpeedKmh == 0

    var isSearchKeyboardVisible: Boolean = false
        private set

    var isScreenLocked: Boolean = false
        private set

    var lockoutWarningDisplayed: Boolean = false
        private set

    var isVideoMirroringPaused: Boolean = false
        private set

    // Observable flows for Compose UI & Background services
    private val _speedFlow = MutableStateFlow(0)
    val speedFlow: StateFlow<Int> = _speedFlow.asStateFlow()

    private val _stationaryFlow = MutableStateFlow(true)
    val stationaryFlow: StateFlow<Boolean> = _stationaryFlow.asStateFlow()

    private val _screenLockedFlow = MutableStateFlow(false)
    val screenLockedFlow: StateFlow<Boolean> = _screenLockedFlow.asStateFlow()

    /**
     * Updates current vehicle speed reading from GPS or BLE telematics.
     * Clamps negative sensor values to 0.
     */
    fun updateSpeed(speedKmh: Int) {
        currentSpeedKmh = speedKmh.coerceAtLeast(0)
        _speedFlow.value = currentSpeedKmh
        val stationary = (currentSpeedKmh == 0)
        _stationaryFlow.value = stationary

        if (!stationary) {
            // Motion safety interlock triggers
            isSearchKeyboardVisible = false
            isScreenLocked = true
            lockoutWarningDisplayed = true
            isVideoMirroringPaused = true
            _screenLockedFlow.value = true
        } else {
            // Stationary: Interactive controls restored
            isScreenLocked = false
            lockoutWarningDisplayed = false
            isVideoMirroringPaused = false
            _screenLockedFlow.value = false
        }
    }

    /**
     * Requests opening the search keyboard.
     * Permitted ONLY when motorcycle is completely stationary (speed == 0).
     */
    fun tryOpenSearchKeyboard(): Boolean {
        return if (isBikeStationary) {
            isSearchKeyboardVisible = true
            true
        } else {
            false // Strictly blocked by automotive safety policy
        }
    }

    /**
     * Manually dismisses the search keyboard.
     */
    fun dismissSearchKeyboard() {
        isSearchKeyboardVisible = false
    }

    /**
     * Touchscreen event filter:
     * Touch events are accepted when stationary, but suppressed while in motion.
     */
    fun handleClusterTouch(touch: TouchPoint): Boolean {
        return isBikeStationary
    }
}
