package com.openride.companion.data.vehicle

import com.openride.companion.model.GarageCatalog
import com.openride.companion.model.RtoPlateValidator
import com.openride.companion.model.Vehicle
import com.openride.companion.model.VinValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface VehicleRepository {
    val vehiclesState: StateFlow<List<Vehicle>>
    val activeVehicle: StateFlow<Vehicle?>

    suspend fun getVehicles(): List<Vehicle>
    suspend fun getActiveVehicle(): Vehicle?
    suspend fun selectActiveVehicle(identifier: String): Result<Vehicle>
    suspend fun addVehicle(vin: String, registrationNumber: String, modelName: String): Result<Vehicle>
    suspend fun removeVehicle(vehicleId: String): Result<Unit>
    suspend fun updateOdometer(vehicleId: String, newOdoKm: Int): Result<Unit>
    suspend fun updateTelemetry(vehicleId: String, fuelPct: Int, batteryPct: Int, voltage12V: Float): Result<Unit>
}

/**
 * Production-ready In-Memory / Local Vehicle Repository pre-seeded with authoritative garage models.
 */
class VehicleRepositoryImpl : VehicleRepository {

    private val initialVehicles = listOf(
        Vehicle(
            id = "BIKE_PULSAR_NS400Z",
            vin = "MD2A11CY4P8712391",
            registrationNumber = "MH 12 TC 5590",
            modelSpec = GarageCatalog.NS400Z,
            odometerKm = 4120,
            fuelLevelPercentage = 85,
            batteryLevelPercentage = 100,
            batteryVoltage12V = 12.8f,
            isActive = true
        ),
        Vehicle(
            id = "BIKE_DOMINAR_400",
            vin = "MD2A11CY4P8712392",
            registrationNumber = "MH 14 AB 1234",
            modelSpec = GarageCatalog.DOMINAR_400,
            odometerKm = 12450,
            fuelLevelPercentage = 60,
            batteryLevelPercentage = 100,
            batteryVoltage12V = 12.6f,
            isActive = false
        ),
        Vehicle(
            id = "BIKE_CHETAK_EV",
            vin = "MD2A11CY4P8712393",
            registrationNumber = "MH 12 EV 9999",
            modelSpec = GarageCatalog.CHETAK_EV,
            odometerKm = 3200,
            fuelLevelPercentage = 0,
            batteryLevelPercentage = 94,
            batteryVoltage12V = 13.2f,
            isActive = false
        ),
        Vehicle(
            id = "BIKE_PULSAR_N250",
            vin = "MD2A11CY4P8712394",
            registrationNumber = "DL 01 AB 5678",
            modelSpec = GarageCatalog.PULSAR_N250,
            odometerKm = 8500,
            fuelLevelPercentage = 70,
            batteryLevelPercentage = 100,
            batteryVoltage12V = 12.7f,
            isActive = false
        ),
        Vehicle(
            id = "BIKE_PULSAR_N160",
            vin = "MD2A11CY4P8712395",
            registrationNumber = "KA 05 CD 4321",
            modelSpec = GarageCatalog.PULSAR_N160,
            odometerKm = 1500,
            fuelLevelPercentage = 90,
            batteryLevelPercentage = 100,
            batteryVoltage12V = 12.8f,
            isActive = false
        )
    )

    private val _vehiclesState = MutableStateFlow(initialVehicles)
    override val vehiclesState: StateFlow<List<Vehicle>> = _vehiclesState.asStateFlow()

    private val _activeVehicle = MutableStateFlow<Vehicle?>(initialVehicles.firstOrNull())
    override val activeVehicle: StateFlow<Vehicle?> = _activeVehicle.asStateFlow()

    override suspend fun getVehicles(): List<Vehicle> = _vehiclesState.value

    override suspend fun getActiveVehicle(): Vehicle? = _activeVehicle.value

    override suspend fun selectActiveVehicle(identifier: String): Result<Vehicle> {
        val list = _vehiclesState.value
        // Find by vehicle id or modelName
        val match = list.find { it.id == identifier || it.modelSpec.modelName.equals(identifier, ignoreCase = true) }
        return if (match != null) {
            _vehiclesState.update { currentList ->
                currentList.map { it.copy(isActive = (it.id == match.id)) }
            }
            val updated = match.copy(isActive = true)
            _activeVehicle.value = updated
            Result.success(updated)
        } else {
            // Check if catalog has this model but not yet instantiated in garage
            val catalogSpec = GarageCatalog.findModelByName(identifier)
            if (catalogSpec != null) {
                val newVehicle = Vehicle(
                    id = "BIKE_${catalogSpec.modelCode}",
                    vin = "MD2A11CY4P87${(1000..9999).random()}",
                    registrationNumber = "MH 12 TC 0000",
                    modelSpec = catalogSpec,
                    isActive = true
                )
                _vehiclesState.update { currentList ->
                    currentList.map { it.copy(isActive = false) } + newVehicle
                }
                _activeVehicle.value = newVehicle
                Result.success(newVehicle)
            } else {
                // If unknown model or ID, retains current bike without crash
                val current = _activeVehicle.value ?: initialVehicles.first()
                Result.success(current)
            }
        }
    }

    override suspend fun addVehicle(vin: String, registrationNumber: String, modelName: String): Result<Vehicle> {
        if (!VinValidator.isValidVin(vin)) {
            return Result.failure(IllegalArgumentException("Invalid 17-character ISO 3779 VIN: $vin"))
        }
        if (!RtoPlateValidator.isValidReg(registrationNumber)) {
            return Result.failure(IllegalArgumentException("Invalid Indian RTO registration plate format: $registrationNumber"))
        }

        val spec = GarageCatalog.findModelByName(modelName)
            ?: GarageCatalog.DOMINAR_400

        val newVehicle = Vehicle(
            vin = VinValidator.normalizeVin(vin),
            registrationNumber = RtoPlateValidator.normalizeReg(registrationNumber),
            modelSpec = spec,
            isActive = false
        )

        _vehiclesState.update { it + newVehicle }
        return Result.success(newVehicle)
    }

    override suspend fun removeVehicle(vehicleId: String): Result<Unit> {
        _vehiclesState.update { current ->
            val filtered = current.filter { it.id != vehicleId }
            if (_activeVehicle.value?.id == vehicleId) {
                _activeVehicle.value = filtered.firstOrNull()
            }
            filtered
        }
        return Result.success(Unit)
    }

    override suspend fun updateOdometer(vehicleId: String, newOdoKm: Int): Result<Unit> {
        _vehiclesState.update { current ->
            current.map {
                if (it.id == vehicleId) {
                    val updated = it.copy(odometerKm = newOdoKm)
                    if (_activeVehicle.value?.id == vehicleId) {
                        _activeVehicle.value = updated
                    }
                    updated
                } else it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun updateTelemetry(vehicleId: String, fuelPct: Int, batteryPct: Int, voltage12V: Float): Result<Unit> {
        _vehiclesState.update { current ->
            current.map {
                if (it.id == vehicleId) {
                    val updated = it.copy(
                        fuelLevelPercentage = fuelPct.coerceIn(0, 100),
                        batteryLevelPercentage = batteryPct.coerceIn(0, 100),
                        batteryVoltage12V = voltage12V
                    )
                    if (_activeVehicle.value?.id == vehicleId) {
                        _activeVehicle.value = updated
                    }
                    updated
                } else it
            }
        }
        return Result.success(Unit)
    }
}
