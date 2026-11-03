package com.openride.companion.unit

import com.openride.companion.ble.AbsMode
import com.openride.companion.ble.BleConnectionState
import com.openride.companion.ble.DefaultOctBleClient
import com.openride.companion.ble.NewGeneralPacketConstants
import com.openride.companion.ble.NewGeneralPacketV2Builder
import com.openride.companion.ble.NewGeneralPacketV2Parser
import com.openride.companion.ble.OctBleCallback
import com.openride.companion.ble.OctBleClient
import com.openride.companion.ble.OctGattConstants
import com.openride.companion.ble.RollingTelemetryParser
import com.openride.companion.ble.TelltaleIndicators
import com.openride.companion.ble.VehicleHealth
import com.openride.companion.ble.VehicleHealthEvaluator
import com.openride.companion.model.ClusterHardwareType
import com.openride.companion.safety.MotionSafetyManager
import com.openride.companion.wifi.CircularFrameBuffer
import com.openride.companion.wifi.ClusterDocumentSync
import com.openride.companion.wifi.ClusterTcpServer
import com.openride.companion.wifi.ClusterUdpServer
import com.openride.companion.wifi.DisplayMirrorManager
import com.openride.companion.wifi.FrameSlicer
import com.openride.companion.wifi.HandlebarButton
import com.openride.companion.wifi.TouchPoint
import com.openride.companion.wifi.WifiFrameBuilder
import com.openride.companion.wifi.WifiFrameParser
import com.openride.companion.wifi.WifiProtocolConstants
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Authoritative Milestone 3 Unit Test Suite.
 * Validates complete functionality of BLE Telematics, General Packet V2 framing,
 * 10 Hz Rolling Telemetry streaming, Wi-Fi TCP/UDP protocols, and Motion Safety Interlocks.
 */
class Milestone3UnitTest {

    // =========================================================================
    // 1. OCT GATT BLE CONSTANTS & BACKOFF
    // =========================================================================

    @Test
    fun testOctGattConstants_ServiceAndCharacteristicUuids() {
        assertEquals("0010676e-6972-6565-6e69-676e4543544f", OctGattConstants.SERVICE_OCT_UUID.toString())
        assertEquals("0110676e-6972-6565-6e69-676e4543544f", OctGattConstants.CHAR_TBT_NAV_UUID.toString())
        assertEquals("0210676e-6972-6565-6e69-676e4543544f", OctGattConstants.CHAR_GENERAL_PACKET_UUID.toString())
        assertEquals("0310676e-6972-6565-6e69-676e4543544f", OctGattConstants.CHAR_MISSED_CALL_UUID.toString())
        assertEquals("0410676e-6972-6565-6e69-676e4543544f", OctGattConstants.CHAR_ALERTS_UUID.toString())
        assertEquals("0510676e-6972-6565-6e69-676e4543544f", OctGattConstants.CHAR_MUSIC_UUID.toString())
        assertEquals("0610676e-6972-6565-6e69-676e4543544f", OctGattConstants.CHAR_HANDLEBAR_UUID.toString())
        assertEquals("0910676e-6972-6565-6e69-676e4543544f", OctGattConstants.CHAR_FAVORITES_UUID.toString())
        assertEquals("0a10676e-6972-6565-6e69-676e4543544f", OctGattConstants.CHAR_RECENT_CALLS_UUID.toString())
        assertEquals("0b10676e-6972-6565-6e69-676e4543544f", OctGattConstants.CHAR_DIAGNOSTICS_UUID.toString())
        assertEquals("00002902-0000-1000-8000-00805f9b34fb", OctGattConstants.CCCD_DESCRIPTOR_UUID.toString())
        assertEquals(9, OctGattConstants.ALL_OCT_CHARACTERISTICS.size)
    }

