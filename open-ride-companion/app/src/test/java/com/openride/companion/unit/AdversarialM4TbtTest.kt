package com.openride.companion.unit

import com.openride.companion.navigation.ble.DistanceUnit
import com.openride.companion.navigation.ble.GoogleManeuverTranslator
import com.openride.companion.navigation.ble.NavigationHelper
import com.openride.companion.navigation.ble.PrimaryTurns
import com.openride.companion.navigation.ble.RoundaboutHelper
import com.openride.companion.navigation.ble.TbtManeuvers
import com.openride.companion.navigation.ble.TbtNavFrame
import com.openride.companion.navigation.ble.TbtNavFrameBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 * Adversarial Stress-Test Suite for Milestone 4 (Features 16 & 17).
 * Empirically challenges and stress-tests:
 * 1. Corrupt byte stream injection into TbtNavFrame (bit flips, arithmetic corruption, truncated & overflow buffers).
 * 2. Little-Endian distance boundary conditions (0m, 999m, 1000m, 1001m, uint16 max, IEEE-754 precision).
 * 3. Maneuver translations (Google codes outside 0..65, Int.MIN/MAX, safe fallbacks).
 * 4. Roundabout regex exit parsing (multi-digit, unicode/malicious, no numbers, case insensitivity).
 * 5. Street name sanitization (null, empty, >1000 chars, special characters, emoji, trailing dot).
 * 6. Modulo-256 checksum boundary rollover (sum 255, 256, 257, 512, 10000, 11985).
 * 7. Compound bitfield validation & multi-threaded concurrency safety.
 */
class AdversarialM4TbtTest {

    // Helper to generate a baseline valid 48-byte Dalvik frame
    private fun createValidDalvikFrame(
        navMode: Int = 1,
        primaryTurnCode: Int = PrimaryTurns.TURN_RIGHT.asciiValue,
        isClockwise: Boolean = false,
        distToTurnMeters: Int = 350,
        distTotalMeters: Int = 15400,
        etaHour: Int = 10,
        etaMinute: Int = 45,
        etaAmPm: Int = 1, // PM
        gpsStatus: Int = 0,
        takeMeHomeAck: Int = 0,
        streetName: String? = "FC Road"
    ): ByteArray {
        return TbtNavFrame.buildNavHelperFrame(
            navMode = navMode,
            primaryTurnCode = primaryTurnCode,
            isClockwise = isClockwise,
            distToTurnMeters = distToTurnMeters,
            distTotalMeters = distTotalMeters,
            etaHour = etaHour,
            etaMinute = etaMinute,
            etaAmPm = etaAmPm,
            gpsStatus = gpsStatus,
            takeMeHomeAck = takeMeHomeAck,
            streetName = streetName
        )
    }

    // =========================================================================
    // 1. CORRUPT BYTE STREAM INJECTION INTO TbtNavFrame
    // =========================================================================

    @Test
    fun testCorruptStream_EverySingleBitFlip_FailsChecksumValidation() {
        val validFrame = createValidDalvikFrame()
        assertEquals(48, validFrame.size)
        assertTrue("Baseline frame must have valid checksum", TbtNavFrame.validateChecksum(validFrame))

        var detectedCorruptions = 0

        // Flip every single bit in all 48 bytes (384 bits total)
        for (byteIdx in 0 until 48) {
            for (bit in 0 until 8) {
                val corrupted = validFrame.copyOf()
                corrupted[byteIdx] = (corrupted[byteIdx].toInt() xor (1 shl bit)).toByte()

                val isValid = TbtNavFrame.validateChecksum(corrupted)
                assertFalse(
                    "Bit flip at byte $byteIdx bit $bit must be detected as corrupt!",
                    isValid
                )
                detectedCorruptions++
            }
        }

        assertEquals("All 384 single-bit flips must be detected", 384, detectedCorruptions)
    }

    @Test
    fun testCorruptStream_ArithmeticChecksumCorruption_Rejected() {
        val validFrame = createValidDalvikFrame()
        val originalChecksum = validFrame[47]

        // Offsets from -128 to +127 (excluding 0)
        for (offset in -128..127) {
            if (offset == 0) continue
            val corrupted = validFrame.copyOf()
            corrupted[47] = ((originalChecksum.toInt() + offset) and 0xFF).toByte()

            assertFalse(
                "Checksum offset $offset must invalidate frame",
                TbtNavFrame.validateChecksum(corrupted)
            )
        }
    }

