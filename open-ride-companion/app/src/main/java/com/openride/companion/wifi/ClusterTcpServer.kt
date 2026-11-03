package com.openride.companion.wifi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TCP Port 5558 client & control session coordinator.
 * Handles bidirectional control events (Handlebar buttons, 4D Touch, BIKE_INFO, Document Sync).
 */
class ClusterTcpServer(
    private val host: String = WifiProtocolConstants.VEHICLE_GATEWAY_IP,
    private val port: Int = WifiProtocolConstants.TCP_CONTROL_PORT,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _handlebarEvents = MutableSharedFlow<HandlebarButton>(extraBufferCapacity = 16)
    val handlebarEvents: SharedFlow<HandlebarButton> = _handlebarEvents.asSharedFlow()

    private val _touchEvents = MutableSharedFlow<TouchPoint>(extraBufferCapacity = 32)
    val touchEvents: SharedFlow<TouchPoint> = _touchEvents.asSharedFlow()

    private val _bikeStationaryEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
    val bikeStationaryEvents: SharedFlow<Boolean> = _bikeStationaryEvents.asSharedFlow()

    private val parser = WifiFrameParser()
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var connectionJob: Job? = null

    fun start() {
        if (_isConnected.value) return
        connectionJob = scope.launch {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 3000)
                socket = s
                outputStream = s.getOutputStream()
                _isConnected.value = true

                val inputStream = s.getInputStream()
                val buffer = ByteArray(4096)
                while (_isConnected.value && !s.isClosed) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        processIncomingData(buffer, bytesRead)
                    } else if (bytesRead == -1) {
                        break
                    }
                }
            } catch (e: Exception) {
                // Socket error or connection closed
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        _isConnected.value = false
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        outputStream = null
        connectionJob?.cancel()
        connectionJob = null
    }

    /**
     * Sends a framed packet over TCP 5558.
     */
    fun sendFrame(groupCode: Byte, commandCode: Byte, payload: ByteArray): Boolean {
        val frame = WifiFrameBuilder(groupCode, commandCode, payload).build()
        return try {
            outputStream?.write(frame)
            outputStream?.flush()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sends document binary with size and MD5 handshake.
     */
    fun sendDocument(docNum: Int, documentBytes: ByteArray): Boolean {
        val md5 = ClusterDocumentSync.calculateMd5Hash(documentBytes)
        val sizeFrame = ClusterDocumentSync.createDocumentSizeFrame(docNum, documentBytes.size)
        val md5Frame = ClusterDocumentSync.createDocumentMd5Frame(docNum, md5)

        return try {
            outputStream?.write(sizeFrame)
            outputStream?.write(md5Frame)
            outputStream?.write(documentBytes)
            outputStream?.flush()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Processes inbound TCP frames and dispatches touch, button, and status events.
     */
    fun processIncomingData(data: ByteArray, length: Int) {
        var offset = 0
        while (offset < length) {
            when (val result = parser.parse(data, offset, length - offset)) {
                is WifiFrameParser.ParseResult.Success -> {
                    dispatchParsedFrame(result.groupCode, result.commandCode, result.payload)
                    offset += WifiProtocolConstants.HEADER_SIZE + result.payload.size + WifiProtocolConstants.CRC_SIZE
                }
                is WifiFrameParser.ParseResult.ChecksumMismatch -> {
                    offset++
                }
                is WifiFrameParser.ParseResult.ResyncNeeded -> {
                    offset++
                }
                is WifiFrameParser.ParseResult.IncompleteFrame -> {
                    break
                }
            }
        }
    }

    /**
     * Dispatches parsed frames to appropriate domain flows.
     */
    fun dispatchParsedFrame(groupCode: Byte, commandCode: Byte, payload: ByteArray) {
        when (groupCode) {
            WifiProtocolConstants.GROUP_INPUT_EVENTS -> {
                if (commandCode == WifiProtocolConstants.CMD_INPUT_TOUCH && payload.size >= 8) {
                    val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
                    val x = buf.short.toFloat()
                    val y = buf.short.toFloat()
                    val z = if (payload.size >= 10) buf.short.toFloat() else 0.0f
                    val pressure = if (payload.size >= 12) (buf.short.toInt() and 0xFFFF) / 1000.0f else 1.0f
                    val touch = TouchPoint(x, y, z, pressure)
                    _touchEvents.tryEmit(touch)
                } else if (payload.isNotEmpty()) {
                    // Handlebar button event
                    val buttonCode = payload[0].toInt() and 0xFF
                    val button = parseHandlebarButton(buttonCode)
                    button?.let { _handlebarEvents.tryEmit(it) }
                }
            }
            WifiProtocolConstants.GROUP_DASH_EVENTS -> {
                if (commandCode == WifiProtocolConstants.CMD_BIKE_INFO && payload.isNotEmpty()) {
                    val isStationary = payload[0].toInt() == 1
                    _bikeStationaryEvents.tryEmit(isStationary)
                }
            }
        }
    }

    /**
     * Simulates receiving a handlebar button event for testing and UI integration.
     */
    fun simulateHandlebarButton(button: HandlebarButton) {
        _handlebarEvents.tryEmit(button)
    }

    /**
     * Simulates receiving a touch coordinate for testing and UI integration.
     */
    fun simulateTouch(touch: TouchPoint) {
        _touchEvents.tryEmit(touch)
    }

    /**
     * Simulates receiving a bike stationary status event.
     */
    fun simulateBikeStationary(isStationary: Boolean) {
        _bikeStationaryEvents.tryEmit(isStationary)
    }

    companion object {
        fun parseHandlebarButton(code: Int): HandlebarButton? {
            return when (code) {
                0 -> HandlebarButton.UP
                1 -> HandlebarButton.DOWN
                2 -> HandlebarButton.LEFT
                3 -> HandlebarButton.RIGHT
                4 -> HandlebarButton.SELECT
                5 -> HandlebarButton.BACK
                else -> null
            }
        }
    }
}
