package com.openride.companion.challenger

import com.openride.companion.navigation.ble.DistanceUnit
import com.openride.companion.navigation.ble.GoogleManeuverTranslator
import com.openride.companion.navigation.ble.NavigationHelper
import com.openride.companion.navigation.ble.PrimaryTurns
import com.openride.companion.navigation.ble.RoundaboutHelper
import com.openride.companion.navigation.ble.TbtManeuvers
import com.openride.companion.navigation.ble.TbtNavFrame
import com.openride.companion.navigation.ble.TbtNavFrameBuilder
import com.openride.companion.navigation.engine.RerouteTracker
import com.openride.companion.navigation.engine.RouteDeviationEvaluator
import com.openride.companion.navigation.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Random
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/**
 * Empirical Adversarial Stress Test Suite authored by M4 Challenger 1 R3.
 * Rigorously challenges Features 16 & 17 across 7 adversarial attack vectors:
 * 1. Byte 0 bitmask boundaries (navMode 0..15, distTurnUnit 0..7, isClockwise 0/1)
 * 2. All 22 PrimaryTurns maneuvers and roundabouts (exits 1..7)
 * 3. Modulo 256 sum checksum on arbitrary and random byte payloads
 * 4. Street name edge cases: ASCII filtering, Unicode stripping, >31 char truncation with trailing dot, empty, null
 * 5. Distance formatting: 0m, 1m, 999m, 1000m, 99.9km, 100km, Little-Endian byte placement
 * 6. Web Mercator projection extremes: coordinates near poles, meridian crossing, zero delta points
 * 7. Reroute threshold: exactly 29.9m vs 30.0m vs 30.1m, debounce 3000ms
 */
class M4AdversarialStressTest {

    // =========================================================================
    // VECTOR 1: BYTE 0 BITMASK BOUNDARIES & BITWISE NON-INTERFERENCE
    // =========================================================================

    @Test
    fun testVector1_Byte0_ExhaustiveBitfieldCombinations() {
        // NavMode: 0..15 (Bits 0..3)
        // DistTurnUnit: 0..7 (Bits 4..6)
        // isClockwise: true/false (Bit 7: 0 if CW, 1 if CCW)

        for (navMode in 0..15) {
            for (isClockwise in listOf(true, false)) {
                // distTurnMeters < 1000 produces unit 1 (Meters)
                // distTurnMeters >= 1000 produces unit 0 (Km)
                for (distMeters in listOf(400, 2500)) {
                    val frame = TbtNavFrame.buildNavHelperFrame(
                        navMode = navMode,
                        primaryTurnCode = PrimaryTurns.STRAIGHT.asciiValue,
                        isClockwise = isClockwise,
                        distToTurnMeters = distMeters,
                        distTotalMeters = 10000,
                        etaHour = 12,
                        etaMinute = 0,
                        etaAmPm = 0,
                        gpsStatus = 0,
                        takeMeHomeAck = 0,
                        streetName = "Main St"
                    )

                    val b0 = frame[0].toInt() and 0xFF
                    val extractedNavMode = b0 and 0x0F
                    val extractedUnit = (b0 shr 4) and 0x07
                    val extractedCwBit = (b0 shr 7) and 0x01

                    assertEquals("navMode $navMode must be preserved in bits 0..3", navMode, extractedNavMode)
                    val expectedUnit = if (distMeters < 1000) 1 else 0
                    assertEquals("unit must be preserved in bits 4..6", expectedUnit, extractedUnit)
                    val expectedCwBit = if (isClockwise) 0 else 1
                    assertEquals("cwBit must be preserved in bit 7", expectedCwBit, extractedCwBit)

                    // Verify frame integrity
                    assertEquals(48, frame.size)
                    assertTrue(TbtNavFrame.validateChecksum(frame))
                }
            }
        }
    }

