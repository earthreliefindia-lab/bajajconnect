package com.openride.companion.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class NearbyCategory(
    val id: String,
    val name: String,
    val iconName: String,
    val searchQuery: String
)

object LocalSearchManager {

    private const val TAG = "LocalSearchManager"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    val QUICK_CATEGORIES = listOf(
        NearbyCategory("fuel", "Fuel Stations", "LocalGasStation", "petrol pump fuel"),
        NearbyCategory("ev", "EV Charging", "ElectricBike", "EV charging station"),
        NearbyCategory("repair", "Bike Repair", "Build", "motorcycle service garage"),
        NearbyCategory("food", "Food & Cafes", "Restaurant", "restaurant cafe food"),
        NearbyCategory("hospital", "Hospitals", "LocalHospital", "hospital medical emergency")
    )

    /**
     * Searches for places, addresses, shops, and landmarks matching the query.
     * Uses system Geocoder first, and falls back to Nominatim OSM API if needed.
     */
    suspend fun searchPlaces(
        context: Context,
        query: String,
        currentLocation: Location? = null,
        maxResults: Int = 10
    ): List<ParsedDestination> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        // 1. Try Android System Geocoder
        val systemResults = trySystemGeocoder(context, query, currentLocation, maxResults)
        if (systemResults.isNotEmpty()) {
            return@withContext systemResults
        }

        // 2. Fallback to OpenStreetMap Nominatim search engine
        val osmResults = searchNominatim(query, currentLocation, maxResults)
        if (osmResults.isNotEmpty()) {
            return@withContext osmResults
        }

        emptyList()
    }

    private suspend fun trySystemGeocoder(
        context: Context,
        query: String,
        currentLocation: Location?,
        maxResults: Int
    ): List<ParsedDestination> {
        if (!Geocoder.isPresent()) return emptyList()

        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocationName(query, maxResults, object : Geocoder.GeocodeListener {
                        override fun onGeocode(results: MutableList<Address>) {
                            continuation.resume(results)
                        }
                        override fun onError(errorMessage: String?) {
                            Log.w(TAG, "Geocoder error: $errorMessage")
                            continuation.resume(emptyList())
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, maxResults) ?: emptyList()
            }

            addresses.map { addr ->
                val title = addr.featureName
                    ?: addr.thoroughfare
                    ?: addr.locality
                    ?: query

                val fullAddress = (0..addr.maxAddressLineIndex)
                    .joinToString(", ") { addr.getAddressLine(it) }

                val distance = currentLocation?.let { current ->
                    val resultLocation = Location("").apply {
                        latitude = addr.latitude
                        longitude = addr.longitude
                    }
                    current.distanceTo(resultLocation)
                }

                ParsedDestination(
                    latitude = addr.latitude,
                    longitude = addr.longitude,
                    title = title,
                    address = fullAddress,
                    rawSource = query,
                    sourceApp = "Local Search",
                    distanceMeters = distance
                )
            }.sortedBy { it.distanceMeters ?: Float.MAX_VALUE }
        } catch (e: Exception) {
            Log.w(TAG, "System Geocoder failed for: $query. Will use OSM fallback.", e)
            emptyList()
        }
    }

    private fun searchNominatim(
        query: String,
        currentLocation: Location?,
        maxResults: Int
    ): List<ParsedDestination> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlBuilder = StringBuilder("https://nominatim.openstreetmap.org/search?format=json&q=$encoded&limit=$maxResults&addressdetails=1")

            // Add proximity bias if current location is known
            if (currentLocation != null) {
                val delta = 0.5 // roughly ~50km bounding box
                val left = currentLocation.longitude - delta
                val top = currentLocation.latitude + delta
                val right = currentLocation.longitude + delta
                val bottom = currentLocation.latitude - delta
                urlBuilder.append("&viewbox=$left,$top,$right,$bottom")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "OpenRideCompanion-Android/1.0 (bajajconnect@github.com)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string() ?: return emptyList()
            val jsonArray = JSONArray(body)
            val list = mutableListOf<ParsedDestination>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val lat = obj.optDouble("lat", 0.0)
                val lon = obj.optDouble("lon", 0.0)
                if (lat == 0.0 && lon == 0.0) continue

                val displayName = obj.optString("display_name", query)
                val parts = displayName.split(",")
                val title = parts.firstOrNull()?.trim() ?: query
                val address = if (parts.size > 1) parts.drop(1).joinToString(",").trim() else displayName

                val distance = currentLocation?.let { current ->
                    val resultLocation = Location("").apply {
                        latitude = lat
                        longitude = lon
                    }
                    current.distanceTo(resultLocation)
                }

                list.add(
                    ParsedDestination(
                        latitude = lat,
                        longitude = lon,
                        title = title,
                        address = address,
                        rawSource = query,
                        sourceApp = "Search",
                        distanceMeters = distance
                    )
                )
            }

            list.sortedBy { it.distanceMeters ?: Float.MAX_VALUE }
        } catch (e: Exception) {
            Log.e(TAG, "Nominatim search error for $query", e)
            emptyList()
        }
    }
}
