package com.openride.companion.model

data class TripRecord(
    val id: String,
    val title: String,
    val dateFormatted: String,
    val durationSeconds: Long,
    val distanceMeters: Float,
    val avgSpeedKmh: Float,
    val topSpeedKmh: Float,
    val fuelEfficiencyKmpl: Float,
    val startAddress: String,
    val endAddress: String
) {
    val formattedDistance: String
        get() = String.format("%.1f km", distanceMeters / 1000f)

    val formattedDuration: String
        get() {
            val mins = (durationSeconds / 60) % 60
            val hours = durationSeconds / 3600
            return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        }
}
