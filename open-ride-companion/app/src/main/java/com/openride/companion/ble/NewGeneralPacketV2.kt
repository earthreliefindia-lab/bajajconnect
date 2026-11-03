package com.openride.companion.ble

import java.nio.charset.StandardCharsets

/**
 * Constants for the 89-byte NewGeneralPacketV2 framing.
 */
object NewGeneralPacketConstants {
    const val GENERAL_PACKET_SIZE = 89
    const val CALLER_NAME_MAX = 32
    const val CALLER_NAME_OFFSET = 20
    const val PHONE_MODEL_MAX = 32
    const val PHONE_MODEL_OFFSET = 56
}

/**
 * Builder and serializer for the 89-byte outbound phone status frame (NewGeneralPacketV2).
 * Transmitted from mobile device to cluster characteristic 0210... at ~1 Hz.
 */
class NewGeneralPacketV2Builder {
    var currentVolume: Int = 10               // 0..15 (4 bits)
    var isHeadsetConnected: Boolean = true    // Bit 4
    var isDndEnabled: Boolean = false         // Bit 7 of byte 1
    var batteryLevelBucket: Int = 3           // 0..3 (2 bits, bits 4..5 of byte 1)
    var callState: Int = 0                    // 0..4 (bits 0..3 of byte 1)
    var birthdayDay: Int = 15                 // 1..31 (bits 3..7 of byte 2)
    var signalStrength: Int = 5               // 0..7 (bits 0..2 of byte 2)
    var birthdayMonth: Int = 8                // 1..12 (bits 0..3 of byte 3)

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
    var callProgressSeconds: Int = 0          // uint16 little endian (bytes 52-53)
    var heartbeat: Byte = 0
    var phoneModel: String = "SM-S918B"

    /**
     * Serializes this builder state into an exact 89-byte binary packet.
     */
    fun build(): ByteArray {
        val packet = ByteArray(NewGeneralPacketConstants.GENERAL_PACKET_SIZE)

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

        // Bytes 18..51: Caller Name (stripping unassigned Unicode [\p{Cn}])
        val cleanCaller = callerName.replace(Regex("[\\p{Cn}]"), "").take(NewGeneralPacketConstants.CALLER_NAME_MAX)
        if (cleanCaller.isNotEmpty()) {
            packet[18] = 0x01.toByte() // Presence flag
            val callerBytes = cleanCaller.toByteArray(StandardCharsets.UTF_8).take(NewGeneralPacketConstants.CALLER_NAME_MAX)
            packet[19] = callerBytes.size.toByte()
            for (i in callerBytes.indices) {
                packet[NewGeneralPacketConstants.CALLER_NAME_OFFSET + i] = callerBytes[i]
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
        val cleanModel = phoneModel.take(NewGeneralPacketConstants.PHONE_MODEL_MAX)
        val modelBytes = cleanModel.toByteArray(StandardCharsets.UTF_8).take(NewGeneralPacketConstants.PHONE_MODEL_MAX)
        packet[55] = modelBytes.size.toByte()
        for (i in modelBytes.indices) {
            packet[NewGeneralPacketConstants.PHONE_MODEL_OFFSET + i] = modelBytes[i]
        }

        // Byte 88: Modulo 256 sum checksum of bytes 0..87
        packet[88] = calculateChecksum(packet, 88)

        return packet
    }

    companion object {
        /**
         * Calculates modulo-256 sum over byte range [0 until length].
         */
        fun calculateChecksum(packet: ByteArray, length: Int = 88): Byte {
            var sum = 0
            val limit = length.coerceAtMost(packet.size)
            for (i in 0 until limit) {
                sum += (packet[i].toInt() and 0xFF)
            }
            return (sum and 0xFF).toByte()
        }

        /**
         * Validates byte 88 checksum against sum of bytes 0..87.
         */
        fun isValidChecksum(packet: ByteArray): Boolean {
            if (packet.size < NewGeneralPacketConstants.GENERAL_PACKET_SIZE) return false
            val expected = calculateChecksum(packet, 88)
            return packet[88] == expected
        }

        /**
         * Maps battery percentage to 2-bit bucket:
         * 0 (Critical <=15%), 1 (Low <=30%), 2 (Med <=70%), 3 (Full >70%)
         */
        fun mapBatteryToBucket(pct: Int): Int {
            return when {
                pct <= 15 -> 0
                pct <= 30 -> 1
                pct <= 70 -> 2
                else -> 3
            }
        }
    }
}