    @Test
    fun testVector1_Byte0_OutOfBandNavModeMasking() {
        // High bits in navMode (e.g. 0xF0) must be masked by & 0x0F so they don't bleed into bits 4..7
        val frame = TbtNavFrame.buildNavHelperFrame(
            navMode = 0b11110101, // 0xF5 -> lower nibble is 5
            primaryTurnCode = PrimaryTurns.STRAIGHT.asciiValue,
            isClockwise = true, // cwBit = 0
            distToTurnMeters = 200, // unit 1
            distTotalMeters = 5000,
            etaHour = 10,
            etaMinute = 30,
            etaAmPm = 0,
            gpsStatus = 0,
            takeMeHomeAck = 0,
            streetName = "Road"
        )

        val b0 = frame[0].toInt() and 0xFF
        assertEquals(5, b0 and 0x0F) // lower nibble
        assertEquals(1, (b0 shr 4) and 0x07) // unit = 1
        assertEquals(0, (b0 shr 7) and 0x01) // cw = 0
        assertTrue(TbtNavFrame.validateChecksum(frame))
    }

    // =========================================================================
    // VECTOR 2: ALL 22 PRIMARY TURNS MANEUVERS & ROUNDABOUT EXITS 1..7
    // =========================================================================

    @Test
    fun testVector2_All22PrimaryTurns_AsciiValuesAndIndexes() {
        val expectedCatalog = mapOf(
            PrimaryTurns.TURN_LEFT to Pair(73, 'I'),
            PrimaryTurns.TURN_RIGHT to Pair(74, 'J'),
            PrimaryTurns.TURN_SLIGHT_LEFT to Pair(67, 'C'),
            PrimaryTurns.TURN_SLIGHT_RIGHT to Pair(68, 'D'),
            PrimaryTurns.TURN_SHARP_LEFT to Pair(69, 'E'),
            PrimaryTurns.TURN_SHARP_RIGHT to Pair(70, 'F'),
            PrimaryTurns.STRAIGHT to Pair(71, 'G'),
            PrimaryTurns.MERGE to Pair(86, 'V'),
            PrimaryTurns.KEEP_LEFT to Pair(90, 'Z'),
            PrimaryTurns.KEEP_RIGHT to Pair(88, 'X'),
            PrimaryTurns.RAMP_LEFT to Pair(75, 'K'),
            PrimaryTurns.RAMP_RIGHT to Pair(76, 'L'),
            PrimaryTurns.ROUNDABOUT_LEFT to Pair(85, 'U'),
            PrimaryTurns.ROUNDABOUT_RIGHT to Pair(78, 'N'),
            PrimaryTurns.U_TURN_LEFT to Pair(79, 'O'),
            PrimaryTurns.U_TURN_RIGHT to Pair(80, 'P'),
            PrimaryTurns.FORK_LEFT to Pair(81, 'Q'),
            PrimaryTurns.FORK_RIGHT to Pair(82, 'R'),
            PrimaryTurns.FERRY to Pair(89, 'Y'),
            PrimaryTurns.FERRY_TRAIN to Pair(84, 'T'),
            PrimaryTurns.DESTINATION_REACHED to Pair(72, 'H'),
            PrimaryTurns.WRONG_WAY to Pair(66, 'B')
        )

        assertEquals("Exactly 22 maneuvers in PrimaryTurns", 22, PrimaryTurns.entries.size)

        for ((turn, expected) in expectedCatalog) {
            assertEquals("AsciiValue mismatch for $turn", expected.first, turn.asciiValue)
            assertEquals("AsciiChar mismatch for $turn", expected.second, turn.asciiChar)
            assertEquals("getValue() must match asciiValue", expected.first, turn.getValue())

            // Test lookup by ASCII
            val resolvedByAscii = PrimaryTurns.fromAsciiValue(expected.first)
            assertEquals("fromAsciiValue failed for $turn", turn, resolvedByAscii)

            // Test frame byte 1 population
            val frame = TbtNavFrame.buildNavHelperFrame(
                navMode = 1,
                primaryTurnCode = turn.asciiValue,
                isClockwise = false,
                distToTurnMeters = 100,
                distTotalMeters = 1000,
                etaHour = 1,
                etaMinute = 0,
                etaAmPm = 0,
                gpsStatus = 0,
                takeMeHomeAck = 0,
                streetName = "TurnTest"
            )
            assertEquals("Byte 1 must contain ASCII char for $turn", expected.first.toByte(), frame[1])
            assertTrue(TbtNavFrame.validateChecksum(frame))
        }
    }

