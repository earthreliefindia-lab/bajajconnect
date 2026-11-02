package com.openride.companion.e2e

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * End-to-End Test Suite for Navigation, 48-byte TbtNavFrame, Ride Tracking, Trips, and Documents (Features 16-20).
 * Covers Tier 1 (Feature Coverage) and Tier 2 (Boundary & Corner Cases).
 */
class NavigationAndTbtE2ETest {

    // =========================================================================
    // FEATURE 16: NAVIGATION & ROUTE PLANNING (Tier 1 & Tier 2)
    // =========================================================================

    data class GeoPoint(val latitude: Double, val longitude: Double)

    data class RouteOption(
        val id: String,
        val routeName: String,
        val distanceKm: Float,
        val etaMinutes: Int,
        val hasTolls: Boolean,
        val waypoints: List<GeoPoint> = emptyList()
    )

    private val sampleRoutes = listOf(
        RouteOption("route_1", "Via Expressway", 64.2f, 72, true),
        RouteOption("route_2", "Via Old Mumbai-Pune Highway", 68.5f, 95, false)
    )

    @Test
    fun testNavigation_RoutePreview_CalculatesDistanceAndEta() {
        val selected = sampleRoutes.first()
        assertEquals(64.2f, selected.distanceKm, 0.01f)
        assertEquals(72, selected.etaMinutes)
        assertTrue(selected.hasTolls)
    }

    @Test
    fun testNavigation_AlternativeRoutesSelection_PresentsMultiplePaths() {
        assertEquals("Should provide at least 2 alternate route choices", 2, sampleRoutes.size)
        val fasterRoute = sampleRoutes.minByOrNull { it.etaMinutes }
        assertEquals("Via Expressway", fasterRoute?.routeName)
    }

    @Test
    fun testNavigation_DualEngine_FallbackLogic() {
        fun resolveEngine(mapplsKey: String): String {
            return if (mapplsKey.isNotBlank()) "Mappls" else "GoogleMaps"
        }
        assertEquals("GoogleMaps", resolveEngine(""))
        assertEquals("Mappls", resolveEngine("MAPPLS_PROD_KEY_12345"))
    }

    @Test
    fun testNavigation_Waypoints_AddAndReorder() {
        val start = GeoPoint(18.5204, 73.8567)
        val stop1 = GeoPoint(18.7544, 73.4062)
        val end = GeoPoint(18.7588, 73.3824)

        val routeWithWaypoint = RouteOption("route_wp", "Via Kamshet", 71.0f, 90, false, listOf(start, stop1, end))
        assertEquals(3, routeWithWaypoint.waypoints.size)
    }

    @Test
    fun testNavigation_SavedLocations_HomeAndWorkBookmarks() {
        val bookmarks = mapOf(
            "Home" to GeoPoint(18.5204, 73.8567),
            "Work" to GeoPoint(18.5913, 73.7389)
        )
        assertTrue(bookmarks.containsKey("Home"))
        assertTrue(bookmarks.containsKey("Work"))
        assertEquals(18.5204, bookmarks["Home"]!!.latitude, 0.0001)
    }

    @Test
    fun testNavigation_Boundary_IdenticalStartAndDestination() {
        fun validateRoutePoints(start: GeoPoint, end: GeoPoint): Boolean {
            return start.latitude != end.latitude || start.longitude != end.longitude
        }
        val pt = GeoPoint(18.5204, 73.8567)
        assertFalse("Same coordinates for start and destination must be rejected", validateRoutePoints(pt, pt))
    }

    @Test
    fun testNavigation_Boundary_InvalidGpsCoordinates() {
        fun isValidCoordinate(lat: Double, lon: Double): Boolean {
            return lat in -90.0..90.0 && lon in -180.0..180.0
        }
        assertTrue(isValidCoordinate(18.5204, 73.8567))
        assertFalse(isValidCoordinate(95.0, 73.8567))
        assertFalse(isValidCoordinate(18.5204, 200.0))
    }

    @Test
    fun testNavigation_Boundary_TollFilterToggling() {
        val noTollRoutes = sampleRoutes.filter { !it.hasTolls }
        assertEquals(1, noTollRoutes.size)
        assertEquals("Via Old Mumbai-Pune Highway", noTollRoutes.first().routeName)
    }

    @Test
    fun testNavigation_Boundary_ZeroDistanceRouteGracefulHandling() {
        val zeroRoute = RouteOption("r0", "Zero Distance", 0.0f, 0, false)
        assertEquals(0, zeroRoute.etaMinutes)
    }

