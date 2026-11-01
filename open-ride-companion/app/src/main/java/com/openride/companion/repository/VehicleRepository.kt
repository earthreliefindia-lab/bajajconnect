package com.openride.companion.repository

import com.openride.companion.model.BleConnectionStatus
import com.openride.companion.model.ClusterType
import com.openride.companion.model.LiveNavInstruction
import com.openride.companion.model.ManeuverType
import com.openride.companion.model.RideMode
import com.openride.companion.model.ServiceCenter
import com.openride.companion.model.TripRecord
import com.openride.companion.model.VehicleDocument
import com.openride.companion.model.VehicleInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object VehicleRepository {

    val GARAGE_MODELS = listOf(
        "Bajaj Pulsar NS400Z",
        "Bajaj Dominar 400 Dual ABS",
        "Bajaj Pulsar N250",
        "Bajaj Pulsar N160",
        "Bajaj Chetak EV (Premium)"
    )

    private val _vehicle = MutableStateFlow(
        VehicleInfo(
            modelName = "Bajaj Pulsar NS400Z",
            vinNumber = "MD2A11CY4P8712391",
            registrationNumber = "MH 12 TC 5590",
            batteryPercentage = 94,
            batteryVoltage = 12.8f,
            fuelPercentage = 72,
            dteKm = 285,
            odoKm = 4120,
            nextServiceDueKm = 880,
            nextServiceDueDays = 28,
            currentRideMode = RideMode.ROAD,
            clusterType = ClusterType.CLUSTER_0265
        )
    )
    val vehicle: StateFlow<VehicleInfo> = _vehicle.asStateFlow()

    private val _documents = MutableStateFlow(
        listOf(
            VehicleDocument(
                id = "doc_rc",
                title = "Registration Certificate (RC)",
                documentNumber = "MH12TC5590/2025/RC",
                expiryDate = "Valid up to 14 Oct 2040",
                isVerified = true,
                issuer = "Ministry of Road Transport & Highways"
            ),
            VehicleDocument(
                id = "doc_ins",
                title = "Comprehensive Bike Insurance",
                documentNumber = "BAJAJ-ALLIANZ-99120488",
                expiryDate = "Valid up to 28 Nov 2027",
                isVerified = true,
                issuer = "Bajaj Allianz General Insurance"
            ),
            VehicleDocument(
                id = "doc_puc",
                title = "Pollution Under Control (PUC)",
                documentNumber = "MH12-PUC-449102",
                expiryDate = "Valid up to 15 Jun 2027",
                isVerified = true,
                issuer = "Govt of Maharashtra Transport Dept"
            ),
            VehicleDocument(
                id = "doc_dl",
                title = "Driving License",
                documentNumber = "DL-1220180049281",
                expiryDate = "Valid up to 2038 (MCWG + LMV)",
                isVerified = true,
                issuer = "Regional Transport Office (RTO)"
            )
        )
    )
    val documents: StateFlow<List<VehicleDocument>> = _documents.asStateFlow()

    private val _serviceCenters = MutableStateFlow(
        listOf(
            ServiceCenter(
                id = "sc_1",
                name = "Sai Service - Bajaj Authorized Workshop",
                address = "Deccan Gymkhana, JM Road, Pune, Maharashtra 411004",
                phone = "020 2553 4401",
                rating = 4.8f,
                distanceKm = 2.4f
            ),
            ServiceCenter(
                id = "sc_2",
                name = "Pawar Auto - Bajaj Care & Spares",
                address = "Old Mumbai-Pune Highway, Wakdewadi, Pune 411003",
                phone = "020 6602 8899",
                rating = 4.6f,
                distanceKm = 4.8f
            ),
            ServiceCenter(
                id = "sc_3",
                name = "Kothari Wheels Bajaj Service Hub",
                address = "Viman Nagar, Nagar Road, Pune 411014",
                phone = "020 4141 5500",
                rating = 4.7f,
                distanceKm = 8.5f
            )
        )
    )
    val serviceCenters: StateFlow<List<ServiceCenter>> = _serviceCenters.asStateFlow()

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

    fun switchBike(modelName: String) {
        val (fuel, odo, dte, cluster) = when (modelName) {
            "Bajaj Dominar 400 Dual ABS" -> listOf(65, 8420, 240, ClusterType.CLUSTER_0265)
            "Bajaj Chetak EV (Premium)" -> listOf(88, 2190, 95, ClusterType.CLUSTER_0265)
            "Bajaj Pulsar N250" -> listOf(70, 5210, 310, ClusterType.CLUSTER_2763)
            "Bajaj Pulsar N160" -> listOf(75, 3400, 360, ClusterType.CLUSTER_2763)
            else -> listOf(72, 4120, 285, ClusterType.CLUSTER_0265)
        }
        _vehicle.value = _vehicle.value.copy(
            modelName = modelName,
            fuelPercentage = fuel as Int,
            odoKm = odo as Int,
            dteKm = dte as Int,
            clusterType = cluster as ClusterType
        )
    }

    fun setRideMode(mode: RideMode) {
        _vehicle.value = _vehicle.value.copy(currentRideMode = mode)
    }

    fun updateClusterType(type: ClusterType) {
        _vehicle.value = _vehicle.value.copy(clusterType = type)
    }

    fun toggleBleConnection() {
        val current = _vehicle.value.connectionStatus
        val next = if (current == BleConnectionStatus.CONNECTED) {
            BleConnectionStatus.DISCONNECTED
        } else {
            BleConnectionStatus.CONNECTED
        }
        _vehicle.value = _vehicle.value.copy(
            connectionStatus = next,
            connectedDeviceName = if (next == BleConnectionStatus.CONNECTED) "Bajaj Connect Console" else null
        )
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
