package com.openride.companion.ble

/**
 * ABS Operating Modes supported by motorcycle cluster.
 */
enum class AbsMode {
    ROAD,
    RAIN,
    OFF_ROAD,
    SPORT
}

/**
 * Evaluated vehicle health domain model.
 */
data class VehicleHealth(
    val batteryVoltage: Float,
    val engineTroubleCodes: List<String> = emptyList(),
    val currentAbsMode: AbsMode = AbsMode.ROAD,
    val tirePressureFrontPsi: Int = RECOMMENDED_TIRE_FRONT_PSI,
    val tirePressureRearPsi: Int = RECOMMENDED_TIRE_REAR_SOLO_PSI
) {
    /**
     * Starter battery voltage is critical when below 11.8V.
     */
    val isBatteryCritical: Boolean
        get() = batteryVoltage < 11.8f

    /**
     * Alternator / regulator overcharging detected when above 14.8V.
     */
    val isOvercharged: Boolean
        get() = batteryVoltage > 14.8f

    /**
     * Detects underinflated tires against recommended thresholds.
     */
    fun isTireUnderInflated(currentPsi: Int, recommendedPsi: Int): Boolean {
        return currentPsi < (recommendedPsi - 4)
    }

    val isFrontTireUnderInflated: Boolean
        get() = isTireUnderInflated(tirePressureFrontPsi, RECOMMENDED_TIRE_FRONT_PSI)

    val isRearSoloTireUnderInflated: Boolean
        get() = isTireUnderInflated(tirePressureRearPsi, RECOMMENDED_TIRE_REAR_SOLO_PSI)

    companion object {
        const val RECOMMENDED_TIRE_FRONT_PSI: Int = 28
        const val RECOMMENDED_TIRE_REAR_SOLO_PSI: Int = 32
        const val RECOMMENDED_TIRE_REAR_PILLION_PSI: Int = 36

        /**
         * Normalizes diagnostic trouble codes into trimmed uppercase format.
         */
        fun formatDtc(code: String): String = code.trim().uppercase()
    }
}

/**
 * Evaluates raw sensor inputs against automotive safety and health thresholds.
 */
object VehicleHealthEvaluator {

    fun evaluate(
        batteryVoltage: Float,
        troubleCodes: List<String> = emptyList(),
        absMode: AbsMode = AbsMode.ROAD,
        frontPsi: Int = VehicleHealth.RECOMMENDED_TIRE_FRONT_PSI,
        rearPsi: Int = VehicleHealth.RECOMMENDED_TIRE_REAR_SOLO_PSI
    ): VehicleHealth {
        val normalizedCodes = troubleCodes.map { VehicleHealth.formatDtc(it) }
        return VehicleHealth(
            batteryVoltage = batteryVoltage,
            engineTroubleCodes = normalizedCodes,
            currentAbsMode = absMode,
            tirePressureFrontPsi = frontPsi,
            tirePressureRearPsi = rearPsi
        )
    }

    fun parseAbsMode(code: Int): AbsMode {
        return when (code) {
            1 -> AbsMode.RAIN
            2 -> AbsMode.OFF_ROAD
            3 -> AbsMode.SPORT
            else -> AbsMode.ROAD
        }
    }

    fun parseAbsMode(name: String?): AbsMode {
        return when (name?.uppercase()?.trim()) {
            "RAIN" -> AbsMode.RAIN
            "OFF_ROAD", "OFFROAD" -> AbsMode.OFF_ROAD
            "SPORT" -> AbsMode.SPORT
            else -> AbsMode.ROAD
        }
    }
}
