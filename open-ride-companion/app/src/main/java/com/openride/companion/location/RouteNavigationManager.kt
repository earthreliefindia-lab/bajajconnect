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
    val steps: List<RouteStep>
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
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Calculates driving route from start to destination using OSRM routing engine.
     */
    suspend fun calculateRoute(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double
    ): RouteResult? = withContext(Dispatchers.IO) {
        try {
            val url = "https://router.project-osrm.org/route/v1/driving/$startLng,$startLat;$destLng,$destLat?overview=full&geometries=geojson&steps=true"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OpenRideCompanion/1.0 (bajajconnect@github.com)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "OSRM routing failed with HTTP code ${response.code}")
                return@withContext createStraightLineFallback(startLat, startLng, destLat, destLng)
            }

            val body = response.body?.string() ?: return@withContext null
            val root = JSONObject(body)
            val routes = root.optJSONArray("routes")
            if (routes == null || routes.length() == 0) {
                return@withContext createStraightLineFallback(startLat, startLng, destLat, destLng)
            }

            val route = routes.getJSONObject(0)
            val distance = route.optDouble("distance", 0.0).toFloat()
            val duration = route.optDouble("duration", 0.0).toLong()

            // Coordinates: GeoJSON is [lon, lat]
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

            // Steps
            val stepsList = mutableListOf<RouteStep>()
            val legs = route.optJSONArray("legs")
            if (legs != null && legs.length() > 0) {
                val leg = legs.getJSONObject(0)
                val steps = leg.optJSONArray("steps")
                if (steps != null) {
                    for (i in 0 until steps.length()) {
                        val s = steps.getJSONObject(i)
                        val stepDistance = s.optDouble("distance", 0.0).toInt()
                        val stepDuration = s.optDouble("duration", 0.0).toLong()
                        val street = s.optString("name", "Road")
                        val maneuverObj = s.optJSONObject("maneuver")

                        val typeStr = maneuverObj?.optString("type", "") ?: ""
                        val modifier = maneuverObj?.optString("modifier", "") ?: ""

                        val (mType, text) = parseManeuver(typeStr, modifier, street, stepDistance)

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
                totalDurationSeconds = duration,
                coordinates = if (coordinates.isNotEmpty()) coordinates else listOf(Pair(startLat, startLng), Pair(destLat, destLng)),
                steps = stepsList
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate route via OSRM", e)
            createStraightLineFallback(startLat, startLng, destLat, destLng)
        }
    }

    private fun parseManeuver(type: String, modifier: String, street: String, distance: Int): Pair<ManeuverType, String> {
        val streetName = if (street.isBlank()) "Road" else street
        val distStr = if (distance >= 1000) String.format("%.1f km", distance / 1000f) else "$distance m"

        return when {
            type == "depart" -> Pair(ManeuverType.DEPART, "Head towards $streetName")
            type == "arrive" -> Pair(ManeuverType.ARRIVE, "You will arrive at destination")
            type == "roundabout" -> Pair(ManeuverType.ROUNDABOUT, "At roundabout, take exit to $streetName")
            modifier.contains("u-turn") -> Pair(ManeuverType.U_TURN, "Make a U-turn onto $streetName")
            modifier == "sharp left" || modifier == "left" -> Pair(ManeuverType.TURN_LEFT, "Turn left onto $streetName")
            modifier == "slight left" -> Pair(ManeuverType.SLIGHT_LEFT, "Slight left onto $streetName")
            modifier == "sharp right" || modifier == "right" -> Pair(ManeuverType.TURN_RIGHT, "Turn right onto $streetName")
            modifier == "slight right" -> Pair(ManeuverType.SLIGHT_RIGHT, "Slight right onto $streetName")
            else -> Pair(ManeuverType.STRAIGHT, "Continue on $streetName")
        }
    }

    private fun createStraightLineFallback(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double
    ): RouteResult {
        val startLoc = Location("").apply { latitude = startLat; longitude = startLng }
        val destLoc = Location("").apply { latitude = destLat; longitude = destLng }
        val dist = startLoc.distanceTo(destLoc)
        val speedKmh = 40f
        val estDuration = ((dist / 1000f) / speedKmh * 3600f).toLong()

        return RouteResult(
            totalDistanceMeters = dist,
            totalDurationSeconds = estDuration,
            coordinates = listOf(Pair(startLat, startLng), Pair(destLat, destLng)),
            steps = listOf(
                RouteStep(
                    maneuverType = ManeuverType.STRAIGHT,
                    instruction = "Proceed towards destination",
                    streetName = "Direct Route",
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
            )
        )
    }
}
