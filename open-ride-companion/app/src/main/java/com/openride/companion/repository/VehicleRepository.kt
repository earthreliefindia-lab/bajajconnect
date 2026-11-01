package com.openride.companion.repository

import com.openride.companion.model.ClusterType
import com.openride.companion.model.LiveNavInstruction
import com.openride.companion.model.ManeuverType
import com.openride.companion.model.TripRecord
import com.openride.companion.model.VehicleInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object VehicleRepository {

    private val _vehicle = MutableStateFlow(
        VehicleInfo(
            modelName = "Bajaj Pulsar N160 Dual Channel ABS",
            vinNumber = "MD2A11CY4P8712391",
            registrationNumber = "MH 12 TC 5590",
            batteryPercentage = 94,
            batteryVoltage = 12.8f,
            fuelPercentage = 72,
            odoKm = 4120,
            nextServiceDueKm = 880,
            nextServiceDueDays = 28,
            clusterType = ClusterType.CLUSTER_2763
        )
    )
    val vehicle: StateFlow<VehicleInfo> = _vehicle.asStateFlow()

    private val _trips = MutableStateFlow<List<TripRecord>>(
        listOf(
            TripRecord(
                id = UUID.randomUUID().toString(),
                title = "Pune to Lonavala Expressway Ride",
                dateFormatted = "Yesterday, 07:30 AM",
                durationSeconds = 4920,
                distanceMeters = 64200f,
                avgSpeedKmh = 54.2f,
                topSpeedKmh = 98.4f,
                fuelEfficiencyKmpl = 44.5f,
                startAddress = "Deccan Gymkhana, Pune",
                endAddress = "Tiger Point, Lonavala"
            ),
            TripRecord(
                id = UUID.randomUUID().toString(),
                title = "FC Road to Hinjewadi Tech Park",
                dateFormatted = "28 Oct 2026, 09:15 AM",
                durationSeconds = 1980,
                distanceMeters = 18400f,
                avgSpeedKmh = 38.6f,
                topSpeedKmh = 72.0f,
                fuelEfficiencyKmpl = 48.0f,
                startAddress = "FC Road, Shivajinagar",
                endAddress = "Phase 1, Hinjewadi"
            ),
            TripRecord(
                id = UUID.randomUUID().toString(),
                title = "City Evening Commute",
                dateFormatted = "25 Oct 2026, 06:45 PM",
                durationSeconds = 1320,
                distanceMeters = 9200f,
                avgSpeedKmh = 29.1f,
                topSpeedKmh = 58.5f,
                fuelEfficiencyKmpl = 46.2f,
                startAddress = "Kalyani Nagar",
                endAddress = "JM Road"
            )
        )
    )
    val trips: StateFlow<List<TripRecord>> = _trips.asStateFlow()

    private val _activeInstruction = MutableStateFlow(
        LiveNavInstruction(
            maneuver = ManeuverType.STRAIGHT,
            instructionText = "Ready for navigation",
            distanceToManeuverMeters = 0,
            nextStreet = "",
            remainingDistanceMeters = 0f,
            remainingTimeSeconds = 0,
            currentSpeedKmh = 0,
            speedLimitKmh = 50,
            laneGuidance = ""
        )
    )
    val activeInstruction: StateFlow<LiveNavInstruction> = _activeInstruction.asStateFlow()

    fun updateClusterType(type: ClusterType) {
        _vehicle.value = _vehicle.value.copy(clusterType = type)
    }

    fun addCompletedTrip(trip: TripRecord) {
        _trips.value = listOf(trip) + _trips.value
    }

    fun updateInstruction(instruction: LiveNavInstruction) {
        _activeInstruction.value = instruction
    }

    fun updateSpeed(speedKmh: Int) {
        _activeInstruction.value = _activeInstruction.value.copy(currentSpeedKmh = speedKmh)
    }
}
