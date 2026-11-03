package com.openride.companion.navigation.engine

import com.openride.companion.ble.OctGattConstants
import com.openride.companion.navigation.model.GeoPoint

/**
 * Evaluates real-time vehicle GPS coordinates against active route polyline
 * and triggers reroute calculations when 3 consecutive deviations exceeding 30 meters occur.
 */
object RouteDeviationEvaluator {
    const val DEVIATION_THRESHOLD_METERS = 30.0
    const val REQUIRED_CONSECUTIVE_DEVIATIONS = 3
    const val REROUTE_HOLD_DURATION_MS = OctGattConstants.TBT_REROUTE_HOLD_MS // 12,000 ms

    /**
     * Determines whether a GPS coordinate is off-route relative to a planned polyline.
     */
    fun isOffRoute(
        currentPos: GeoPoint,
        polyline: List<GeoPoint>,
        thresholdMeters: Double = DEVIATION_THRESHOLD_METERS
    ): Boolean {
        if (polyline.size < 2) return false

        var minDistance = Double.MAX_VALUE
        for (i in 0 until polyline.size - 1) {
            val dist = currentPos.distanceToSegment(polyline[i], polyline[i + 1])
            if (dist < minDistance) {
                minDistance = dist
            }
            if (minDistance <= thresholdMeters) return false
        }
        return minDistance > thresholdMeters
    }
}

/**
 * Stateful tracker debouncing GPS deviation points before triggering an expensive reroute.
 */
class RerouteTracker(
    private val thresholdMeters: Double = RouteDeviationEvaluator.DEVIATION_THRESHOLD_METERS,
    private val requiredConsecutiveCount: Int = RouteDeviationEvaluator.REQUIRED_CONSECUTIVE_DEVIATIONS,
    private val holdDurationMs: Long = RouteDeviationEvaluator.REROUTE_HOLD_DURATION_MS
) {
    private var consecutiveOffRouteCount = 0
    private var rerouteTriggeredTimestampMs: Long = 0L

    fun onLocationUpdate(currentPos: GeoPoint, polyline: List<GeoPoint>): Boolean {
        val offRoute = RouteDeviationEvaluator.isOffRoute(currentPos, polyline, thresholdMeters)
        if (offRoute) {
            consecutiveOffRouteCount++
            if (consecutiveOffRouteCount >= requiredConsecutiveCount) {
                rerouteTriggeredTimestampMs = System.currentTimeMillis()
                return true
            }
        } else {
            consecutiveOffRouteCount = 0
        }
        return false
    }

    val isRerouteHolding: Boolean
        get() {
            if (rerouteTriggeredTimestampMs == 0L) return false
            val elapsed = System.currentTimeMillis() - rerouteTriggeredTimestampMs
            return elapsed < holdDurationMs
        }

    fun reset() {
        consecutiveOffRouteCount = 0
        rerouteTriggeredTimestampMs = 0L
    }
}
