package com.openride.companion.navigation.engine

import com.openride.companion.navigation.model.BreadcrumbPoint
import com.openride.companion.navigation.model.GeoPoint
import com.openride.companion.navigation.model.HardBrakingEvent
import com.openride.companion.navigation.model.LiveTelemetry
import com.openride.companion.navigation.model.TrackingFormatters
import com.openride.companion.navigation.model.TripRecord
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/**
 * Validates GPS coordinate movements against physical kinematic maximums
 * to eliminate cellular and multipath teleportation spikes.
 */
object GpsJitterFilter {
    const val MAX_SPEED_MPS = 70.0 // ~252 km/h
    const val MAX_ACCEPTABLE_ACCURACY_METERS = 25.0f

    fun isValidStep(
        p1: GeoPoint,
        p2: GeoPoint,
        deltaSeconds: Long,
        accuracyMeters: Float = 5.0f
    ): Boolean {
        if (accuracyMeters > MAX_ACCEPTABLE_ACCURACY_METERS) return false
        val distMeters = p1.distanceTo(p2)
        val speedMps = distMeters / deltaSeconds.coerceAtLeast(1)
        return speedMps < MAX_SPEED_MPS
    }
}

/**
 * Clamps raw gyro/IMU lean angles to motorcycle physical safety limits [-60°, +60°]
 * and records peak left and right cornering angles.
 */
class LeanAngleProcessor {
    var maxLeanAngleLeft: Float = 0f
        private set
    var maxLeanAngleRight: Float = 0f
        private set

    fun processAngle(rawAngleDegrees: Float): Float {
        val clamped = rawAngleDegrees.coerceIn(-60f, 60f)
        if (clamped < 0f) {
            maxLeanAngleLeft = max(maxLeanAngleLeft, abs(clamped))
        } else if (clamped > 0f) {
            maxLeanAngleRight = max(maxLeanAngleRight, clamped)
        }
        return clamped
    }

    fun reset() {
        maxLeanAngleLeft = 0f
        maxLeanAngleRight = 0f
    }
}

/**
 * Detects harsh braking events when deceleration exceeds 15 km/h per second.
 */
class HardBrakingDetector(
    private val thresholdKmhPerSec: Float = 15.0f
) {
    private var lastSpeedKmh: Float? = null
    private var lastTimestampMs: Long = 0L

    fun evaluate(
        currentSpeedKmh: Float,
        currentTimestampMs: Long,
        currentLat: Double,
        currentLon: Double
    ): HardBrakingEvent? {
        val prevSpeed = lastSpeedKmh
        val prevTime = lastTimestampMs
        lastSpeedKmh = currentSpeedKmh
        lastTimestampMs = currentTimestampMs

        if (prevSpeed == null || prevTime == 0L) return null
        val deltaSeconds = (currentTimestampMs - prevTime) / 1000.0f
        if (deltaSeconds <= 0f || deltaSeconds > 2.0f) return null // Guard against long pauses

        val speedDrop = prevSpeed - currentSpeedKmh
        val decelerationRate = speedDrop / deltaSeconds

        if (decelerationRate > thresholdKmhPerSec) {
            return HardBrakingEvent(
                id = "brake_${currentTimestampMs}",
                timestampMillis = currentTimestampMs,
                latitude = currentLat,
                longitude = currentLon,
                speedBeforeKmh = prevSpeed,
                speedAfterKmh = currentSpeedKmh,
                decelerationKmhPerSec = decelerationRate
            )
        }
        return null
    }

    fun reset() {
        lastSpeedKmh = null
        lastTimestampMs = 0L
    }
}

/**
 * Elevation profile tracker supporting below sea-level (negative) altitudes
 * and jitter deadband filtering.
 */
class ElevationTracker {
    var currentElevationMeters: Float = 0f
        private set
    var elevationGainMeters: Float = 0f
        private set
    private var lastRecordedElevation: Float? = null

    fun updateElevation(elevationMeters: Float) {
        currentElevationMeters = elevationMeters
        val prev = lastRecordedElevation
        if (prev != null) {
            val delta = elevationMeters - prev
            if (delta > 1.5f) { // 1.5m deadband for barometer / satellite jitter
                elevationGainMeters += delta
            }
        }
        lastRecordedElevation = elevationMeters
    }

    fun reset() {
        currentElevationMeters = 0f
        elevationGainMeters = 0f
        lastRecordedElevation = null
    }
}

/**
 * 1 Hz live ride recorder combining telemetry speed, IMU lean, elevation,
 * and GPS breadcrumbs into an active session.
 */
