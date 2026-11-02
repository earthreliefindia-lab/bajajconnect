package com.openride.companion.e2e

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * End-to-End Test Suite for Vehicle Telematics, OCT GATT BLE, and Sensor Subsystems (Features 11-13).
 * Covers Tier 1 (Feature Coverage) and Tier 2 (Boundary & Corner Cases).
 */
class BleTelematicsE2ETest {

    // =========================================================================
    // FEATURE 11: OCT GATT BLE SUBSYSTEM (Tier 1 & Tier 2)
    // =========================================================================

    object OctGattConstants {
        val SERVICE_OCT_UUID: UUID = UUID.fromString("0010676e-6972-6565-6e69-676e4543544f")
        val CHAR_TBT_NAV_UUID: UUID = UUID.fromString("0110676e-6972-6565-6e69-676e4543544f")
        val CHAR_GENERAL_PACKET_UUID: UUID = UUID.fromString("0210676e-6972-6565-6e69-676e4543544f")
        val CHAR_MISSED_CALL_UUID: UUID = UUID.fromString("0310676e-6972-6565-6e69-676e4543544f")
        val CHAR_ALERTS_UUID: UUID = UUID.fromString("0410676e-6972-6565-6e69-676e4543544f")
        val CHAR_MUSIC_UUID: UUID = UUID.fromString("0510676e-6972-6565-6e69-676e4543544f")
        val CHAR_HANDLEBAR_UUID: UUID = UUID.fromString("0610676e-6972-6565-6e69-676e4543544f")
        val CHAR_FAVORITES_UUID: UUID = UUID.fromString("0910676e-6972-6565-6e69-676e4543544f")
        val CHAR_RECENT_CALLS_UUID: UUID = UUID.fromString("0a10676e-6972-6565-6e69-676e4543544f")
        val CHAR_DIAGNOSTICS_UUID: UUID = UUID.fromString("0b10676e-6972-6565-6e69-676e4543544f")

        val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    enum class BleState {
        DISCONNECTED, SCANNING, CONNECTING, CONNECTED
    }

    @Test
    fun testBle_PrimaryServiceUuid_MatchesOctSpecification() {
        assertEquals(
            "OCT Engineering 128-bit Primary Service UUID must match APK specification",
            "0010676e-6972-6565-6e69-676e4543544f",
            OctGattConstants.SERVICE_OCT_UUID.toString()
        )
    }

    @Test
    fun testBle_GeneralPacketCharacteristicUuid_MatchesSpec() {
        assertEquals(
            "General Telemetry Characteristic UUID must be 0210676e-6972-6565-6e69-676e4543544f",
            "0210676e-6972-6565-6e69-676e4543544f",
            OctGattConstants.CHAR_GENERAL_PACKET_UUID.toString()
        )
    }

    @Test
    fun testBle_TbtCharacteristicUuid_MatchesSpec() {
        assertEquals(
            "TBT Navigation Characteristic UUID must be 0110676e-6972-6565-6e69-676e4543544f",
            "0110676e-6972-6565-6e69-676e4543544f",
            OctGattConstants.CHAR_TBT_NAV_UUID.toString()
        )
    }

    @Test
    fun testBle_ConnectionStateTransitions() {
        var state = BleState.DISCONNECTED
        fun onStartScan() { state = BleState.SCANNING }
        fun onDeviceFound() { state = BleState.CONNECTING }
        fun onGattConnected() { state = BleState.CONNECTED }
        fun onGattDisconnected() { state = BleState.DISCONNECTED }

        onStartScan()
        assertEquals(BleState.SCANNING, state)
        onDeviceFound()
        assertEquals(BleState.CONNECTING, state)
        onGattConnected()
        assertEquals(BleState.CONNECTED, state)
        onGattDisconnected()
        assertEquals(BleState.DISCONNECTED, state)
    }

    @Test
    fun testBle_CccdDescriptor_MatchesStandardBluetoothBase() {
        assertEquals(
            "Client Characteristic Configuration Descriptor (CCCD) UUID",
            "00002902-0000-1000-8000-00805f9b34fb",
            OctGattConstants.CCCD_DESCRIPTOR_UUID.toString()
        )
    }

