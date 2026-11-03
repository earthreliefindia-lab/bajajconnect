package com.openride.companion.navigation.ble

import java.nio.charset.StandardCharsets

/**
 * 22 Maneuver codes matching reverse-engineered Bajaj TBT Protocol and E2E test suites.
 */
object TbtManeuvers {
    const val STRAIGHT = 0
    const val TURN_LEFT = 1
    const val TURN_RIGHT = 2
    const val TURN_SLIGHT_LEFT = 3
    const val TURN_SLIGHT_RIGHT = 4
    const val TURN_SHARP_LEFT = 5
    const val TURN_SHARP_RIGHT = 6
    const val U_TURN_LEFT = 7
    const val U_TURN_RIGHT = 8
    const val KEEP_LEFT = 9
    const val KEEP_RIGHT = 10
    const val FORK_LEFT = 11
    const val FORK_RIGHT = 12
    const val MERGE = 13
    const val RAMP_LEFT = 14
    const val RAMP_RIGHT = 15
    const val ROUNDABOUT_LEFT = 16
    const val ROUNDABOUT_RIGHT = 17
    const val FERRY = 18
    const val FERRY_TRAIN = 19
    const val DESTINATION_REACHED = 20
    const val WRONG_WAY = 21

    val ALL_CODES = listOf(
        STRAIGHT, TURN_LEFT, TURN_RIGHT, TURN_SLIGHT_LEFT, TURN_SLIGHT_RIGHT,
        TURN_SHARP_LEFT, TURN_SHARP_RIGHT, U_TURN_LEFT, U_TURN_RIGHT,
        KEEP_LEFT, KEEP_RIGHT, FORK_LEFT, FORK_RIGHT, MERGE, RAMP_LEFT,
        RAMP_RIGHT, ROUNDABOUT_LEFT, ROUNDABOUT_RIGHT, FERRY, FERRY_TRAIN,
        DESTINATION_REACHED, WRONG_WAY
    )
}

/**
 * Unit of measurement for cluster distance display.
 */
enum class DistanceUnit(val code: Int) {
    METER(0),
    KM(1),
    MILES(2),
    YARD(3),
    FOOT(4)
}

/**
 * 22 Primary Maneuver codes mapped directly to Dalvik decompilation `Lcom/bajajconnect/enums/PrimaryTurns;`.
 */
enum class PrimaryTurns(val asciiValue: Int, val asciiChar: Char, val maneuverIndex: Int) {
    TURN_LEFT(73, 'I', TbtManeuvers.TURN_LEFT),
    TURN_RIGHT(74, 'J', TbtManeuvers.TURN_RIGHT),
    TURN_SLIGHT_LEFT(67, 'C', TbtManeuvers.TURN_SLIGHT_LEFT),
    TURN_SLIGHT_RIGHT(68, 'D', TbtManeuvers.TURN_SLIGHT_RIGHT),
    TURN_SHARP_LEFT(69, 'E', TbtManeuvers.TURN_SHARP_LEFT),
    TURN_SHARP_RIGHT(70, 'F', TbtManeuvers.TURN_SHARP_RIGHT),
    STRAIGHT(71, 'G', TbtManeuvers.STRAIGHT),
    MERGE(86, 'V', TbtManeuvers.MERGE),
    KEEP_LEFT(90, 'Z', TbtManeuvers.KEEP_LEFT),
    KEEP_RIGHT(88, 'X', TbtManeuvers.KEEP_RIGHT),
    RAMP_LEFT(75, 'K', TbtManeuvers.RAMP_LEFT),
    RAMP_RIGHT(76, 'L', TbtManeuvers.RAMP_RIGHT),
    ROUNDABOUT_LEFT(85, 'U', TbtManeuvers.ROUNDABOUT_LEFT),
    ROUNDABOUT_RIGHT(78, 'N', TbtManeuvers.ROUNDABOUT_RIGHT),
    U_TURN_LEFT(79, 'O', TbtManeuvers.U_TURN_LEFT),
    U_TURN_RIGHT(80, 'P', TbtManeuvers.U_TURN_RIGHT),
    FORK_LEFT(81, 'Q', TbtManeuvers.FORK_LEFT),
    FORK_RIGHT(82, 'R', TbtManeuvers.FORK_RIGHT),
    FERRY(89, 'Y', TbtManeuvers.FERRY),
    FERRY_TRAIN(84, 'T', TbtManeuvers.FERRY_TRAIN),
    DESTINATION_REACHED(72, 'H', TbtManeuvers.DESTINATION_REACHED),
    WRONG_WAY(66, 'B', TbtManeuvers.WRONG_WAY);

