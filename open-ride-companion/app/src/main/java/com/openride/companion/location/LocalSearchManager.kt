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
import org.json.JSONObject
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
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    val QUICK_CATEGORIES = listOf(
        NearbyCategory("fuel", "Fuel Stations", "LocalGasStation", "petrol pump fuel station"),
        NearbyCategory("ev", "EV Charging", "ElectricBike", "EV charging station"),
        NearbyCategory("repair", "Bike Service", "Build", "bajaj motorcycle service repair"),
        NearbyCategory("food", "Food & Cafes", "Restaurant", "restaurant cafe dhaba"),
        NearbyCategory("hospital", "Hospitals", "LocalHospital", "hospital clinic emergency")
    )

    /**
     * Searches for places matching the query, biased around rider's current location.
     * Uses Photon (Komoot OSM) first for ultra-fast, robust results, then Nominatim, then Geocoder.
     */
    suspend fun searchPlaces(
        context: Context,
        query: String,
        currentLocation: Location? = null,
        maxResults: Int = 10
    ): List<ParsedDestination> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        // 1. Photon by Komoot (blazing fast, worldwide OSM data, location-biased)
        val photonResults = searchPhoton(trimmed, currentLocation, maxResults)
        if (photonResults.isNotEmpty()) {
            return@withContext photonResults
        }

        // 2. OpenStreetMap Nominatim
        val osmResults = searchNominatim(trimmed, currentLocation, maxResults)
        if (osmResults.isNotEmpty()) {
            return@withContext osmResults
        }

        // 3. Android System Geocoder fallback
        val systemResults = trySystemGeocoder(context, trimmed, currentLocation, maxResults)
        if (systemResults.isNotEmpty()) {
            return@withContext systemResults
        }

        emptyList()
    }

    /**
     * Photon Search API (OSM powered, fast & reliable).
     */
    private fun searchPhoton(
        query: String,
        currentLocation: Location?,
        maxResults: Int
    ): List<ParsedDestination> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlBuilder = StringBuilder("https://photon.komoot.io/api/?q=$encoded&limit=$maxResults")

            if (currentLocation != null) {
                urlBuilder.append("&lat=${currentLocation.latitude}&lon=${currentLocation.longitude}")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "OpenRideCompanion/1.0 (Android; BajajConnect)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string() ?: return emptyList()
            val root = JSONObject(body)
            val features = root.optJSONArray("features") ?: return emptyList()
            val list = mutableListOf<ParsedDestination>()

            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val geometry = feature.optJSONObject("geometry") ?: continue
                val coords = geometry.optJSONArray("coordinates") ?: continue
                val lon = coords.optDouble(0, 0.0)
                val lat = coords.optDouble(1, 0.0)
                if (lat == 0.0 && lon == 0.0) continue

                val props = feature.optJSONObject("properties") ?: JSONObject()
                val name = props.optString("name", "").ifBlank {
                    props.optString("street", "").ifBlank { query }
                }

                val city = props.optString("city", "").ifBlank {
                    props.optString("state", "")
                }
                val country = props.optString("country", "")
                val addressParts = listOf(
                    props.optString("housenumber", ""),
                    props.optString("street", ""),
                    props.optString("district", ""),
                    city,
                    props.optString("postcode", ""),
                    country
                ).filter { it.isNotBlank() }
                val address = if (addressParts.isNotEmpty()) addressParts.joinToString(", ") else name

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
                        title = name,
                        address = address,
                        rawSource = query,
                        sourceApp = "Photon OSM",
                        distanceMeters = distance
                    )
                )
            }

            list.sortedBy { it.distanceMeters ?: Float.MAX_VALUE }
        } catch (e: Exception) {
            Log.w(TAG, "Photon search error: ${e.message}")
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

            if (currentLocation != null) {
                val delta = 0.5
                val left = currentLocation.longitude - delta
                val top = currentLocation.latitude + delta
                val right = currentLocation.longitude + delta
                val bottom = currentLocation.latitude - delta
                urlBuilder.append("&viewbox=$left,$top,$right,$bottom")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "OpenRideCompanion/1.0 (contact@bajajrideconnect.org)")
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
                        sourceApp = "Nominatim",
                        distanceMeters = distance
                    )
                )
            }

            list.sortedBy { it.distanceMeters ?: Float.MAX_VALUE }
        } catch (e: Exception) {
            Log.w(TAG, "Nominatim search error: ${e.message}")
            emptyList()
        }
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
                    sourceApp = "System Geocoder",
                    distanceMeters = distance
                )
            }.sortedBy { it.distanceMeters ?: Float.MAX_VALUE }
        } catch (e: Exception) {
            Log.w(TAG, "System Geocoder failed: ${e.message}")
            emptyList()
        }
    }
}