    @Test
    fun testCorruptStream_TruncatedBuffers_RejectedSafelyWithoutException() {
        val validFrame = createValidDalvikFrame()

        // Test truncated lengths from 0 up to 47
        val testTruncatedLengths = listOf(0, 1, 2, 7, 14, 15, 31, 46, 47)
        for (len in testTruncatedLengths) {
            val truncated = validFrame.copyOf(len)
            assertFalse(
                "Truncated buffer of length $len must be rejected by validateChecksum",
                TbtNavFrame.validateChecksum(truncated)
            )
        }
    }

    @Test
    fun testCorruptStream_BufferOverflows_RejectedSafelyWithoutException() {
        val validFrame = createValidDalvikFrame()

        // Test overflow lengths beyond 48
        val testOverflowLengths = listOf(49, 50, 64, 128, 256, 1024)
        for (len in testOverflowLengths) {
            val overflow = validFrame.copyOf(len)
            assertFalse(
                "Overflow buffer of length $len must be rejected by validateChecksum",
                TbtNavFrame.validateChecksum(overflow)
            )
        }
    }

    @Test
    fun testCorruptStream_RandomMutations_HighDetectionRate() {
        val validFrame = createValidDalvikFrame()
        val rng = java.util.Random(1337)
        var testedCount = 0

        for (i in 0 until 1000) {
            val mutated = validFrame.copyOf()
            val numBytesToCorrupt = rng.nextInt(5) + 1 // 1 to 5 bytes
            for (k in 0 until numBytesToCorrupt) {
                val bytePos = rng.nextInt(48)
                val delta = (rng.nextInt(255) + 1).toByte()
                mutated[bytePos] = (mutated[bytePos].toInt() xor delta.toInt()).toByte()
            }

            // Calculate expected checksum manually
            var expectedSum = 0
            for (b in 0 until 47) {
                expectedSum += (mutated[b].toInt() and 0xFF)
            }
            val expectedChecksum = (expectedSum and 0xFF).toByte()

            val actualChecksum = mutated[47]
            val reportedValid = TbtNavFrame.validateChecksum(mutated)

            assertEquals(
                "validateChecksum must match arithmetic equality exactly on iteration $i",
                actualChecksum == expectedChecksum,
                reportedValid
            )
            testedCount++
        }
        assertEquals(1000, testedCount)
    }

    // =========================================================================
    // 2. LITTLE-ENDIAN DISTANCE BOUNDARY CONDITIONS
    // =========================================================================

    @Test
    fun testDistance_0Meters_Boundary() {
        val res = NavigationHelper.formatDistance(0.0)
        assertEquals(1.toByte(), res[0]) // Unit: 1 = Meters
        assertEquals(0.toByte(), res[1]) // Int MSB
        assertEquals(0.toByte(), res[2]) // Int LSB
        assertEquals(0.toByte(), res[3]) // Frac MSB
        assertEquals(0.toByte(), res[4]) // Frac LSB

        val frame = createValidDalvikFrame(distToTurnMeters = 0, distTotalMeters = 0)
        // Bytes 2..5: Frac LSB, Frac MSB, Int LSB, Int MSB
        assertEquals(0.toByte(), frame[2])
        assertEquals(0.toByte(), frame[3])
        assertEquals(0.toByte(), frame[4])
        assertEquals(0.toByte(), frame[5])

        val reconstructedTurn = ((frame[5].toInt() and 0xFF) shl 8) or (frame[4].toInt() and 0xFF)
        assertEquals(0, reconstructedTurn)
        assertTrue(TbtNavFrame.validateChecksum(frame))
    }

    @Test
    fun testDistance_999Meters_BoundaryMetersUpperThreshold() {
        val res = NavigationHelper.formatDistance(999.0)
        assertEquals(1.toByte(), res[0]) // Unit: 1 = Meters
        val intVal = ((res[1].toInt() and 0xFF) shl 8) or (res[2].toInt() and 0xFF)
        assertEquals(999, intVal)
        assertEquals(0.toByte(), res[3]) // Frac MSB
        assertEquals(0.toByte(), res[4]) // Frac LSB

        val frame = createValidDalvikFrame(distToTurnMeters = 999)
        // Little-Endian bytes: 999 = 0x03E7 -> LSB = 0xE7, MSB = 0x03
        assertEquals(0xE7.toByte(), frame[4])
        assertEquals(0x03.toByte(), frame[5])

        val reconstructed = ((frame[5].toInt() and 0xFF) shl 8) or (frame[4].toInt() and 0xFF)
        assertEquals(999, reconstructed)
        assertTrue(TbtNavFrame.validateChecksum(frame))
    }