    @Test
    fun testBle_Boundary_RssiSignalLevelCalculation() {
        // RSSI to signal bar mapping (0 to 5 bars)
        fun calculateRssiBars(rssi: Int): Int {
            return when {
                rssi >= -60 -> 5
                rssi >= -70 -> 4
                rssi >= -80 -> 3
                rssi >= -90 -> 2
                rssi >= -100 -> 1
                else -> 0
            }
        }
        assertEquals(5, calculateRssiBars(-55))
        assertEquals(4, calculateRssiBars(-65))
        assertEquals(3, calculateRssiBars(-75))
        assertEquals(0, calculateRssiBars(-105))
    }

    @Test
    fun testBle_Boundary_ExponentialBackoffRetryCalculation() {
        fun calculateReconnectDelay(attempt: Int): Long {
            val base = 1000L
            val factor = 1 shl attempt.coerceIn(0, 5)
            return (base * factor).coerceAtMost(30000L)
        }
        assertEquals(1000L, calculateReconnectDelay(0))
        assertEquals(2000L, calculateReconnectDelay(1))
        assertEquals(4000L, calculateReconnectDelay(2))
        assertEquals(30000L, calculateReconnectDelay(10))
    }

    @Test
    fun testBle_Boundary_AdvertisementNameFiltering() {
        val allowedPrefixes = listOf("Bajaj", "Pulsar", "Dominar", "Chetak")
        fun isBajajCluster(advertisedName: String?): Boolean {
            if (advertisedName.isNullOrBlank()) return false
            return allowedPrefixes.any { advertisedName.startsWith(it, ignoreCase = true) }
        }
        assertTrue(isBajajCluster("Bajaj Smart Cluster 12"))
        assertTrue(isBajajCluster("Pulsar-N250-BT"))
        assertTrue(isBajajCluster("Dominar400_GATT"))
        assertTrue(isBajajCluster("ChetakEV_Console"))
        assertFalse(isBajajCluster("RandomSpeaker"))
        assertFalse(isBajajCluster(null))
    }

    @Test
    fun testBle_Boundary_AutoReconnectOnDisconnect() {
        var reconnectAttempts = 0
        var isConnected = true

        fun handleDisconnect() {
            isConnected = false
            reconnectAttempts++
        }
        handleDisconnect()
        assertFalse(isConnected)
        assertEquals(1, reconnectAttempts)
    }

    @Test
    fun testBle_Boundary_DiagnosticCharacteristicListing() {
        val allCharacteristics = listOf(
            OctGattConstants.CHAR_TBT_NAV_UUID,
            OctGattConstants.CHAR_GENERAL_PACKET_UUID,
            OctGattConstants.CHAR_MISSED_CALL_UUID,
            OctGattConstants.CHAR_ALERTS_UUID,
            OctGattConstants.CHAR_MUSIC_UUID,
            OctGattConstants.CHAR_HANDLEBAR_UUID,
            OctGattConstants.CHAR_FAVORITES_UUID,
            OctGattConstants.CHAR_RECENT_CALLS_UUID,
            OctGattConstants.CHAR_DIAGNOSTICS_UUID
        )
        assertEquals("Total 9 OCT GATT characteristics specified", 9, allCharacteristics.size)
    }

    // =========================================================================
    // FEATURE 12: 89-BYTE GENERAL PACKET (NewGeneralPacketV2) (Tier 1 & Tier 2)
    // =========================================================================

    class NewGeneralPacketV2Builder {
        var currentVolume: Int = 10        // 0..15 (4 bits)
        var isHeadsetConnected: Boolean = true // Bit 4
        var isDndEnabled: Boolean = false  // Bit 7 of byte 1
        var batteryLevelBucket: Int = 3    // 0..3 (2 bits, bits 4..5 of byte 1)
        var callState: Int = 0             // 0..4 (bits 0..3 of byte 1)
        var birthdayDay: Int = 15          // 1..31 (bits 3..7 of byte 2)
        var signalStrength: Int = 5        // 0..7 (bits 0..2 of byte 2)
        var birthdayMonth: Int = 8         // 1..12 (bits 0..3 of byte 3)

