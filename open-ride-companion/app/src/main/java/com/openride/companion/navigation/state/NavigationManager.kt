package com.openride.companion.navigation.state

import com.openride.companion.ble.OctGattConstants
import com.openride.companion.ble.VehicleTelemetryProvider
import com.openride.companion.navigation.ble.PrimaryTurns
import com.openride.companion.navigation.ble.TbtManeuvers
import com.openride.companion.navigation.ble.TbtNavFrameBuilder
import com.openride.companion.navigation.engine.RerouteTracker
import com.openride.companion.navigation.engine.RouteCalculationEngine
import com.openride.companion.navigation.model.GeoPoint
import com.openride.companion.navigation.model.NavigationRoute
import com.openride.companion.navigation.model.NavigationStep
import com.openride.companion.safety.MotionSafetyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Navigation lifecycle states.
 */
enum class NavigationState {
    IDLE,
    ROUTE_PLANNING,
    NAVIGATING,
    REROUTING,
    PAUSED,
    ARRIVED
}

/**
 * Voice guidance announcement stages.
 */
enum class VoiceAlertStage {
    APPROACH,   // ~500m
    PREPARE,    // ~200m
    EXECUTE     // ~30m
}

/**
 * Central Navigation Manager orchestrating route tracking, step advance,
 * deviation detection, voice guidance dispatch, and BLE cluster frame transmission.
 */