    @Test
    fun testDistance_1000Meters_BoundaryTransitionToKm() {
        val res = NavigationHelper.formatDistance(1000.0)
        assertEquals(0.toByte(), res[0]) // Unit: 0 = Kilometers
        val intVal = ((res[1].toInt() and 0xFF) shl 8) or (res[2].toInt() and 0xFF)
        assertEquals(1, intVal)
        val fracVal = ((res[3].toInt() and 0xFF) shl 8) or (res[4].toInt() and 0xFF)
        assertEquals(0, fracVal)

        val frame = createValidDalvikFrame(distToTurnMeters = 1000)
        assertEquals(0.toByte(), frame[2]) // Frac LSB
        assertEquals(0.toByte(), frame[3]) // Frac MSB
        assertEquals(1.toByte(), frame[4]) // Int LSB
        assertEquals(0.toByte(), frame[5]) // Int MSB
        assertTrue(TbtNavFrame.validateChecksum(frame))
    }

    @Test
    fun testDistance_1001Meters_And_1050Meters_Precision() {
        // 1001m -> 1.001 km -> 1 km, 0 frac
        val res1001 = NavigationHelper.formatDistance(1001.0)
        assertEquals(0.toByte(), res1001[0]) // KM
        val int1001 = ((res1001[1].toInt() and 0xFF) shl 8) or (res1001[2].toInt() and 0xFF)
        val frac1001 = ((res1001[3].toInt() and 0xFF) shl 8) or (res1001[4].toInt() and 0xFF)
        assertEquals(1, int1001)
        assertEquals(0, frac1001)

        // 1050m -> 1.050 km -> 1 km, 5 frac
        val res1050 = NavigationHelper.formatDistance(1050.0)
        assertEquals(0.toByte(), res1050[0]) // KM
        val int1050 = ((res1050[1].toInt() and 0xFF) shl 8) or (res1050[2].toInt() and 0xFF)
        val frac1050 = ((res1050[3].toInt() and 0xFF) shl 8) or (res1050[4].toInt() and 0xFF)
        assertEquals(1, int1050)
        assertEquals(5, frac1050)
    }

    @Test
    fun testDistance_Uint16Max_AndExtremeDistances() {
        // 65535 km = 65,535,000 meters
        val resMaxKm = NavigationHelper.formatDistance(65535000.0)
        assertEquals(0.toByte(), resMaxKm[0])
        val intKm = ((resMaxKm[1].toInt() and 0xFF) shl 8) or (resMaxKm[2].toInt() and 0xFF)
        assertEquals(65535, intKm)

        // Frame with total distance 65535000 meters
        val frame = createValidDalvikFrame(distTotalMeters = 65535000)
        // Bytes 10..11: Int LSB, Int MSB
        assertEquals(0xFF.toByte(), frame[10])
        assertEquals(0xFF.toByte(), frame[11])
        val reconstructedTotal = ((frame[11].toInt() and 0xFF) shl 8) or (frame[10].toInt() and 0xFF)
        assertEquals(65535, reconstructedTotal)
        assertTrue(TbtNavFrame.validateChecksum(frame))
    }

    @Test
    fun testDistance_Ieee754RoundingEdgeCases() {
        // 1990.0m -> 1.99 km -> int 1, frac 99
        val res1990 = NavigationHelper.formatDistance(1990.0)
        val frac1990 = ((res1990[3].toInt() and 0xFF) shl 8) or (res1990[4].toInt() and 0xFF)
        assertEquals(99, frac1990)

        // 1234.567m -> 1.234567 km -> int 1, frac round(23.4567) = 23
        val res1234 = NavigationHelper.formatDistance(1234.567)
        val frac1234 = ((res1234[3].toInt() and 0xFF) shl 8) or (res1234[4].toInt() and 0xFF)
        assertEquals(23, frac1234)

        // 999.9m -> under 1000.0 -> unit 1 (meters), truncated to 999, frac 0
        val res9999 = NavigationHelper.formatDistance(999.9)
        assertEquals(1.toByte(), res9999[0])
        val int9999 = ((res9999[1].toInt() and 0xFF) shl 8) or (res9999[2].toInt() and 0xFF)
        assertEquals(999, int9999)
        assertEquals(0.toByte(), res9999[3])
        assertEquals(0.toByte(), res9999[4])
    }