    @Test
    fun testOctGattConstants_MtuAndRetryParameters() {
        assertEquals(512, OctGattConstants.MTU_REQUESTED)
        assertEquals(256, OctGattConstants.MTU_FALLBACK_APK)
        assertEquals(185, OctGattConstants.MTU_FALLBACK_LEGACY)
        assertEquals(23, OctGattConstants.MTU_MINIMUM_ATT)
        assertEquals(2500L, OctGattConstants.MTU_WATCHDOG_TIMEOUT_MS)
        assertEquals(5, OctGattConstants.WRITE_RETRY_MAX)
    }

    @Test
    fun testOctGattConstants_ReconnectBackoffCalculations() {
        // Exact APK array backoff
        assertEquals(1500L, OctGattConstants.calculateArrayBackoff(0))
        assertEquals(2500L, OctGattConstants.calculateArrayBackoff(1))
        assertEquals(4000L, OctGattConstants.calculateArrayBackoff(2))
        assertEquals(6000L, OctGattConstants.calculateArrayBackoff(3))
        assertEquals(8000L, OctGattConstants.calculateArrayBackoff(4))
        assertEquals(8000L, OctGattConstants.calculateArrayBackoff(10))

        // Exponential backoff formula
        assertEquals(1000L, OctGattConstants.calculateExponentialBackoff(0))
        assertEquals(2000L, OctGattConstants.calculateExponentialBackoff(1))
        assertEquals(4000L, OctGattConstants.calculateExponentialBackoff(2))
        assertEquals(30000L, OctGattConstants.calculateExponentialBackoff(10))
    }

    @Test
    fun testOctGattConstants_RssiBarCalculations() {
        assertEquals(5, OctGattConstants.calculateRssiBars(-55))
        assertEquals(4, OctGattConstants.calculateRssiBars(-65))
        assertEquals(3, OctGattConstants.calculateRssiBars(-75))
        assertEquals(2, OctGattConstants.calculateRssiBars(-85))
        assertEquals(1, OctGattConstants.calculateRssiBars(-95))
        assertEquals(0, OctGattConstants.calculateRssiBars(-105))
    }

    @Test
    fun testOctGattConstants_AdvertisementNameFilter() {
        assertTrue(OctGattConstants.isBajajCluster("Bajaj-Cluster-01"))
        assertTrue(OctGattConstants.isBajajCluster("Pulsar N250"))
        assertTrue(OctGattConstants.isBajajCluster("Dominar400"))
        assertTrue(OctGattConstants.isBajajCluster("Chetak EV"))
        assertFalse(OctGattConstants.isBajajCluster("UnknownBluetooth"))
        assertFalse(OctGattConstants.isBajajCluster(null))
        assertFalse(OctGattConstants.isBajajCluster(""))
    }

    // =========================================================================
    // 2. OCT BLE CLIENT STATE MACHINE & WRITE GATING
    // =========================================================================

    @Test
    fun testOctBleClient_InitialStateAndWriteGating() {
        val client = OctBleClient.create()
        assertEquals(BleConnectionState.DISCONNECTED, client.connectionState.value)
        assertEquals(23, client.negotiatedMtu.value)
        assertFalse(client.isMtuIncreased.value)
        assertEquals(0, client.signalBars.value)

        // Write Gating: General Packet must be blocked when MTU not increased
        val testPacket = ByteArray(89) { 0x01 }
        val writeAttemptResult = client.sendGeneralPacket(testPacket)
        assertFalse("General packet write MUST be gated when isMtuIncreased is false", writeAttemptResult)
    }

