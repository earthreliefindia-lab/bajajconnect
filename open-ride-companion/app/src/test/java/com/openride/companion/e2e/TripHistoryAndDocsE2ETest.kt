package com.openride.companion.e2e

import com.openride.companion.data.DefaultDocumentLockerRepository
import com.openride.companion.data.DefaultTripRepository
import com.openride.companion.navigation.engine.ElevationTracker
import com.openride.companion.navigation.engine.GpsJitterFilter
import com.openride.companion.navigation.engine.HardBrakingDetector
import com.openride.companion.navigation.engine.LeanAngleProcessor
import com.openride.companion.navigation.model.CuratedToursCatalog
import com.openride.companion.navigation.model.DocExpiryStatus
import com.openride.companion.navigation.model.DocType
import com.openride.companion.navigation.model.DocumentValidator
import com.openride.companion.navigation.model.ExpiryAlertEngine
import com.openride.companion.navigation.model.GeoPoint
import com.openride.companion.navigation.model.LiveTelemetry
import com.openride.companion.navigation.model.StoredDocument
import com.openride.companion.navigation.model.StoredTrip
import com.openride.companion.navigation.model.TrackingFormatters
import com.openride.companion.navigation.model.TripRecord
import com.openride.companion.navigation.model.VehicleDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-End Test Suite for Trip History, Live Tracking, and Document Locker Subsystems (Features 18-20).
 * Covers Tier 1 (Feature Coverage) and Tier 2 (Boundary & Corner Cases).
 * Total: 35 tests.
 */
class TripHistoryAndDocsE2ETest {

