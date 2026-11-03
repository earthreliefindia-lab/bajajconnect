package com.openride.companion.navigation.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight stored trip representation matching E2E test assertions.
 */
data class StoredTrip(
    val id: String,
    val title: String,
    val dateFormatted: String,
    val distanceKm: Float,
    val topSpeedKmh: Float,
    val fuelEfficiencyKmpl: Float,
    val startLocation: String,
    val endLocation: String,
    val photoUrls: List<String> = emptyList()
)

/**
 * Complete persisted record of a finished ride session.
 */
data class TripRecord(
    val id: String,
    val vehicleVin: String,
    val vehicleModelName: String,
    val title: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val distanceKm: Float,
    val durationSeconds: Long,
    val maxSpeedKmh: Float,
    val avgSpeedKmh: Float,
    val maxLeanAngleLeft: Float,
    val maxLeanAngleRight: Float,
    val hardBrakingCount: Int,
    val elevationGainMeters: Float,
    val fuelEfficiencyKmpl: Float = 42.0f,
    val startLocationName: String,
    val endLocationName: String,
    val photoUrls: List<String> = emptyList(),
    val userNote: String? = null,
    val breadcrumbPoints: List<BreadcrumbPoint> = emptyList(),
    val encodedPolyline: String = "",
    val hardBrakingEvents: List<HardBrakingEvent> = emptyList()
) {
    val dateFormatted: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
            return sdf.format(Date(startTimeMillis))
        }

    fun toStoredTrip(): StoredTrip {
        return StoredTrip(
            id = id,
            title = title,
            dateFormatted = dateFormatted,
            distanceKm = distanceKm,
            topSpeedKmh = maxSpeedKmh,
            fuelEfficiencyKmpl = fuelEfficiencyKmpl,
            startLocation = startLocationName,
            endLocation = endLocationName,
            photoUrls = photoUrls
        )
    }

    companion object {
        fun resolveTitle(title: String?, startLocation: String, endLocation: String): String {
            return if (!title.isNullOrBlank()) title else "$startLocation to $endLocation Ride"
        }
    }
}

/**
 * Curated scenic motorcycle tour template.
 */
data class CuratedTour(
    val id: String,
    val assetKey: String,
    val title: String,
    val subtitle: String,
    val distanceKm: Float,
    val estimatedDurationHours: Float,
    val difficulty: String, // "Easy", "Moderate", "Challenging", "Epic"
    val description: String,
    val scenicHighlights: List<String>,
    val startCity: String,
    val destinationCity: String
)

/**
 * Authoritative 5-tour curated catalog matching APK survey and E2E test suites.
 */
object CuratedToursCatalog {
    val tours: List<CuratedTour> = listOf(
        CuratedTour(
            id = "tour_kanyakumari",
            assetKey = "tripkanyakumari",
            title = "Southern Coastline Odyssey",
            subtitle = "Kanyakumari Coastal Run",
            distanceKm = 420.0f,
            estimatedDurationHours = 8.5f,
            difficulty = "Moderate",
            description = "Ride down the edge of the Indian peninsula along scenic coastal highways with sweeping sea breeze twisties.",
            scenicHighlights = listOf("Vivekananda Rock Memorial Viewpoint", "East Coast Highway Curve", "Pamban Bridge Gateway"),
            startCity = "Madurai",
            destinationCity = "Kanyakumari"
        ),
        CuratedTour(
            id = "tour_kashmir",
            assetKey = "tripkashmir",
            title = "Great Himalayan Expedition",
            subtitle = "Kashmir Valley to High Passes",
            distanceKm = 580.0f,
            estimatedDurationHours = 14.0f,
            difficulty = "Epic",
            description = "High altitude mountain passes, winding switchbacks, dramatic elevation gains up to 3,200m above sea level.",
            scenicHighlights = listOf("Zojila Pass Ascent", "Sonamarg Glacial Valley", "Dal Lake Boulevard"),
            startCity = "Jammu",
            destinationCity = "Srinagar"
        ),
        CuratedTour(
            id = "tour_kerala",
            assetKey = "tripkerala",
            title = "Munnar Tea Estate Hairpins",
            subtitle = "Western Ghats Mountain Twisties",
            distanceKm = 185.0f,
            estimatedDurationHours = 5.0f,
            difficulty = "Challenging",
            description = "Tight hairpin corners, 35° lean angles, mist-covered mountain tarmac through rolling emerald tea plantations.",
            scenicHighlights = listOf("Cheeyappara Waterfalls", "Lockhart Gap Viewpoint", "Top Station Peak"),
            startCity = "Kochi",
            destinationCity = "Munnar"
        ),
        CuratedTour(
            id = "tour_mumbai",
            assetKey = "tripmumbai",
            title = "Konkan Coastal Heritage Run",
            subtitle = "Mumbai to Goa via Coastal Highway",
            distanceKm = 540.0f,
            estimatedDurationHours = 11.0f,
            difficulty = "Challenging",
            description = "The definitive Indian motorcycle road trip along NH66 and the coastal ferry routes.",
            scenicHighlights = listOf("Alibaug Coastal Bypass", "Kashid Beach Straight", "Chiplun Ghats"),
            startCity = "Mumbai",
            destinationCity = "Goa"
        ),
        CuratedTour(
            id = "tour_agrafort",
            assetKey = "agrafort",
            title = "Taj Heritage Speed Run",
            subtitle = "Yamuna Expressway to Agra Fort",
            distanceKm = 210.0f,
            estimatedDurationHours = 3.0f,
            difficulty = "Easy",
            description = "High-speed cruising on India's premier multi-lane concrete expressway leading to Mughal heritage architecture.",
            scenicHighlights = listOf("Jewar High-Speed Sector", "Mathura Heritage Junction", "Yamuna River Bank"),
            startCity = "Noida",
            destinationCity = "Agra"
        )
    )

    val tourKeys: List<String> = tours.map { it.assetKey }
}
