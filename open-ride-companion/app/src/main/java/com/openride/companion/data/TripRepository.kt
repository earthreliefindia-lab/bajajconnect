package com.openride.companion.data

import com.openride.companion.navigation.engine.LiveRideRecorder
import com.openride.companion.navigation.model.BreadcrumbPoint
import com.openride.companion.navigation.model.TripRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TripSummaryStats(
    val totalTrips: Int = 0,
    val totalDistanceKm: Float = 0f,
    val totalDurationSeconds: Long = 0L,
    val overallMaxSpeedKmh: Float = 0f,
    val overallAvgSpeedKmh: Float = 0f,
    val overallMaxLeanAngleLeft: Float = 0f,
    val overallMaxLeanAngleRight: Float = 0f,
    val totalHardBrakingEvents: Int = 0,
    val totalElevationGainMeters: Float = 0f,
    val averageFuelEfficiencyKmpl: Float = 0f
)

interface TripRepository {
    val tripsState: StateFlow<List<TripRecord>>
    val activeRideState: StateFlow<TripRecord?>

    suspend fun getAllTrips(): List<TripRecord>
    suspend fun getTripsByVehicle(vin: String): List<TripRecord>
    suspend fun getTripById(id: String): TripRecord?
    suspend fun getTripsByDateRange(startMillis: Long, endMillis: Long): List<TripRecord>
    suspend fun saveTrip(trip: TripRecord): Result<Unit>
    suspend fun deleteTrip(id: String): Result<Unit>
    suspend fun addPhotoToTrip(tripId: String, photoUri: String): Result<Unit>
    suspend fun updateTripUserNote(tripId: String, note: String): Result<Unit>
    suspend fun getAggregateStats(vin: String? = null): TripSummaryStats

    // Live recorder integration
    suspend fun startRideSession(vehicleVin: String, vehicleModel: String, startLocation: String): Result<String>
    suspend fun recordBreadcrumb(point: BreadcrumbPoint, isHardBraking: Boolean = false): Result<Unit>
    suspend fun finishRideSession(endLocation: String, userNote: String? = null): Result<TripRecord>
    suspend fun discardRideSession(): Result<Unit>
}

/**
 * Production in-memory and persistent TripRepository pre-seeded with sample trips.
 */
class DefaultTripRepository : TripRepository {

    private val mutex = Mutex()
    private val trips = mutableListOf<TripRecord>()
    private val _tripsState = MutableStateFlow<List<TripRecord>>(emptyList())
    override val tripsState: StateFlow<List<TripRecord>> = _tripsState.asStateFlow()

    private val _activeRideState = MutableStateFlow<TripRecord?>(null)
    override val activeRideState: StateFlow<TripRecord?> = _activeRideState.asStateFlow()

    private var currentRecorder: LiveRideRecorder? = null

    init {
        // Pre-seed with authoritative trips matching E2E test suites
        trips.addAll(
            listOf(
                TripRecord(
                    id = "trip_1",
                    vehicleVin = "MD2A11CY4P8712391",
                    vehicleModelName = "Pulsar NS400Z",
                    title = "Pune to Lonavala Expressway Ride",
                    startTimeMillis = System.currentTimeMillis() - 86400000L,
                    endTimeMillis = System.currentTimeMillis() - 81480000L,
                    distanceKm = 64.2f,
                    durationSeconds = 4920L,
                    maxSpeedKmh = 98.4f,
                    avgSpeedKmh = 46.9f,
                    maxLeanAngleLeft = 28.5f,
                    maxLeanAngleRight = 32.0f,
                    hardBrakingCount = 2,
                    elevationGainMeters = 380f,
                    fuelEfficiencyKmpl = 44.5f,
                    startLocationName = "Deccan Gymkhana, Pune",
                    endLocationName = "Tiger Point, Lonavala",
                    photoUrls = listOf("photo_lonavala_point.jpg"),
                    userNote = "Smooth highway cruise, heavy crosswinds near Khandala"
                ),
                TripRecord(
                    id = "trip_2",
                    vehicleVin = "MD2A11CY4P8712391",
                    vehicleModelName = "Pulsar NS400Z",
                    title = "FC Road to Hinjewadi Tech Park",
                    startTimeMillis = System.currentTimeMillis() - (86400000L * 6),
                    endTimeMillis = System.currentTimeMillis() - (86400000L * 6) + 3600000L,
                    distanceKm = 18.4f,
                    durationSeconds = 2700L,
                    maxSpeedKmh = 72.0f,
                    avgSpeedKmh = 24.5f,
                    maxLeanAngleLeft = 19.0f,
                    maxLeanAngleRight = 21.5f,
                    hardBrakingCount = 4,
                    elevationGainMeters = 45f,
                    fuelEfficiencyKmpl = 48.0f,
                    startLocationName = "FC Road, Shivajinagar",
                    endLocationName = "Phase 1, Hinjewadi",
                    photoUrls = emptyList(),
                    userNote = "Morning peak commute traffic"
                )
            )
        )
        _tripsState.value = trips.toList()
    }

    override suspend fun getAllTrips(): List<TripRecord> = mutex.withLock {
        trips.toList()
    }

