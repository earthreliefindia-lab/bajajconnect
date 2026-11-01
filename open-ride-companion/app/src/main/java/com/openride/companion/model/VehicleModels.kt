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

enum class RideMode(
    val title: String,
    val subtitle: String,
    val description: String,
    val colorHex: Long
) {
    ROAD(
        title = "Road",
        subtitle = "City & Highway",
        description = "Balanced on all kind of road surfaces, ideal for everyday riding with smooth performance.",
        colorHex = 0xFF00E5FF
    ),
    RAIN(
        title = "Rain",
        subtitle = "Wet & Slippery",
        description = "Smoother ABS with enhanced traction control and stability during wet weather conditions.",
        colorHex = 0xFF2979FF
    ),
    OFF_ROAD(
        title = "Off-Road",
        subtitle = "Gravel & Dirt",
        description = "Minimum stopping distance with adequate tire traction & braking stability on unpaved roads.",
        colorHex = 0xFFFF9100
    ),
    SPORT(
        title = "Sport",
        subtitle = "Dynamic Throttle",
        description = "Reduced ABS intervention to allow aggressive braking and maximum acceleration responsiveness.",
        colorHex = 0xFFFF3D00
    )
}

data class VehicleInfo(
    val modelName: String = "Bajaj Pulsar NS400Z",
    val vinNumber: String = "MD2A11CY4P8712391",
    val registrationNumber: String = "MH 12 TC 5590",
    val batteryPercentage: Int = 94,
    val batteryVoltage: Float = 12.8f,
    val fuelPercentage: Int = 72,
    val dteKm: Int = 285,
    val odoKm: Int = 4120,
    val nextServiceDueKm: Int = 880,
    val nextServiceDueDays: Int = 28,
    val currentRideMode: RideMode = RideMode.ROAD,
    val clusterType: ClusterType = ClusterType.CLUSTER_0265,
    val connectionStatus: BleConnectionStatus = BleConnectionStatus.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val signalRssi: Int = -65,
    val lastParkedLatitude: Double = 18.5204,
    val lastParkedLongitude: Double = 73.8567,
    val lastParkedAddress: String = "FC Road, Shivajinagar, Pune"
)

data class VehicleDocument(
    val id: String,
    val title: String,
    val documentNumber: String,
    val expiryDate: String,
    val isVerified: Boolean = true,
    val issuer: String
)

data class ServiceCenter(
    val id: String,
    val name: String,
    val address: String,
    val phone: String,
    val rating: Float,
    val distanceKm: Float
)