class NavigationManager(
    val telemetryProvider: VehicleTelemetryProvider? = null,
    val safetyManager: MotionSafetyManager? = null,
    val routeEngine: RouteCalculationEngine? = null,
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _navigationState = MutableStateFlow(NavigationState.IDLE)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val _currentRoute = MutableStateFlow<NavigationRoute?>(null)
    val currentRoute: StateFlow<NavigationRoute?> = _currentRoute.asStateFlow()

    private val _activeStepIndex = MutableStateFlow(0)
    val activeStepIndex: StateFlow<Int> = _activeStepIndex.asStateFlow()

    private val _remainingStepDistanceMeters = MutableStateFlow(0)
    val remainingStepDistanceMeters: StateFlow<Int> = _remainingStepDistanceMeters.asStateFlow()

    private val _totalRemainingDistanceMeters = MutableStateFlow(0)
    val totalRemainingDistanceMeters: StateFlow<Int> = _totalRemainingDistanceMeters.asStateFlow()

    private val _currentVoicePrompt = MutableStateFlow<String?>(null)
    val currentVoicePrompt: StateFlow<String?> = _currentVoicePrompt.asStateFlow()

    private val rerouteTracker = RerouteTracker()
    private var lastEmittedFrame: ByteArray? = null

    val activeStep: NavigationStep?
        get() {
            val route = _currentRoute.value ?: return null
            val idx = _activeStepIndex.value
            return if (idx in route.steps.indices) route.steps[idx] else null
        }

    val nextStep: NavigationStep?
        get() {
            val route = _currentRoute.value ?: return null
            val idx = _activeStepIndex.value + 1
            return if (idx in route.steps.indices) route.steps[idx] else null
        }

    fun selectRoute(route: NavigationRoute) {
        _currentRoute.value = route
        _activeStepIndex.value = 0
        _remainingStepDistanceMeters.value = route.steps.firstOrNull()?.distanceMeters ?: 0
        _totalRemainingDistanceMeters.value = route.distanceMeters
        _navigationState.value = NavigationState.ROUTE_PLANNING
    }

    fun startNavigation() {
        val route = _currentRoute.value ?: return
        _navigationState.value = NavigationState.NAVIGATING
        _activeStepIndex.value = 0
        rerouteTracker.reset()
        sendTbtBleFrame()
    }

    fun pauseNavigation() {
        if (_navigationState.value == NavigationState.NAVIGATING) {
            _navigationState.value = NavigationState.PAUSED
        }
    }

    fun resumeNavigation() {
        if (_navigationState.value == NavigationState.PAUSED) {
            _navigationState.value = NavigationState.NAVIGATING
            sendTbtBleFrame()
        }
    }

    fun stopNavigation() {
        _navigationState.value = NavigationState.IDLE
        _currentRoute.value = null
        _activeStepIndex.value = 0
        rerouteTracker.reset()
    }

    fun onLocationUpdate(currentPos: GeoPoint) {
        val route = _currentRoute.value ?: return
        if (_navigationState.value != NavigationState.NAVIGATING) return

        // 1. Check for off-route deviation
        val offRouteTriggered = rerouteTracker.onLocationUpdate(currentPos, route.polyline)
        if (offRouteTriggered) {
            _navigationState.value = NavigationState.REROUTING
            handleReroute(currentPos, route)
            return
        }

        // 2. Evaluate current step countdown
        val step = activeStep ?: return
        val distToStepEnd = currentPos.distanceTo(step.endCoordinate).roundToInt()
        _remainingStepDistanceMeters.value = distToStepEnd

        // Check voice alert thresholds
        when {
            distToStepEnd in 450..550 -> _currentVoicePrompt.value = "In 500 meters, ${step.voiceInstruction}"
            distToStepEnd in 180..220 -> _currentVoicePrompt.value = "In 200 meters, prepare to ${step.voiceInstruction}"
            distToStepEnd in 20..35 -> _currentVoicePrompt.value = step.voiceInstruction
        }

        // 3. Advance step when within 15 meters of step destination
        if (distToStepEnd <= 15) {
            if (_activeStepIndex.value < route.steps.size - 1) {
                _activeStepIndex.value++
                val next = activeStep
                _remainingStepDistanceMeters.value = next?.distanceMeters ?: 0
                sendTbtBleFrame()
            } else {
                _navigationState.value = NavigationState.ARRIVED
                _remainingStepDistanceMeters.value = 0
                _totalRemainingDistanceMeters.value = 0
                sendDestinationReachedFrame()
            }
        } else {
            sendTbtBleFrame()
        }
    }

    private fun handleReroute(currentPos: GeoPoint, currentRoute: NavigationRoute) {
        val engine = routeEngine
        if (engine != null) {
            val newRoutes = engine.calculateRoutes(
                origin = currentPos,
                destination = currentRoute.polyline.last(),
                waypoints = currentRoute.waypoints,
                avoidTolls = currentRoute.hasTolls.not()
            )
            if (newRoutes.isNotEmpty()) {
                val newRoute = newRoutes.first()
                _currentRoute.value = newRoute
                _activeStepIndex.value = 0
                _remainingStepDistanceMeters.value = newRoute.steps.firstOrNull()?.distanceMeters ?: 0
                _totalRemainingDistanceMeters.value = newRoute.distanceMeters
                _navigationState.value = NavigationState.NAVIGATING
                rerouteTracker.reset()
                sendTbtBleFrame()
            }
        }
    }

    fun sendTbtBleFrame(): Boolean {
        val step = activeStep ?: return false
        val frame = TbtNavFrameBuilder().apply {
            maneuverCode = step.maneuverCode
            distanceToTurnMeters = _remainingStepDistanceMeters.value
            streetName = step.streetName
            roundaboutExit = step.roundaboutExit
            etaMinutes = _currentRoute.value?.etaMinutes ?: 0
        }.build()

        lastEmittedFrame = frame
        return telemetryProvider?.sendTbtFrame(frame) ?: true
    }

    private fun sendDestinationReachedFrame(): Boolean {
        val frame = TbtNavFrameBuilder().apply {
            maneuverCode = TbtManeuvers.DESTINATION_REACHED
            distanceToTurnMeters = 0
            streetName = "Destination Reached"
            etaMinutes = 0
        }.build()

        lastEmittedFrame = frame
        return telemetryProvider?.sendTbtFrame(frame) ?: true
    }
}