    fun getValue(): Int = asciiValue

    companion object {
        fun fromManeuverIndex(index: Int): PrimaryTurns {
            return entries.firstOrNull { it.maneuverIndex == index } ?: STRAIGHT
        }

        fun fromAsciiValue(value: Int): PrimaryTurns {
            return entries.firstOrNull { it.asciiValue == value } ?: WRONG_WAY
        }
    }
}

/**
 * Dalvik NavigationHelper distance calculation:
 * Converts distance in meters to a 5-byte intermediate array [unit, intMSB, intLSB, fracMSB, fracLSB].
 */
object NavigationHelper {
    fun formatDistance(distanceInMeters: Double): ByteArray {
        val unit: Byte
        val displayDistance: Double
        if (distanceInMeters < 1000.0) {
            unit = 1.toByte() // 1 = Meters
            displayDistance = distanceInMeters.toInt().toDouble() // Truncated to whole meters
        } else {
            unit = 0.toByte() // 0 = Kilometers
            displayDistance = distanceInMeters / 1000.0
        }

        val integerPart = displayDistance.toInt()
        val fracPart = if (unit.toInt() == 1) 0 else kotlin.math.round((displayDistance - integerPart.toDouble()) * 100.0).toInt()

        return byteArrayOf(
            unit,
            ((integerPart shr 8) and 0xFF).toByte(), // Int MSB
            (integerPart and 0xFF).toByte(),         // Int LSB
            ((fracPart shr 8) and 0xFF).toByte(),    // Frac MSB
            (fracPart and 0xFF).toByte()             // Frac LSB
        )
    }
}

/**
 * 66-entry translation switch from Google Navigation SDK maneuver IDs to PrimaryTurns.
 */
object GoogleManeuverTranslator {
    fun mapGoogleManeuver(maneuverId: Int): PrimaryTurns {
        return when (maneuverId) {
            0 -> PrimaryTurns.WRONG_WAY
            1 -> PrimaryTurns.STRAIGHT
            2, 3, 4 -> PrimaryTurns.DESTINATION_REACHED
            5 -> PrimaryTurns.STRAIGHT
            6 -> PrimaryTurns.TURN_LEFT
            7 -> PrimaryTurns.TURN_RIGHT
            8 -> PrimaryTurns.KEEP_LEFT
            9 -> PrimaryTurns.KEEP_RIGHT
            10 -> PrimaryTurns.TURN_SLIGHT_LEFT
            11 -> PrimaryTurns.TURN_SLIGHT_RIGHT
            12 -> PrimaryTurns.TURN_SHARP_LEFT
            13 -> PrimaryTurns.TURN_SHARP_RIGHT
            14 -> PrimaryTurns.U_TURN_RIGHT
            15 -> PrimaryTurns.U_TURN_LEFT
            16, 17, 18 -> PrimaryTurns.MERGE
            19 -> PrimaryTurns.FORK_LEFT
            20 -> PrimaryTurns.FORK_RIGHT
            21 -> PrimaryTurns.STRAIGHT
            22 -> PrimaryTurns.TURN_LEFT
            23 -> PrimaryTurns.TURN_RIGHT
            24, 25 -> PrimaryTurns.WRONG_WAY
            26 -> PrimaryTurns.TURN_SLIGHT_LEFT
            27 -> PrimaryTurns.TURN_SLIGHT_RIGHT
            28 -> PrimaryTurns.TURN_SHARP_LEFT
            29 -> PrimaryTurns.TURN_SHARP_RIGHT
            30, 31 -> PrimaryTurns.WRONG_WAY
            32 -> PrimaryTurns.STRAIGHT
            33 -> PrimaryTurns.TURN_LEFT
            34 -> PrimaryTurns.TURN_RIGHT
            35, 36 -> PrimaryTurns.WRONG_WAY
            37 -> PrimaryTurns.TURN_SLIGHT_LEFT
            38 -> PrimaryTurns.TURN_SLIGHT_RIGHT
            39 -> PrimaryTurns.TURN_SHARP_LEFT
            40 -> PrimaryTurns.TURN_SHARP_RIGHT
            41, 42 -> PrimaryTurns.WRONG_WAY
            43, 45, 47, 49, 51, 53, 55, 57, 59, 61 -> PrimaryTurns.ROUNDABOUT_LEFT // Clockwise
            44, 46, 48, 50, 52, 54, 56, 58, 60, 62 -> PrimaryTurns.ROUNDABOUT_RIGHT // Anti-Clockwise
            63 -> PrimaryTurns.FERRY
            64 -> PrimaryTurns.FERRY_TRAIN
            65 -> PrimaryTurns.STRAIGHT
            else -> PrimaryTurns.WRONG_WAY
        }
    }
}

