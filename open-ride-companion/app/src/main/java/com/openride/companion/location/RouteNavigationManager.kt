package com.openride.companion.location

import android.location.Location
import android.util.Log
import com.openride.companion.navigation.ble.TbtManeuvers
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
    val maneuverCode: Int,
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

    suspend fun calculateRoute(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double
    ): RouteResult? = withContext(Dispatchers.IO) {
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

                        val (mCode, text) = parseBikeManeuver(typeStr, modifier, street)

                        stepsList.add(
                            RouteStep(
                                maneuverCode = mCode,
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

    private fun parseBikeManeuver(type: String, modifier: String, street: String): Pair<Int, String> {
        val streetName = if (street.isBlank()) "Road" else street

        return when {
            type == "depart" -> Pair(TbtManeuvers.STRAIGHT, "Start bike ride onto $streetName")
            type == "arrive" -> Pair(TbtManeuvers.DESTINATION_REACHED, "Arrive at destination")
            type == "roundabout" -> Pair(TbtManeuvers.ROUNDABOUT_RIGHT, "At roundabout, proceed onto $streetName")
            modifier.contains("u-turn") -> Pair(TbtManeuvers.U_TURN_LEFT, "Make a U-turn onto $streetName")
            modifier == "sharp left" -> Pair(TbtManeuvers.TURN_SHARP_LEFT, "Sharp left onto $streetName")
            modifier == "left" -> Pair(TbtManeuvers.TURN_LEFT, "Turn left onto $streetName")
            modifier == "slight left" -> Pair(TbtManeuvers.TURN_SLIGHT_LEFT, "Slight left onto $streetName")
            modifier == "sharp right" -> Pair(TbtManeuvers.TURN_SHARP_RIGHT, "Sharp right onto $streetName")
            modifier == "right" -> Pair(TbtManeuvers.TURN_RIGHT, "Turn right onto $streetName")
            modifier == "slight right" -> Pair(TbtManeuvers.TURN_SLIGHT_RIGHT, "Slight right onto $streetName")
            else -> Pair(TbtManeuvers.STRAIGHT, "Continue straight on $streetName")
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
                    maneuverCode = TbtManeuvers.STRAIGHT,
                    instruction = "Proceed on bike route",
                    streetName = "Motorcycle Route",
                    distanceMeters = dist.toInt(),
                    durationSeconds = estDuration
                ),
                RouteStep(
                    maneuverCode = TbtManeuvers.DESTINATION_REACHED,
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
