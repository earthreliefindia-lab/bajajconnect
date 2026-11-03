package com.openride.companion.navigation.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geographic coordinate pair with strict validation and great-circle calculations.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude out of range [-90, 90]: $latitude" }
        require(longitude in -180.0..180.0) { "Longitude out of range [-180, 180]: $longitude" }
    }

    /**
     * Accurate Great-Circle distance using the Haversine formula in meters.
     */
    fun distanceTo(other: GeoPoint): Double {
        val r = 6371000.0 // Earth radius in meters
        val lat1Rad = Math.toRadians(latitude)
        val lat2Rad = Math.toRadians(other.latitude)
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)

        val a = sin(dLat / 2).let { it * it } +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(dLon / 2).let { it * it }
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Initial bearing from this point to destination in degrees [0..360).
     */
    fun bearingTo(other: GeoPoint): Float {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLon = Math.toRadians(other.longitude - longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return (((deg + 360.0) % 360.0)).toFloat()
    }

    /**
     * Computes the shortest perpendicular distance from this point to a line segment [p1, p2] in meters.
     */
    fun distanceToSegment(p1: GeoPoint, p2: GeoPoint): Double {
        val segmentLength = p1.distanceTo(p2)
        if (segmentLength < 1.0) return distanceTo(p1)

        val x0 = longitude
        val y0 = latitude
        val x1 = p1.longitude
        val y1 = p1.latitude
        val x2 = p2.longitude
        val y2 = p2.latitude

        val dx = x2 - x1
        val dy = y2 - y1
        val t = (((x0 - x1) * dx + (y0 - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)

        val projLon = x1 + t * dx
        val projLat = y1 + t * dy
        return distanceTo(GeoPoint(projLat, projLon))
    }
}
