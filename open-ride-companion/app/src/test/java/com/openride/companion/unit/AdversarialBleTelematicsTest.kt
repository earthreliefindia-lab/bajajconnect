package com.openride.companion.unit

import com.openride.companion.ble.AbsMode
import com.openride.companion.ble.BleConnectionState
import com.openride.companion.ble.DefaultOctBleClient
import com.openride.companion.ble.NewGeneralPacketConstants
import com.openride.companion.ble.NewGeneralPacketV2Builder
import com.openride.companion.ble.NewGeneralPacketV2Parser
import com.openride.companion.ble.OctBleCallback
import com.openride.companion.ble.OctGattConstants
import com.openride.companion.ble.RollingTelemetryParser
import com.openride.companion.ble.VehicleHealth
import com.openride.companion.ble.VehicleHealthEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Random

/**
 * Adversarial Test Suite for Milestone 3 BLE GATT Subsystem & Telematics Framing.
 * Stress-tests assumptions, edge cases, corruption recovery, overflow/wraparound,
 * MTU gating barriers, watchdog recovery, and Unicode sanitization.
 */
class AdversarialBleTelematicsTest {

    // =========================================================================
    // 1. PACKET CORRUPTION & TRUNCATION ADVERSARIAL PROBES
    // =========================================================================

    @Test
    fun testProbe1_1_TruncatedFramesRejected() {
        val truncatedSizes = listOf(0, 1, 2, 10, 35, 50, 87, 88)
        for (size in truncatedSizes) {
            val truncatedFrame = ByteArray(size) { 0x55 }
            val result = NewGeneralPacketV2Parser.parse(truncatedFrame)
            assertTrue("Frame of size $size must be rejected as Error", result is NewGeneralPacketV2Parser.ParseResult.Error)
            val error = result as NewGeneralPacketV2Parser.ParseResult.Error
            assertTrue(error.reason.contains("Invalid packet length") || error.reason.contains("expected 89 bytes"))

            assertFalse("NewGeneralPacketV2Builder.isValidChecksum must return false for size $size",
                NewGeneralPacketV2Builder.isValidChecksum(truncatedFrame))
        }
    }

    @Test
    fun testProbe1_2_OversizedFramesRejected() {
        val oversizedSizes = listOf(90, 100, 128, 256, 1024)
        for (size in oversizedSizes) {
            val oversizedFrame = ByteArray(size) { 0x00 }
            oversizedFrame[0] = NewGeneralPacketV2Parser.TELEMETRY_SYNC_BYTE_0
            oversizedFrame[1] = NewGeneralPacketV2Parser.TELEMETRY_SYNC_BYTE_1
            val result = NewGeneralPacketV2Parser.parse(oversizedFrame)
            assertTrue("Oversized frame of size $size must be rejected", result is NewGeneralPacketV2Parser.ParseResult.Error)
        }
    }

    @Test
    fun testProbe1_3_SingleBitChecksumCorruption_ExhaustiveTest() {
        // Construct a valid baseline frame
        val baseline = NewGeneralPacketV2Parser.createTelemetryFrame(
            sequence = 42,
            speedKmh = 55,
            rpm = 5000,
            fuelPercent = 80,
            gear = 4,
            voltageMilliVolts = 13200,
            coolantTempC = 85,
            ambientTempC = 25
        )
        assertEquals(89, baseline.size)
        assertTrue(NewGeneralPacketV2Parser.parse(baseline) is NewGeneralPacketV2Parser.ParseResult.Success)

        // Corrupt every single bit across the payload bytes (bytes 2 to 88)
        // Note: Corrupting byte 0 or 1 will fail header check first, which is also an Error.
        for (byteIdx in 2 until 89) {
            for (bit in 0..7) {
                val corrupted = baseline.clone()
                corrupted[byteIdx] = (corrupted[byteIdx].toInt() xor (1 shl bit)).toByte()

                val result = NewGeneralPacketV2Parser.parse(corrupted)
                assertTrue("Single-bit flip at byte $byteIdx bit $bit must be rejected",
                    result is NewGeneralPacketV2Parser.ParseResult.Error)

                if (byteIdx == 88) {
                    val error = result as NewGeneralPacketV2Parser.ParseResult.Error
                    assertTrue("Byte 88 bit flip must trigger Checksum mismatch", error.reason.contains("Checksum mismatch"))
                }
            }
        }
    }