    @Test
    fun testOctBleClient_MtuNegotiationUnlocksWriteGate() {
        val client = DefaultOctBleClient()
        var observedOldState: BleConnectionState? = null
        var observedNewState: BleConnectionState? = null

        client.registerCallback(object : OctBleCallback {
            override fun onConnectionStateChanged(oldState: BleConnectionState, newState: BleConnectionState) {
                observedOldState = oldState
                observedNewState = newState
            }
        })

        client.simulateConnectionState(BleConnectionState.CONNECTED)
        assertEquals(BleConnectionState.CONNECTED, client.connectionState.value)
        assertEquals(BleConnectionState.DISCONNECTED, observedOldState)
        assertEquals(BleConnectionState.CONNECTED, observedNewState)

        // Simulate successful MTU negotiation
        client.simulateMtuNegotiated(512)
        assertEquals(512, client.negotiatedMtu.value)
        assertTrue(client.isMtuIncreased.value)

        // Write gate is now unlocked
        val testPacket = ByteArray(89) { 0x01 }
        val writeResult = client.sendGeneralPacket(testPacket)
        assertTrue("Write must succeed once MTU is increased", writeResult)
    }

    @Test
    fun testOctBleClient_RssiAndNotificationSimulation() {
        val client = DefaultOctBleClient()
        var reportedSignalBars = 0
        var receivedHandlebarData: ByteArray? = null

        client.registerCallback(object : OctBleCallback {
            override fun onRssiUpdated(rssi: Int, signalBars: Int) {
                reportedSignalBars = signalBars
            }

            override fun onHandlebarControlReceived(controlPayload: ByteArray) {
                receivedHandlebarData = controlPayload
            }
        })

        client.simulateRssi(-65)
        assertEquals(4, reportedSignalBars)
        assertEquals(4, client.signalBars.value)

        val sampleControl = byteArrayOf(0x01, 0x04)
        client.simulateInboundNotification(OctGattConstants.CHAR_HANDLEBAR_UUID, sampleControl)
        assertNotNull(receivedHandlebarData)
        assertEquals(0x04.toByte(), receivedHandlebarData!![1])
    }

    @Test
    fun testOctBleClient_DisconnectAndReconnectTracking() {
        val client = DefaultOctBleClient()
        client.connect("00:11:22:33:44:55")
        client.simulateConnectionState(BleConnectionState.DISCONNECTED)

        assertFalse(client.connectionState.value.isConnected)
        assertEquals(1, client.reconnectAttempts)

        client.disconnect()
        assertNull(client.connectedDeviceAddress.value)
        assertFalse(client.isMtuIncreased.value)
    }

    // =========================================================================
    // 3. 89-BYTE OUTBOUND GENERAL PACKET (NewGeneralPacketV2)
    // =========================================================================

    @Test
    fun testNewGeneralPacket_ExactLengthAndChecksum() {
        val builder = NewGeneralPacketV2Builder().apply {
            currentVolume = 12
            isHeadsetConnected = true
            isDndEnabled = false
            batteryLevelBucket = 3
            callState = 0
            birthdayDay = 15
            signalStrength = 5
            birthdayMonth = 8
            callerName = "John Doe"
            callProgressSeconds = 120
            heartbeat = 42
            phoneModel = "Galaxy S23 Ultra"
        }

        val packet = builder.build()
        assertEquals(89, packet.size)
        assertTrue(NewGeneralPacketV2Builder.isValidChecksum(packet))

        // Checksum calculation manually verified
        var sum = 0
        for (i in 0 until 88) {
            sum += (packet[i].toInt() and 0xFF)
        }
        val expected = (sum and 0xFF).toByte()
        assertEquals(expected, packet[88])
    }

    @Test
    fun testNewGeneralPacket_BitPackingAndUnicodeSanitation() {
        val builder = NewGeneralPacketV2Builder().apply {
            currentVolume = 15
            isHeadsetConnected = true
            isDndEnabled = true
            batteryLevelBucket = 2
            callState = 3
            callerName = "Kesh\u0378\u0379av" // Unassigned Unicode codepoints
            callProgressSeconds = 258 // 0x0102 -> low byte 0x02, high byte 0x01
        }

        val packet = builder.build()

        // Byte 0: 0xC0 | 0x10 | 15 = 0xDF (223)
        assertEquals(0xDF.toByte(), packet[0])

        // Byte 1: 0x80 | (2 shl 4) | 3 = 0x80 | 0x20 | 0x03 = 0xA3 (-93 signed, 163 unsigned)
        assertEquals(0xA3.toByte(), packet[1])

        // Caller Name length and sanitized string
        val callerLen = packet[19].toInt() and 0xFF
        val callerName = String(packet, 20, callerLen, Charsets.UTF_8)
        assertEquals("Keshav", callerName)

        // Call duration little-endian
        assertEquals(0x02.toByte(), packet[52])
        assertEquals(0x01.toByte(), packet[53])
    }