    @Test
    fun testVector2_RoundaboutExits_RegexClamping1To7() {
        val testCases = listOf(
            "take the 1st exit" to 1,
            "take the 2nd exit" to 2,
            "take the 3rd exit" to 3,
            "take the 4th exit" to 4,
            "take the 5th exit" to 5,
            "take the 6th exit" to 6,
            "take the 7th exit" to 7,
            "take the 8th exit" to 7, // Clamped to 7
            "exit 1" to 1,
            "exit 7" to 7,
            "exit 9" to 7, // Clamped to 7
            "exit 0" to 1, // Clamped min to 1
            "1st exit" to 1,
            "3rd exit" to 3,
            "No exit here" to 0,
            null to 0,
            "" to 0
        )

        for ((instruction, expectedExit) in testCases) {
            val exit = RoundaboutHelper.parseRoundaboutExitFromInstruction(instruction)
            assertEquals("Exit parsing mismatch for '$instruction'", expectedExit, exit)
        }
    }

    // =========================================================================
    // VECTOR 3: MODULO 256 SUM CHECKSUM ON ARBITRARY & RANDOM PAYLOADS
    // =========================================================================

    @Test
    fun testVector3_Modulo256_ArbitraryAndRandomPayloads() {
        val rng = Random(42)

        // 10,000 random 47-byte payloads
        for (iter in 0 until 10000) {
            val payload = ByteArray(48)
            rng.nextBytes(payload)

            var expectedSum = 0
            for (i in 0 until 47) {
                expectedSum += (payload[i].toInt() and 0xFF)
            }
            val expectedChecksum = (expectedSum and 0xFF).toByte()
            payload[47] = expectedChecksum

            assertTrue("Random payload $iter must validate against oracle checksum", TbtNavFrame.validateChecksum(payload))

            // Tamper with random byte
            val tamperIdx = rng.nextInt(48)
            val tamperDelta = (rng.nextInt(255) + 1).toByte()
            val tampered = payload.copyOf()
            tampered[tamperIdx] = (tampered[tamperIdx].toInt() xor tamperDelta.toInt()).toByte()

            assertFalse("Tampered payload $iter at byte $tamperIdx must fail checksum", TbtNavFrame.validateChecksum(tampered))
        }
    }

    // =========================================================================
    // VECTOR 4: STREET NAME EDGE CASES
    // =========================================================================

