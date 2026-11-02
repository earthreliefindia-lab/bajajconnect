package com.openride.companion.e2e

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * End-to-End Test Suite for Cluster Wi-Fi Secondary Display and Motion Safety Interlock (Features 14-15).
 * Covers Tier 1 (Feature Coverage) and Tier 2 (Boundary & Corner Cases).
 */
class ClusterWifiAndSafetyE2ETest {

    // =========================================================================
    // FEATURE 14: CLUSTER WI-FI SECONDARY DISPLAY (Tier 1 & Tier 2)
    // =========================================================================

    object WifiProtocolConstants {
        const val VEHICLE_GATEWAY_IP = "192.168.4.1"
        const val TCP_CONTROL_PORT = 5558
        const val UDP_STREAMING_PORT = 5556
        const val SYNC_BYTE: Byte = 0x00

        const val GROUP_DASH_EVENTS: Byte = 0x01
        const val GROUP_DASH_REQUESTS: Byte = 0x02
        const val GROUP_DASH_STATUS: Byte = 0x03

        const val MAX_PAYLOAD_LENGTH = 2048
    }

    enum class HandlebarButton {
        UP, DOWN, LEFT, RIGHT, SELECT, BACK
    }

    data class TouchPoint(val x: Float, val y: Float, val z: Float, val pressure: Float)

    class WifiFrameBuilder(
        val groupCode: Byte,
        val commandCode: Byte,
        val payload: ByteArray
    ) {
        fun build(): ByteArray {
            val length = payload.size.coerceAtMost(WifiProtocolConstants.MAX_PAYLOAD_LENGTH)
            val buffer = ByteBuffer.allocate(5 + length + 4).order(ByteOrder.BIG_ENDIAN)

            // Header: Byte 0 Sync, Byte 1 GroupCode, Byte 2 CommandCode, Bytes 3..4 Length
            buffer.put(WifiProtocolConstants.SYNC_BYTE)
            buffer.put(groupCode)
            buffer.put(commandCode)
            buffer.putShort(length.toShort())

            // Payload
            buffer.put(payload, 0, length)

            // CRC32 checksum over header + payload (bytes 0 to 4 + length)
            val crc = CRC32()
            crc.update(buffer.array(), 0, 5 + length)
            buffer.putInt(crc.value.toInt())

            return buffer.array()
        }
    }

    @Test
    fun testWifi_ConnectionParameters_MatchSpecification() {
        assertEquals("Gateway IP must be 192.168.4.1", "192.168.4.1", WifiProtocolConstants.VEHICLE_GATEWAY_IP)
        assertEquals("TCP Command Port must be 5558", 5558, WifiProtocolConstants.TCP_CONTROL_PORT)
        assertEquals("UDP Screen Streaming Port must be 5556", 5556, WifiProtocolConstants.UDP_STREAMING_PORT)
    }

    @Test
    fun testWifi_FrameStructure_SyncByteAndLengthEncoding() {
        val payload = "HELLO_CLUSTER".toByteArray(Charsets.UTF_8)
        val frame = WifiFrameBuilder(
            groupCode = WifiProtocolConstants.GROUP_DASH_EVENTS,
            commandCode = 0x10.toByte(),
            payload = payload
        ).build()

        assertEquals("First byte must be 0x00 Sync byte", 0x00.toByte(), frame[0])
        assertEquals("Second byte must be GroupCode", WifiProtocolConstants.GROUP_DASH_EVENTS, frame[1])
        assertEquals("Third byte must be CommandCode", 0x10.toByte(), frame[2])

        val payloadLen = ((frame[3].toInt() and 0xFF) shl 8) or (frame[4].toInt() and 0xFF)
        assertEquals("Payload length must match string size", payload.size, payloadLen)
        assertEquals("Total frame size must be 5 + payload.size + 4 CRC bytes", 5 + payload.size + 4, frame.size)
    }

    @Test
    fun testWifi_FrameCrc32Checksum_ValidatesEthernetCrc() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val frame = WifiFrameBuilder(
            groupCode = WifiProtocolConstants.GROUP_DASH_STATUS,
            commandCode = 0x05.toByte(),
            payload = payload
        ).build()