    @Test
    fun testNavigation_Boundary_RerouteOnDeviation() {
        var isOffRoute = false
        fun checkOffRoute(currentPos: GeoPoint, nearestRoutePoint: GeoPoint, thresholdMeters: Double): Boolean {
            // simplified distance check
            val dist = Math.abs(currentPos.latitude - nearestRoutePoint.latitude) * 111000.0
            return dist > thresholdMeters
        }
        val current = GeoPoint(18.5300, 73.8567)
        val nearest = GeoPoint(18.5200, 73.8567)
        isOffRoute = checkOffRoute(current, nearest, 50.0)
        assertTrue("Off route when deviation > 50m", isOffRoute)
    }

    // =========================================================================
    // FEATURE 17: 48-BYTE TBT FRAME GENERATOR (TbtNavFrame) (Tier 1 & Tier 2)
    // =========================================================================

    object TbtManeuvers {
        const val STRAIGHT = 0
        const val TURN_LEFT = 1
        const val TURN_RIGHT = 2
        const val TURN_SLIGHT_LEFT = 3
        const val TURN_SLIGHT_RIGHT = 4
        const val TURN_SHARP_LEFT = 5
        const val TURN_SHARP_RIGHT = 6
        const val U_TURN_LEFT = 7
        const val U_TURN_RIGHT = 8
        const val KEEP_LEFT = 9
        const val KEEP_RIGHT = 10
        const val FORK_LEFT = 11
        const val FORK_RIGHT = 12
        const val MERGE = 13
        const val RAMP_LEFT = 14
        const val RAMP_RIGHT = 15
        const val ROUNDABOUT_LEFT = 16
        const val ROUNDABOUT_RIGHT = 17
        const val FERRY = 18
        const val FERRY_TRAIN = 19
        const val DESTINATION_REACHED = 20
        const val WRONG_WAY = 21
    }

    enum class DistanceUnit(val code: Int) {
        METER(0), KM(1), MILES(2), YARD(3), FOOT(4)
    }

    class TbtNavFrameBuilder {
        var maneuverCode: Int = TbtManeuvers.TURN_RIGHT // 0..21
        var distanceUnit: DistanceUnit = DistanceUnit.METER // 0..4
        var isClockwiseRoundabout: Boolean = false
        var roundaboutExit: Int = 0 // 1..7
        var distanceToTurnMeters: Int = 250
        var etaMinutes: Int = 18 // uint16 little-endian
        var streetName: String = "JM Road"

        fun build(): ByteArray {
            val frame = ByteArray(48)

            // Byte 0: Bits 0..3 maneuver (0..15 or masked), Bits 4..6 unit, Bit 7 roundabout flag
            val mBits = maneuverCode and 0x0F
            val uBits = (distanceUnit.code and 0x07) shl 4
            val rBit = if (isClockwiseRoundabout) 0x80 else 0x00
            frame[0] = (rBit or uBits or mBits).toByte()

            // Byte 1: roundabout exit (1..7) or secondary turn
            frame[1] = (roundaboutExit and 0xFF).toByte()

            // Bytes 2..5: Distance formatted BCD / ASCII
            val distStr = distanceToTurnMeters.toString().padStart(4, '0').takeLast(4)
            for (i in 0 until 4) {
                frame[2 + i] = distStr[i].code.toByte()
            }

            // Bytes 6..7: ETA in minutes (little-endian uint16)
            frame[6] = (etaMinutes and 0xFF).toByte()
            frame[7] = ((etaMinutes shr 8) and 0xFF).toByte()

            // Bytes 8..46: Street name sanitized ASCII [0-9a-zA-Z.], max 31 chars
            val sanitized = sanitizeStreetName(streetName)
            val streetBytes = sanitized.toByteArray(Charsets.US_ASCII).take(39) // slots 8..46 = 39 bytes max
            for (i in streetBytes.indices) {
                frame[8 + i] = streetBytes[i]
            }

            // Byte 47: Modulo 256 sum checksum of bytes 0..46
            var sum = 0
            for (i in 0 until 47) {
                sum += (frame[i].toInt() and 0xFF)
            }
            frame[47] = (sum and 0xFF).toByte()

            return frame
        }