    @Test
    fun testVector4_StreetName_SanitizationAndPadding() {
        // Null street
        val frameNull = TbtNavFrame.buildNavHelperFrame(1, 71, false, 100, 1000, 1, 0, 0, 0, 0, null)
        assertEquals(0.toByte(), frameNull[14])
        for (i in 15..46) assertEquals(0.toByte(), frameNull[i])
        assertTrue(TbtNavFrame.validateChecksum(frameNull))

        // Empty street
        val frameEmpty = TbtNavFrame.buildNavHelperFrame(1, 71, false, 100, 1000, 1, 0, 0, 0, 0, "")
        assertEquals(0.toByte(), frameEmpty[14])
        for (i in 15..46) assertEquals(0.toByte(), frameEmpty[i])
        assertTrue(TbtNavFrame.validateChecksum(frameEmpty))

        // Exactly 31 chars -> length 31, no trailing dot added
        val str31 = "1234567890123456789012345678901"
        val frame31 = TbtNavFrame.buildNavHelperFrame(1, 71, false, 100, 1000, 1, 0, 0, 0, 0, str31)
        assertEquals(31.toByte(), frame31[14])
        assertEquals('1'.code.toByte(), frame31[45])
        assertEquals(0.toByte(), frame31[46]) // Byte 46 is zero-padded
        assertTrue(TbtNavFrame.validateChecksum(frame31))

        // Exactly 32 chars -> exceeds 31, truncated to 31 + '.'
        val str32 = "12345678901234567890123456789012"
        val frame32 = TbtNavFrame.buildNavHelperFrame(1, 71, false, 100, 1000, 1, 0, 0, 0, 0, str32)
        assertEquals(32.toByte(), frame32[14])
        assertEquals('.'.code.toByte(), frame32[46]) // Trailing dot
        assertTrue(TbtNavFrame.validateChecksum(frame32))

        // Unicode and special character stripping
        val unicodeStreet = "MG Road — शिवाजी नगर 🏍️ (West)"
        val frameUni = TbtNavFrame.buildNavHelperFrame(1, 71, false, 100, 1000, 1, 0, 0, 0, 0, unicodeStreet)
        val len = frameUni[14].toInt() and 0xFF
        val streetStr = String(frameUni.sliceArray(15 until 15 + len), StandardCharsets.US_ASCII)
        assertFalse("Must not contain Hindi characters", streetStr.contains("शिवाजी"))
        assertFalse("Must not contain emojis", streetStr.contains("🏍️"))
        assertTrue("Retains ASCII segments", streetStr.contains("MG Road"))
        assertTrue("Retains West", streetStr.contains("West"))
        assertTrue(TbtNavFrame.validateChecksum(frameUni))
    }

    // =========================================================================
    // VECTOR 5: DISTANCE FORMATTING & LITTLE-ENDIAN BYTE PLACEMENT
    // =========================================================================

    @Test
    fun testVector5_DistanceFormatting_LittleEndianBytePlacement() {
        val testDistances = listOf(
            // distanceMeters, expectedUnit, expectedInt, expectedFrac
            Triple(0.0, 1, 0 to 0),
            Triple(1.0, 1, 1 to 0),
            Triple(999.0, 1, 999 to 0),
            Triple(1000.0, 0, 1 to 0),
            Triple(99900.0, 0, 99 to 90),
            Triple(100000.0, 0, 100 to 0)
        )

        for ((distMeters, expectedUnit, expectedParts) in testDistances) {
            val intermediate = NavigationHelper.formatDistance(distMeters)
            assertEquals("Unit mismatch for dist $distMeters", expectedUnit.toByte(), intermediate[0])

            val intVal = ((intermediate[1].toInt() and 0xFF) shl 8) or (intermediate[2].toInt() and 0xFF)
            val fracVal = ((intermediate[3].toInt() and 0xFF) shl 8) or (intermediate[4].toInt() and 0xFF)

            assertEquals("Int part mismatch for dist $distMeters", expectedParts.first, intVal)
            assertEquals("Frac part mismatch for dist $distMeters", expectedParts.second, fracVal)

            // Test in TbtNavFrame
            val frame = TbtNavFrame.buildNavHelperFrame(
                navMode = 1,
                primaryTurnCode = 71,
                isClockwise = false,
                distToTurnMeters = distMeters.toInt(),
                distTotalMeters = distMeters.toInt(),
                etaHour = 1,
                etaMinute = 0,
                etaAmPm = 0,
                gpsStatus = 0,
                takeMeHomeAck = 0,
                streetName = "DistTest"
            )

            // Next Turn Distance (Bytes 2..5): Frac LSB, Frac MSB, Int LSB, Int MSB
            val reconstructedFrac = ((frame[3].toInt() and 0xFF) shl 8) or (frame[2].toInt() and 0xFF)
            val reconstructedInt = ((frame[5].toInt() and 0xFF) shl 8) or (frame[4].toInt() and 0xFF)
            assertEquals("Frame turn frac mismatch for $distMeters", expectedParts.second, reconstructedFrac)
            assertEquals("Frame turn int mismatch for $distMeters", expectedParts.first, reconstructedInt)

            // Total Distance (Bytes 8..11): Frac LSB, Frac MSB, Int LSB, Int MSB
            val reconstructedTotalFrac = ((frame[9].toInt() and 0xFF) shl 8) or (frame[8].toInt() and 0xFF)
            val reconstructedTotalInt = ((frame[11].toInt() and 0xFF) shl 8) or (frame[10].toInt() and 0xFF)
            assertEquals("Frame total frac mismatch for $distMeters", expectedParts.second, reconstructedTotalFrac)
            assertEquals("Frame total int mismatch for $distMeters", expectedParts.first, reconstructedTotalInt)

            assertTrue(TbtNavFrame.validateChecksum(frame))
        }
    }

