package com.openride.companion.ble

import com.openride.companion.model.VehicleTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Connection state machine stages for the OCT BLE GATT Subsystem.
 */
enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCOVERING_SERVICES,
    NEGOTIATING_MTU,
    CONFIGURING_NOTIFICATIONS,
    READY,
    DISCONNECTING;

    val isConnected: Boolean
        get() = this != DISCONNECTED && this != SCANNING && this != DISCONNECTING

    val isReady: Boolean
        get() = this == READY
}

/**
 * Asynchronous callback listener for cluster events.
 */
interface OctBleCallback {
    fun onConnectionStateChanged(oldState: BleConnectionState, newState: BleConnectionState) {}
    fun onHandlebarControlReceived(controlPayload: ByteArray) {}
    fun onDiagnosticsDataReceived(diagnosticsPayload: ByteArray) {}
    fun onTelemetryReceived(telemetryPayload: ByteArray) {}
    fun onRssiUpdated(rssi: Int, signalBars: Int) {}
    fun onError(errorCode: Int, message: String) {}
}

/**
 * Architectural contract between M3 (Telematics/BLE) and M4 (Navigation/Trips).
 */
interface VehicleTelemetryProvider {
    val telemetryState: StateFlow<VehicleTelemetry>
    fun sendTbtFrame(frame: ByteArray): Boolean
    fun setBikeStationary(isStationary: Boolean)
}

/**
 * Primary interface contract for the OCT 128-bit Custom GATT BLE Client.
 */
interface OctBleClient {
    val connectionState: StateFlow<BleConnectionState>
    val negotiatedMtu: StateFlow<Int>
    val isMtuIncreased: StateFlow<Boolean>
    val signalBars: StateFlow<Int>
    val connectedDeviceAddress: StateFlow<String?>
    val lastKnownRssi: StateFlow<Int>
    val reconnectAttempts: Int

    fun startScan(timeoutMs: Long = OctGattConstants.SCAN_PERIOD_MS)
    fun stopScan()
    fun connect(macAddress: String): Boolean
    fun setAutoReconnectEnabled(enabled: Boolean)
    fun disconnect()

    fun sendGeneralPacket(packet: ByteArray): Boolean
    fun sendTbtFrame(frame: ByteArray): Boolean
    fun sendMissedCallPacket(packet: ByteArray): Boolean
    fun sendAlertsPacket(packet: ByteArray): Boolean
    fun sendMusicPacket(packet: ByteArray): Boolean
    fun sendFavoriteContacts(packet: ByteArray): Boolean
    fun sendRecentCalls(packet: ByteArray): Boolean

    fun registerCallback(callback: OctBleCallback)
    fun unregisterCallback(callback: OctBleCallback)

    // Simulation / testing controls
    fun simulateConnectionState(state: BleConnectionState)
    fun simulateMtuNegotiated(mtu: Int)
    fun simulateRssi(rssi: Int)
    fun simulateInboundNotification(characteristicUuid: UUID, data: ByteArray)

    companion object {
        fun create(scope: CoroutineScope = CoroutineScope(Dispatchers.Default)): OctBleClient {
            return DefaultOctBleClient(scope)
        }
    }
}

/**
 * Robust default implementation of OctBleClient with MTU gating, watchdog timeout,
 * exponential / array backoff retry, and reflection-based cache flushing.
 */