    // =========================================================================
    // 3. MANEUVER TRANSLATIONS & BOUNDARY SAFETY
    // =========================================================================

    @Test
    fun testManeuver_OutOfRangeCodes_FallbackSafelyToWrongWay() {
        val outOfRangeCodes = listOf(
            -1, -2, -100, -9999, Int.MIN_VALUE,
            66, 67, 100, 255, 9999, Int.MAX_VALUE
        )

        for (code in outOfRangeCodes) {
            val result = GoogleManeuverTranslator.mapGoogleManeuver(code)
            assertEquals(
                "Code $code must safely map to WRONG_WAY",
                PrimaryTurns.WRONG_WAY,
                result
            )
            assertEquals(66, result.asciiValue)
            assertEquals('B', result.asciiChar)
        }
    }

    @Test
    fun testManeuver_Code0_FallbackToWrongWay() {
        assertEquals(PrimaryTurns.WRONG_WAY, GoogleManeuverTranslator.mapGoogleManeuver(0))
    }

    @Test
    fun testManeuver_BoundaryCodesWithin0To65() {
        // First valid after 0:
        assertEquals(PrimaryTurns.STRAIGHT, GoogleManeuverTranslator.mapGoogleManeuver(1))
        // Destination codes:
        assertEquals(PrimaryTurns.DESTINATION_REACHED, GoogleManeuverTranslator.mapGoogleManeuver(2))
        assertEquals(PrimaryTurns.DESTINATION_REACHED, GoogleManeuverTranslator.mapGoogleManeuver(3))
        assertEquals(PrimaryTurns.DESTINATION_REACHED, GoogleManeuverTranslator.mapGoogleManeuver(4))
        // Roundabout bands (43..62):
        assertEquals(PrimaryTurns.ROUNDABOUT_LEFT, GoogleManeuverTranslator.mapGoogleManeuver(43))
        assertEquals(PrimaryTurns.ROUNDABOUT_RIGHT, GoogleManeuverTranslator.mapGoogleManeuver(44))
        assertEquals(PrimaryTurns.ROUNDABOUT_LEFT, GoogleManeuverTranslator.mapGoogleManeuver(61))
        assertEquals(PrimaryTurns.ROUNDABOUT_RIGHT, GoogleManeuverTranslator.mapGoogleManeuver(62))
        // Ferries:
        assertEquals(PrimaryTurns.FERRY, GoogleManeuverTranslator.mapGoogleManeuver(63))
        assertEquals(PrimaryTurns.FERRY_TRAIN, GoogleManeuverTranslator.mapGoogleManeuver(64))
        // Last valid code 65:
        assertEquals(PrimaryTurns.STRAIGHT, GoogleManeuverTranslator.mapGoogleManeuver(65))
    }

    @Test
    fun testPrimaryTurns_FromAsciiValue_And_FromManeuverIndex_Fallbacks() {
        // Unknown ASCII value falls back to WRONG_WAY
        assertEquals(PrimaryTurns.WRONG_WAY, PrimaryTurns.fromAsciiValue(-1))
        assertEquals(PrimaryTurns.WRONG_WAY, PrimaryTurns.fromAsciiValue(0))
        assertEquals(PrimaryTurns.WRONG_WAY, PrimaryTurns.fromAsciiValue(999))

        // Known ASCII values
        assertEquals(PrimaryTurns.STRAIGHT, PrimaryTurns.fromAsciiValue('G'.code))
        assertEquals(PrimaryTurns.TURN_LEFT, PrimaryTurns.fromAsciiValue('I'.code))
        assertEquals(PrimaryTurns.TURN_RIGHT, PrimaryTurns.fromAsciiValue('J'.code))

        // Unknown maneuver index falls back to STRAIGHT
        assertEquals(PrimaryTurns.STRAIGHT, PrimaryTurns.fromManeuverIndex(-1))
        assertEquals(PrimaryTurns.STRAIGHT, PrimaryTurns.fromManeuverIndex(999))
    }

    // =========================================================================
    // 4. ROUNDABOUT REGEX EXIT PARSING
    // =========================================================================