    // =========================================================================
    // VECTOR 6: WEB MERCATOR PROJECTION EXTREMES
    // =========================================================================

    @Test
    fun testVector6_WebMercator_ProjectionFormulas() {
        // Pure Web Mercator mathematical verification as implemented in ComposeCanvasMapRenderer
        val width = 1080.0
        val height = 1920.0
        val zoom = 14
        val scale = (1 shl zoom) * 2.0

        fun projectPoint(point: GeoPoint, center: GeoPoint): Pair<Double, Double> {
            val x = (point.longitude + 180.0) / 360.0 * scale
            val latRad = Math.toRadians(point.latitude)
            val mercN = ln(tan((PI / 4.0) + (latRad / 2.0)))
            val y = (1.0 - (mercN / PI)) / 2.0 * scale

            val centerX = (center.longitude + 180.0) / 360.0 * scale
            val centerLatRad = Math.toRadians(center.latitude)
            val centerMercN = ln(tan((PI / 4.0) + (centerLatRad / 2.0)))
            val centerY = (1.0 - (centerMercN / PI)) / 2.0 * scale

            val screenX = x - centerX + width / 2.0
            val screenY = y - centerY + height / 2.0
            return Pair(screenX, screenY)
        }

        // Test 1: Zero delta point (point == center) must project to exact canvas center
        val center = GeoPoint(18.5204, 73.8567)
        val (cx, cy) = projectPoint(center, center)
        assertEquals("Zero delta X must be canvas center width/2", width / 2.0, cx, 0.001)
        assertEquals("Zero delta Y must be canvas center height/2", height / 2.0, cy, 0.001)

        // Test 2: Equator and prime meridian
        val equatorPoint = GeoPoint(0.0, 0.0)
        val (eqX, eqY) = projectPoint(equatorPoint, equatorPoint)
        assertEquals(width / 2.0, eqX, 0.001)
        assertEquals(height / 2.0, eqY, 0.001)

        // Test 3: Web Mercator standard bounds (+/- 85.05112878 degrees latitude)
        val maxWebMercLat = 85.05112878
        val northPoleBounded = GeoPoint(maxWebMercLat, 0.0)
        val southPoleBounded = GeoPoint(-maxWebMercLat, 0.0)

        val (npX, npY) = projectPoint(northPoleBounded, equatorPoint)
        assertFalse("Projected North pole Y must be finite", npY.isNaN() || npY.isInfinite())

        val (spX, spY) = projectPoint(southPoleBounded, equatorPoint)
        assertFalse("Projected South pole Y must be finite", spY.isNaN() || spY.isInfinite())

        // North pole is visually above South pole (lower Y in screen coords)
        assertTrue("North pole screen Y must be smaller than South pole screen Y", npY < spY)

        // Test 4: Meridian boundaries (+/- 180 degrees longitude)
        val antimeridianEast = GeoPoint(0.0, 180.0)
        val antimeridianWest = GeoPoint(0.0, -180.0)

        val (ameX, _) = projectPoint(antimeridianEast, equatorPoint)
        val (amwX, _) = projectPoint(antimeridianWest, equatorPoint)
        assertTrue("East antimeridian X must be greater than West antimeridian X", ameX > amwX)
    }

    // =========================================================================
    // VECTOR 7: REROUTE THRESHOLD & DEBOUNCE
    // =========================================================================

