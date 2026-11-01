package com.openride.companion.location

/**
 * Data model representing a target destination parsed from an intent, link, or search query.
 */
data class ParsedDestination(
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val address: String? = null,
    val rawSource: String? = null,
    val sourceApp: String = "App Search",
    val distanceMeters: Float? = null
) {
    val formattedCoordinates: String
        get() = String.format("%.5f, %.5f", latitude, longitude)

    val formattedDistance: String?
        get() = distanceMeters?.let { meters ->
            if (meters >= 1000) {
                String.format("%.1f km", meters / 1000f)
            } else {
                String.format("%.0f m", meters)
            }
        }
}
