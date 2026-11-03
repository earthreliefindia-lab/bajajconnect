package com.openride.companion.navigation.model

import kotlin.math.ceil

/**
 * High-level route alternative option for selection list.
 */
data class RouteOption(
    val id: String,
    val routeName: String,
    val distanceKm: Float,
    val etaMinutes: Int,
    val hasTolls: Boolean,
    val waypoints: List<GeoPoint> = emptyList()
)

/**
 * Route optimization strategy types.
 */
enum class RouteAlternativeType {
    FASTEST,
    SHORTEST,
    ECO
}

/**
 * Ordered waypoint along a route.
 */
data class Waypoint(
    val id: String,
    val name: String,
    val coordinate: GeoPoint,
    val orderIndex: Int,
    val isVisited: Boolean = false
)

/**
 * Single turn-by-turn guidance step.
 */
data class NavigationStep(
    val stepIndex: Int,
    val maneuverCode: Int,           // 0..21 matching TbtManeuvers
    val roundaboutExit: Int = 0,     // 1..7 for roundabouts, 0 otherwise
    val streetName: String,          // Next street name
    val startCoordinate: GeoPoint,
    val endCoordinate: GeoPoint,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val voiceInstruction: String
)

/**
 * Full navigational route model with detailed polyline and guidance steps.
 */
data class NavigationRoute(
    val id: String,
    val name: String,
    val alternativeType: RouteAlternativeType,
    val polyline: List<GeoPoint>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val steps: List<NavigationStep>,
    val waypoints: List<GeoPoint> = emptyList(),
    val hasTolls: Boolean = false,
    val elevationGainMeters: Float = 0f
) {
    val distanceKm: Float get() = distanceMeters / 1000f
    val etaMinutes: Int get() = ceil(durationSeconds / 60.0).toInt()

    fun toRouteOption(): RouteOption {
        return RouteOption(
            id = id,
            routeName = name,
            distanceKm = distanceKm,
            etaMinutes = etaMinutes,
            hasTolls = hasTolls,
            waypoints = waypoints
        )
    }
}