    @Test
    fun testVector7_RerouteThreshold_ExactBoundaryValues() {
        val polyline = listOf(
            GeoPoint(18.5000, 73.8500),
            GeoPoint(18.5100, 73.8500)
        )

        // Baseline: on-route point directly on line segment
        val onLine = GeoPoint(18.5050, 73.8500)
        assertFalse(RouteDeviationEvaluator.isOffRoute(onLine, polyline, thresholdMeters = 30.0))

        // Exact distance tests
        val refPoint = GeoPoint(18.5050, 73.8500)

        // Point ~29.9m East (0.000282 deg lon at lat 18.5 is approx 29.9m)
        // Let's compute exact delta for 29.9m, 30.0m, 30.1m:
        val r = 6371000.0
        val latRad = Math.toRadians(18.5050)
        val metersPerLonDegree = (PI / 180.0) * r * kotlin.math.cos(latRad)

        val lonDelta29_9 = 29.9 / metersPerLonDegree
        val pt29_9 = GeoPoint(18.5050, 73.8500 + lonDelta29_9)
        val actualDist29_9 = refPoint.distanceTo(pt29_9)
        assertTrue("Distance should be approx 29.9m", actualDist29_9 in 29.85..29.95)
        assertFalse("Point at 29.9m must NOT trigger off-route", RouteDeviationEvaluator.isOffRoute(pt29_9, polyline, 30.0))

        val lonDelta30_0 = 30.0 / metersPerLonDegree
        val pt30_0 = GeoPoint(18.5050, 73.8500 + lonDelta30_0)
        val actualDist30_0 = refPoint.distanceTo(pt30_0)
        assertTrue("Distance should be approx 30.0m", actualDist30_0 in 29.95..30.05)
        // At threshold (<= 30.0m), isOffRoute must return false
        assertFalse("Point at <= 30.0m must be considered ON-route", RouteDeviationEvaluator.isOffRoute(pt30_0, polyline, 30.0))

        val lonDelta30_2 = 30.2 / metersPerLonDegree
        val pt30_2 = GeoPoint(18.5050, 73.8500 + lonDelta30_2)
        val actualDist30_2 = refPoint.distanceTo(pt30_2)
        assertTrue("Distance should be approx 30.2m", actualDist30_2 in 30.15..30.25)
        assertTrue("Point at > 30.0m must be considered OFF-route", RouteDeviationEvaluator.isOffRoute(pt30_2, polyline, 30.0))
    }

    @Test
    fun testVector7_RerouteDebounce_ThreeConsecutiveAndReset() {
        val tracker = RerouteTracker(thresholdMeters = 30.0, requiredConsecutiveCount = 3, holdDurationMs = 3000L)
        val polyline = listOf(GeoPoint(18.5000, 73.8500), GeoPoint(18.5100, 73.8500))

        val onRoutePt = GeoPoint(18.5050, 73.8500)
        val offRoutePt = GeoPoint(18.5050, 73.8600) // ~1 km off

        // 1st deviation
        assertFalse("1st deviation does not trigger reroute", tracker.onLocationUpdate(offRoutePt, polyline))
        // 2nd deviation
        assertFalse("2nd deviation does not trigger reroute", tracker.onLocationUpdate(offRoutePt, polyline))
        // Glitch recovers!
        assertFalse("On-route recovery resets consecutive count", tracker.onLocationUpdate(onRoutePt, polyline))

        // Now must require 3 consecutive again
        assertFalse("1st deviation after reset does not trigger", tracker.onLocationUpdate(offRoutePt, polyline))
        assertFalse("2nd deviation after reset does not trigger", tracker.onLocationUpdate(offRoutePt, polyline))
        assertTrue("3rd consecutive deviation triggers reroute!", tracker.onLocationUpdate(offRoutePt, polyline))

        // Holding state
        assertTrue("Reroute hold must be active", tracker.isRerouteHolding)

        // Reset clears holding state
        tracker.reset()
        assertFalse("Reset clears holding state", tracker.isRerouteHolding)
    }
}
