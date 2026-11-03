package com.openride.companion.wifi

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * Binary frame serializer for Cluster Wi-Fi TCP control protocol.
 * Layout: 0x00 (Sync) + GroupCode (1B) + CommandCode (1B) + Length (2B BE) + Payload (<=2048B) + CRC32 (4B BE).
 */
class WifiFrameBuilder(
    val groupCode: Byte,
    val commandCode: Byte,
    val payload: ByteArray
) {
    fun build(): ByteArray {
        val length = payload.size.coerceAtMost(WifiProtocolConstants.MAX_PAYLOAD_LENGTH)
        val buffer = ByteBuffer.allocate(WifiProtocolConstants.HEADER_SIZE + length + WifiProtocolConstants.CRC_SIZE)
            .order(ByteOrder.BIG_ENDIAN)

        // Header: Byte 0 Sync, Byte 1 GroupCode, Byte 2 CommandCode, Bytes 3..4 Length
        buffer.put(WifiProtocolConstants.SYNC_BYTE)
        buffer.put(groupCode)
        buffer.put(commandCode)
        buffer.putShort(length.toShort())

        // Payload
        buffer.put(payload, 0, length)

        // CRC32 Ethernet checksum over header + payload (bytes 0 to 4 + length)
        val crc = CRC32()
        crc.update(buffer.array(), 0, WifiProtocolConstants.HEADER_SIZE + length)
        buffer.putInt(crc.value.toInt())

        return buffer.array()
    }
}

/**
 * Resilient stream parser for Cluster Wi-Fi TCP control protocol.
 * Handles noisy streams, sync byte recovery (0x00), and CRC32 verification.
 */
class WifiFrameParser {
    sealed class ParseResult {
        data class Success(val groupCode: Byte, val commandCode: Byte, val payload: ByteArray) : ParseResult()
        object ChecksumMismatch : ParseResult()
        object IncompleteFrame : ParseResult()
        object ResyncNeeded : ParseResult()
    }

    fun parse(data: ByteArray, offset: Int = 0, length: Int = data.size): ParseResult {
        if (length < WifiProtocolConstants.MIN_FRAME_SIZE) return ParseResult.IncompleteFrame

        // 1. Header Resynchronization: Locate 0x00 sync header byte
        var syncIndex = -1
        for (i in offset until (offset + length)) {
            if (data[i] == WifiProtocolConstants.SYNC_BYTE) {
                syncIndex = i
                break
            }
        }
        if (syncIndex == -1) return ParseResult.ResyncNeeded
        val available = (offset + length) - syncIndex
        if (available < WifiProtocolConstants.MIN_FRAME_SIZE) return ParseResult.IncompleteFrame

        val buffer = ByteBuffer.wrap(data, syncIndex, available).order(ByteOrder.BIG_ENDIAN)
        buffer.get() // Skip sync byte 0x00
        val groupCode = buffer.get()
        val commandCode = buffer.get()
        val payloadLen = buffer.short.toInt() and 0xFFFF

        if (payloadLen > WifiProtocolConstants.MAX_PAYLOAD_LENGTH) return ParseResult.ResyncNeeded
        val totalExpectedSize = WifiProtocolConstants.HEADER_SIZE + payloadLen + WifiProtocolConstants.CRC_SIZE
        if (available < totalExpectedSize) return ParseResult.IncompleteFrame

        val payload = ByteArray(payloadLen)
        buffer.get(payload)
        val receivedCrc = buffer.int

        // 2. Validate CRC32 Ethernet checksum
        val crcCalc = CRC32()
        crcCalc.update(data, syncIndex, WifiProtocolConstants.HEADER_SIZE + payloadLen)
        val calculatedCrc = crcCalc.value.toInt()

        return if (calculatedCrc == receivedCrc) {
            ParseResult.Success(groupCode, commandCode, payload)
        } else {
            ParseResult.ChecksumMismatch
        }
    }
}

/**
 * Helper object for Document synchronization over Wi-Fi with MD5 integrity validation.
 */
object ClusterDocumentSync {

    fun calculateMd5Hash(data: ByteArray): String {
        val md5Digest = MessageDigest.getInstance("MD5")
        return md5Digest.digest(data).joinToString("") { String.format("%02x", it) }
    }

    fun verifyDocumentSyncHash(data: ByteArray, remoteHash: String): Boolean {
        val localHash = calculateMd5Hash(data)
        return localHash.equals(remoteHash, ignoreCase = true)
    }

    fun createDocumentSizeFrame(docNum: Int, imageSize: Int): ByteArray {
        val payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putInt(docNum)
            .putInt(imageSize)
            .array()
        return WifiFrameBuilder(
            groupCode = WifiProtocolConstants.GROUP_DASH_REQUESTS,
            commandCode = WifiProtocolConstants.CMD_DOC_IMAGE_REQUEST,
            payload = payload
        ).build()
    }

    fun createDocumentMd5Frame(docNum: Int, md5Hash: String): ByteArray {
        val hashBytes = md5Hash.toByteArray(Charsets.UTF_8)
        val payload = ByteBuffer.allocate(4 + hashBytes.size).order(ByteOrder.BIG_ENDIAN)
            .putInt(docNum)
            .put(hashBytes)
            .array()
        return WifiFrameBuilder(
            groupCode = WifiProtocolConstants.GROUP_DASH_REQUESTS,
            commandCode = 0x03,
            payload = payload
        ).build()
    }
}