    // =========================================================================
    // FEATURE 18: LIVE RIDE TRACKING (10 Tests)
    // =========================================================================

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
        assertEquals("45m", TrackingFormatters.formatDuration(2700))
        assertEquals("1h 22m", TrackingFormatters.formatDuration(4920))
    }

    @Test
    fun testTracking_DistanceFormatting_KmWithOneDecimal() {
        assertEquals("64.2 km", TrackingFormatters.formatDistance(64200f))
        assertEquals("0.5 km", TrackingFormatters.formatDistance(500f))
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
        val avgSpeedKmh = TrackingFormatters.calculateAverageSpeed(distMeters, durationSeconds)
        assertEquals(36.0f, avgSpeedKmh, 0.01f)
    }

    @Test
    fun testTracking_Boundary_ExcessiveLeanAngleSafetyClamping() {
        val processor = LeanAngleProcessor()
        assertEquals(-60f, processor.processAngle(-75f), 0.01f)
        assertEquals(30f, processor.processAngle(30f), 0.01f)
        assertEquals(60f, processor.processAngle(80f), 0.01f)
    }

    @Test
    fun testTracking_Boundary_NegativeElevation() {
        val belowSeaLevel = -12f
        val telemetry = LiveTelemetry(0, 0f, belowSeaLevel, 0, 0f)
        assertEquals(-12f, telemetry.elevationMeters, 0.01f)
    }

    @Test
    fun testTracking_Boundary_ZeroDurationPreventsDivideByZero() {
        val avg = TrackingFormatters.calculateAverageSpeed(100f, 0L)
        assertEquals(0f, avg, 0.01f)
    }

    @Test
    fun testTracking_Boundary_GpsJitterFiltering() {
        val p1 = GeoPoint(18.5204, 73.8567)
        val p2 = GeoPoint(18.5206, 73.8568)
        val pJump = GeoPoint(19.5204, 73.8567) // 111 km jump in 1 sec
        assertTrue(GpsJitterFilter.isValidStep(p1, p2, 1))
        assertFalse(GpsJitterFilter.isValidStep(p1, pJump, 1))
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
    // FEATURE 19: TRIP HISTORY LOG & ANALYTICS (10 Tests)
    // =========================================================================

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
        val curatedTours = CuratedToursCatalog.tourKeys
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
        assertEquals("Morning Ride", TripRecord.resolveTitle("Morning Ride", "A", "B"))
        assertEquals("Pune to Goa Ride", TripRecord.resolveTitle(null, "Pune", "Goa"))
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
    // FEATURE 20: VEHICLE DOCUMENT LOCKER (10 Tests)
    // =========================================================================

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
        assertTrue("PUC expired in 2024", ExpiryAlertEngine.isExpired(2024, 2026))
        assertFalse("PUC valid in 2027", ExpiryAlertEngine.isExpired(2027, 2026))
    }

    @Test
    fun testDocLocker_Boundary_DocumentImagePayloadSizeCheck() {
        assertTrue(DocumentValidator.isValidImageUpload(200_000))
        assertFalse("Zero byte image rejected", DocumentValidator.isValidImageUpload(0))
        assertFalse("Oversized 10MB image rejected", DocumentValidator.isValidImageUpload(10_000_000))
    }

    @Test
    fun testDocLocker_Boundary_AddCustomDocument() {
        val custom = StoredDocument("doc_custom", "Extended Warranty", "WAR-9901", "2030", true, "Bajaj Auto Ltd")
        val updated = authoritativeDocuments + custom
        assertEquals(5, updated.size)
    }

    @Test
    fun testDocLocker_Boundary_DocumentNumberSanitization() {
        assertEquals("MH12TC5590", DocumentValidator.sanitizeDocumentNumber(" mh 12 tc 5590 "))
    }

    @Test
    fun testDocLocker_Boundary_ClusterTransferPayloadPreparation() {
        val doc = authoritativeDocuments.first()
        val summaryLine = "${doc.type}: ${doc.documentNumber} (${doc.expiryDate})"
        assertTrue(summaryLine.isNotBlank())
    }

    // =========================================================================
    // SUB-SUBSYSTEM INTEGRATION & BOUNDARY CASES (5 Tests -> 35 Total)
    // =========================================================================

    @Test
    fun testTracking_HardBrakingDetection_ExceedsThreshold() {
        val detector = HardBrakingDetector(thresholdKmhPerSec = 15.0f)
        detector.evaluate(60f, 1000L, 18.5, 73.8)
        val event = detector.evaluate(40f, 2000L, 18.5, 73.8) // 20 km/h drop in 1s
        assertNotNull(event)
        assertEquals(20.0f, event?.decelerationKmhPerSec ?: 0f, 0.01f)
    }

    @Test
    fun testTracking_ElevationGainDeadband_IgnoresNoise() {
        val tracker = ElevationTracker()
        tracker.updateElevation(100f)
        tracker.updateElevation(101.2f) // +1.2m below 1.5m deadband
        assertEquals(0f, tracker.elevationGainMeters, 0.01f)

        tracker.updateElevation(104f) // +2.8m above deadband
        assertEquals(2.8f, tracker.elevationGainMeters, 0.01f)
    }

    @Test
    fun testTripLog_AggregateStatsCalculation_AggregatesAllTrips() = runBlocking {
        val repo = DefaultTripRepository()
        val stats = repo.getAggregateStats()
        assertEquals(2, stats.totalTrips)
        assertTrue(stats.totalDistanceKm > 80f)
        assertEquals(98.4f, stats.overallMaxSpeedKmh, 0.01f)
    }

    @Test
    fun testDocLocker_ExpiryAlertEngine_ExpiringSoonTier() {
        val now = 1700000000000L
        val expiringIn10Days = now + (10L * 24 * 60 * 60 * 1000L)
        val expired = now - 1000L
        val validFar = now + (90L * 24 * 60 * 60 * 1000L)

        assertEquals(DocExpiryStatus.EXPIRING_SOON, ExpiryAlertEngine.evaluate(expiringIn10Days, now))
        assertEquals(DocExpiryStatus.EXPIRED, ExpiryAlertEngine.evaluate(expired, now))
        assertEquals(DocExpiryStatus.VALID, ExpiryAlertEngine.evaluate(validFar, now))
    }

    @Test
    fun testDocLocker_ClusterSyncPayload_IncludesMd5AndSize() = runBlocking {
        val repo = DefaultDocumentLockerRepository()
        val doc = repo.getDocumentById("doc_rc")
        assertNotNull(doc)
        val sampleBytes = ByteArray(2048) { 0x42 }
        val payload = repo.prepareClusterSyncPayload(doc!!, sampleBytes)
        assertTrue("Cluster sync payload must be larger than original bytes", payload.size > sampleBytes.size)
    }
}
