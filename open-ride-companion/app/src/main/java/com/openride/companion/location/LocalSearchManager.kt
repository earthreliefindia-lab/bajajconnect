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
import java.util.Locale
import kotlin.coroutines.resume

data class NearbyCategory(
    val id: String,
    val name: String,
    val iconName: String,
    val searchQuery: String
)

object LocalSearchManager {

    private const val TAG = "LocalSearchManager"

    val QUICK_CATEGORIES = listOf(
        NearbyCategory("fuel", "Fuel Stations", "LocalGasStation", "Petrol Pump Fuel"),
        NearbyCategory("ev", "EV Charging", "ElectricBike", "EV Charging Station"),
        NearbyCategory("repair", "Bike Repair", "Build", "Motorcycle Repair Puncture"),
        NearbyCategory("food", "Food & Cafes", "Restaurant", "Restaurant Cafe Food"),
        NearbyCategory("hospital", "Hospitals", "LocalHospital", "Hospital Medical Clinic")
    )

    /**
     * Searches for places, addresses, shops, and landmarks matching the query.
     * Computes distance relative to the user's current location and sorts results by proximity.
     */
    suspend fun searchPlaces(
        context: Context,
        query: String,
        currentLocation: Location? = null,
        maxResults: Int = 8
    ): List<ParsedDestination> = withContext(Dispatchers.IO) {
        if (query.isBlank() || !Geocoder.isPresent()) return@withContext emptyList()

        try {
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
            Log.e(TAG, "Local search failed for query: $query", e)
            emptyList()
        }
    }
}