    @Test
    fun testRoundaboutExit_MultiDigitStrings_ClampedToSeven() {
        assertEquals(7, RoundaboutHelper.parseRoundaboutExitFromInstruction("take the 12th exit"))
        assertEquals(7, RoundaboutHelper.parseRoundaboutExitFromInstruction("take the 25th exit"))
        assertEquals(7, RoundaboutHelper.parseRoundaboutExitFromInstruction("exit 99"))
        assertEquals(7, RoundaboutHelper.parseRoundaboutExitFromInstruction("exit 8"))
        assertEquals(7, RoundaboutHelper.parseRoundaboutExitFromInstruction("exit 7"))
        assertEquals(6, RoundaboutHelper.parseRoundaboutExitFromInstruction("exit 6"))
        assertEquals(1, RoundaboutHelper.parseRoundaboutExitFromInstruction("exit 1"))
    }

    @Test
    fun testRoundaboutExit_ClampedMinToOne() {
        assertEquals(1, RoundaboutHelper.parseRoundaboutExitFromInstruction("exit 0"))
    }

    @Test
    fun testRoundaboutExit_UnicodeAndMaliciousStrings() {
        // Script injection attempt
        assertEquals(
            3,
            RoundaboutHelper.parseRoundaboutExitFromInstruction("<script>alert('xss')</script> take the 3rd exit")
        )
        // SQL injection attempt
        assertEquals(
            2,
            RoundaboutHelper.parseRoundaboutExitFromInstruction("take the 2nd exit'; DROP TABLE routes; --")
        )
        // Extremely long number exceeding Int range -> safe fallback clamped within 1..7
        val overflowExit = RoundaboutHelper.parseRoundaboutExitFromInstruction("exit 9999999999999999999999999999")
        assertTrue("Overflowing number string must safely clamp to valid exit range 1..7", overflowExit in 1..7)

        // Non-matching numbers
        assertEquals(0, RoundaboutHelper.parseRoundaboutExitFromInstruction("exit -5"))
    }

    @Test
    fun testRoundaboutExit_EdgeCasesWithoutNumbers() {
        assertEquals(0, RoundaboutHelper.parseRoundaboutExitFromInstruction(null))
        assertEquals(0, RoundaboutHelper.parseRoundaboutExitFromInstruction(""))
        assertEquals(0, RoundaboutHelper.parseRoundaboutExitFromInstruction("    "))
        assertEquals(0, RoundaboutHelper.parseRoundaboutExitFromInstruction("Enter the roundabout and continue"))
        assertEquals(0, RoundaboutHelper.parseRoundaboutExitFromInstruction("Take the exit onto expressway"))
        assertEquals(0, RoundaboutHelper.parseRoundaboutExitFromInstruction("!@#$%^&*()"))
    }

    @Test
    fun testRoundaboutExit_CaseInsensitivity() {
        assertEquals(1, RoundaboutHelper.parseRoundaboutExitFromInstruction("TAKE THE 1ST EXIT"))
        assertEquals(4, RoundaboutHelper.parseRoundaboutExitFromInstruction("EXIT 4"))
        assertEquals(5, RoundaboutHelper.parseRoundaboutExitFromInstruction("5th exit"))
    }

    // =========================================================================
    // 5. STREET NAME SANITIZATION & BUFFER POPULATION
    // =========================================================================

    @Test
    fun testStreetName_NullAndEmpty_SafePadding() {
        val frameNull = createValidDalvikFrame(streetName = null)
        assertEquals(0.toByte(), frameNull[14]) // Length 0
        for (i in 15..46) {
            assertEquals(0.toByte(), frameNull[i]) // Zero padded
        }
        assertTrue(TbtNavFrame.validateChecksum(frameNull))

        val frameEmpty = createValidDalvikFrame(streetName = "")
        assertEquals(0.toByte(), frameEmpty[14]) // Length 0
        for (i in 15..46) {
            assertEquals(0.toByte(), frameEmpty[i])
        }
        assertTrue(TbtNavFrame.validateChecksum(frameEmpty))
    }

