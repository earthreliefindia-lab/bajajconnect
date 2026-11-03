package com.openride.companion.model

/**
 * Real-time operational telemetry state emitted by motorcycle sensors and BLE subsystems.
 */
data class VehicleTelemetry(
    val vehicleId: String,
    val speedKmh: Int = 0,
    val engineRpm: Int = 0,
    val odometerKm: Int = 0,
    val fuelLevelPercent: Int = 100,
    val batterySocPercent: Int = 100,
    val battery12VVoltage: Float = 12.8f,
    val leanAngleDegrees: Float = 0f,
    val gearPosition: Int = 0, // 0 = Neutral, 1..6
    val absMode: String = "ROAD", // ROAD, RAIN, SPORT, OFF_ROAD
    val isSidestandDown: Boolean = false,
    val isBikeStationary: Boolean = true,
    val lastUpdateTimestampMs: Long = System.currentTimeMillis()
) {
    val isStarterBatteryLow: Boolean get() = battery12VVoltage < 12.4f
    val isStarterBatteryCritical: Boolean get() = battery12VVoltage < 11.8f
    val isFuelLow: Boolean get() = fuelLevelPercent <= 15
    val isFuelCritical: Boolean get() = fuelLevelPercent <= 8
    val isEvBatteryLow: Boolean get() = batterySocPercent <= 20
    val isEvBatteryCritical: Boolean get() = batterySocPercent <= 10
}

/**
 * Service status classification for garage display.
 */
enum class ServiceUrgency {
    NORMAL,
    APPROACHING,
    IMMINENT_DUE
}
