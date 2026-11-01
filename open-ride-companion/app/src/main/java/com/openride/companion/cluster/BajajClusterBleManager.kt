package com.openride.companion.cluster

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.openride.companion.model.BleConnectionStatus
import com.openride.companion.model.ClusterType
import com.openride.companion.model.LiveNavInstruction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Manages Bluetooth Low Energy (BLE) communication with Bajaj motorcycle digital clusters.
 * Supports Pulsar, Dominar, and Chetak cluster protocols.
 */
class BajajClusterBleManager(private val context: Context) {

    companion object {
        private const val TAG = "BajajClusterBleManager"

        // Standard Bajaj / Mappls Connected Vehicle GATT Service UUIDs
        val SERVICE_CLUSTER_UUID: UUID = UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb")
        val CHAR_TBT_NAVIGATION_UUID: UUID = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")
        val CHAR_TELEPHONY_NOTIF_UUID: UUID = UUID.fromString("0000fee2-0000-1000-8000-00805f9b34fb")
        val CHAR_VEHICLE_TELEMETRY_UUID: UUID = UUID.fromString("0000fee3-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothAdapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow(BleConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionStatus> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = BleConnectionStatus.CONNECTED
                    _connectedDeviceName.value = gatt?.device?.name ?: "Bajaj Smart Cluster"
                    gatt?.discoverServices()
                    Log.d(TAG, "Connected to motorcycle cluster: ${gatt?.device?.address}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = BleConnectionStatus.DISCONNECTED
                    _connectedDeviceName.value = null
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    Log.d(TAG, "Disconnected from motorcycle cluster")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Cluster GATT services discovered successfully")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter?.isEnabled == true) {
            _connectionState.value = BleConnectionStatus.SCANNING
            Log.d(TAG, "Scanning for nearby Bajaj motorcycle clusters...")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        _connectionState.value = BleConnectionStatus.CONNECTING
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting GATT", e)
        }
    }

    /**
     * Builds and sends a Turn-by-Turn (TBT) frame to the motorcycle instrument cluster.
     * Frame format:
     * [0] = 0x01 (Opcode: TBT)
     * [1] = Maneuver Code (0 to 8)
     * [2..3] = Distance to turn in meters (Big Endian UInt16)
     * [4..N] = Next street name (ASCII bytes, null-padded)
     */
    fun sendTbtInstruction(instruction: LiveNavInstruction, clusterType: ClusterType) {
        val streetBytes = instruction.nextStreet.toByteArray(Charsets.US_ASCII).take(16).toByteArray()
        val buffer = ByteBuffer.allocate(4 + streetBytes.size).order(ByteOrder.BIG_ENDIAN)

        buffer.put(0x01.toByte())
        buffer.put(instruction.maneuver.clusterCode.toByte())
        buffer.putShort(instruction.distanceToManeuverMeters.coerceAtMost(65535).toShort())
        buffer.put(streetBytes)

        writeCharacteristic(CHAR_TBT_NAVIGATION_UUID, buffer.array())
    }

    /**
     * Sends an incoming caller ID or missed call alert to the handlebar display.
     */
    fun sendIncomingCall(callerName: String, callerNumber: String, isRinging: Boolean) {
        val nameBytes = callerName.toByteArray(Charsets.US_ASCII).take(16).toByteArray()
        val buffer = ByteBuffer.allocate(2 + nameBytes.size)

        buffer.put(0x02.toByte())
        buffer.put(if (isRinging) 0x01.toByte() else 0x00.toByte())
        buffer.put(nameBytes)

        writeCharacteristic(CHAR_TELEPHONY_NOTIF_UUID, buffer.array())
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(uuid: UUID, data: ByteArray) {
        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(SERVICE_CLUSTER_UUID) ?: return
        val characteristic = service.getCharacteristic(uuid) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = data
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }
}