    @Test
    fun testStreetName_MassiveString_TruncatedWithTrailingDot() {
        val massiveStreet = "A".repeat(1500)
        val frame = createValidDalvikFrame(streetName = massiveStreet)

        assertEquals("Length byte must be exactly 32", 32.toByte(), frame[14])
        // Bytes 15..45 must be 'A' (31 chars)
        for (i in 15..45) {
            assertEquals('A'.code.toByte(), frame[i])
        }
        // Byte 46 must be trailing dot '.'
        assertEquals('.'.code.toByte(), frame[46])
        assertTrue(TbtNavFrame.validateChecksum(frame))

        // Same test via TbtNavFrameBuilder
        val sanitized = TbtNavFrameBuilder.sanitizeStreetName(massiveStreet)
        assertEquals(32, sanitized.length)
        assertTrue(sanitized.endsWith("."))
        assertEquals("A".repeat(31) + ".", sanitized)
    }

    @Test
    fun testStreetName_Boundary31And32Chars() {
        val str31 = "1234567890123456789012345678901" // 31 chars
        val frame31 = createValidDalvikFrame(streetName = str31)
        assertEquals(31.toByte(), frame31[14])
        assertEquals('1'.code.toByte(), frame31[45])
        assertEquals(0.toByte(), frame31[46]) // Unused byte must be 0x00
        assertTrue(TbtNavFrame.validateChecksum(frame31))

        val str32 = "12345678901234567890123456789012" // 32 chars -> exceeds 31, truncated to 31 + '.'
        val frame32 = createValidDalvikFrame(streetName = str32)
        assertEquals(32.toByte(), frame32[14])
        assertEquals('.'.code.toByte(), frame32[46])
        assertTrue(TbtNavFrame.validateChecksum(frame32))
    }

    @Test
    fun testStreetName_SpecialCharactersAndEmoji() {
        val input = "Road 🏍️ #42 / North @ Pune! ... St."
        val frame = createValidDalvikFrame(streetName = input)
        val len = frame[14].toInt() and 0xFF
        val streetBytes = ByteArray(len) { frame[15 + it] }
        val streetStr = String(streetBytes, StandardCharsets.US_ASCII)

        // Non-alphanumeric except dot replaced by spaces, collapsed
        assertFalse("Must not contain emojis or symbols", streetStr.contains("🏍️"))
        assertFalse(streetStr.contains("#"))
        assertFalse(streetStr.contains("@"))
        assertFalse(streetStr.contains("!"))
        assertTrue("Contains alphanumeric segments", streetStr.contains("Road"))
        assertTrue(streetStr.contains("42"))
        assertTrue(streetStr.contains("North"))
        assertTrue(streetStr.contains("Pune"))
        assertTrue(TbtNavFrame.validateChecksum(frame))
    }

    // =========================================================================
    // 6. MODULO-256 CHECKSUM BOUNDARY ROLLOVER
    // =========================================================================

    @Test
    fun testChecksum_ExactRolloverBoundaries() {
        fun makeCraftedFrame(targetSumBytes0To46: Int): ByteArray {
            val frame = ByteArray(48)
            var remaining = targetSumBytes0To46
            for (i in 0 until 47) {
                val fill = minOf(remaining, 255)
                frame[i] = fill.toByte()
                remaining -= fill
            }
            frame[47] = (targetSumBytes0To46 and 0xFF).toByte()
            return frame
        }

        // Sum = 255 -> checksum = 0xFF (-1)
        val frame255 = makeCraftedFrame(255)
        assertEquals(0xFF.toByte(), frame255[47])
        assertTrue("Sum 255 must validate", TbtNavFrame.validateChecksum(frame255))

        // Sum = 256 -> checksum = 0x00 (Rollover!)
        val frame256 = makeCraftedFrame(256)
        assertEquals(0x00.toByte(), frame256[47])
        assertTrue("Sum 256 (0) must validate", TbtNavFrame.validateChecksum(frame256))

        // Sum = 257 -> checksum = 0x01
        val frame257 = makeCraftedFrame(257)
        assertEquals(0x01.toByte(), frame257[47])
        assertTrue("Sum 257 (1) must validate", TbtNavFrame.validateChecksum(frame257))

        // Sum = 512 -> checksum = 0x00 (Rollover 2x!)
        val frame512 = makeCraftedFrame(512)
        assertEquals(0x00.toByte(), frame512[47])
        assertTrue("Sum 512 (0) must validate", TbtNavFrame.validateChecksum(frame512))

        // Sum = 10000 -> checksum = 10000 & 0xFF = 16 (0x10)
        val frame10000 = makeCraftedFrame(10000)
        assertEquals(16.toByte(), frame10000[47])
        assertTrue("Sum 10000 (16) must validate", TbtNavFrame.validateChecksum(frame10000))

        // Sum = 11985 (47 * 255, maximum possible sum) -> 11985 & 0xFF = 177 (0xB1)
        val frameMax = makeCraftedFrame(47 * 255)
        assertEquals((11985 and 0xFF).toByte(), frameMax[47])
        assertTrue("Maximum sum 11985 must validate", TbtNavFrame.validateChecksum(frameMax))
    }

