package com.openride.companion.wifi

import com.openride.companion.model.ClusterHardwareType
import com.openride.companion.safety.MotionSafetyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Secondary presentation coordinator for Cluster Wi-Fi mirroring.
 * Enforces hardware compatibility: connects on TFT_COLOR_0265, disables on LCD_INVERSION_2763.
 * Cooperates with MotionSafetyManager to blackout or pause interactive mirror during vehicle motion.
 */
class DisplayMirrorManager(
    private val safetyManager: MotionSafetyManager? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _isMirroringActive = MutableStateFlow(false)
    val isMirroringActive: StateFlow<Boolean> = _isMirroringActive.asStateFlow()

    private val _isPausedBySafety = MutableStateFlow(false)
    val isPausedBySafety: StateFlow<Boolean> = _isPausedBySafety.asStateFlow()

    private val _activeClusterType = MutableStateFlow<ClusterHardwareType?>(null)
    val activeClusterType: StateFlow<ClusterHardwareType?> = _activeClusterType.asStateFlow()

    val tcpServer = ClusterTcpServer(scope = scope)
    val udpServer = ClusterUdpServer(scope = scope)

    /**
     * Checks whether a given cluster hardware type is eligible for Wi-Fi mirroring.
     */
    fun canMirror(hardwareType: ClusterHardwareType): Boolean {
        return hardwareType.supportsWifiMirroring
    }

    /**
     * Starts mirroring session for an authorized vehicle cluster.
     * Rejects clusters without Wi-Fi support (e.g. LCD_INVERSION_2763).
     */
    fun startMirroring(hardwareType: ClusterHardwareType): Boolean {
        if (!canMirror(hardwareType)) {
            // Hardware platform does not support Wi-Fi mirroring
            return false
        }

        _activeClusterType.value = hardwareType
        _isMirroringActive.value = true

        tcpServer.start()
        udpServer.startStreaming()

        // Monitor motion safety
        safetyManager?.let { safety ->
            scope.launch {
                // If bike starts moving, update mirroring safety status
                if (!safety.isBikeStationary) {
                    _isPausedBySafety.value = true
                }
            }
        }

        return true
    }

    /**
     * Updates motion state to pause or resume mirroring display.
     */
    fun updateMotionState(isStationary: Boolean) {
        if (!isStationary) {
            _isPausedBySafety.value = true
        } else {
            _isPausedBySafety.value = false
        }
    }

    /**
     * Stops current mirroring session and releases network sockets.
     */
    fun stopMirroring() {
        _isMirroringActive.value = false
        _isPausedBySafety.value = false
        _activeClusterType.value = null

        tcpServer.stop()
        udpServer.stopStreaming()
    }

    /**
     * Renders a blank / blackout safety frame when in motion.
     */
    fun generateSafetyBlackoutFrame(width: Int = WifiProtocolConstants.TFT_WIDTH, height: Int = WifiProtocolConstants.TFT_HEIGHT): ByteArray {
        // Minimal valid JPEG binary or zeroed buffer representing blackout
        return ByteArray(128) { 0x00 }
    }
}