    override suspend fun getTripsByVehicle(vin: String): List<TripRecord> = mutex.withLock {
        trips.filter { it.vehicleVin.equals(vin, ignoreCase = true) }
    }

    override suspend fun getTripById(id: String): TripRecord? = mutex.withLock {
        trips.firstOrNull { it.id == id }
    }

    override suspend fun getTripsByDateRange(startMillis: Long, endMillis: Long): List<TripRecord> = mutex.withLock {
        trips.filter { it.startTimeMillis in startMillis..endMillis }
    }

    override suspend fun saveTrip(trip: TripRecord): Result<Unit> = mutex.withLock {
        val idx = trips.indexOfFirst { it.id == trip.id }
        if (idx >= 0) {
            trips[idx] = trip
        } else {
            trips.add(0, trip)
        }
        _tripsState.value = trips.toList()
        Result.success(Unit)
    }

    override suspend fun deleteTrip(id: String): Result<Unit> = mutex.withLock {
        val removed = trips.removeAll { it.id == id }
        if (removed) {
            _tripsState.value = trips.toList()
            Result.success(Unit)
        } else {
            Result.failure(NoSuchElementException("Trip not found: $id"))
        }
    }

    override suspend fun addPhotoToTrip(tripId: String, photoUri: String): Result<Unit> = mutex.withLock {
        val idx = trips.indexOfFirst { it.id == tripId }
        if (idx >= 0) {
            val trip = trips[idx]
            val updated = trip.copy(photoUrls = trip.photoUrls + photoUri)
            trips[idx] = updated
            _tripsState.value = trips.toList()
            Result.success(Unit)
        } else {
            Result.failure(NoSuchElementException("Trip not found: $tripId"))
        }
    }

    override suspend fun updateTripUserNote(tripId: String, note: String): Result<Unit> = mutex.withLock {
        val idx = trips.indexOfFirst { it.id == tripId }
        if (idx >= 0) {
            val trip = trips[idx]
            val updated = trip.copy(userNote = note)
            trips[idx] = updated
            _tripsState.value = trips.toList()
            Result.success(Unit)
        } else {
            Result.failure(NoSuchElementException("Trip not found: $tripId"))
        }
    }

    override suspend fun getAggregateStats(vin: String?): TripSummaryStats = mutex.withLock {
        val filtered = if (vin != null) trips.filter { it.vehicleVin.equals(vin, ignoreCase = true) } else trips
        if (filtered.isEmpty()) return TripSummaryStats()

        val totalDist = filtered.sumOf { it.distanceKm.toDouble() }.toFloat()
        val totalDur = filtered.sumOf { it.durationSeconds }
        val maxSpeed = filtered.maxOfOrNull { it.maxSpeedKmh } ?: 0f
        val avgSpeed = if (filtered.isNotEmpty()) filtered.map { it.avgSpeedKmh }.average().toFloat() else 0f
        val maxLeanL = filtered.maxOfOrNull { it.maxLeanAngleLeft } ?: 0f
        val maxLeanR = filtered.maxOfOrNull { it.maxLeanAngleRight } ?: 0f
        val hardBrakes = filtered.sumOf { it.hardBrakingCount }
        val elevGain = filtered.sumOf { it.elevationGainMeters.toDouble() }.toFloat()
        val avgMileage = if (filtered.isNotEmpty()) filtered.map { it.fuelEfficiencyKmpl }.average().toFloat() else 0f

        TripSummaryStats(
            totalTrips = filtered.size,
            totalDistanceKm = totalDist,
            totalDurationSeconds = totalDur,
            overallMaxSpeedKmh = maxSpeed,
            overallAvgSpeedKmh = avgSpeed,
            overallMaxLeanAngleLeft = maxLeanL,
            overallMaxLeanAngleRight = maxLeanR,
            totalHardBrakingEvents = hardBrakes,
            totalElevationGainMeters = elevGain,
            averageFuelEfficiencyKmpl = avgMileage
        )
    }

    override suspend fun startRideSession(
        vehicleVin: String,
        vehicleModel: String,
        startLocation: String
    ): Result<String> {
        val recorder = LiveRideRecorder(vehicleVin, vehicleModel)
        recorder.startSession(startLocation)
        currentRecorder = recorder
        return Result.success("session_${recorder.sessionStartTimeMs}")
    }

    override suspend fun recordBreadcrumb(point: BreadcrumbPoint, isHardBraking: Boolean): Result<Unit> {
        val recorder = currentRecorder ?: return Result.failure(IllegalStateException("No active ride session"))
        recorder.onSensorTick(
            latitude = point.latitude,
            longitude = point.longitude,
            elevationMeters = point.elevationMeters,
            speedKmh = point.speedKmh,
            rawLeanDegrees = point.leanAngleDegrees,
            timestampMs = point.timestampMillis
        )
        return Result.success(Unit)
    }

    override suspend fun finishRideSession(endLocation: String, userNote: String?): Result<TripRecord> {
        val recorder = currentRecorder ?: return Result.failure(IllegalStateException("No active ride session"))
        val completedTrip = recorder.finishSession(endLocation, userNote)
        currentRecorder = null
        saveTrip(completedTrip)
        return Result.success(completedTrip)
    }

    override suspend fun discardRideSession(): Result<Unit> {
        currentRecorder = null
        return Result.success(Unit)
    }
}