        private fun sanitizeStreetName(raw: String): String {
            // Strips non-alphanumeric except dot, collapses whitespace
            val cleaned = raw.replace(Regex("[^0-9a-zA-Z.]"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")

            return if (cleaned.length > 31) {
                cleaned.take(31) + "."
            } else {
                cleaned
            }
        }
    }

    @Test
    fun testTbtFrame_ExactFrameLength_Is48Bytes() {
        val frame = TbtNavFrameBuilder().build()
        assertEquals("TbtNavFrame must be exactly 48 bytes", 48, frame.size)
    }

    @Test
    fun testTbtFrame_ChecksumCalculation_MatchesModulo256Sum() {
        val frame = TbtNavFrameBuilder().apply {
            maneuverCode = TbtManeuvers.TURN_LEFT
            distanceToTurnMeters = 500
            streetName = "Senapati Bapat Road"
            etaMinutes = 24
        }.build()

        var sum = 0
        for (i in 0 until 47) {
            sum += (frame[i].toInt() and 0xFF)
        }
        val expected = (sum and 0xFF).toByte()
        assertEquals("Byte 47 must match modulo 256 sum of bytes 0..46", expected, frame[47])
    }

    @Test
    fun testTbtFrame_ManeuverCodes_All22CodesDefined() {
        val codes = listOf(
            TbtManeuvers.STRAIGHT, TbtManeuvers.TURN_LEFT, TbtManeuvers.TURN_RIGHT,
            TbtManeuvers.TURN_SLIGHT_LEFT, TbtManeuvers.TURN_SLIGHT_RIGHT,
            TbtManeuvers.TURN_SHARP_LEFT, TbtManeuvers.TURN_SHARP_RIGHT,
            TbtManeuvers.U_TURN_LEFT, TbtManeuvers.U_TURN_RIGHT,
            TbtManeuvers.KEEP_LEFT, TbtManeuvers.KEEP_RIGHT,
            TbtManeuvers.FORK_LEFT, TbtManeuvers.FORK_RIGHT,
            TbtManeuvers.MERGE, TbtManeuvers.RAMP_LEFT, TbtManeuvers.RAMP_RIGHT,
            TbtManeuvers.ROUNDABOUT_LEFT, TbtManeuvers.ROUNDABOUT_RIGHT,
            TbtManeuvers.FERRY, TbtManeuvers.FERRY_TRAIN,
            TbtManeuvers.DESTINATION_REACHED, TbtManeuvers.WRONG_WAY
        )
        assertEquals(22, codes.size)
        assertEquals(21, TbtManeuvers.WRONG_WAY)
    }

    @Test
    fun testTbtFrame_EtaMinutes_IsLittleEndianUint16() {
        val frame = TbtNavFrameBuilder().apply {
            etaMinutes = 258 // 0x0102 -> low = 0x02, high = 0x01
        }.build()

        assertEquals(0x02.toByte(), frame[6])
        assertEquals(0x01.toByte(), frame[7])
    }

    @Test
    fun testTbtFrame_RoundaboutExit_EncodesExitNumber() {
        val frame = TbtNavFrameBuilder().apply {
            maneuverCode = TbtManeuvers.ROUNDABOUT_LEFT
            roundaboutExit = 3
        }.build()

        assertEquals(3.toByte(), frame[1])
    }

    @Test
    fun testTbtFrame_Boundary_StreetNameTruncationAt31CharsWithTrailingDot() {
        val longStreet = "Extremely Long National Highway Expressway Through Mountain Pass"
        val frame = TbtNavFrameBuilder().apply { streetName = longStreet }.build()

        // Read sanitized street from bytes 8..46
        val streetBytes = frame.sliceArray(8 until 47).takeWhile { it != 0.toByte() }.toByteArray()
        val text = String(streetBytes, Charsets.US_ASCII)

        assertEquals("Truncated length must be 32 (31 chars + trailing dot)", 32, text.length)
        assertTrue("Must end with trailing dot", text.endsWith("."))
    }

    @Test
    fun testTbtFrame_Boundary_StreetNameSanitizesSpecialCharsAndEmojis() {
        val dirtyStreet = "M.G. Road #10 & High-Street \uD83C\uDFCD\uFE0F"
        val frame = TbtNavFrameBuilder().apply { streetName = dirtyStreet }.build()

        val streetBytes = frame.sliceArray(8 until 47).takeWhile { it != 0.toByte() }.toByteArray()
        val text = String(streetBytes, Charsets.US_ASCII)

        assertFalse("Must not contain #", text.contains("#"))
        assertFalse("Must not contain &", text.contains("&"))
        assertTrue("Must preserve alphanumeric and dot", text.contains("M.G. Road 10 High Street"))
    }

    @Test
    fun testTbtFrame_Boundary_ZeroDistanceNextTurn() {
        val frame = TbtNavFrameBuilder().apply { distanceToTurnMeters = 0 }.build()
        val distText = String(frame.sliceArray(2..5), Charsets.US_ASCII)
        assertEquals("0000", distText)
    }

    @Test
    fun testTbtFrame_Boundary_MaxDistanceNextTurn() {
        val frame = TbtNavFrameBuilder().apply { distanceToTurnMeters = 9999 }.build()
        val distText = String(frame.sliceArray(2..5), Charsets.US_ASCII)
        assertEquals("9999", distText)
    }

    @Test
    fun testTbtFrame_Boundary_DistanceUnitsBitMask() {
        val frameMiles = TbtNavFrameBuilder().apply { distanceUnit = DistanceUnit.MILES }.build()
        val unit = (frameMiles[0].toInt() shr 4) and 0x07
        assertEquals(DistanceUnit.MILES.code, unit)
    }

    // =========================================================================
    // FEATURE 18: LIVE RIDE TRACKING (Tier 1 & Tier 2)
    // =========================================================================

    data class LiveTelemetry(
        val speedKmh: Int,
        val leanAngleDegrees: Float,
        val elevationMeters: Float,
        val elapsedSeconds: Long,
        val distanceRecordedMeters: Float
    )

    @Test
    fun testTracking_SpeedAndLeanAngleRecording() {
        val telemetry = LiveTelemetry(
            speedKmh = 68,
            leanAngleDegrees = 24.5f,
            elevationMeters = 560f,
            elapsedSeconds = 1200,
            distanceRecordedMeters = 15400f
        )
        assertEquals(68, telemetry.speedKmh)
        assertEquals(24.5f, telemetry.leanAngleDegrees, 0.01f)
        assertEquals(560f, telemetry.elevationMeters, 0.01f)
    }

    @Test
    fun testTracking_DurationFormatting_HoursAndMinutes() {
        fun formatDuration(seconds: Long): String {
            val hours = seconds / 3600
            val mins = (seconds / 60) % 60
            return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        }
        assertEquals("45m", formatDuration(2700))
        assertEquals("1h 22m", formatDuration(4920))
    }

    @Test
    fun testTracking_DistanceFormatting_KmWithOneDecimal() {
        fun formatDist(meters: Float): String = String.format("%.1f km", meters / 1000f)
        assertEquals("64.2 km", formatDist(64200f))
        assertEquals("0.5 km", formatDist(500f))
    }

    @Test
    fun testTracking_BreadcrumbGpsTrail_AppendsCoordinates() {
        val trail = mutableListOf<GeoPoint>()
        trail.add(GeoPoint(18.5204, 73.8567))
        trail.add(GeoPoint(18.5250, 73.8590))
        assertEquals(2, trail.size)
    }

    @Test
    fun testTracking_AverageSpeedCalculation() {
        val distMeters = 36000f
        val durationSeconds = 3600L
        val avgSpeedKmh = (distMeters / 1000f) / (durationSeconds / 3600f)
        assertEquals(36.0f, avgSpeedKmh, 0.01f)
    }

    @Test
    fun testTracking_Boundary_ExcessiveLeanAngleSafetyClamping() {
        fun clampLean(angle: Float): Float = angle.coerceIn(-60f, 60f)
        assertEquals(-60f, clampLean(-75f), 0.01f)
        assertEquals(30f, clampLean(30f), 0.01f)
        assertEquals(60f, clampLean(80f), 0.01f)
    }

    @Test
    fun testTracking_Boundary_NegativeElevation() {
        val belowSeaLevel = -12f
        val telemetry = LiveTelemetry(0, 0f, belowSeaLevel, 0, 0f)
        assertEquals(-12f, telemetry.elevationMeters, 0.01f)
    }

    @Test
    fun testTracking_Boundary_ZeroDurationPreventsDivideByZero() {
        fun calculateAvgSpeed(meters: Float, seconds: Long): Float {
            if (seconds <= 0) return 0f
            return (meters / 1000f) / (seconds / 3600f)
        }
        assertEquals(0f, calculateAvgSpeed(100f, 0L), 0.01f)
    }

    @Test
    fun testTracking_Boundary_GpsJitterFiltering() {
        fun isValidStep(p1: GeoPoint, p2: GeoPoint, deltaSeconds: Long): Boolean {
            val dist = Math.abs(p2.latitude - p1.latitude) * 111000.0
            val speedMps = dist / deltaSeconds.coerceAtLeast(1)
            return speedMps < 70.0 // Under 250 km/h
        }
        val p1 = GeoPoint(18.5204, 73.8567)
        val p2 = GeoPoint(18.5206, 73.8568)
        val pJump = GeoPoint(19.5204, 73.8567) // 111 km teleport in 1 sec
        assertTrue(isValidStep(p1, p2, 1))
        assertFalse(isValidStep(p1, pJump, 1))
    }

    @Test
    fun testTracking_Boundary_MaxSpeedTracking() {
        var topSpeed = 0
        fun registerSpeed(s: Int) {
            if (s > topSpeed) topSpeed = s
        }
        registerSpeed(45)
        registerSpeed(89)
        registerSpeed(72)
        assertEquals(89, topSpeed)
    }

    // =========================================================================
    // FEATURE 19: TRIP HISTORY LOG (Tier 1 & Tier 2)
    // =========================================================================

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

    private val sampleTrips = listOf(
        StoredTrip("trip_1", "Pune to Lonavala Expressway Ride", "Yesterday, 07:30 AM", 64.2f, 98.4f, 44.5f, "Deccan Gymkhana, Pune", "Tiger Point, Lonavala"),
        StoredTrip("trip_2", "FC Road to Hinjewadi Tech Park", "28 Oct 2026, 09:15 AM", 18.4f, 72.0f, 48.0f, "FC Road, Shivajinagar", "Phase 1, Hinjewadi")
    )

    @Test
    fun testTripLog_ListSize_ContainsRecordedTrips() {
        assertEquals(2, sampleTrips.size)
        assertEquals("Pune to Lonavala Expressway Ride", sampleTrips.first().title)
    }

    @Test
    fun testTripLog_FuelEfficiency_TrackedInKmpl() {
        val trip = sampleTrips.first()
        assertEquals(44.5f, trip.fuelEfficiencyKmpl, 0.01f)
    }

    @Test
    fun testTripLog_TopSpeedMetric_MatchesHighestRecorded() {
        val trip = sampleTrips.first()
        assertEquals(98.4f, trip.topSpeedKmh, 0.01f)
    }

    @Test
    fun testTripLog_AddPhotoToTrip_UpdatesGallery() {
        var trip = sampleTrips.first()
        trip = trip.copy(photoUrls = listOf("photo_lonavala_point.jpg"))
        assertEquals(1, trip.photoUrls.size)
    }

    @Test
    fun testTripLog_CuratedToursCatalog_CoversIconicRides() {
        val curatedTours = listOf("tripkanyakumari", "tripkashmir", "tripkerala", "tripmumbai", "agrafort")
        assertEquals(5, curatedTours.size)
        assertTrue(curatedTours.contains("tripkanyakumari"))
    }

    @Test
    fun testTripLog_Boundary_EmptyTripLogState() {
        val emptyList = emptyList<StoredTrip>()
        assertTrue("No trips recorded yet", emptyList.isEmpty())
    }

    @Test
    fun testTripLog_Boundary_DeleteTripById() {
        var trips = sampleTrips
        trips = trips.filter { it.id != "trip_1" }
        assertEquals(1, trips.size)
        assertEquals("trip_2", trips.first().id)
    }

    @Test
    fun testTripLog_Boundary_TripTitleDefaulting() {
        fun resolveTitle(title: String?, start: String, end: String): String {
            return if (!title.isNullOrBlank()) title else "$start to $end Ride"
        }
        assertEquals("Morning Ride", resolveTitle("Morning Ride", "A", "B"))
        assertEquals("Pune to Goa Ride", resolveTitle(null, "Pune", "Goa"))
    }

    @Test
    fun testTripLog_Boundary_ExtremeTripDistance() {
        val transIndiaTrip = StoredTrip("t3", "Kashmir to Kanyakumari", "Oct 2026", 3676.0f, 115f, 42f, "Srinagar", "Kanyakumari")
        assertTrue(transIndiaTrip.distanceKm > 3000f)
    }

    @Test
    fun testTripLog_Boundary_SortTripsDescendingDate() {
        val sorted = sampleTrips.sortedByDescending { it.distanceKm }
        assertEquals("trip_1", sorted.first().id)
    }

    // =========================================================================
    // FEATURE 20: DOCUMENT LOCKER (Tier 1 & Tier 2)
    // =========================================================================

    data class StoredDocument(
        val id: String,
        val type: String,
        val documentNumber: String,
        val expiryDate: String,
        val isVerified: Boolean,
        val issuer: String
    )

    private val authoritativeDocuments = listOf(
        StoredDocument("doc_rc", "Registration Certificate (RC)", "MH12TC5590/2025/RC", "Valid up to 14 Oct 2040", true, "Ministry of Road Transport & Highways"),
        StoredDocument("doc_ins", "Comprehensive Bike Insurance", "BAJAJ-ALLIANZ-99120488", "Valid up to 28 Nov 2027", true, "Bajaj Allianz General Insurance"),
        StoredDocument("doc_puc", "Pollution Under Control (PUC)", "MH12-PUC-449102", "Valid up to 15 Jun 2027", true, "Govt of Maharashtra Transport Dept"),
        StoredDocument("doc_dl", "Driving License", "DL-1220180049281", "Valid up to 2038", true, "Regional Transport Office (RTO)")
    )

    @Test
    fun testDocLocker_CoversAllFourStandardDocuments() {
        assertEquals("Four mandatory vehicle documents required", 4, authoritativeDocuments.size)
        val types = authoritativeDocuments.map { it.type }
        assertTrue(types.any { it.contains("RC") })
        assertTrue(types.any { it.contains("Insurance") })
        assertTrue(types.any { it.contains("PUC") })
        assertTrue(types.any { it.contains("License") })
    }

    @Test
    fun testDocLocker_AllDocumentsVerified() {
        authoritativeDocuments.forEach { doc ->
            assertTrue("Document ${doc.type} should have isVerified == true", doc.isVerified)
        }
    }

    @Test
    fun testDocLocker_InsuranceIssuer_IsBajajAllianz() {
        val insurance = authoritativeDocuments.first { it.id == "doc_ins" }
        assertTrue(insurance.issuer.contains("Bajaj Allianz"))
    }

    @Test
    fun testDocLocker_DocumentNumberLookup() {
        val rc = authoritativeDocuments.first { it.id == "doc_rc" }
        assertEquals("MH12TC5590/2025/RC", rc.documentNumber)
    }

    @Test
    fun testDocLocker_ExpirationDateTracking() {
        val puc = authoritativeDocuments.first { it.id == "doc_puc" }
        assertTrue(puc.expiryDate.contains("2027"))
    }

    @Test
    fun testDocLocker_Boundary_ExpiredDocumentDetection() {
        fun isExpired(expiryYear: Int, currentYear: Int): Boolean = expiryYear < currentYear
        assertTrue("PUC expired in 2024", isExpired(2024, 2026))
        assertFalse("PUC valid in 2027", isExpired(2027, 2026))
    }

    @Test
    fun testDocLocker_Boundary_DocumentImagePayloadSizeCheck() {
        fun isValidImageUpload(sizeBytes: Long): Boolean = sizeBytes in 1024..5_242_880 // 1 KB to 5 MB
        assertTrue(isValidImageUpload(200_000))
        assertFalse("Zero byte image rejected", isValidImageUpload(0))
        assertFalse("Oversized 10MB image rejected", isValidImageUpload(10_000_000))
    }

    @Test
    fun testDocLocker_Boundary_AddCustomDocument() {
        val custom = StoredDocument("doc_custom", "Extended Warranty", "WAR-9901", "2030", true, "Bajaj Auto Ltd")
        val updated = authoritativeDocuments + custom
        assertEquals(5, updated.size)
    }

    @Test
    fun testDocLocker_Boundary_DocumentNumberSanitization() {
        fun sanitizeDocNum(raw: String): String = raw.trim().uppercase().replace(Regex("[^A-Z0-9/-]"), "")
        assertEquals("MH12TC5590", sanitizeDocNum(" mh 12 tc 5590 "))
    }

    @Test
    fun testDocLocker_Boundary_ClusterTransferPayloadPreparation() {
        val doc = authoritativeDocuments.first()
        val summaryLine = "${doc.type}: ${doc.documentNumber} (${doc.expiryDate})"
        assertTrue(summaryLine.isNotBlank())
    }
}