/**
 * Roundabout direction resolver and instruction text regex parser.
 */
object RoundaboutHelper {
    private val PATTERNS = listOf(
        Regex("take the (\\d+)(?:st|nd|rd|th) exit", RegexOption.IGNORE_CASE),
        Regex("(\\d+)(?:st|nd|rd|th) exit", RegexOption.IGNORE_CASE),
        Regex("exit\\s+(\\d+)", RegexOption.IGNORE_CASE)
    )

    fun isClockwise(googleManeuverId: Int): Boolean {
        return googleManeuverId in 43..62 && (googleManeuverId % 2 != 0)
    }

    fun parseRoundaboutExitFromInstruction(instruction: String?): Int {
        if (instruction.isNullOrBlank()) return 0
        for (pattern in PATTERNS) {
            val match = pattern.find(instruction)
            if (match != null) {
                val num = match.groupValues[1].toIntOrNull() ?: 0
                return num.coerceIn(1, 7)
            }
        }
        return 0
    }
}

/**
 * Builder matching the exact API and byte layout tested in NavigationAndTbtE2ETest.kt.
 */
class TbtNavFrameBuilder {
    var maneuverCode: Int = TbtManeuvers.TURN_RIGHT // 0..21
    var distanceUnit: DistanceUnit = DistanceUnit.METER // 0..4
    var isClockwiseRoundabout: Boolean = false
    var roundaboutExit: Int = 0 // 1..7
    var distanceToTurnMeters: Int = 250
    var etaMinutes: Int = 18 // uint16 little-endian
    var streetName: String = "JM Road"

    fun build(): ByteArray {
        val frame = ByteArray(48)

        // Byte 0: Bits 0..3 maneuver (0..15 or masked), Bits 4..6 unit, Bit 7 roundabout flag
        val mBits = maneuverCode and 0x0F
        val uBits = (distanceUnit.code and 0x07) shl 4
        val rBit = if (isClockwiseRoundabout) 0x80 else 0x00
        frame[0] = (rBit or uBits or mBits).toByte()

        // Byte 1: roundabout exit (1..7) or secondary turn
        frame[1] = (roundaboutExit and 0xFF).toByte()

        // Bytes 2..5: Distance formatted BCD / ASCII (clamped 0000..9999)
        val clampedDist = distanceToTurnMeters.coerceIn(0, 9999)
        val distStr = clampedDist.toString().padStart(4, '0').takeLast(4)
        for (i in 0 until 4) {
            frame[2 + i] = distStr[i].code.toByte()
        }

        // Bytes 6..7: ETA in minutes (little-endian uint16)
        frame[6] = (etaMinutes and 0xFF).toByte()
        frame[7] = ((etaMinutes shr 8) and 0xFF).toByte()

        // Bytes 8..46: Street name sanitized ASCII [0-9a-zA-Z.], max 31 chars + trailing dot if truncated
        val sanitized = sanitizeStreetName(streetName)
        val streetBytes = sanitized.toByteArray(StandardCharsets.US_ASCII).take(39)
        for (i in streetBytes.indices) {
            frame[8 + i] = streetBytes[i]
        }

        // Byte 47: Modulo 256 sum checksum of bytes 0..46
        var sum = 0
        for (i in 0 until 47) {
            sum += (frame[i].toInt() and 0xFF)
        }
        frame[47] = (sum and 0xFF).toByte()

        return frame
    }

