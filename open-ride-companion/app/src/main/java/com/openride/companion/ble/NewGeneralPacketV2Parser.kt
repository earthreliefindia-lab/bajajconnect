package com.openride.companion.ble

import com.openride.companion.model.VehicleTelemetry
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Resilient Inbound 89-Byte Telemetry Frame Parser.
 * Emitted by motorcycle cluster/ECU via GATT notifications at 10 Hz (every 100ms).
 */
object NewGeneralPacketV2Parser {

    const val TELEMETRY_SYNC_BYTE_0: Byte = 0xBA.toByte()
    const val TELEMETRY_SYNC_BYTE_1: Byte = 0x5A.toByte()
    const val PACKET_SIZE = 89

    sealed class ParseResult {
        data class Success(
            val sequenceCounter: Int,
            val telemetry: VehicleTelemetry,
            val telltales: TelltaleIndicators,
            val coolantTempC: Int,
            val ambientTempC: Int,
            val tripAKm: Float,
            val tripBKm: Float,
            val leanAngleDegrees: Float = 0f,
            val dtcCodes: List<String> = emptyList(),
            val tirePressureFrontPsi: Int = 28,
            val tirePressureRearPsi: Int = 32
        ) : ParseResult()

        data class Error(val reason: String) : ParseResult()
    }

    /**
     * Parses an 89-byte frame into structured vehicle telemetry.
     */
    fun parse(frame: ByteArray, vehicleId: String = "ACTIVE_VEHICLE"): ParseResult {
        if (frame.size != PACKET_SIZE) {
            return ParseResult.Error("Invalid packet length: ${frame.size}, expected $PACKET_SIZE bytes")
        }

        // Verify Sync Header: 0xBA 0x5A
        if (frame[0] != TELEMETRY_SYNC_BYTE_0 || frame[1] != TELEMETRY_SYNC_BYTE_1) {
            val hex0 = String.format("%02X", frame[0])
            val hex1 = String.format("%02X", frame[1])
            return ParseResult.Error("Invalid sync header: [0x$hex0, 0x$hex1], expected [0xBA, 0x5A]")
        }

        // Verify Modulo-256 Checksum at byte 88
        var sum = 0
        for (i in 0 until 88) {
            sum += (frame[i].toInt() and 0xFF)
        }
        val expectedChecksum = (sum and 0xFF).toByte()
        if (frame[88] != expectedChecksum) {
            val hexGot = String.format("%02X", frame[88])
            val hexExp = String.format("%02X", expectedChecksum)
            return ParseResult.Error("Checksum mismatch: got 0x$hexGot, expected 0x$hexExp")
        }

        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)

        val sequenceCounter = frame[2].toInt() and 0xFF
        val speedKmh = buffer.getShort(3).toInt() and 0xFFFF
        val engineRpm = buffer.getShort(5).toInt() and 0xFFFF
        val fuelLevelPercent = frame[7].toInt() and 0xFF
        val currentGear = frame[8].toInt() and 0xFF // 0 = Neutral, 1..6 = Gears, 0xFF = Unknown

        // Voltage: millivolts (e.g. 12800 -> 12.8f) or decivolts (128 -> 12.8f)
        val rawVoltage = buffer.getShort(9).toInt() and 0xFFFF
        val battery12VVoltage = when {
            rawVoltage > 500 -> rawVoltage / 1000.0f
            rawVoltage > 0 -> rawVoltage / 10.0f
            else -> 0.0f
        }

        val coolantTempC = frame[11].toInt()
        val ambientTempC = frame[12].toInt()

        // Trip meters & Odometer (Little Endian uint32)
        val rawTripA = buffer.getInt(13).toLong() and 0xFFFFFFFFL
        val tripAKm = rawTripA / 10.0f
        val rawTripB = buffer.getInt(17).toLong() and 0xFFFFFFFFL
        val tripBKm = rawTripB / 10.0f
        val rawOdo = buffer.getInt(21).toLong() and 0xFFFFFFFFL
        val odometerKm = (rawOdo / 10.0f).toInt().coerceAtLeast(rawOdo.toInt())

        // Telltale indicators (Bytes 25-26)
        val telltaleMask = buffer.getShort(25).toInt() and 0xFFFF
        val telltales = TelltaleIndicators.fromBitmask(telltaleMask)

        // Lean angle (Bytes 27-28, signed int16 in 0.1 deg)
        val rawLean = buffer.getShort(27).toFloat()
        val leanAngleDegrees = rawLean / 10.0f