    // =========================================================================
    // 7. COMPOUND BITFIELDS & MULTI-THREADED CONCURRENCY
    // =========================================================================

    @Test
    fun testCompoundBitfields_Byte0_AllCombinations() {
        for (navMode in 0..1) {
            for (distTurnMeters in listOf(250, 1500)) { // 250m -> unit 1; 1500m -> unit 0
                for (isClockwise in listOf(true, false)) {
                    val frame = createValidDalvikFrame(
                        navMode = navMode,
                        distToTurnMeters = distTurnMeters,
                        isClockwise = isClockwise
                    )

                    val b0 = frame[0].toInt() and 0xFF
                    val extractedNavMode = b0 and 0x0F
                    val extractedUnit = (b0 shr 4) and 0x07
                    val extractedCwBit = (b0 shr 7) and 0x01

                    assertEquals("navMode bitfield mismatch", navMode, extractedNavMode)
                    val expectedUnit = if (distTurnMeters < 1000) 1 else 0
                    assertEquals("unit bitfield mismatch", expectedUnit, extractedUnit)
                    val expectedCwBit = if (isClockwise) 0 else 1
                    assertEquals("cwBit bitfield mismatch", expectedCwBit, extractedCwBit)

                    assertTrue(TbtNavFrame.validateChecksum(frame))
                }
            }
        }
    }

    @Test
    fun testCompoundBitfields_Byte7_EtaHourAndAmPm() {
        for (hour in 1..12) {
            for (isPm in listOf(0, 1)) {
                val frame = createValidDalvikFrame(etaHour = hour, etaAmPm = isPm)
                val b7 = frame[7].toInt() and 0xFF
                val extractedHour = b7 and 0x0F
                val extractedAmPm = (b7 shr 4) and 0x0F

                assertEquals(hour, extractedHour)
                assertEquals(isPm, extractedAmPm)
                assertTrue(TbtNavFrame.validateChecksum(frame))
            }
        }
    }

    @Test
    fun testCompoundBitfields_Byte12_DistTotalUnitAndGps() {
        for (distMeters in listOf(500, 25000)) { // 500m -> unit 1, 25000m -> unit 0
            for (gps in 0..3) {
                val frame = createValidDalvikFrame(distTotalMeters = distMeters, gpsStatus = gps)
                val b12 = frame[12].toInt() and 0xFF
                val extractedUnit = b12 and 0x03
                val extractedGps = (b12 shr 2) and 0x07

                val expectedUnit = if (distMeters < 1000) 1 else 0
                assertEquals(expectedUnit, extractedUnit)
                assertEquals(gps, extractedGps)
                assertTrue(TbtNavFrame.validateChecksum(frame))
            }
        }
    }

    @Test
    fun testConcurrency_ParallelFrameGeneration_NoRaceConditions() = runBlocking {
        val successCount = AtomicInteger(0)
        val threadCount = 10
        val iterationsPerThread = 100

        val jobs = List(threadCount) { threadIdx ->
            launch(Dispatchers.Default) {
                for (iter in 0 until iterationsPerThread) {
                    val frame = createValidDalvikFrame(
                        navMode = iter % 2,
                        primaryTurnCode = PrimaryTurns.entries[iter % PrimaryTurns.entries.size].asciiValue,
                        isClockwise = (iter % 3 == 0),
                        distToTurnMeters = (threadIdx * 100) + iter,
                        distTotalMeters = 5000 + (threadIdx * 500) + iter,
                        etaHour = 1 + (iter % 12),
                        etaMinute = iter % 60,
                        etaAmPm = iter % 2,
                        streetName = "Road_${threadIdx}_$iter"
                    )

                    if (frame.size == 48 && TbtNavFrame.validateChecksum(frame)) {
                        successCount.incrementAndGet()
                    }
                }
            }
        }

        jobs.forEach { it.join() }
        assertEquals(
            "All parallel frame generations must succeed with valid checksums",
            threadCount * iterationsPerThread,
            successCount.get()
        )
    }
}
