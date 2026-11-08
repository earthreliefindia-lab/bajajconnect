package com.openride.companion.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handlebar switch and media actions received from Bajaj Pulsar cluster.
 */
sealed class HandlebarAction {
    object Idle : HandlebarAction()
    object MenuUp : HandlebarAction()
    object MenuDown : HandlebarAction()
    object MenuSelect : HandlebarAction()
    object MenuBack : HandlebarAction()
    object AcceptCall : HandlebarAction()
    object RejectCall : HandlebarAction()
    object RejectWithSms : HandlebarAction()
    object MusicPlayPause : HandlebarAction()
    object MusicNextTrack : HandlebarAction()
    object MusicPrevTrack : HandlebarAction()
    data class Unknown(val rawByte0: Int, val rawByte1: Int) : HandlebarAction()
}

/**
 * Production Controller for Bajaj Pulsar Digital Console (SKU: 00JR33BT).
 * Validated against physical cluster packet traces captured from hardware MAC E4:FA:5B:59:60:B5.
 */
class PulsarClusterController {

    companion object {
        const val TARGET_SKU = "00JR33BT"
        const val KNOWN_PULSAR_MAC = "E4:FA:5B:59:60:B5"
        const val STATUS_PACKET_SIZE = 55
        const val HANDLEBAR_FRAME_SIZE = 26
    }

    private val _connectionState = MutableStateFlow("DISCONNECTED")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private val _lastAction = MutableStateFlow<HandlebarAction>(HandlebarAction.Idle)
    val lastAction: StateFlow<HandlebarAction> = _lastAction.asStateFlow()

    private var heartbeatCounter: Byte = 0

    /**
     * Creates the exact 55-byte status frame written to Characteristic 0210... at 1 Hz.
     */
    fun createKeepaliveFrame(
        batteryPct: Int = 85,
        signalBars: Int = 4,
        isHeadsetConnected: Boolean = true,
        volume: Int = 10,
        callerName: String = "",
        callProgressSec: Int = 0
    ): ByteArray {
        heartbeatCounter = ((heartbeatCounter + 1) and 0xFF).toByte()

        val builder = NewGeneralPacketV2Builder().apply {
            this.currentVolume = volume.coerceIn(0, 15)
            this.isHeadsetConnected = isHeadsetConnected
            this.signalStrength = signalBars.coerceIn(0, 5)
            this.batteryLevelBucket = NewGeneralPacketV2Builder.mapBatteryToBucket(batteryPct)
            this.callerName = callerName
            this.callProgressSeconds = callProgressSec
            this.heartbeat = heartbeatCounter
        }

        return builder.buildPulsar55()
    }

    /**
     * Parses the 26-byte handlebar / control frame received from Characteristic 0a10... / 0610...
     */
    fun parseHandlebarFrame(frame: ByteArray): HandlebarAction {
        if (frame.isEmpty()) return HandlebarAction.Idle

        val byte0 = frame[0].toInt() and 0xFF
        val actionMode = (byte0 shr 6) and 0x03 // Bits 6..7
        val buttonCode = if (frame.size > 1) frame[1].toInt() and 0xFF else 0

        val callAction = if (frame.size > 4) frame[4].toInt() and 0xFF else 0
        val rejectSmsAction = if (frame.size > 6) frame[6].toInt() and 0xFF else 0
        val musicPlayPause = if (frame.size > 7) frame[7].toInt() and 0xFF else 0
        val musicNav = if (frame.size > 8) frame[8].toInt() and 0xFF else 0

        val action = when {
            callAction == 1 -> HandlebarAction.AcceptCall
            callAction == 2 -> HandlebarAction.RejectCall
            rejectSmsAction == 1 -> HandlebarAction.RejectWithSms
            musicPlayPause == 1 -> HandlebarAction.MusicPlayPause
            musicNav == 1 -> HandlebarAction.MusicNextTrack
            musicNav == 2 -> HandlebarAction.MusicPrevTrack
            buttonCode == 1 -> HandlebarAction.MenuUp
            buttonCode == 2 -> HandlebarAction.MenuDown
            buttonCode == 3 -> HandlebarAction.MenuSelect
            buttonCode == 4 -> HandlebarAction.MenuBack
            actionMode == 0 && buttonCode == 0 -> HandlebarAction.Idle
            else -> HandlebarAction.Unknown(byte0, buttonCode)
        }

        _lastAction.value = action
        return action
    }

    fun updateConnectionState(state: String) {
        _connectionState.value = state
    }
}