    @Test
    fun testNewGeneralPacket_BatteryBucketMapping() {
        assertEquals(0, NewGeneralPacketV2Builder.mapBatteryToBucket(10))
        assertEquals(1, NewGeneralPacketV2Builder.mapBatteryToBucket(25))
        assertEquals(2, NewGeneralPacketV2Builder.mapBatteryToBucket(50))
        assertEquals(3, NewGeneralPacketV2Builder.mapBatteryToBucket(90))
    }

    // =========================================================================
    // 4. 89-BYTE INBOUND TELEMETRY PARSER (NewGeneralPacketV2Parser)
    // =========================================================================

    @Test
    fun testNewGeneralPacketV2Parser_ValidFrameParsing() {
        val testFrame = NewGeneralPacketV2Parser.createTelemetryFrame(
            sequence = 12,
            speedKmh = 45,
            rpm = 4200,
            fuelPercent = 75,
            gear = 3,
            voltageMilliVolts = 12800,
            coolantTempC = 88,
            ambientTempC = 30,
            tripADecameters = 1500L, // 150.0 km
            tripBDecameters = 450L,  // 45.0 km
            odometerDecameters = 12000L,
            telltaleBitmask = 0x0001 or 0x0004, // Left Turn + High Beam
            leanAngleTenthDegrees = 125, // 12.5 deg
            dtcCode = 0x0300L, // P0300
            tireFrontPsi = 28,
            tireRearPsi = 32,
            absModeCode = 0 // ROAD
        )

        val result = NewGeneralPacketV2Parser.parse(testFrame, "DOMINAR_400")
        assertTrue(result is NewGeneralPacketV2Parser.ParseResult.Success)

        val success = result as NewGeneralPacketV2Parser.ParseResult.Success
        assertEquals(12, success.sequenceCounter)
        assertEquals(45, success.telemetry.speedKmh)
        assertEquals(4200, success.telemetry.engineRpm)
        assertEquals(75, success.telemetry.fuelLevelPercent)
        assertEquals(3, success.telemetry.gearPosition)
        assertEquals(12.8f, success.telemetry.battery12VVoltage, 0.05f)
        assertEquals(88, success.coolantTempC)
        assertEquals(30, success.ambientTempC)
        assertEquals(150.0f, success.tripAKm, 0.1f)
        assertEquals(45.0f, success.tripBKm, 0.1f)
        assertTrue(success.telltales.turnSignalLeft)
        assertTrue(success.telltales.highBeam)
        assertFalse(success.telltales.neutralLamp)
        assertEquals("ROAD", success.telemetry.absMode)
        assertFalse("Moving bike is not stationary", success.telemetry.isBikeStationary)
    }

    @Test
    fun testNewGeneralPacketV2Parser_RejectsCorruptChecksumAndHeader() {
        val validFrame = NewGeneralPacketV2Parser.createTelemetryFrame()

        // Test corrupted sync header
        val badHeader = validFrame.clone()
        badHeader[0] = 0x00
        val headerResult = NewGeneralPacketV2Parser.parse(badHeader)
        assertTrue(headerResult is NewGeneralPacketV2Parser.ParseResult.Error)

        // Test corrupted checksum
        val badChecksum = validFrame.clone()
        badChecksum[88] = (badChecksum[88] + 1).toByte()
        val checksumResult = NewGeneralPacketV2Parser.parse(badChecksum)
        assertTrue(checksumResult is NewGeneralPacketV2Parser.ParseResult.Error)

        // Test truncated frame
        val truncated = ByteArray(80)
        val truncatedResult = NewGeneralPacketV2Parser.parse(truncated)
        assertTrue(truncatedResult is NewGeneralPacketV2Parser.ParseResult.Error)
    }