    @Test
    fun testProbe1_4_CorruptedSyncHeaderRejected() {
        val validFrame = NewGeneralPacketV2Parser.createTelemetryFrame()

        // Sync byte 0 corrupted
        val badSync0 = validFrame.clone()
        badSync0[0] = 0xBB.toByte()
        assertTrue(NewGeneralPacketV2Parser.parse(badSync0) is NewGeneralPacketV2Parser.ParseResult.Error)

        // Sync byte 1 corrupted
        val badSync1 = validFrame.clone()
        badSync1[1] = 0x5B.toByte()
        assertTrue(NewGeneralPacketV2Parser.parse(badSync1) is NewGeneralPacketV2Parser.ParseResult.Error)

        // Both inverted
        val invertedSync = validFrame.clone()
        invertedSync[0] = 0x5A.toByte()
        invertedSync[1] = 0xBA.toByte()
        assertTrue(NewGeneralPacketV2Parser.parse(invertedSync) is NewGeneralPacketV2Parser.ParseResult.Error)
    }

    @Test
    fun testProbe1_5_Modulo256ChecksumSumOverflowBoundary() {
        // Construct frame with all 0xFF bytes for payload
        val saturatedPacket = ByteArray(89)
        var sum = 0
        for (i in 0 until 88) {
            saturatedPacket[i] = 0xFF.toByte()
            sum += 0xFF
        }
        val expectedChecksum = (sum and 0xFF).toByte()
        saturatedPacket[88] = expectedChecksum

        // Verify calculateChecksum handles sum > 256 (here sum = 88 * 255 = 22440)
        assertEquals(expectedChecksum, NewGeneralPacketV2Builder.calculateChecksum(saturatedPacket, 88))
        assertTrue(NewGeneralPacketV2Builder.isValidChecksum(saturatedPacket))
    }

    @Test
    fun testProbe1_6_SlidingWindowResync_RecoversAfter100BytesRandomGarbage() = runBlocking {
        val parser = RollingTelemetryParser(defaultVehicleId = "ADVERSARIAL_TEST")
        val validFrame = NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 77, speedKmh = 65, rpm = 6000)

        // Generate 100 bytes of deterministic pseudo-random garbage
        val random = Random(42)
        val garbage = ByteArray(100)
        random.nextBytes(garbage)
        // Ensure no accidental 0xBA 0x5A at the boundary
        garbage[98] = 0x00
        garbage[99] = 0x00

        val stream = garbage + validFrame
        parser.onBytesReceived(stream)

        // Poll for processing completion
        var attempts = 0
        while (parser.totalPacketsReceived < 1 && attempts < 20) {
            delay(50)
            attempts++
        }

