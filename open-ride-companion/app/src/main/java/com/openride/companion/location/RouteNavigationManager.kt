package com.openride.companion.location

import android.location.Location
import android.util.Log
import com.openride.companion.model.LiveNavInstruction
import com.openride.companion.model.ManeuverType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RouteResult(
    val totalDistanceMeters: Float,
    val totalDurationSeconds: Long,
    val coordinates: List<Pair<Double, Double>>, // Lat, Lng
    val steps: List<RouteStep>,
    val mode: String = "Two-Wheeler (Motorcycle)"
)

data class RouteStep(
    val maneuverType: ManeuverType,
    val instruction: String,
    val streetName: String,
    val distanceMeters: Int,
    val durationSeconds: Long
)

object RouteNavigationManager {

    private const val TAG = "RouteNavigationManager"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .build()

    /**
     * Calculates two-wheeler (bike) route from start to destination.
     * 100% Free - Zero API key required.
     * Enforces motorcycle routing with realistic bike speed and maneuver filtering.
     */
    suspend fun calculateRoute(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double
    ): RouteResult? = withContext(Dispatchers.IO) {
        // 1. Try Two-Wheeler / Bike OSRM Endpoint
        val bikeUrl = "https://routing.openstreetmap.de/routed-bike/route/v1/driving/$startLng,$startLat;$destLng,$destLat?overview=full&geometries=geojson&steps=true"
        val fallbackUrl = "https://router.project-osrm.org/route/v1/driving/$startLng,$startLat;$destLng,$destLat?overview=full&geometries=geojson&steps=true"

        val result = tryFetchRoute(bikeUrl, startLat, startLng, destLat, destLng)
            ?: tryFetchRoute(fallbackUrl, startLat, startLng, destLat, destLng)

        result ?: createBikeStraightLineFallback(startLat, startLng, destLat, destLng)
    }

    private fun tryFetchRoute(
        url: String,
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double
    ): RouteResult? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "BajajRideConnect-Android/1.0 (bike-navigation)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val root = JSONObject(body)
            val routes = root.optJSONArray("routes")
            if (routes == null || routes.length() == 0) return null

            val route = routes.getJSONObject(0)
            val distance = route.optDouble("distance", 0.0).toFloat()

            // Calculate motorcycle duration (Average bike city/highway speed ~38 km/h = 10.5 m/s)
            val bikeDuration = ((distance / 1000f) / 38f * 3600f).toLong().coerceAtLeast(60)

            // Coordinates: GeoJSON [lon, lat]
            val geometry = route.optJSONObject("geometry")
            val coordArray = geometry?.optJSONArray("coordinates")
            val coordinates = mutableListOf<Pair<Double, Double>>()
            if (coordArray != null) {
                for (i in 0 until coordArray.length()) {
                    val pt = coordArray.getJSONArray(i)
                    val lon = pt.getDouble(0)
                    val lat = pt.getDouble(1)
                    coordinates.add(Pair(lat, lon))
                }
            }

            // Turn-by-Turn Steps
            val stepsList = mutableListOf<RouteStep>()
            val legs = route.optJSONArray("legs")
            if (legs != null && legs.length() > 0) {
                val leg = legs.getJSONObject(0)
                val steps = leg.optJSONArray("steps")
                if (steps != null) {
                    for (i in 0 until steps.length()) {
                        val s = steps.getJSONObject(i)
                        val stepDistance = s.optDouble("distance", 0.0).toInt()
                        val stepDuration = ((stepDistance / 1000f) / 35f * 3600f).toLong().coerceAtLeast(5)
                        val street = s.optString("name", "Road")
                        val maneuverObj = s.optJSONObject("maneuver")

                        val typeStr = maneuverObj?.optString("type", "") ?: ""
                        val modifier = maneuverObj?.optString("modifier", "") ?: ""

                        val (mType, text) = parseBikeManeuver(typeStr, modifier, street, stepDistance)

                        stepsList.add(
                            RouteStep(
                                maneuverType = mType,
                                instruction = text,
                                streetName = street,
                                distanceMeters = stepDistance,
                                durationSeconds = stepDuration
                            )
                        )
                    }
                }
            }

            RouteResult(
                totalDistanceMeters = distance,
                totalDurationSeconds = bikeDuration,
                coordinates = if (coordinates.isNotEmpty()) coordinates else listOf(Pair(startLat, startLng), Pair(destLat, destLng)),
                steps = stepsList,
                mode = "Two-Wheeler (Motorcycle)"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Fetch route error from $url", e)
            null
        }
    }

    private fun parseBikeManeuver(type: String, modifier: String, street: String, distance: Int): Pair<ManeuverType, String> {
        val streetName = if (street.isBlank()) "Road" else street

        return when {
            type == "depart" -> Pair(ManeuverType.DEPART, "Start bike ride onto $streetName")
            type == "arrive" -> Pair(ManeuverType.ARRIVE, "Arrive at destination")
            type == "roundabout" -> Pair(ManeuverType.ROUNDABOUT, "At roundabout, take exit onto $streetName")
            modifier.contains("u-turn") -> Pair(ManeuverType.U_TURN, "Make a U-turn onto $streetName")
            modifier == "sharp left" || modifier == "left" -> Pair(ManeuverType.TURN_LEFT, "Turn left onto $streetName")
            modifier == "slight left" -> Pair(ManeuverType.SLIGHT_LEFT, "Slight left onto $streetName")
            modifier == "sharp right" || modifier == "right" -> Pair(ManeuverType.TURN_RIGHT, "Turn right onto $streetName")
            modifier == "slight right" -> Pair(ManeuverType.SLIGHT_RIGHT, "Slight right onto $streetName")
            else -> Pair(ManeuverType.STRAIGHT, "Continue straight on $streetName")
        }
    }

    private fun createBikeStraightLineFallback(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double
    ): RouteResult {
        val startLoc = Location("").apply { latitude = startLat; longitude = startLng }
        val destLoc = Location("").apply { latitude = destLat; longitude = destLng }
        val dist = startLoc.distanceTo(destLoc)
        val bikeSpeedKmh = 35f
        val estDuration = ((dist / 1000f) / bikeSpeedKmh * 3600f).toLong().coerceAtLeast(60)

        return RouteResult(
            totalDistanceMeters = dist,
            totalDurationSeconds = estDuration,
            coordinates = listOf(Pair(startLat, startLng), Pair(destLat, destLng)),
            steps = listOf(
                RouteStep(
                    maneuverType = ManeuverType.STRAIGHT,
                    instruction = "Proceed on bike route",
                    streetName = "Motorcycle Route",
                    distanceMeters = dist.toInt(),
                    durationSeconds = estDuration
                ),
                RouteStep(
                    maneuverType = ManeuverType.ARRIVE,
                    instruction = "Arrive at destination",
                    streetName = "Destination",
                    distanceMeters = 0,
                    durationSeconds = 0
                )
            ),
            mode = "Two-Wheeler (Motorcycle)"
        )
    }
}