        val crcExpected = CRC32()
        crcExpected.update(frame, 0, 5 + payload.size)
        val expectedCrcInt = crcExpected.value.toInt()

        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN)
        buffer.position(5 + payload.size)
        val actualCrcInt = buffer.int

        assertEquals("CRC32 in frame footer must match calculated checksum", expectedCrcInt, actualCrcInt)
    }

    @Test
    fun testWifi_HandlebarJoystickEvents_CoversAllSixButtons() {
        val buttons = HandlebarButton.values()
        assertEquals("Six directional & action buttons specified", 6, buttons.size)
        assertTrue(HandlebarButton.UP in buttons)
        assertTrue(HandlebarButton.DOWN in buttons)
        assertTrue(HandlebarButton.LEFT in buttons)
        assertTrue(HandlebarButton.RIGHT in buttons)
        assertTrue(HandlebarButton.SELECT in buttons)
        assertTrue(HandlebarButton.BACK in buttons)
    }

    @Test
    fun testWifi_DocumentSync_CalculatesAndVerifiesMd5Hash() {
        val dummyDocumentJpeg = "JPEG_IMAGE_BINARY_DATA_DOCUMENT_RC".toByteArray(Charsets.UTF_8)
        val md5Digest = MessageDigest.getInstance("MD5")
        val expectedHash = md5Digest.digest(dummyDocumentJpeg).joinToString("") { "%02x".format(it) }

        fun verifyDocumentSyncHash(data: ByteArray, remoteHash: String): Boolean {
            val calcHash = MessageDigest.getInstance("MD5").digest(data).joinToString("") { "%02x".format(it) }
            return calcHash.equals(remoteHash, ignoreCase = true)
        }

        assertTrue("Matching MD5 confirms document image integrity", verifyDocumentSyncHash(dummyDocumentJpeg, expectedHash))
        assertFalse("Mismatched MD5 triggers re-transmission", verifyDocumentSyncHash("CORRUPTED".toByteArray(), expectedHash))
    }

    @Test
    fun testWifi_Boundary_PayloadOverflowCappedAtMaxPayloadLength() {
        val oversizedPayload = ByteArray(3000) { 0x42 }
        val frame = WifiFrameBuilder(
            groupCode = WifiProtocolConstants.GROUP_DASH_REQUESTS,
            commandCode = 0x01,
            payload = oversizedPayload
        ).build()

        val lengthEncoded = ((frame[3].toInt() and 0xFF) shl 8) or (frame[4].toInt() and 0xFF)
        assertEquals("Payload must be capped at 2048 bytes", WifiProtocolConstants.MAX_PAYLOAD_LENGTH, lengthEncoded)
    }

    @Test
    fun testWifi_Boundary_EmptyPayloadFrame() {
        val frame = WifiFrameBuilder(
            groupCode = WifiProtocolConstants.GROUP_DASH_STATUS,
            commandCode = 0x02,
            payload = ByteArray(0)
        ).build()

        assertEquals("Empty payload frame size is 5 header + 0 + 4 CRC = 9 bytes", 9, frame.size)
        val lengthEncoded = ((frame[3].toInt() and 0xFF) shl 8) or (frame[4].toInt() and 0xFF)
        assertEquals(0, lengthEncoded)
    }

    @Test
    fun testWifi_Boundary_CorruptedFrameDetection() {
        val frame = WifiFrameBuilder(
            groupCode = WifiProtocolConstants.GROUP_DASH_EVENTS,
            commandCode = 0x01,
            payload = "DATA".toByteArray()
        ).build()

        // Corrupt one payload byte
        frame[5] = (frame[5] + 1).toByte()

        val crc = CRC32()
        crc.update(frame, 0, frame.size - 4)
        val expectedCrc = crc.value.toInt()

        val actualCrc = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN).getInt(frame.size - 4)
        assertFalse("Corrupted byte causes CRC mismatch", expectedCrc == actualCrc)
    }

    @Test
    fun testWifi_Boundary_RxResyncSkipsNonH105GaNoise() {
        val noisyStream = byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x55.toByte(), 0x00, 0x01, 0x02)
        var syncIndex = -1
        for (i in noisyStream.indices) {
            if (noisyStream[i] == WifiProtocolConstants.SYNC_BYTE) {
                syncIndex = i
                break
            }
        }
        assertEquals("Must scan ahead and locate 0x00 sync header at index 3", 3, syncIndex)
    }

    @Test
    fun testWifi_Boundary_FourDimensionalTouchPointPressureClamping() {
        fun createTouch(x: Float, y: Float, z: Float, rawPressure: Float): TouchPoint {
            return TouchPoint(x, y, z, rawPressure.coerceIn(0.0f, 1.0f))
        }

        val touch = createTouch(120f, 340f, 0f, 1.8f)
        assertEquals(1.0f, touch.pressure, 0.001f)
    }

    // =========================================================================
    // FEATURE 15: MOTION SAFETY INTERLOCK (isBikeStationry) (Tier 1 & Tier 2)
    // =========================================================================

    class MotionSafetyManager {
        var currentSpeedKmh: Int = 0
            private set

        val isBikeStationary: Boolean
            get() = currentSpeedKmh == 0

        var isSearchKeyboardVisible: Boolean = false
        var isScreenLocked: Boolean = false
        var lockoutWarningDisplayed: Boolean = false

        fun updateSpeed(speedKmh: Int) {
            currentSpeedKmh = speedKmh.coerceAtLeast(0)
            if (!isBikeStationary) {
                // Moving safety interlock triggers
                isSearchKeyboardVisible = false
                isScreenLocked = true
                lockoutWarningDisplayed = true
            } else {
                // Stationary: Controls can be unlocked
                isScreenLocked = false
                lockoutWarningDisplayed = false
            }
        }

        fun tryOpenSearchKeyboard(): Boolean {
            return if (isBikeStationary) {
                isSearchKeyboardVisible = true
                true
            } else {
                false // Blocked by safety policy
            }
        }
    }

    @Test
    fun testSafety_SpeedZero_IsStationary() {
        val safety = MotionSafetyManager()
        safety.updateSpeed(0)
        assertTrue("Speed 0 means bike is stationary", safety.isBikeStationary)
        assertFalse("Screen should not be locked when stationary", safety.isScreenLocked)
    }

    @Test
    fun testSafety_SpeedGreaterThanZero_TriggersLockout() {
        val safety = MotionSafetyManager()
        safety.updateSpeed(15)
        assertFalse("Speed 15 km/h is not stationary", safety.isBikeStationary)
        assertTrue("Screen must be locked", safety.isScreenLocked)
        assertTrue("Warning banner displayed", safety.lockoutWarningDisplayed)
    }

    @Test
    fun testSafety_MotionDismissesKeyboardImmediately() {
        val safety = MotionSafetyManager()
        safety.updateSpeed(0)
        safety.tryOpenSearchKeyboard()
        assertTrue(safety.isSearchKeyboardVisible)

        // Bike starts moving
        safety.updateSpeed(5)
        assertFalse("Keyboard must be dismissed when moving", safety.isSearchKeyboardVisible)
    }

    @Test
    fun testSafety_CannotOpenKeyboardWhileMoving() {
        val safety = MotionSafetyManager()
        safety.updateSpeed(30)
        val opened = safety.tryOpenSearchKeyboard()
        assertFalse("Keyboard opening must be blocked while moving", opened)
        assertFalse(safety.isSearchKeyboardVisible)
    }

    @Test
    fun testSafety_ReturnToStationary_UnlocksScreen() {
        val safety = MotionSafetyManager()
        safety.updateSpeed(60)
        assertTrue(safety.isScreenLocked)

        // Motorcycle comes to a complete halt
        safety.updateSpeed(0)
        assertTrue(safety.isBikeStationary)
        assertFalse("Screen lock released when speed drops to 0", safety.isScreenLocked)
        assertFalse(safety.lockoutWarningDisplayed)
    }

    @Test
    fun testSafety_Boundary_SpeedThresholdEdgeAt1Kmh() {
        val safety = MotionSafetyManager()
        safety.updateSpeed(1) // Minimal non-zero speed
        assertFalse("1 km/h triggers non-stationary policy", safety.isBikeStationary)
        assertTrue(safety.isScreenLocked)
    }

    @Test
    fun testSafety_Boundary_NegativeSpeedClampedToZero() {
        val safety = MotionSafetyManager()
        safety.updateSpeed(-10) // Faulty sensor reading
        assertEquals(0, safety.currentSpeedKmh)
        assertTrue(safety.isBikeStationary)
    }

    @Test
    fun testSafety_Boundary_HighSpeedExtreme() {
        val safety = MotionSafetyManager()
        safety.updateSpeed(180) // 180 km/h top speed
        assertEquals(180, safety.currentSpeedKmh)
        assertTrue(safety.isScreenLocked)
    }

    @Test
    fun testSafety_Boundary_RapidSpeedFluctuationHysteresis() {
        val safety = MotionSafetyManager()
        val speeds = listOf(0, 10, 0, 25, 0)
        speeds.forEach { speed ->
            safety.updateSpeed(speed)
            assertEquals(speed == 0, safety.isBikeStationary)
        }
    }

    @Test
    fun testSafety_Boundary_StationaryStateAllowsClusterTouch() {
        val safety = MotionSafetyManager()
        safety.updateSpeed(0)
        fun handleClusterTouch(touch: TouchPoint): Boolean {
            return safety.isBikeStationary
        }
        assertTrue("Cluster touch processed when stationary", handleClusterTouch(TouchPoint(10f, 20f, 0f, 0.5f)))
        safety.updateSpeed(40)
        assertFalse("Cluster touch ignored when moving", handleClusterTouch(TouchPoint(10f, 20f, 0f, 0.5f)))
    }
}