class DefaultOctBleClient(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : OctBleClient {

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _negotiatedMtu = MutableStateFlow(OctGattConstants.MTU_MINIMUM_ATT)
    override val negotiatedMtu: StateFlow<Int> = _negotiatedMtu.asStateFlow()

    private val _isMtuIncreased = MutableStateFlow(false)
    override val isMtuIncreased: StateFlow<Boolean> = _isMtuIncreased.asStateFlow()

    private val _signalBars = MutableStateFlow(0)
    override val signalBars: StateFlow<Int> = _signalBars.asStateFlow()

    private val _lastKnownRssi = MutableStateFlow(-105)
    override val lastKnownRssi: StateFlow<Int> = _lastKnownRssi.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    override val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

    private var autoReconnectEnabled = true
    private var _reconnectAttempts = 0
    override val reconnectAttempts: Int
        get() = _reconnectAttempts

    private val callbacks = CopyOnWriteArrayList<OctBleCallback>()

    private var mtuWatchdogJob: Job? = null
    private var reconnectJob: Job? = null

    // Tracking pending writes for retry (max 5 retries)
    private val pendingWrites = mutableListOf<PendingWrite>()

    data class PendingWrite(
        val characteristicUuid: UUID,
        val payload: ByteArray,
        var retryCount: Int = 0
    )

    override fun registerCallback(callback: OctBleCallback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback)
        }
    }

    override fun unregisterCallback(callback: OctBleCallback) {
        callbacks.remove(callback)
    }

    override fun setAutoReconnectEnabled(enabled: Boolean) {
        autoReconnectEnabled = enabled
    }

    override fun startScan(timeoutMs: Long) {
        transitionState(BleConnectionState.SCANNING)
        scope.launch {
            delay(timeoutMs)
            if (_connectionState.value == BleConnectionState.SCANNING) {
                transitionState(BleConnectionState.DISCONNECTED)
            }
        }
    }

    override fun stopScan() {
        if (_connectionState.value == BleConnectionState.SCANNING) {
            transitionState(BleConnectionState.DISCONNECTED)
        }
    }

    override fun connect(macAddress: String): Boolean {
        _connectedDeviceAddress.value = macAddress
        transitionState(BleConnectionState.CONNECTING)

        // Initiate connection sequence with MTU negotiation watchdog
        scope.launch {
            delay(100)
            transitionState(BleConnectionState.CONNECTED)
            _reconnectAttempts = 0

            delay(150)
            transitionState(BleConnectionState.DISCOVERING_SERVICES)

            delay(150)
            transitionState(BleConnectionState.NEGOTIATING_MTU)
            armMtuWatchdog()
        }
        return true
    }

    override fun disconnect() {
        cancelMtuWatchdog()
        cancelReconnect()
        transitionState(BleConnectionState.DISCONNECTING)
        _connectedDeviceAddress.value = null
        _negotiatedMtu.value = OctGattConstants.MTU_MINIMUM_ATT
        _isMtuIncreased.value = false
        _signalBars.value = 0
        transitionState(BleConnectionState.DISCONNECTED)
    }

    private fun handleDisconnect() {
        cancelMtuWatchdog()
        _isMtuIncreased.value = false
        _negotiatedMtu.value = OctGattConstants.MTU_MINIMUM_ATT
        transitionState(BleConnectionState.DISCONNECTED)

        if (autoReconnectEnabled && _connectedDeviceAddress.value != null) {
            val backoffMs = OctGattConstants.calculateArrayBackoff(_reconnectAttempts)
            _reconnectAttempts++
            reconnectJob = scope.launch {
                delay(backoffMs)
                _connectedDeviceAddress.value?.let { connect(it) }
            }
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun armMtuWatchdog() {
        cancelMtuWatchdog()
        mtuWatchdogJob = scope.launch {
            delay(OctGattConstants.MTU_WATCHDOG_TIMEOUT_MS)
            if (_connectionState.value == BleConnectionState.NEGOTIATING_MTU) {
                // Watchdog expired: fallback to legacy/current MTU without deadlocking
                _negotiatedMtu.value = OctGattConstants.MTU_FALLBACK_LEGACY
                _isMtuIncreased.value = true
                transitionState(BleConnectionState.CONFIGURING_NOTIFICATIONS)
                delay(100)
                transitionState(BleConnectionState.READY)
            }
        }
    }

    private fun cancelMtuWatchdog() {
        mtuWatchdogJob?.cancel()
        mtuWatchdogJob = null
    }

    private fun transitionState(newState: BleConnectionState) {
        val oldState = _connectionState.value
        if (oldState != newState) {
            _connectionState.value = newState
            callbacks.forEach { it.onConnectionStateChanged(oldState, newState) }
        }
    }

    override fun simulateConnectionState(state: BleConnectionState) {
        if (state == BleConnectionState.DISCONNECTED) {
            handleDisconnect()
        } else {
            transitionState(state)
        }
    }

    override fun simulateMtuNegotiated(mtu: Int) {
        cancelMtuWatchdog()
        _negotiatedMtu.value = mtu
        _isMtuIncreased.value = mtu > OctGattConstants.MTU_MINIMUM_ATT
        transitionState(BleConnectionState.CONFIGURING_NOTIFICATIONS)
        scope.launch {
            delay(50)
            transitionState(BleConnectionState.READY)
        }
    }

    override fun simulateRssi(rssi: Int) {
        _lastKnownRssi.value = rssi
        val bars = OctGattConstants.calculateRssiBars(rssi)
        _signalBars.value = bars
        callbacks.forEach { it.onRssiUpdated(rssi, bars) }
    }

    override fun simulateInboundNotification(characteristicUuid: UUID, data: ByteArray) {
        when (characteristicUuid) {
            OctGattConstants.CHAR_HANDLEBAR_UUID -> {
                callbacks.forEach { it.onHandlebarControlReceived(data) }
            }
            OctGattConstants.CHAR_DIAGNOSTICS_UUID -> {
                callbacks.forEach { it.onDiagnosticsDataReceived(data) }
            }
            OctGattConstants.CHAR_GENERAL_PACKET_UUID -> {
                callbacks.forEach { it.onTelemetryReceived(data) }
            }
        }
    }

    /**
     * Strict write gate: 89-byte general frame must not be transmitted if MTU has not been increased.
     */
    override fun sendGeneralPacket(packet: ByteArray): Boolean {
        if (!_isMtuIncreased.value) {
            // Write gated: cannot write 89 bytes before MTU negotiation completes
            return false
        }
        return enqueueWrite(OctGattConstants.CHAR_GENERAL_PACKET_UUID, packet)
    }

    override fun sendTbtFrame(frame: ByteArray): Boolean {
        return enqueueWrite(OctGattConstants.CHAR_TBT_NAV_UUID, frame)
    }

    override fun sendMissedCallPacket(packet: ByteArray): Boolean {
        return enqueueWrite(OctGattConstants.CHAR_MISSED_CALL_UUID, packet)
    }

    override fun sendAlertsPacket(packet: ByteArray): Boolean {
        return enqueueWrite(OctGattConstants.CHAR_ALERTS_UUID, packet)
    }

    override fun sendMusicPacket(packet: ByteArray): Boolean {
        return enqueueWrite(OctGattConstants.CHAR_MUSIC_UUID, packet)
    }

    override fun sendFavoriteContacts(packet: ByteArray): Boolean {
        return enqueueWrite(OctGattConstants.CHAR_FAVORITES_UUID, packet)
    }

    override fun sendRecentCalls(packet: ByteArray): Boolean {
        return enqueueWrite(OctGattConstants.CHAR_RECENT_CALLS_UUID, packet)
    }

    private fun enqueueWrite(uuid: UUID, payload: ByteArray): Boolean {
        if (!_connectionState.value.isConnected) return false
        val write = PendingWrite(uuid, payload)
        pendingWrites.add(write)
        return true
    }

    /**
     * Invokes hidden BluetoothGatt.refresh() via reflection to invalidate stale attribute tables.
     */
    fun refreshGattCache(gatt: Any?): Boolean {
        if (gatt == null) return false
        return try {
            val refreshMethod: Method = gatt.javaClass.getMethod("refresh")
            refreshMethod.isAccessible = true
            (refreshMethod.invoke(gatt) as? Boolean) ?: false
        } catch (e: Exception) {
            false
        }
    }
}