    companion object {
        fun sanitizeStreetName(raw: String): String {
            val cleaned = raw.replace(Regex("[^0-9a-zA-Z.]"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")

            return if (cleaned.length > 31) {
                cleaned.take(31) + "."
            } else {
                cleaned
            }
        }
    }
}

/**
 * Authoritative Dalvik TbtNavFrame generator matching classes3.dex decompilation.
 */
object TbtNavFrame {

    /**
     * Exact decompiled buildNavHelperFrame method from classes3.dex:
     * Lcom/bajajconnect/utils/TbtNavFrame;.buildNavHelperFrame:(IIZIIIIILjava/lang/String;)[B
     */
    fun buildNavHelperFrame(
        navMode: Int,
        primaryTurnCode: Int,
        isClockwise: Boolean,
        distToTurnMeters: Int,
        distTotalMeters: Int,
        etaHour: Int,
        etaMinute: Int,
        etaAmPm: Int,
        gpsStatus: Int,
        takeMeHomeAck: Int,
        streetName: String?
    ): ByteArray {
        val frame = ByteArray(48)

        val distTurn = NavigationHelper.formatDistance(distToTurnMeters.toDouble())
        val distTotal = NavigationHelper.formatDistance(distTotalMeters.toDouble())

        // Sanitize street
        var sanitizedStreet = (streetName ?: "")
            .replace(Regex("[^0-9a-zA-Z.]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        if (sanitizedStreet.length > 31) {
            sanitizedStreet = sanitizedStreet.substring(0, 31) + "."
        }

        // Byte 0: Compound Navigation Header
        // Bits 0..3: navMode (1 = active, 0 = idle)
        // Bits 4..6: distTurnUnit (1 = Meter, 0 = Km)
        // Bit 7: inverted clockwise bit (0 if clockwise, 1 if anti-clockwise)
        val distTurnUnit = distTurn[0].toInt() and 0x07
        val cwBit = if (isClockwise) 0 else 1
        frame[0] = ((navMode and 0x0F) or (distTurnUnit shl 4) or ((cwBit and 0x01) shl 7)).toByte()

        // Byte 1: Maneuver ASCII code
        frame[1] = (primaryTurnCode and 0xFF).toByte()

        // Bytes 2..5: Distance to turn (Little-Endian: Frac LSB, Frac MSB, Int LSB, Int MSB)
        frame[2] = distTurn[4] // Frac LSB
        frame[3] = distTurn[3] // Frac MSB
        frame[4] = distTurn[2] // Int LSB
        frame[5] = distTurn[1] // Int MSB

        // Byte 6: ETA Minute (0..59)
        frame[6] = (etaMinute and 0xFF).toByte()

        // Byte 7: ETA Hour & AM/PM
        frame[7] = ((etaHour and 0x0F) or ((etaAmPm and 0x0F) shl 4)).toByte()

        // Bytes 8..11: Distance Total (Little-Endian: Frac LSB, Frac MSB, Int LSB, Int MSB)
        frame[8] = distTotal[4] // Frac LSB
        frame[9] = distTotal[3] // Frac MSB
        frame[10] = distTotal[2] // Int LSB
        frame[11] = distTotal[1] // Int MSB

        // Byte 12: Total Distance Unit & GPS Status
        val distTotalUnit = distTotal[0].toInt() and 0x03
        val gpsBits = (gpsStatus and 0x07) shl 2
        frame[12] = (distTotalUnit or gpsBits).toByte()

        // Byte 13: Take Me Home Ack
        frame[13] = (takeMeHomeAck and 0xFF).toByte()

        // Byte 14: Street Name Length
        val streetBytes = sanitizedStreet.toByteArray(StandardCharsets.US_ASCII)
        val streetLen = minOf(streetBytes.size, 32)
        frame[14] = streetLen.toByte()

        // Bytes 15..46: Street Name ASCII zero-padded
        System.arraycopy(streetBytes, 0, frame, 15, streetLen)

        // Byte 47: Checksum modulo 256
        var sum = 0
        for (i in 0 until 47) {
            sum += (frame[i].toInt() and 0xFF)
        }
        frame[47] = (sum and 0xFF).toByte()

        return frame
    }

    /**
     * Validates frame checksum.
     */
    fun validateChecksum(frame: ByteArray): Boolean {
        if (frame.size != 48) return false
        var sum = 0
        for (i in 0 until 47) {
            sum += (frame[i].toInt() and 0xFF)
        }
        return frame[47] == (sum and 0xFF).toByte()
    }
}
