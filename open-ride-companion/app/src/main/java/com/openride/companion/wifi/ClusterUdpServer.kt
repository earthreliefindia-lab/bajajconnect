package com.openride.companion.wifi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Single fragment packet for UDP screen streaming.
 */
data class FrameSlicePacket(
    val frameSeq: Int,
    val fragmentIndex: Int,
    val totalFragments: Int,
    val payload: ByteArray,
    val packetBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FrameSlicePacket
        return frameSeq == other.frameSeq &&
                fragmentIndex == other.fragmentIndex &&
                totalFragments == other.totalFragments &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = frameSeq
        result = 31 * result + fragmentIndex
        result = 31 * result + totalFragments
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Utility to slice full JPEG frames into 1008-byte MTU-safe fragments with 16-byte headers.
 */
object FrameSlicer {
    const val SLICE_HEADER_SIZE = 16

    fun sliceFrame(jpegBytes: ByteArray, frameSeq: Int): List<FrameSlicePacket> {
        val totalLen = jpegBytes.size
        val chunkSize = WifiProtocolConstants.FRAME_SIZE_PAYLOAD
        val totalFragments = ((totalLen + chunkSize - 1) / chunkSize).coerceAtLeast(1)

        val crcCalc = CRC32()
        crcCalc.update(jpegBytes)
        val frameCrc = crcCalc.value.toInt()

        val packets = mutableListOf<FrameSlicePacket>()
        for (i in 0 until totalFragments) {
            val start = i * chunkSize
            val end = (start + chunkSize).coerceAtMost(totalLen)
            val sliceLen = end - start
            val slicePayload = jpegBytes.copyOfRange(start, end)

            val packetBuffer = ByteBuffer.allocate(SLICE_HEADER_SIZE + sliceLen)
                .order(ByteOrder.BIG_ENDIAN)
            packetBuffer.putInt(frameSeq)
            packetBuffer.putShort(i.toShort())
            packetBuffer.putShort(totalFragments.toShort())
            packetBuffer.putShort(sliceLen.toShort())
            packetBuffer.putInt(frameCrc)
            packetBuffer.putShort(0.toShort()) // Flags / Reserved
            packetBuffer.put(slicePayload)

            packets.add(
                FrameSlicePacket(
                    frameSeq = frameSeq,
                    fragmentIndex = i,
                    totalFragments = totalFragments,
                    payload = slicePayload,
                    packetBytes = packetBuffer.array()
                )
            )
        }
        return packets
    }

    fun reassembleFrame(slices: List<FrameSlicePacket>): ByteArray? {
        if (slices.isEmpty()) return null
        val sorted = slices.sortedBy { it.fragmentIndex }
        val totalExpected = sorted.first().totalFragments
        if (sorted.size != totalExpected) return null

        val totalBytes = sorted.sumOf { it.payload.size }
        val out = ByteArray(totalBytes)
        var offset = 0
        for (slice in sorted) {
            System.arraycopy(slice.payload, 0, out, offset, slice.payload.size)
            offset += slice.payload.size
        }
        return out
    }
}

/**
 * Thread-safe Circular FIFO Frame Buffer.
 */
class CircularFrameBuffer(private val capacity: Int = 10) {
    private val queue = ArrayDeque<ByteArray>(capacity)

    @Synchronized
    fun push(frame: ByteArray) {
        if (queue.size >= capacity) {
            queue.pollFirst() // Drop oldest frame to maintain low latency
        }
        queue.addLast(frame)
    }

    @Synchronized
    fun pop(): ByteArray? {
        return queue.pollFirst()
    }

    @Synchronized
    fun size(): Int = queue.size

    @Synchronized
    fun clear() {
        queue.clear()
    }
}

/**
 * UDP Port 5556 high-performance screen streaming service.
 * Delivers sliced 30 fps video frames to TFT cluster display.
 */
class ClusterUdpServer(
    private val targetIp: String = WifiProtocolConstants.VEHICLE_GATEWAY_IP,
    private val port: Int = WifiProtocolConstants.UDP_STREAMING_PORT,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentFps = MutableStateFlow(0)
    val currentFps: StateFlow<Int> = _currentFps.asStateFlow()

    private val frameBuffer = CircularFrameBuffer(capacity = 8)
    private var streamingJob: Job? = null
    private var datagramSocket: DatagramSocket? = null
    private var frameSequence: Int = 0

    var compressionQuality: Int = WifiProtocolConstants.INITIAL_COMPRESSION_QUALITY
        private set

    /**
     * Optional AES-256 encryption using the static key mined from APK.
     */
    fun encryptPayload(data: ByteArray): ByteArray {
        return try {
            val keyBytes = WifiProtocolConstants.AES_256_KEY.toByteArray(Charsets.UTF_8).take(32).toByteArray()
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher.doFinal(data)
        } catch (e: Exception) {
            data
        }
    }

    fun submitFrame(jpegBytes: ByteArray) {
        // Dynamic compression throttling
        adjustCompressionForSize(jpegBytes.size)
        frameBuffer.push(jpegBytes)
    }

    private fun adjustCompressionForSize(frameSizeBytes: Int) {
        if (frameSizeBytes > WifiProtocolConstants.MAX_IMG_SIZE) {
            compressionQuality = (compressionQuality - 10).coerceAtLeast(WifiProtocolConstants.MIN_COMPRESSION_QUALITY)
        } else if (frameSizeBytes < WifiProtocolConstants.THRESHOLD_IMG_SIZE &&
            compressionQuality < WifiProtocolConstants.INITIAL_COMPRESSION_QUALITY) {
            compressionQuality = (compressionQuality + 5).coerceAtMost(WifiProtocolConstants.INITIAL_COMPRESSION_QUALITY)
        }
    }

    fun startStreaming() {
        if (_isStreaming.value) return
        _isStreaming.value = true

        streamingJob = scope.launch {
            try {
                val socket = DatagramSocket()
                datagramSocket = socket
                val targetAddress = InetAddress.getByName(targetIp)

                var framesSentThisSecond = 0
                var lastFpsCheckTime = System.currentTimeMillis()

                while (_isStreaming.value) {
                    val frame = frameBuffer.pop()
                    if (frame != null) {
                        frameSequence++
                        val slices = FrameSlicer.sliceFrame(frame, frameSequence)
                        for (slice in slices) {
                            val packet = DatagramPacket(
                                slice.packetBytes,
                                slice.packetBytes.size,
                                targetAddress,
                                port
                            )
                            socket.send(packet)
                        }
                        framesSentThisSecond++
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastFpsCheckTime >= 1000L) {
                        _currentFps.value = framesSentThisSecond
                        framesSentThisSecond = 0
                        lastFpsCheckTime = now
                    }

                    delay(WifiProtocolConstants.FRAME_INTERVAL_MS)
                }
            } catch (e: Exception) {
                // Streaming stopped or network error
            } finally {
                stopStreaming()
            }
        }
    }

    fun stopStreaming() {
        _isStreaming.value = false
        _currentFps.value = 0
        try {
            datagramSocket?.close()
        } catch (_: Exception) {}
        datagramSocket = null
        streamingJob?.cancel()
        streamingJob = null
        frameBuffer.clear()
    }
}
