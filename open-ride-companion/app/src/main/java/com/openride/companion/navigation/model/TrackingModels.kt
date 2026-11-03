package com.openride.companion.navigation.model

import java.util.Locale

/**
 * Instantaneous breadcrumb sample recorded at 1 Hz during an active ride.
 */
data class BreadcrumbPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val speedKmh: Float,
    val leanAngleDegrees: Float,
    val elevationMeters: Float
)

/**
 * Event recorded when motorcycle experiences extreme deceleration exceeding 15 km/h in 1 second.
 */
data class HardBrakingEvent(
    val id: String,
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val speedBeforeKmh: Float,
    val speedAfterKmh: Float,
    val decelerationKmhPerSec: Float
) {
    val decelerationMps2: Float get() = (decelerationKmhPerSec * 1000f) / 3600f
}

/**
 * Live telemetry snapshot published to UI and ride recorder.
 */
data class LiveTelemetry(
    val speedKmh: Int,
    val leanAngleDegrees: Float,
    val elevationMeters: Float,
    val elapsedSeconds: Long,
    val distanceRecordedMeters: Float
)

/**
 * String formatting utilities conforming strictly to E2E test specifications.
 */
object TrackingFormatters {

    /**
     * Formats duration into hours and minutes, e.g. "45m" or "1h 22m".
     */
    fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val mins = (seconds / 60) % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    /**
     * Formats distance in meters to kilometers with 1 decimal place, e.g. "64.2 km".
     */
    fun formatDistance(meters: Float): String {
        return String.format(Locale.US, "%.1f km", meters / 1000f)
    }

    /**
     * Calculates average speed avoiding divide-by-zero.
     */
    fun calculateAverageSpeed(distanceMeters: Float, durationSeconds: Long): Float {
        if (durationSeconds <= 0L) return 0f
        return (distanceMeters / 1000f) / (durationSeconds / 3600f)
    }

    /**
     * Clamps lean angle to safety bounds [-60.0f, +60.0f].
     */
    fun clampLeanAngle(angle: Float): Float {
        return angle.coerceIn(-60f, 60f)
    }
}
