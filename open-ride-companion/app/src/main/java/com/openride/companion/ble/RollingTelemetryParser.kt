package com.openride.companion.ble

import com.openride.companion.model.VehicleTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * High-Speed 10 Hz Rolling Telemetry Streaming Pipeline.
 * Buffers arbitrary GATT notification fragments, scans for 0xBA 0x5A sync markers,
 * validates checksums, tracks packet gaps, and emits the latest state via StateFlow.
 */
class RollingTelemetryParser(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val defaultVehicleId: String = "ACTIVE_VEHICLE"
) {
    // Inbound bounded channel to absorb 10 Hz burst spikes without blocking BLE callbacks
    private val incomingChannel = Channel<ByteArray>(
        capacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _telemetryState = MutableStateFlow(VehicleTelemetry(vehicleId = defaultVehicleId))
    val telemetryState: StateFlow<VehicleTelemetry> = _telemetryState.asStateFlow()

    private val _telltaleState = MutableStateFlow(TelltaleIndicators())
    val telltaleState: StateFlow<TelltaleIndicators> = _telltaleState.asStateFlow()

    private val _errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()

    // Metrics & loss tracking
    var totalPacketsReceived: Long = 0
        private set
    var totalPacketsCorrupted: Long = 0
        private set
    var totalPacketsDropped: Long = 0
        private set
    private var lastSequenceCounter: Int = -1

    // Stream synchronizer accumulator
    private val streamBuffer = ByteArrayOutputStream(1024)

    init {
        scope.launch {
            processStream()
        }
    }

    /**
     * Entrypoint called directly from onCharacteristicChanged or simulation harness.
     * Non-blocking, thread-safe.
     */
    fun onBytesReceived(data: ByteArray) {
        incomingChannel.trySend(data)
    }

    /**
     * Direct synchronous parse method for testing and single-frame decodes.
     */
    fun parseDirect(data: ByteArray): NewGeneralPacketV2Parser.ParseResult {
        val result = NewGeneralPacketV2Parser.parse(data, defaultVehicleId)
        if (result is NewGeneralPacketV2Parser.ParseResult.Success) {
            totalPacketsReceived++
            trackSequenceGap(result.sequenceCounter)
            _telemetryState.value = result.telemetry
            _telltaleState.value = result.telltales
        } else if (result is NewGeneralPacketV2Parser.ParseResult.Error) {
            totalPacketsCorrupted++
            _errorFlow.tryEmit(result.reason)
        }
        return result
    }

    fun resetMetrics() {
        totalPacketsReceived = 0
        totalPacketsCorrupted = 0
        totalPacketsDropped = 0
        lastSequenceCounter = -1
    }

    private suspend fun processStream() {
        for (chunk in incomingChannel) {
            streamBuffer.write(chunk)
            val currentBytes = streamBuffer.toByteArray()

            var offset = 0
            while (offset + NewGeneralPacketV2Parser.PACKET_SIZE <= currentBytes.size) {
                // Search for 0xBA 0x5A magic sync bytes
                if (currentBytes[offset] == NewGeneralPacketV2Parser.TELEMETRY_SYNC_BYTE_0 &&
                    currentBytes[offset + 1] == NewGeneralPacketV2Parser.TELEMETRY_SYNC_BYTE_1) {

                    val candidate = currentBytes.copyOfRange(offset, offset + NewGeneralPacketV2Parser.PACKET_SIZE)
                    when (val result = NewGeneralPacketV2Parser.parse(candidate, defaultVehicleId)) {
                        is NewGeneralPacketV2Parser.ParseResult.Success -> {
                            totalPacketsReceived++
                            trackSequenceGap(result.sequenceCounter)

                            // Publish latest state
                            _telemetryState.value = result.telemetry
                            _telltaleState.value = result.telltales

                            offset += NewGeneralPacketV2Parser.PACKET_SIZE
                        }
                        is NewGeneralPacketV2Parser.ParseResult.Error -> {
                            totalPacketsCorrupted++
                            _errorFlow.tryEmit(result.reason)
                            // Advance by 1 to resync if false magic match
                            offset++
                        }
                    }
                } else {
                    offset++
                }
            }

            // Keep remaining unparsed tail in streamBuffer
            streamBuffer.reset()
            if (offset < currentBytes.size) {
                streamBuffer.write(currentBytes, offset, currentBytes.size - offset)
            }
        }
    }

    private fun trackSequenceGap(currentSeq: Int) {
        if (lastSequenceCounter != -1) {
            val expectedSeq = (lastSequenceCounter + 1) and 0xFF
            val gap = (currentSeq - expectedSeq) and 0xFF
            // If gap is forward between 1 and 127, count as dropped packets
            if (gap in 1..127) {
                totalPacketsDropped += gap
            }
        }
        lastSequenceCounter = currentSeq
    }
}
