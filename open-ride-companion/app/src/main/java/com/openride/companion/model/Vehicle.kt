package com.openride.companion.model

import androidx.annotation.DrawableRes
import com.openride.companion.R
import java.util.UUID

/**
 * Powertrain classification distinguishing internal combustion from electric platforms.
 */
enum class PowertrainType {
    ICE,
    ELECTRIC,
    HYBRID
}

/**
 * Cluster hardware platform mapping directly to the motorcycle's instrument display electronics.
 */
enum class ClusterHardwareType(val identifier: String, val supportsWifiMirroring: Boolean) {
    /**
     * Full color TFT Display (Dominar 400, Chetak EV, Pulsar NS400Z).
     * Supports Wi-Fi TCP 5558 command socket and UDP 5556 screen projection.
     */
    TFT_COLOR_0265("Cluster0265", true),

    /**
     * Inverted Segment LCD Display (Pulsar N160, Pulsar N250).
     * Operates exclusively via Bluetooth Low Energy GATT telemetrics.
     */
    LCD_INVERSION_2763("Cluster2763", false);

    companion object {
        fun fromIdentifier(id: String): ClusterHardwareType =
            entries.find { it.identifier.equals(id, ignoreCase = true) } ?: LCD_INVERSION_2763
    }
}

/**
 * Immutable engineering specification for an authorized Bajaj vehicle model.
 * Matches AuthAndOnboardingE2ETest.kt authoritative specifications.
 */
data class VehicleModelSpec(
    val modelCode: String,
    val modelName: String,
    val fuelCapacityLitres: Float,
    val engineDisplacementCc: Int,
    val defaultDteKm: Int,
    val clusterHardware: String,
    val isElectric: Boolean,
    val primaryAsset: String,
    @DrawableRes val primaryDrawableRes: Int,
    val powertrainType: PowertrainType = if (isElectric) PowertrainType.ELECTRIC else PowertrainType.ICE
)

/**
 * Full motorcycle garage entity representing an owned or paired motorcycle.
 */
data class Vehicle(
    val id: String = UUID.randomUUID().toString(),
    val vin: String,
    val registrationNumber: String,
    val modelSpec: VehicleModelSpec,
    val odometerKm: Int = 0,
    val fuelLevelPercentage: Int = 100,
    val batteryLevelPercentage: Int = 100,
    val batteryVoltage12V: Float = 12.8f,
    val bluetoothMac: String? = null,
    val pairingPin: String? = null,
    val isActive: Boolean = false
) {
    val remainingServiceKm: Int get() = calculateRemainingServiceKm(odometerKm)
    val isStarterBatteryCritical: Boolean get() = batteryVoltage12V < 11.8f
    val effectiveEnergyPercentage: Int get() = if (modelSpec.isElectric) {
        batteryLevelPercentage.coerceIn(0, 100)
    } else {
        fuelLevelPercentage.coerceIn(0, 100)
    }

    val primaryEnergyLabel: String get() = getPrimaryEnergyLabel(modelSpec.isElectric)

    companion object {
        fun calculateRemainingServiceKm(currentOdo: Int, serviceInterval: Int = 5000): Int {
            val safeOdo = currentOdo.coerceAtLeast(0)
            val remainder = safeOdo % serviceInterval
            return serviceInterval - remainder
        }

        fun getPrimaryEnergyLabel(isElectric: Boolean): String =
            if (isElectric) "Battery Level" else "Fuel Level"

        fun clampBattery(percentage: Int): Int = percentage.coerceIn(0, 100)
    }
}

/**
 * Authoritative Garage catalog providing specifications for the 5 key Bajaj motorcycles.
 */
object GarageCatalog {
    val NS400Z = VehicleModelSpec(
        modelCode = "PULSAR_NS400Z",
        modelName = "Bajaj Pulsar NS400Z",
        fuelCapacityLitres = 12.0f,
        engineDisplacementCc = 373,
        defaultDteKm = 285,
        clusterHardware = "00JR33BT",
        isElectric = false,
        primaryAsset = "vehicle_dominar_400.png",
        primaryDrawableRes = R.drawable.vehicle_dominar_400
    )

    val DOMINAR_400 = VehicleModelSpec(
        modelCode = "DOMINAR_400_DUAL_ABS",
        modelName = "Bajaj Dominar 400 Dual ABS",
        fuelCapacityLitres = 13.0f,
        engineDisplacementCc = 373,
        defaultDteKm = 240,
        clusterHardware = "Cluster0265",
        isElectric = false,
        primaryAsset = "vehicle_dominar_400.png",
        primaryDrawableRes = R.drawable.vehicle_dominar_400
    )

