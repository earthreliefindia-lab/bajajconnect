package com.openride.companion.model

enum class ClusterType(val displayName: String, val hardwareId: String) {
    CLUSTER_2763("Pulsar / Dominar LCD (Type 2763)", "Cluster2763"),
    CLUSTER_0265("Full TFT Color Display (Type 0265)", "Cluster0265")
}

enum class BleConnectionStatus {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

data class VehicleInfo(
    val modelName: String = "Bajaj Pulsar N160",
    val vinNumber: String = "MD2A11CY4P1234567",
    val registrationNumber: String = "MH 12 AB 1234",
    val batteryPercentage: Int = 92,
    val batteryVoltage: Float = 12.8f,
    val fuelPercentage: Int = 68,
    val odoKm: Int = 3842,
    val nextServiceDueKm: Int = 1158,
    val nextServiceDueDays: Int = 42,
    val clusterType: ClusterType = ClusterType.CLUSTER_2763,
    val connectionStatus: BleConnectionStatus = BleConnectionStatus.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val signalRssi: Int = -65
)