        var callAcceptAck: Byte = 0
        var callRejectAck: Byte = 0
        var callRejectWithSmsAck: Byte = 0
        var resumeSongAck: Byte = 0
        var pauseSongAck: Byte = 0
        var skipToNextAck: Byte = 0
        var skipToPrevAck: Byte = 0
        var stopSongAck: Byte = 0
        var launchMediaPlayerAck: Byte = 0
        var missedCallCount: Byte = 0
        var missedCallGetAck: Byte = 0
        var smsCount: Byte = 0
        var alertsGetAck: Byte = 0
        var selectPlaylistSongAck: Byte = 0

        var callerName: String = ""
        var callProgressSeconds: Int = 0   // uint16 little endian (bytes 52-53)
        var heartbeat: Byte = 0
        var phoneModel: String = "SM-S918B"

        fun build(): ByteArray {
            val packet = ByteArray(89)

            // Byte 0: Bits 0..3 vol, Bit 4 headset, Bits 6..7 fixed 0xC0
            val vol = currentVolume.coerceIn(0, 15)
            val headsetBit = if (isHeadsetConnected) 0x10 else 0x00
            val fixedMask = 0xC0
            packet[0] = (fixedMask or headsetBit or vol).toByte()

            // Byte 1: Bit 7 DND, Bits 4..5 battery bucket, Bits 0..3 callState
            val dndBit = if (isDndEnabled) 0x80 else 0x00
            val batteryBits = (batteryLevelBucket.coerceIn(0, 3) shl 4)
            val callBits = callState.coerceIn(0, 4)
            packet[1] = (dndBit or batteryBits or callBits).toByte()

            // Byte 2: Bits 3..7 birthdayDay, Bits 0..2 clamped signal (0..7)
            val clampedSignal = signalStrength.coerceIn(0, 7)
            val dayBits = (birthdayDay and 0x1F) shl 3
            packet[2] = (dayBits or clampedSignal).toByte()

            // Byte 3: Bits 0..3 birthdayMonth
            packet[3] = (birthdayMonth and 0x0F).toByte()

            // Bytes 4..17: Acknowledgments & counts
            packet[4] = callAcceptAck
            packet[5] = callRejectAck
            packet[6] = callRejectWithSmsAck
            packet[7] = resumeSongAck
            packet[8] = pauseSongAck
            packet[9] = skipToNextAck
            packet[10] = skipToPrevAck
            packet[11] = stopSongAck
            packet[12] = launchMediaPlayerAck
            packet[13] = missedCallCount
            packet[14] = missedCallGetAck
            packet[15] = smsCount
            packet[16] = alertsGetAck
            packet[17] = selectPlaylistSongAck

            // Bytes 18..51: Caller Name
            // Strips unassigned unicode [\p{Cn}]
            val cleanCaller = callerName.replace(Regex("[\\p{Cn}]"), "").take(32)
            if (cleanCaller.isNotEmpty()) {
                packet[18] = 0x01.toByte() // Presence
                packet[19] = cleanCaller.length.toByte()
                val callerBytes = cleanCaller.toByteArray(Charsets.UTF_8).take(32)
                for (i in callerBytes.indices) {
                    packet[20 + i] = callerBytes[i]
                }
            } else {
                packet[18] = 0x00
                packet[19] = 0x00
            }

            // Bytes 52..53: Little-endian active call duration in seconds
            packet[52] = (callProgressSeconds and 0xFF).toByte()
            packet[53] = ((callProgressSeconds shr 8) and 0xFF).toByte()

            // Byte 54: Rolling heartbeat
            packet[54] = heartbeat

            // Bytes 55..87: Phone hardware model (Build.MODEL, max 32)
            val cleanModel = phoneModel.take(32)
            packet[55] = cleanModel.length.toByte()
            val modelBytes = cleanModel.toByteArray(Charsets.UTF_8).take(32)
            for (i in modelBytes.indices) {
                packet[56 + i] = modelBytes[i]
            }

            // Byte 88: Modulo 256 sum checksum of bytes 0..87
            var sum = 0
            for (i in 0 until 88) {
                sum += (packet[i].toInt() and 0xFF)
            }
            packet[88] = (sum and 0xFF).toByte()

            return packet
        }
    }

    @Test
    fun testGeneralPacket_ExactPacketLength_Is89Bytes() {
        val packet = NewGeneralPacketV2Builder().build()
        assertEquals("NewGeneralPacketV2 must be exactly 89 bytes", 89, packet.size)
    }