        assertEquals("Valid packet must be parsed after 100 bytes garbage", 1, parser.totalPacketsReceived)
        assertEquals(65, parser.telemetryState.value.speedKmh)
        assertEquals(6000, parser.telemetryState.value.engineRpm)
    }

    @Test
    fun testProbe1_7_SlidingWindowResync_FalseSyncMarkerInGarbage() = runBlocking {
        val parser = RollingTelemetryParser(defaultVehicleId = "ADVERSARIAL_TEST")
        val validFrame = NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 99, speedKmh = 80)

        // 30 bytes noise + false 0xBA 0x5A with invalid checksum + 20 bytes noise + valid frame
        val falseFrame = ByteArray(89) { 0x11 }
        falseFrame[0] = NewGeneralPacketV2Parser.TELEMETRY_SYNC_BYTE_0
        falseFrame[1] = NewGeneralPacketV2Parser.TELEMETRY_SYNC_BYTE_1
        falseFrame[88] = 0x00 // bad checksum

        val stream = ByteArray(30) { 0xEE.toByte() } + falseFrame + ByteArray(20) { 0xDD.toByte() } + validFrame
        parser.onBytesReceived(stream)

        var attempts = 0
        while (parser.totalPacketsReceived < 1 && attempts < 20) {
            delay(50)
            attempts++
        }

        assertEquals("Must successfully recover and parse true frame", 1, parser.totalPacketsReceived)
        assertTrue("Corrupted candidate count must be incremented", parser.totalPacketsCorrupted >= 1)
        assertEquals(80, parser.telemetryState.value.speedKmh)
    }

    // =========================================================================
    // 2. SEQUENCE GAP TRACKING ADVERSARIAL PROBES
    // =========================================================================

    @Test
    fun testProbe2_1_ContinuousSequences_NoDroppedPackets() {
        val parser = RollingTelemetryParser()
        for (seq in 0..255) {
            val frame = NewGeneralPacketV2Parser.createTelemetryFrame(sequence = seq)
            val result = parser.parseDirect(frame)
            assertTrue(result is NewGeneralPacketV2Parser.ParseResult.Success)
        }
        assertEquals(256, parser.totalPacketsReceived)
        assertEquals(0, parser.totalPacketsDropped)
    }

    @Test
    fun testProbe2_2_SequenceRollover_From255To0_NoDrop() {
        val parser = RollingTelemetryParser()

        // Sequence 255
        parser.parseDirect(NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 255))
        assertEquals(1, parser.totalPacketsReceived)
        assertEquals(0, parser.totalPacketsDropped)

        // Rollover to 0
        parser.parseDirect(NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 0))
        assertEquals(2, parser.totalPacketsReceived)
        assertEquals("Rollover from 255 to 0 must not count as dropped packet", 0, parser.totalPacketsDropped)

        // Continue to 1
        parser.parseDirect(NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 1))
        assertEquals(3, parser.totalPacketsReceived)
        assertEquals(0, parser.totalPacketsDropped)
    }

    @Test
    fun testProbe2_3_SequenceRollover_WithDroppedPacketsAcrossBoundary() {
        val parser = RollingTelemetryParser()

        // Frame with sequence 253
        parser.parseDirect(NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 253))
        assertEquals(0, parser.totalPacketsDropped)

        // Jump across boundary to sequence 2 (dropped 254, 255, 0, 1 -> 4 packets)
        parser.parseDirect(NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 2))
        assertEquals(2, parser.totalPacketsReceived)
        assertEquals(4, parser.totalPacketsDropped)
    }

    @Test
    fun testProbe2_4_DuplicateAndOutOfOrderSequences_DoNotCountAsDropped() {
        val parser = RollingTelemetryParser()

        parser.parseDirect(NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 10))
        assertEquals(0, parser.totalPacketsDropped)

        // Duplicate sequence 10
        parser.parseDirect(NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 10))
        assertEquals("Duplicate packet must not increment dropped count", 0, parser.totalPacketsDropped)

        // Out-of-order sequence 9
        parser.parseDirect(NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 9))
        assertEquals("Out-of-order packet must not increment dropped count", 0, parser.totalPacketsDropped)
    }

    @Test
    fun testProbe2_5_DiscontinuousJump_GreaterThan127_Ignored() {
        val parser = RollingTelemetryParser()

        parser.parseDirect(NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 10))
        // Jump from 10 to 200 (gap = 189 > 127)
        parser.parseDirect(NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 200))
        assertEquals(0, parser.totalPacketsDropped)
    }

    @Test
    fun testProbe2_6_ResetMetrics_ClearsAllCounters() {
        val parser = RollingTelemetryParser()
        parser.parseDirect(NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 1))
        parser.parseDirect(NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 5)) // 3 dropped
        assertEquals(2, parser.totalPacketsReceived)
        assertEquals(3, parser.totalPacketsDropped)

        parser.resetMetrics()
        assertEquals(0, parser.totalPacketsReceived)
        assertEquals(0, parser.totalPacketsDropped)
        assertEquals(0, parser.totalPacketsCorrupted)

        // Next packet starts fresh without gap
        parser.parseDirect(NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 20))
        assertEquals(1, parser.totalPacketsReceived)
        assertEquals(0, parser.totalPacketsDropped)
    }

    // =========================================================================
    // 3. EXTREME VALUES & SENSOR BOUNDARIES
    // =========================================================================

    @Test
    fun testProbe3_1_ExtremeSpeedClamping_SpeedAbove300() {
        // Speed 350 km/h (higher than Bajaj 299 limit)
        val frame350 = NewGeneralPacketV2Parser.createTelemetryFrame(speedKmh = 350)
        val result350 = NewGeneralPacketV2Parser.parse(frame350)
        assertTrue(result350 is NewGeneralPacketV2Parser.ParseResult.Success)
        val success350 = result350 as NewGeneralPacketV2Parser.ParseResult.Success
        assertEquals("Speed must be clamped to 299", 299, success350.telemetry.speedKmh)
        assertFalse("Bike at 350 km/h is not stationary", success350.telemetry.isBikeStationary)

        // Max uint16 speed (65535)
        val frameMax = NewGeneralPacketV2Parser.createTelemetryFrame(speedKmh = 65535)
        val resultMax = NewGeneralPacketV2Parser.parse(frameMax)
        assertTrue(resultMax is NewGeneralPacketV2Parser.ParseResult.Success)
        val successMax = resultMax as NewGeneralPacketV2Parser.ParseResult.Success
        assertEquals("Speed must be clamped to 299", 299, successMax.telemetry.speedKmh)
    }

    @Test
    fun testProbe3_2_ExtremeRpmClamping_RpmAbove15000() {
        val frame18000 = NewGeneralPacketV2Parser.createTelemetryFrame(rpm = 18000)
        val result18000 = NewGeneralPacketV2Parser.parse(frame18000)
        assertTrue(result18000 is NewGeneralPacketV2Parser.ParseResult.Success)
        val success18000 = result18000 as NewGeneralPacketV2Parser.ParseResult.Success
        assertEquals("Engine RPM must be clamped to 16000", 16000, success18000.telemetry.engineRpm)

        val frame65535 = NewGeneralPacketV2Parser.createTelemetryFrame(rpm = 65535)
        val success65535 = NewGeneralPacketV2Parser.parse(frame65535) as NewGeneralPacketV2Parser.ParseResult.Success
        assertEquals("Engine RPM must be clamped to 16000", 16000, success65535.telemetry.engineRpm)
    }

    @Test
    fun testProbe3_3_ExtremeFuelClamping() {
        val frameFuel200 = NewGeneralPacketV2Parser.createTelemetryFrame(fuelPercent = 200)
        val success = NewGeneralPacketV2Parser.parse(frameFuel200) as NewGeneralPacketV2Parser.ParseResult.Success
        assertEquals("Fuel percent must be clamped to 100", 100, success.telemetry.fuelLevelPercent)
    }

    @Test
    fun testProbe3_4_BatteryVoltageExtremesAndAlerts() {
        // Critical low voltage
        val healthLow = VehicleHealthEvaluator.evaluate(batteryVoltage = 9.5f)
        assertTrue("9.5V is critical low", healthLow.isBatteryCritical)
        assertFalse(healthLow.isOvercharged)

        // Negative voltage value
        val healthNeg = VehicleHealthEvaluator.evaluate(batteryVoltage = -1.0f)
        assertTrue("Negative voltage is critical low", healthNeg.isBatteryCritical)
        assertFalse(healthNeg.isOvercharged)

        // Overcharged voltage
        val healthHigh = VehicleHealthEvaluator.evaluate(batteryVoltage = 15.8f)
        assertFalse(healthHigh.isBatteryCritical)
        assertTrue("15.8V is overcharged", healthHigh.isOvercharged)

        // Exact boundary checks (11.8V critical, 14.8V overcharge)
        val healthAtBoundary11_79 = VehicleHealthEvaluator.evaluate(batteryVoltage = 11.79f)
        assertTrue(healthAtBoundary11_79.isBatteryCritical)

        val healthAtBoundary11_80 = VehicleHealthEvaluator.evaluate(batteryVoltage = 11.80f)
        assertFalse(healthAtBoundary11_80.isBatteryCritical)

        val healthAtBoundary14_80 = VehicleHealthEvaluator.evaluate(batteryVoltage = 14.80f)
        assertFalse(healthAtBoundary14_80.isOvercharged)

        val healthAtBoundary14_81 = VehicleHealthEvaluator.evaluate(batteryVoltage = 14.81f)
        assertTrue(healthAtBoundary14_81.isOvercharged)
    }

    @Test
    fun testProbe3_5_DtcDiagnosticFormattingAndParsing() {
        // From telemetry frame
        val frameDtc = NewGeneralPacketV2Parser.createTelemetryFrame(dtcCode = 0x0171L)
        val success = NewGeneralPacketV2Parser.parse(frameDtc) as NewGeneralPacketV2Parser.ParseResult.Success
        assertEquals(listOf("P0171"), success.dtcCodes)

        // Zero DTC produces empty list
        val frameNoDtc = NewGeneralPacketV2Parser.createTelemetryFrame(dtcCode = 0L)
        val successNoDtc = NewGeneralPacketV2Parser.parse(frameNoDtc) as NewGeneralPacketV2Parser.ParseResult.Success
        assertTrue(successNoDtc.dtcCodes.isEmpty())

        // Normalization in VehicleHealthEvaluator
        val rawCodes = listOf("  p0300 ", "tc-01", "  abs_err_02  ")
        val health = VehicleHealthEvaluator.evaluate(12.5f, troubleCodes = rawCodes)
        assertEquals(listOf("P0300", "TC-01", "ABS_ERR_02"), health.engineTroubleCodes)
    }

    @Test
    fun testProbe3_6_AbsModeParsingExtremes() {
        // Numeric codes
        assertEquals(AbsMode.ROAD, VehicleHealthEvaluator.parseAbsMode(0))
        assertEquals(AbsMode.RAIN, VehicleHealthEvaluator.parseAbsMode(1))
        assertEquals(AbsMode.OFF_ROAD, VehicleHealthEvaluator.parseAbsMode(2))
        assertEquals(AbsMode.SPORT, VehicleHealthEvaluator.parseAbsMode(3))
        assertEquals(AbsMode.ROAD, VehicleHealthEvaluator.parseAbsMode(99)) // Invalid fallback

        // String representations
        assertEquals(AbsMode.ROAD, VehicleHealthEvaluator.parseAbsMode(null))
        assertEquals(AbsMode.ROAD, VehicleHealthEvaluator.parseAbsMode(""))
        assertEquals(AbsMode.ROAD, VehicleHealthEvaluator.parseAbsMode("UNKNOWN_MODE"))
        assertEquals(AbsMode.RAIN, VehicleHealthEvaluator.parseAbsMode("rain"))
        assertEquals(AbsMode.OFF_ROAD, VehicleHealthEvaluator.parseAbsMode("OFFROAD"))
        assertEquals(AbsMode.OFF_ROAD, VehicleHealthEvaluator.parseAbsMode("off_road"))
        assertEquals(AbsMode.SPORT, VehicleHealthEvaluator.parseAbsMode("  sport  "))
    }

    @Test
    fun testProbe3_7_TirePressureUnderInflationBoundaries() {
        // Front Recommended: 28 PSI. Underinflated threshold: < 24 PSI.
        val frontExactThreshold = VehicleHealthEvaluator.evaluate(12.5f, frontPsi = 24)
        assertFalse("24 PSI is exactly at boundary, not underinflated", frontExactThreshold.isFrontTireUnderInflated)

        val frontUnder = VehicleHealthEvaluator.evaluate(12.5f, frontPsi = 23)
        assertTrue("23 PSI is underinflated", frontUnder.isFrontTireUnderInflated)

        // Rear Solo Recommended: 32 PSI. Underinflated threshold: < 28 PSI.
        val rearExactThreshold = VehicleHealthEvaluator.evaluate(12.5f, rearPsi = 28)
        assertFalse("28 PSI is exactly at boundary, not underinflated", rearExactThreshold.isRearSoloTireUnderInflated)

        val rearUnder = VehicleHealthEvaluator.evaluate(12.5f, rearPsi = 27)
        assertTrue("27 PSI is underinflated", rearUnder.isRearSoloTireUnderInflated)

        // Flat tire (0 PSI)
        val flatTire = VehicleHealthEvaluator.evaluate(12.5f, frontPsi = 0, rearPsi = 0)
        assertTrue(flatTire.isFrontTireUnderInflated)
        assertTrue(flatTire.isRearSoloTireUnderInflated)
    }

    // =========================================================================
    // 4. GATING & WATCHDOG ADVERSARIAL PROBES
    // =========================================================================

    @Test
    fun testProbe4_1_WriteBarrier_StrictlyRejectsWhenMtuNotIncreased() {
        val client = DefaultOctBleClient()
        val dummyPacket = ByteArray(89) { 0x01 }

        // State: DISCONNECTED, MTU: 23, isMtuIncreased: false
        assertFalse("Write must fail when isMtuIncreased is false", client.sendGeneralPacket(dummyPacket))

        // State: CONNECTED, MTU: 23, isMtuIncreased: false
        client.simulateConnectionState(BleConnectionState.CONNECTED)
        assertFalse("Write must fail when MTU is not increased, even if CONNECTED", client.sendGeneralPacket(dummyPacket))

        // State: NEGOTIATING_MTU, MTU: 23, isMtuIncreased: false
        client.simulateConnectionState(BleConnectionState.NEGOTIATING_MTU)
        assertFalse("Write must fail during MTU negotiation", client.sendGeneralPacket(dummyPacket))
    }

    @Test
    fun testProbe4_2_WriteBarrier_PermitsWriteWhenMtuIncreased() {
        val client = DefaultOctBleClient()
        val dummyPacket = ByteArray(89) { 0x01 }

        client.simulateConnectionState(BleConnectionState.CONNECTED)
        client.simulateMtuNegotiated(256)

        assertTrue(client.isMtuIncreased.value)
        assertEquals(256, client.negotiatedMtu.value)

        // Now write succeeds
        assertTrue("Write must succeed once MTU is increased", client.sendGeneralPacket(dummyPacket))
    }

    @Test
    fun testProbe4_3_MtuNegotiation_WatchdogTimeoutRecovery() = runBlocking {
        val client = DefaultOctBleClient()
        client.connect("00:11:22:33:44:55")

        // Wait for it to enter NEGOTIATING_MTU
        var wait = 0
        while (client.connectionState.value != BleConnectionState.NEGOTIATING_MTU && wait < 20) {
            delay(50)
            wait++
        }
        assertEquals(BleConnectionState.NEGOTIATING_MTU, client.connectionState.value)
        assertFalse(client.isMtuIncreased.value)

        // Wait for watchdog to trigger (timeout is 2500ms)
        // We delay for 2700ms to allow the watchdog coroutine to fire and fallback
        delay(2700)

        // Watchdog must have fallen back to MTU_FALLBACK_LEGACY (185) and transitioned to READY
        assertEquals(OctGattConstants.MTU_FALLBACK_LEGACY, client.negotiatedMtu.value)
        assertTrue(client.isMtuIncreased.value)
        assertEquals(BleConnectionState.READY, client.connectionState.value)

        // Write barrier is now unlocked
        val dummyPacket = ByteArray(89) { 0x02 }
        assertTrue("Write must succeed after watchdog recovery", client.sendGeneralPacket(dummyPacket))
    }

    @Test
    fun testProbe4_4_MtuNegotiation_SuccessCancelsWatchdog() = runBlocking {
        val client = DefaultOctBleClient()
        client.connect("00:11:22:33:44:55")

        delay(500) // Now in NEGOTIATING_MTU
        assertEquals(BleConnectionState.NEGOTIATING_MTU, client.connectionState.value)

        // Cluster responds with MTU 512 before 2500ms timeout
        client.simulateMtuNegotiated(512)
        assertEquals(512, client.negotiatedMtu.value)
        assertTrue(client.isMtuIncreased.value)

        // Wait past watchdog timeout to ensure it doesn't overwrite MTU
        delay(2600)
        assertEquals("MTU must remain 512 and not be overwritten by watchdog", 512, client.negotiatedMtu.value)
    }

    @Test
    fun testProbe4_5_Disconnect_ResetsMtuAndGating() {
        val client = DefaultOctBleClient()
        client.connect("00:11:22:33:44:55")
        client.simulateMtuNegotiated(512)
        assertTrue(client.isMtuIncreased.value)

        client.disconnect()
        assertEquals(BleConnectionState.DISCONNECTED, client.connectionState.value)
        assertEquals(23, client.negotiatedMtu.value)
        assertFalse(client.isMtuIncreased.value)

        // Write must fail again
        assertFalse(client.sendGeneralPacket(ByteArray(89)))
    }

    @Test
    fun testProbe4_6_ReconnectBackoff_SafeIndexClamping() {
        // Negative attempts clamp to 0 (1500L)
        assertEquals(1500L, OctGattConstants.calculateArrayBackoff(-1))
        assertEquals(1500L, OctGattConstants.calculateArrayBackoff(-10))

        // Normal range
        assertEquals(1500L, OctGattConstants.calculateArrayBackoff(0))
        assertEquals(2500L, OctGattConstants.calculateArrayBackoff(1))
        assertEquals(4000L, OctGattConstants.calculateArrayBackoff(2))
        assertEquals(6000L, OctGattConstants.calculateArrayBackoff(3))
        assertEquals(8000L, OctGattConstants.calculateArrayBackoff(4))

        // Large attempts clamp to max (8000L) without IndexOutOfBounds
        assertEquals(8000L, OctGattConstants.calculateArrayBackoff(5))
        assertEquals(8000L, OctGattConstants.calculateArrayBackoff(100))
        assertEquals(8000L, OctGattConstants.calculateArrayBackoff(Int.MAX_VALUE))
    }

    // =========================================================================
    // 5. UNICODE SANITIZATION & BOUNDARY TRUNCATION
    // =========================================================================

    @Test
    fun testProbe5_1_CallerName_UnassignedUnicodeStripping() {
        // Inject multiple unassigned Unicode characters (\p{Cn})
        // \u0378, \u0379 (Greek unassigned), \u0530 (Armenian unassigned), \u088F (Arabic unassigned)
        val dirtyName = "John\u0378\u0379 \u0530Doe\u088F"
        val packet = NewGeneralPacketV2Builder().apply {
            callerName = dirtyName
        }.build()

        assertEquals(0x01.toByte(), packet[18]) // Presence flag
        val length = packet[19].toInt() and 0xFF
        val sanitized = String(packet, 20, length, StandardCharsets.UTF_8)
        assertEquals("John Doe", sanitized)
        assertEquals(8, length)
    }

    @Test
    fun testProbe5_2_CallerName_OnlyUnassignedUnicode_ResultsInEmpty() {
        val pureUnassigned = "\u0378\u0379\u0530\u088F"
        val packet = NewGeneralPacketV2Builder().apply {
            callerName = pureUnassigned
        }.build()

        assertEquals("Presence flag must be 0 for empty caller name", 0x00.toByte(), packet[18])
        assertEquals("Length must be 0", 0x00.toByte(), packet[19])
    }

    @Test
    fun testProbe5_3_CallerName_TruncationTo32Chars_PreservesByte52() {
        val veryLongName = "A".repeat(100)
        val packet = NewGeneralPacketV2Builder().apply {
            callerName = veryLongName
            callProgressSeconds = 0x1234 // Byte 52 should be 0x34, Byte 53 should be 0x12
        }.build()

        assertEquals(0x01.toByte(), packet[18])
        val length = packet[19].toInt() and 0xFF
        assertEquals("Caller name length byte must be capped at 32", 32, length)

        // Verify caller name doesn't bleed into callProgressSeconds
        assertEquals(0x34.toByte(), packet[52])
        assertEquals(0x12.toByte(), packet[53])
    }

    @Test
    fun testProbe5_4_CallerName_MultiByteUtf8CappedAt32Bytes() {
        // Devanagari Hindi text: "नमस्ते भारत" (each character is 3 bytes in UTF-8)
        val hindiName = "नमस्ते भारतनमस्ते भारतनमस्ते भारत"
        val packet = NewGeneralPacketV2Builder().apply {
            callerName = hindiName
            callProgressSeconds = 0x00FF
        }.build()

        val length = packet[19].toInt() and 0xFF
        assertTrue("Byte length must not exceed 32", length <= 32)
        assertEquals(0xFF.toByte(), packet[52])
        assertEquals(0x00.toByte(), packet[53])
    }

    @Test
    fun testProbe5_5_PhoneModel_TruncationTo32Chars_PreservesChecksum() {
        val longModel = "SamsungGalaxyUltraSuperLongModelIdentifier_1234567890"
        val packet = NewGeneralPacketV2Builder().apply {
            phoneModel = longModel
        }.build()

        val modelLen = packet[55].toInt() and 0xFF
        assertEquals(32, modelLen)
        assertTrue("Checksum must be valid even with long phone model",
            NewGeneralPacketV2Builder.isValidChecksum(packet))
    }

    @Test
    fun testProbe5_6_HeartbeatWrapAround() {
        var hb: Byte = 255.toByte()
        val packet255 = NewGeneralPacketV2Builder().apply { heartbeat = hb }.build()
        assertEquals(255.toByte(), packet255[54])

        hb = (hb + 1).toByte()
        val packet0 = NewGeneralPacketV2Builder().apply { heartbeat = hb }.build()
        assertEquals(0.toByte(), packet0[54])
    }

    @Test
    fun testProbe5_7_CallDuration_LittleEndianUint16Range() {
        val testDurations = listOf(0, 1, 60, 300, 3600, 65535)
        for (sec in testDurations) {
            val packet = NewGeneralPacketV2Builder().apply {
                callProgressSeconds = sec
            }.build()

            val low = packet[52].toInt() and 0xFF
            val high = packet[53].toInt() and 0xFF
            val decoded = (high shl 8) or low
            assertEquals("Call duration $sec must match decoded little-endian uint16", sec, decoded)
        }
    }
}