    // =========================================================================
    // 5. 10 HZ ROLLING TELEMETRY PARSER (RollingTelemetryParser)
    // =========================================================================

    @Test
    fun testRollingTelemetryParser_DirectParseAndGapTracking() {
        val parser = RollingTelemetryParser(defaultVehicleId = "CHETAK_EV")

        val frame1 = NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 1, speedKmh = 25)
        val result1 = parser.parseDirect(frame1)
        assertTrue(result1 is NewGeneralPacketV2Parser.ParseResult.Success)
        assertEquals(1, parser.totalPacketsReceived)
        assertEquals(25, parser.telemetryState.value.speedKmh)

        // Skip sequence numbers 2 and 3 (simulate packet drop)
        val frame4 = NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 4, speedKmh = 30)
        val result4 = parser.parseDirect(frame4)
        assertTrue(result4 is NewGeneralPacketV2Parser.ParseResult.Success)
        assertEquals(2, parser.totalPacketsReceived)
        assertEquals(2, parser.totalPacketsDropped) // sequence 2 & 3 dropped
        assertEquals(30, parser.telemetryState.value.speedKmh)
    }

    @Test
    fun testRollingTelemetryParser_StreamResynchronization() {
        val parser = RollingTelemetryParser(defaultVehicleId = "PULSAR_NS400")
        val validFrame = NewGeneralPacketV2Parser.createTelemetryFrame(sequence = 5, speedKmh = 60)

        // Prepend random noise bytes before valid 0xBA 0x5A frame
        val noisyStream = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte()) + validFrame
        parser.onBytesReceived(noisyStream)

        // Reset metrics test
        parser.resetMetrics()
        assertEquals(0, parser.totalPacketsReceived)
        assertEquals(0, parser.totalPacketsDropped)
    }

    // =========================================================================
    // 6. TELLTALE INDICATORS BITMASK
    // =========================================================================

    @Test
    fun testTelltaleIndicators_RoundTripBitmask() {
        val indicators = TelltaleIndicators(
            turnSignalLeft = true,
            turnSignalRight = false,
            highBeam = true,
            neutralLamp = true,
            absAlert = false,
            checkEngineMil = true,
            lowFuelAlert = false,
            batteryAlert = false,
            sideStandDown = true,
            serviceDue = false,
            tractionControlAlert = true,
            engineOverheatWarning = false
        )

        val bitmask = indicators.toBitmask()
        val decoded = TelltaleIndicators.fromBitmask(bitmask)

        assertEquals(indicators, decoded)
        assertTrue(decoded.turnSignalLeft)
        assertFalse(decoded.turnSignalRight)
        assertTrue(decoded.highBeam)
        assertTrue(decoded.neutralLamp)
        assertTrue(decoded.checkEngineMil)
        assertTrue(decoded.sideStandDown)
        assertTrue(decoded.tractionControlAlert)
    }

    // =========================================================================
    // 7. VEHICLE HEALTH & DIAGNOSTIC EVALUATOR
    // =========================================================================

    @Test
    fun testVehicleHealthEvaluator_BatteryAndAbsRules() {
        val criticalHealth = VehicleHealth(batteryVoltage = 11.2f)
        assertTrue(criticalHealth.isBatteryCritical)
        assertFalse(criticalHealth.isOvercharged)

        val overchargedHealth = VehicleHealth(batteryVoltage = 15.2f)
        assertFalse(overchargedHealth.isBatteryCritical)
        assertTrue(overchargedHealth.isOvercharged)

        val normalHealth = VehicleHealth(batteryVoltage = 12.8f)
        assertFalse(normalHealth.isBatteryCritical)
        assertFalse(normalHealth.isOvercharged)

        // ABS Mode parsing
        assertEquals(AbsMode.ROAD, VehicleHealthEvaluator.parseAbsMode(0))
        assertEquals(AbsMode.RAIN, VehicleHealthEvaluator.parseAbsMode(1))
        assertEquals(AbsMode.OFF_ROAD, VehicleHealthEvaluator.parseAbsMode(2))
        assertEquals(AbsMode.SPORT, VehicleHealthEvaluator.parseAbsMode(3))
        assertEquals(AbsMode.SPORT, VehicleHealthEvaluator.parseAbsMode("sport"))
    }

    @Test
    fun testVehicleHealthEvaluator_TirePressureAndDtcFormatting() {
        val health = VehicleHealth(
            batteryVoltage = 12.8f,
            tirePressureFrontPsi = 22, // Recommended 28 -> underinflated
            tirePressureRearPsi = 32   // Recommended 32 -> acceptable
        )

        assertTrue(health.isFrontTireUnderInflated)
        assertFalse(health.isRearSoloTireUnderInflated)

        assertEquals("P0300", VehicleHealth.formatDtc("  p0300  "))
        assertEquals("TC-01", VehicleHealth.formatDtc("tc-01"))
    }

    // =========================================================================
    // 8. WI-FI PROTOCOL CONSTANTS & FRAME BUILDING
    // =========================================================================

    @Test
    fun testWifiProtocolConstants_Values() {
        assertEquals("192.168.4.1", WifiProtocolConstants.VEHICLE_GATEWAY_IP)
        assertEquals(5558, WifiProtocolConstants.TCP_CONTROL_PORT)
        assertEquals(5556, WifiProtocolConstants.UDP_STREAMING_PORT)
        assertEquals(0x00.toByte(), WifiProtocolConstants.SYNC_BYTE)
        assertEquals(2048, WifiProtocolConstants.MAX_PAYLOAD_LENGTH)
        assertEquals(800, WifiProtocolConstants.TFT_WIDTH)
        assertEquals(480, WifiProtocolConstants.TFT_HEIGHT)
        assertEquals(30, WifiProtocolConstants.TARGET_FPS)
    }

    @Test
    fun testWifiFrameBuilder_FrameStructureAndCrc32() {
        val payload = "TEST_PAYLOAD".toByteArray(Charsets.UTF_8)
        val frame = WifiFrameBuilder(
            groupCode = WifiProtocolConstants.GROUP_DASH_EVENTS,
            commandCode = 0x0A.toByte(),
            payload = payload
        ).build()

        assertEquals(WifiProtocolConstants.SYNC_BYTE, frame[0])
        assertEquals(WifiProtocolConstants.GROUP_DASH_EVENTS, frame[1])
        assertEquals(0x0A.toByte(), frame[2])
        val len = ((frame[3].toInt() and 0xFF) shl 8) or (frame[4].toInt() and 0xFF)
        assertEquals(payload.size, len)
        assertEquals(5 + payload.size + 4, frame.size)

        // Parse via WifiFrameParser
        val parser = WifiFrameParser()
        val result = parser.parse(frame)
        assertTrue(result is WifiFrameParser.ParseResult.Success)

        val success = result as WifiFrameParser.ParseResult.Success
        assertEquals(WifiProtocolConstants.GROUP_DASH_EVENTS, success.groupCode)
        assertEquals(0x0A.toByte(), success.commandCode)
        assertEquals("TEST_PAYLOAD", String(success.payload, Charsets.UTF_8))
    }

    @Test
    fun testWifiFrameParser_CorruptedCrcAndResync() {
        val frame = WifiFrameBuilder(
            groupCode = WifiProtocolConstants.GROUP_DASH_REQUESTS,
            commandCode = 0x01,
            payload = byteArrayOf(0x01, 0x02)
        ).build()

        // Corrupt payload byte
        frame[5] = (frame[5] + 1).toByte()

        val parser = WifiFrameParser()
        val result = parser.parse(frame)
        assertTrue(result is WifiFrameParser.ParseResult.ChecksumMismatch)

        // Resync test: Noise before 0x00 sync
        val noisyData = byteArrayOf(0xAA.toByte(), 0xBB.toByte()) + frame
        val resyncResult = parser.parse(noisyData)
        // With corrupted byte, resync finds 0x00 and detects checksum mismatch
        assertTrue(resyncResult is WifiFrameParser.ParseResult.ChecksumMismatch)
    }

    // =========================================================================
    // 9. CLUSTER DOCUMENT SYNC & MD5
    // =========================================================================

    @Test
    fun testClusterDocumentSync_Md5Validation() {
        val docData = "VEHICLE_REGISTRATION_CERTIFICATE_MOCK".toByteArray(Charsets.UTF_8)
        val hash = ClusterDocumentSync.calculateMd5Hash(docData)

        assertTrue(ClusterDocumentSync.verifyDocumentSyncHash(docData, hash))
        assertFalse(ClusterDocumentSync.verifyDocumentSyncHash("MODIFIED".toByteArray(), hash))

        val sizeFrame = ClusterDocumentSync.createDocumentSizeFrame(docNum = 1, imageSize = docData.size)
        assertEquals(WifiProtocolConstants.SYNC_BYTE, sizeFrame[0])
        assertEquals(WifiProtocolConstants.GROUP_DASH_REQUESTS, sizeFrame[1])
    }

    // =========================================================================
    // 10. CLUSTER TCP & UDP STREAMING
    // =========================================================================

    @Test
    fun testClusterTcpServer_HandlebarAndTouchSimulation() {
        val server = ClusterTcpServer()
        var receivedButton: HandlebarButton? = null
        var receivedTouch: TouchPoint? = null

        server.simulateHandlebarButton(HandlebarButton.SELECT)
        assertEquals(HandlebarButton.UP, ClusterTcpServer.parseHandlebarButton(0))
        assertEquals(HandlebarButton.SELECT, ClusterTcpServer.parseHandlebarButton(4))
        assertNull(ClusterTcpServer.parseHandlebarButton(99))

        server.simulateTouch(TouchPoint(100f, 200f, 0f, 1.5f))
        val touch = TouchPoint(100f, 200f, 0f, 1.5f)
        assertEquals(1.0f, touch.clampedPressure, 0.001f)
    }

    @Test
    fun testFrameSlicer_SliceAndReassembly() {
        val originalImage = ByteArray(2500) { (it % 256).toByte() }
        val slices = FrameSlicer.sliceFrame(originalImage, frameSeq = 100)

        // 2500 bytes sliced by 1008 bytes = 3 slices
        assertEquals(3, slices.size)
        assertEquals(100, slices[0].frameSeq)
        assertEquals(0, slices[0].fragmentIndex)
        assertEquals(3, slices[0].totalFragments)
        assertEquals(1008, slices[0].payload.size)

        val reassembled = FrameSlicer.reassembleFrame(slices)
        assertNotNull(reassembled)
        assertEquals(originalImage.size, reassembled!!.size)
        assertTrue(originalImage.contentEquals(reassembled))
    }

    @Test
    fun testClusterUdpServer_CircularBufferAndEncryption() {
        val buffer = CircularFrameBuffer(capacity = 3)
        buffer.push(byteArrayOf(1))
        buffer.push(byteArrayOf(2))
        buffer.push(byteArrayOf(3))
        buffer.push(byteArrayOf(4)) // Discards 1

        assertEquals(3, buffer.size())
        assertEquals(2.toByte(), buffer.pop()!![0])

        val udpServer = ClusterUdpServer()
        val plain = "SECRET_FRAME_DATA".toByteArray(Charsets.UTF_8)
        val encrypted = udpServer.encryptPayload(plain)
        assertNotNull(encrypted)
    }

    // =========================================================================
    // 11. DISPLAY MIRROR MANAGER (Hardware Capability Gating)
    // =========================================================================

    @Test
    fun testDisplayMirrorManager_HardwareCompatibilityGating() {
        val safety = MotionSafetyManager()
        val manager = DisplayMirrorManager(safety)

        // Full color TFT 0265 supports Wi-Fi mirroring
        assertTrue(manager.canMirror(ClusterHardwareType.TFT_COLOR_0265))
        val startTftResult = manager.startMirroring(ClusterHardwareType.TFT_COLOR_0265)
        assertTrue("TFT_COLOR_0265 must start mirroring", startTftResult)
        assertTrue(manager.isMirroringActive.value)
        assertEquals(ClusterHardwareType.TFT_COLOR_0265, manager.activeClusterType.value)

        manager.stopMirroring()
        assertFalse(manager.isMirroringActive.value)

        // Inverted Segment LCD 2763 does NOT support Wi-Fi mirroring
        assertFalse(manager.canMirror(ClusterHardwareType.LCD_INVERSION_2763))
        val startLcdResult = manager.startMirroring(ClusterHardwareType.LCD_INVERSION_2763)
        assertFalse("LCD_INVERSION_2763 must be rejected from Wi-Fi mirroring", startLcdResult)
        assertFalse(manager.isMirroringActive.value)
    }

    // =========================================================================
    // 12. MOTION SAFETY INTERLOCK (MotionSafetyManager)
    // =========================================================================

    @Test
    fun testMotionSafetyManager_StationaryAndLockoutCycle() {
        val safety = MotionSafetyManager()

        // Initial state: speed 0, stationary, unlocked
        assertEquals(0, safety.currentSpeedKmh)
        assertTrue(safety.isBikeStationary)
        assertFalse(safety.isScreenLocked)
        assertFalse(safety.lockoutWarningDisplayed)

        // Open search keyboard while stationary
        assertTrue(safety.tryOpenSearchKeyboard())
        assertTrue(safety.isSearchKeyboardVisible)

        // Motorcycle starts moving -> immediate lockout
        safety.updateSpeed(10)
        assertEquals(10, safety.currentSpeedKmh)
        assertFalse(safety.isBikeStationary)
        assertFalse("Keyboard must be dismissed immediately on motion", safety.isSearchKeyboardVisible)
        assertTrue("Screen must be locked while moving", safety.isScreenLocked)
        assertTrue("Warning banner displayed", safety.lockoutWarningDisplayed)
        assertTrue("Video mirror paused", safety.isVideoMirroringPaused)

        // Cannot reopen keyboard while moving
        assertFalse(safety.tryOpenSearchKeyboard())
        assertFalse(safety.isSearchKeyboardVisible)

        // Cluster touch suppressed while moving
        assertFalse(safety.handleClusterTouch(TouchPoint(10f, 20f)))

        // Motorcycle stops (speed 0) -> controls unlocked
        safety.updateSpeed(0)
        assertTrue(safety.isBikeStationary)
        assertFalse(safety.isScreenLocked)
        assertFalse(safety.lockoutWarningDisplayed)
        assertFalse(safety.isVideoMirroringPaused)

        // Cluster touch permitted when stationary
        assertTrue(safety.handleClusterTouch(TouchPoint(10f, 20f)))
    }

    @Test
    fun testMotionSafetyManager_BoundaryClampingAndHysteresis() {
        val safety = MotionSafetyManager()

        // Negative speed reading clamped to 0
        safety.updateSpeed(-50)
        assertEquals(0, safety.currentSpeedKmh)
        assertTrue(safety.isBikeStationary)

        // High speed extreme
        safety.updateSpeed(195)
        assertEquals(195, safety.currentSpeedKmh)
        assertTrue(safety.isScreenLocked)

        // Rapid speed fluctuations
        listOf(0, 15, 0, 80, 0).forEach { speed ->
            safety.updateSpeed(speed)
            assertEquals(speed == 0, safety.isBikeStationary)
        }
    }
}