class LiveRideRecorder(
    val vehicleVin: String = "MD2A11CY4P8712391",
    val vehicleModel: String = "Pulsar NS400Z"
) {
    var isRecording: Boolean = false
        private set
    var sessionStartTimeMs: Long = 0L
        private set
    var startLocationName: String = "Current Location"
        private set

    val leanProcessor = LeanAngleProcessor()
    val brakingDetector = HardBrakingDetector()
    val elevationTracker = ElevationTracker()

    private val breadcrumbs = mutableListOf<BreadcrumbPoint>()
    private val hardBrakingEvents = mutableListOf<HardBrakingEvent>()

    private var lastPoint: GeoPoint? = null
    private var lastPointTimestampMs: Long = 0L
    private var totalDistanceMeters: Float = 0f
    private var topSpeedKmh: Float = 0f

    fun startSession(startLocation: String = "Current Location") {
        isRecording = true
        sessionStartTimeMs = System.currentTimeMillis()
        startLocationName = startLocation
        breadcrumbs.clear()
        hardBrakingEvents.clear()
        leanProcessor.reset()
        brakingDetector.reset()
        elevationTracker.reset()
        lastPoint = null
        lastPointTimestampMs = 0L
        totalDistanceMeters = 0f
        topSpeedKmh = 0f
    }

    fun onSensorTick(
        latitude: Double,
        longitude: Double,
        elevationMeters: Float,
        speedKmh: Float,
        rawLeanDegrees: Float,
        timestampMs: Long = System.currentTimeMillis()
    ): LiveTelemetry {
        val currentPoint = GeoPoint(latitude, longitude)
        val clampedLean = leanProcessor.processAngle(rawLeanDegrees)
        elevationTracker.updateElevation(elevationMeters)

        // GPS Jitter gate
        val prev = lastPoint
        val prevTime = lastPointTimestampMs
        var stepValid = true
        if (prev != null && prevTime > 0L) {
            val deltaSec = (timestampMs - prevTime) / 1000L
            stepValid = GpsJitterFilter.isValidStep(prev, currentPoint, deltaSec)
            if (stepValid) {
                totalDistanceMeters += prev.distanceTo(currentPoint).toFloat()
            }
        }

        if (speedKmh > topSpeedKmh) {
            topSpeedKmh = speedKmh
        }

        if (stepValid) {
            lastPoint = currentPoint
            lastPointTimestampMs = timestampMs
            breadcrumbs.add(
                BreadcrumbPoint(
                    latitude = latitude,
                    longitude = longitude,
                    timestampMillis = timestampMs,
                    speedKmh = speedKmh,
                    leanAngleDegrees = clampedLean,
                    elevationMeters = elevationMeters
                )
            )
        }

        // Hard braking check
        val brakeEvent = brakingDetector.evaluate(speedKmh, timestampMs, latitude, longitude)
        if (brakeEvent != null) {
            hardBrakingEvents.add(brakeEvent)
        }

        val elapsedSec = (timestampMs - sessionStartTimeMs) / 1000L

        return LiveTelemetry(
            speedKmh = speedKmh.toInt(),
            leanAngleDegrees = clampedLean,
            elevationMeters = elevationMeters,
            elapsedSeconds = elapsedSec,
            distanceRecordedMeters = totalDistanceMeters
        )
    }

    fun finishSession(endLocation: String, userNote: String? = null): TripRecord {
        val endTime = System.currentTimeMillis()
        val durationSec = ((endTime - sessionStartTimeMs) / 1000L).coerceAtLeast(1L)
        val distanceKm = totalDistanceMeters / 1000f
        val avgSpeed = TrackingFormatters.calculateAverageSpeed(totalDistanceMeters, durationSec)

        val trip = TripRecord(
            id = "trip_${sessionStartTimeMs}",
            vehicleVin = vehicleVin,
            vehicleModelName = vehicleModel,
            title = TripRecord.resolveTitle(null, startLocationName, endLocation),
            startTimeMillis = sessionStartTimeMs,
            endTimeMillis = endTime,
            distanceKm = distanceKm,
            durationSeconds = durationSec,
            maxSpeedKmh = topSpeedKmh,
            avgSpeedKmh = avgSpeed,
            maxLeanAngleLeft = leanProcessor.maxLeanAngleLeft,
            maxLeanAngleRight = leanProcessor.maxLeanAngleRight,
            hardBrakingCount = hardBrakingEvents.size,
            elevationGainMeters = elevationTracker.elevationGainMeters,
            fuelEfficiencyKmpl = 44.0f,
            startLocationName = startLocationName,
            endLocationName = endLocation,
            photoUrls = emptyList(),
            userNote = userNote,
            breadcrumbPoints = breadcrumbs.toList(),
            encodedPolyline = "",
            hardBrakingEvents = hardBrakingEvents.toList()
        )

        isRecording = false
        return trip
    }
}