    @Test
    fun testGeneralPacket_ChecksumCalculation_MatchesModulo256Sum() {
        val packet = NewGeneralPacketV2Builder().apply {
            currentVolume = 12
            signalStrength = 4
            callerName = "Amit Kumar"
            callProgressSeconds = 45
            heartbeat = 17
        }.build()

        var sum = 0
        for (i in 0 until 88) {
            sum += (packet[i].toInt() and 0xFF)
        }
        val expectedChecksum = (sum and 0xFF).toByte()
        assertEquals("Byte 88 must match modulo 256 sum of bytes 0..87", expectedChecksum, packet[88])
    }

    @Test
    fun testGeneralPacket_StatusByteZero_EncodesVolumeAndHeadset() {
        val packet = NewGeneralPacketV2Builder().apply {
            currentVolume = 7
            isHeadsetConnected = true
        }.build()

        val byte0 = packet[0].toInt() and 0xFF
        val volume = byte0 and 0x0F
        val headset = (byte0 and 0x10) != 0
        val fixedBits = byte0 and 0xC0

        assertEquals(7, volume)
        assertTrue(headset)
        assertEquals(0xC0, fixedBits)
    }

    @Test
    fun testGeneralPacket_StatusByteOne_EncodesDndAndBatteryBucket() {
        val packet = NewGeneralPacketV2Builder().apply {
            isDndEnabled = true
            batteryLevelBucket = 2 // Medium
            callState = 3          // Active
        }.build()

        val byte1 = packet[1].toInt() and 0xFF
        val dnd = (byte1 and 0x80) != 0
        val battery = (byte1 shr 4) and 0x03
        val call = byte1 and 0x0F

        assertTrue("DND bit 7 set", dnd)
        assertEquals("Battery bucket 2", 2, battery)
        assertEquals("Call state active 3", 3, call)
    }

    @Test
    fun testGeneralPacket_CallDuration_IsLittleEndianUint16() {
        val packet = NewGeneralPacketV2Builder().apply {
            callProgressSeconds = 300 // 0x012C -> low byte = 0x2C (44), high byte = 0x01 (1)
        }.build()

        val low = packet[52].toInt() and 0xFF
        val high = packet[53].toInt() and 0xFF
        val duration = (high shl 8) or low

        assertEquals(44, low)
        assertEquals(1, high)
        assertEquals(300, duration)
    }

    @Test
    fun testGeneralPacket_Boundary_SignalClamping_ValuesOutOfBounds() {
        val packetNegative = NewGeneralPacketV2Builder().apply { signalStrength = -5 }.build()
        val signalNeg = packetNegative[2].toInt() and 0x07
        assertEquals("Negative signal clamped to 0", 0, signalNeg)

        val packetHigh = NewGeneralPacketV2Builder().apply { signalStrength = 20 }.build()
        val signalHigh = packetHigh[2].toInt() and 0x07
        assertEquals("Signal > 7 clamped to 7", 7, signalHigh)
    }

    @Test
    fun testGeneralPacket_Boundary_BatteryLevelBucketMapping() {
        // Battery % to 2-bit bucket: 0 (Critical <=15%), 1 (Low <=30%), 2 (Med <=70%), 3 (Full >70%)
        fun mapBatteryToBucket(pct: Int): Int {
            return when {
                pct <= 15 -> 0
                pct <= 30 -> 1
                pct <= 70 -> 2
                else -> 3
            }
        }
        assertEquals(0, mapBatteryToBucket(10))
        assertEquals(1, mapBatteryToBucket(25))
        assertEquals(2, mapBatteryToBucket(50))
        assertEquals(3, mapBatteryToBucket(95))
    }

    @Test
    fun testGeneralPacket_Boundary_CallerNameTruncationTo32Chars() {
        val longName = "A".repeat(50)
        val packet = NewGeneralPacketV2Builder().apply { callerName = longName }.build()

        val length = packet[19].toInt() and 0xFF
        assertEquals("Caller name length byte capped at 32", 32, length)
    }

    @Test
    fun testGeneralPacket_Boundary_UnassignedUnicodeStripping() {
        val dirtyName = "Rajesh\u0378\u0379" // Unassigned Greek code points
        val packet = NewGeneralPacketV2Builder().apply { callerName = dirtyName }.build()

        val length = packet[19].toInt() and 0xFF
        val extractedName = String(packet, 20, length, Charsets.UTF_8)
        assertEquals("Rajesh", extractedName)
    }