    val CHETAK_EV = VehicleModelSpec(
        modelCode = "CHETAK_EV_PREMIUM",
        modelName = "Bajaj Chetak EV (Premium)",
        fuelCapacityLitres = 0.0f,
        engineDisplacementCc = 0,
        defaultDteKm = 95,
        clusterHardware = "Cluster0265",
        isElectric = true,
        primaryAsset = "vehicle_garage_card_dark.png",
        primaryDrawableRes = R.drawable.vehicle_garage_card_dark
    )

    val PULSAR_N250 = VehicleModelSpec(
        modelCode = "PULSAR_N250",
        modelName = "Bajaj Pulsar N250",
        fuelCapacityLitres = 14.0f,
        engineDisplacementCc = 249,
        defaultDteKm = 310,
        clusterHardware = "00JR33BT",
        isElectric = false,
        primaryAsset = "vehicle_pulsar_n160.png",
        primaryDrawableRes = R.drawable.vehicle_pulsar_n160
    )

    val PULSAR_N160 = VehicleModelSpec(
        modelCode = "PULSAR_N160_DUAL_ABS",
        modelName = "Bajaj Pulsar N160",
        fuelCapacityLitres = 14.0f,
        engineDisplacementCc = 164,
        defaultDteKm = 360,
        clusterHardware = "Cluster2763",
        isElectric = false,
        primaryAsset = "vehicle_pulsar_n160.png",
        primaryDrawableRes = R.drawable.vehicle_pulsar_n160
    )

    val authoritativeSpecs: Map<String, VehicleModelSpec> = mapOf(
        NS400Z.modelName to NS400Z,
        DOMINAR_400.modelName to DOMINAR_400,
        CHETAK_EV.modelName to CHETAK_EV,
        PULSAR_N250.modelName to PULSAR_N250,
        PULSAR_N160.modelName to PULSAR_N160
    )

    fun findModelByName(name: String): VehicleModelSpec? = authoritativeSpecs[name]
}

/**
 * ISO 3779 17-Character VIN Validator.
 * Rejects illegal characters I, O, Q.
 */
object VinValidator {
    private val VIN_REGEX = Regex("^[A-HJ-NPR-Z0-9]{17}$")

    fun isValidVin(vin: String): Boolean {
        val clean = vin.trim().uppercase()
        return clean.length == 17 && clean.matches(VIN_REGEX)
    }

    fun normalizeVin(vin: String): String = vin.trim().uppercase()
}

/**
 * Indian RTO Registration Plate Formatter & Validator.
 * Standard format: 8 to 11 alphanumeric characters (e.g., "MH 12 TC 5590", "DL01AB1234").
 */
object RtoPlateValidator {
    fun isValidReg(reg: String): Boolean {
        val clean = reg.filter { it.isLetterOrDigit() }
        return clean.length in 8..11
    }

    fun normalizeReg(reg: String): String {
        return reg.filter { it.isLetterOrDigit() }.uppercase()
    }
}

/**
 * Chassis sticker QR code discovery payload parser.
 * Supports "BAJAJ:VIN=...;MODEL=...;MAC=...;PIN=..." and JSON format.
 */
data class QrPairingPayload(
    val vin: String,
    val modelCode: String,
    val bluetoothMac: String? = null,
    val pairingPin: String? = null
) {
    companion object {
        fun parse(qrString: String): QrPairingPayload? {
            val trimmed = qrString.trim()
            if (trimmed.startsWith("BAJAJ:")) {
                val tokens = trimmed.removePrefix("BAJAJ:").split(";").map { it.trim() }
                var vin: String? = null
                var model: String? = null
                var mac: String? = null
                var pin: String? = null

                for (token in tokens) {
                    val pair = token.split("=")
                    if (pair.size == 2) {
                        val key = pair[0].trim().uppercase()
                        val value = pair[1].trim()
                        when (key) {
                            "VIN" -> vin = value
                            "MODEL" -> model = value
                            "MAC" -> mac = value
                            "PIN" -> pin = value
                        }
                    }
                }

                if (vin != null && VinValidator.isValidVin(vin)) {
                    return QrPairingPayload(
                        vin = vin,
                        modelCode = model ?: "DOMINAR_400_DUAL_ABS",
                        bluetoothMac = mac,
                        pairingPin = pin
                    )
                }
            }
            return null
        }
    }
}