        // DTC Diagnostic error code (Bytes 29-32)
        val rawDtc = buffer.getInt(29).toLong() and 0xFFFFFFFFL
        val dtcCodes = if (rawDtc != 0L) {
            listOf(String.format("P%04X", rawDtc and 0xFFFF))
        } else {
            emptyList()
        }

        // Tire pressure (Bytes 33-34)
        val frontPsi = frame[33].toInt() and 0xFF
        val rearPsi = frame[34].toInt() and 0xFF

        // ABS Mode (Byte 35)
        val absModeCode = frame[35].toInt() and 0xFF
        val absModeStr = when (absModeCode) {
            1 -> "RAIN"
            2 -> "OFF_ROAD"
            3 -> "SPORT"
            else -> "ROAD"
        }

        val isStationary = (speedKmh == 0)

        val telemetry = VehicleTelemetry(
            vehicleId = vehicleId,
            speedKmh = speedKmh.coerceIn(0, 299),
            engineRpm = engineRpm.coerceIn(0, 16000),
            odometerKm = odometerKm,
            fuelLevelPercent = fuelLevelPercent.coerceIn(0, 100),
            batterySocPercent = 100,
            battery12VVoltage = battery12VVoltage,
            leanAngleDegrees = leanAngleDegrees,
            gearPosition = if (currentGear == 0xFF) -1 else currentGear,
            absMode = absModeStr,
            isSidestandDown = telltales.sideStandDown,
            isBikeStationary = isStationary,
            lastUpdateTimestampMs = System.currentTimeMillis()
        )

        return ParseResult.Success(
            sequenceCounter = sequenceCounter,
            telemetry = telemetry,
            telltales = telltales,
            coolantTempC = coolantTempC,
            ambientTempC = ambientTempC,
            tripAKm = tripAKm,
            tripBKm = tripBKm,
            leanAngleDegrees = leanAngleDegrees,
            dtcCodes = dtcCodes,
            tirePressureFrontPsi = if (frontPsi > 0) frontPsi else 28,
            tirePressureRearPsi = if (rearPsi > 0) rearPsi else 32
        )
    }

    /**
     * Factory utility to construct an 89-byte inbound telemetry packet with valid sync and checksum.
     */
    fun createTelemetryFrame(
        sequence: Int = 0,
        speedKmh: Int = 0,
        rpm: Int = 0,
        fuelPercent: Int = 100,
        gear: Int = 0,
        voltageMilliVolts: Int = 12800,
        coolantTempC: Int = 85,
        ambientTempC: Int = 28,
        tripADecameters: Long = 1250L,
        tripBDecameters: Long = 340L,
        odometerDecameters: Long = 54200L,
        telltaleBitmask: Int = 0,
        leanAngleTenthDegrees: Int = 0,
        dtcCode: Long = 0L,
        tireFrontPsi: Int = 28,
        tireRearPsi: Int = 32,
        absModeCode: Int = 0
    ): ByteArray {
        val frame = ByteArray(PACKET_SIZE)
        frame[0] = TELEMETRY_SYNC_BYTE_0
        frame[1] = TELEMETRY_SYNC_BYTE_1
        frame[2] = (sequence and 0xFF).toByte()

        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(3, speedKmh.toShort())
        buffer.putShort(5, rpm.toShort())
        frame[7] = (fuelPercent and 0xFF).toByte()
        frame[8] = (gear and 0xFF).toByte()
        buffer.putShort(9, voltageMilliVolts.toShort())
        frame[11] = (coolantTempC and 0xFF).toByte()
        frame[12] = (ambientTempC and 0xFF).toByte()
        buffer.putInt(13, tripADecameters.toInt())
        buffer.putInt(17, tripBDecameters.toInt())
        buffer.putInt(21, odometerDecameters.toInt())
        buffer.putShort(25, telltaleBitmask.toShort())
        buffer.putShort(27, leanAngleTenthDegrees.toShort())
        buffer.putInt(29, dtcCode.toInt())
        frame[33] = (tireFrontPsi and 0xFF).toByte()
        frame[34] = (tireRearPsi and 0xFF).toByte()
        frame[35] = (absModeCode and 0xFF).toByte()

        var sum = 0
        for (i in 0 until 88) {
            sum += (frame[i].toInt() and 0xFF)
        }
        frame[88] = (sum and 0xFF).toByte()

        return frame
    }
}