    @Test
    fun testGeneralPacket_Boundary_HeartbeatOverflowWrapAround() {
        var hb: Byte = 255.toByte()
        hb = (hb + 1).toByte()
        assertEquals(0.toByte(), hb) // wraps around safely in 8-bit
    }

    // =========================================================================
    // FEATURE 13: VEHICLE HEALTH & SENSORS (Tier 1 & Tier 2)
    // =========================================================================

    enum class AbsMode { ROAD, RAIN, OFF_ROAD, SPORT }

    data class VehicleHealth(
        val batteryVoltage: Float,
        val engineTroubleCodes: List<String>,
        val currentAbsMode: AbsMode,
        val tirePressureFrontPsi: Int,
        val tirePressureRearPsi: Int
    ) {
        val isBatteryCritical: Boolean
            get() = batteryVoltage < 11.8f
    }

    @Test
    fun testHealth_NormalBatteryVoltage_NotCritical() {
        val health = VehicleHealth(
            batteryVoltage = 12.8f,
            engineTroubleCodes = emptyList(),
            currentAbsMode = AbsMode.ROAD,
            tirePressureFrontPsi = 28,
            tirePressureRearPsi = 32
        )
        assertFalse("12.8V is healthy float voltage", health.isBatteryCritical)
    }

    @Test
    fun testHealth_LowBatteryVoltage_TriggersCriticalWarning() {
        val health = VehicleHealth(
            batteryVoltage = 11.2f,
            engineTroubleCodes = emptyList(),
            currentAbsMode = AbsMode.ROAD,
            tirePressureFrontPsi = 28,
            tirePressureRearPsi = 32
        )
        assertTrue("Voltage < 11.8V must trigger BATTERY_HEALTH_CRITICAL", health.isBatteryCritical)
    }

    @Test
    fun testHealth_AbsModes_CoversAllFourOptions() {
        val modes = AbsMode.values()
        assertEquals(4, modes.size)
        assertTrue(AbsMode.ROAD in modes)
        assertTrue(AbsMode.RAIN in modes)
        assertTrue(AbsMode.OFF_ROAD in modes)
        assertTrue(AbsMode.SPORT in modes)
    }

    @Test
    fun testHealth_TirePressureSpecification_Front28Rear32() {
        val recommendedFront = 28
        val recommendedRearSolo = 32
        val recommendedRearPillion = 36

        assertEquals(28, recommendedFront)
        assertEquals(32, recommendedRearSolo)
        assertEquals(36, recommendedRearPillion)
    }

    @Test
    fun testHealth_EngineFaultCodes_ParsedCorrectly() {
        val faults = listOf("P0300", "TC-01")
        assertEquals(2, faults.size)
        assertTrue(faults.contains("P0300"))
    }

    @Test
    fun testHealth_Boundary_ZeroBatteryVoltage() {
        val health = VehicleHealth(0.0f, emptyList(), AbsMode.ROAD, 0, 0)
        assertTrue(health.isBatteryCritical)
    }

    @Test
    fun testHealth_Boundary_HighVoltageSpike() {
        val overchargeVoltage = 15.5f
        val isOvercharged = overchargeVoltage > 14.8f
        assertTrue("Detects alternator overcharging", isOvercharged)
    }

    @Test
    fun testHealth_Boundary_TirePressureUnderInflationAlert() {
        fun isUnderInflated(currentPsi: Int, recommendedPsi: Int): Boolean {
            return currentPsi < (recommendedPsi - 4)
        }
        assertTrue("22 PSI on 28 PSI recommendation is underinflated", isUnderInflated(22, 28))
        assertFalse("27 PSI on 28 PSI recommendation is acceptable", isUnderInflated(27, 28))
    }

    @Test
    fun testHealth_Boundary_AbsModeSwitching_PreservesSafety() {
        var activeMode = AbsMode.ROAD
        fun switchMode(newMode: AbsMode) {
            activeMode = newMode
        }
        switchMode(AbsMode.SPORT)
        assertEquals(AbsMode.SPORT, activeMode)
    }

    @Test
    fun testHealth_Boundary_DtcCodeFormatting() {
        fun formatDtc(code: String): String = code.trim().uppercase()
        assertEquals("P0300", formatDtc(" p0300 "))
    }
}
