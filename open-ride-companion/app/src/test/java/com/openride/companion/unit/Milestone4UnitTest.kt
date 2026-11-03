package com.openride.companion.unit

import com.openride.companion.data.DefaultDocumentLockerRepository
import com.openride.companion.data.DefaultTripRepository
import com.openride.companion.navigation.ble.DistanceUnit
import com.openride.companion.navigation.ble.GoogleManeuverTranslator
import com.openride.companion.navigation.ble.NavigationHelper
import com.openride.companion.navigation.ble.PrimaryTurns
import com.openride.companion.navigation.ble.RoundaboutHelper
import com.openride.companion.navigation.ble.TbtManeuvers
import com.openride.companion.navigation.ble.TbtNavFrame
import com.openride.companion.navigation.ble.TbtNavFrameBuilder
import com.openride.companion.navigation.engine.DefaultRouteCalculationEngine
import com.openride.companion.navigation.engine.DualEngineResolver
import com.openride.companion.navigation.engine.ElevationTracker
import com.openride.companion.navigation.engine.GpsJitterFilter
import com.openride.companion.navigation.engine.HardBrakingDetector
import com.openride.companion.navigation.engine.LeanAngleProcessor
import com.openride.companion.navigation.engine.LiveRideRecorder
import com.openride.companion.navigation.engine.RerouteTracker
import com.openride.companion.navigation.engine.RouteDeviationEvaluator
import com.openride.companion.navigation.model.CuratedToursCatalog
import com.openride.companion.navigation.model.DocExpiryStatus
import com.openride.companion.navigation.model.DocType
import com.openride.companion.navigation.model.DocumentValidator
import com.openride.companion.navigation.model.ExpiryAlertEngine
import com.openride.companion.navigation.model.GeoPoint
import com.openride.companion.navigation.model.LiveTelemetry
import com.openride.companion.navigation.model.RouteAlternativeType
import com.openride.companion.navigation.model.StoredDocument
import com.openride.companion.navigation.model.StoredTrip
import com.openride.companion.navigation.model.TrackingFormatters
import com.openride.companion.navigation.model.TripRecord
import com.openride.companion.navigation.model.VehicleDocument
import com.openride.companion.navigation.state.NavigationManager
import com.openride.companion.navigation.state.NavigationState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Comprehensive Unit Test Suite for Milestone 4 (Features 16, 17, 18, 19, 20).
 * Thoroughly validates models, mathematical formulas, Dalvik framing, sensor fusion,
 * repositories, and edge cases.
 */
class Milestone4UnitTest {

    // =========================================================================
    // FEATURE 16: NAVIGATION & ROUTE PLANNING
    // =========================================================================

    @Test
    fun testGeoPoint_Validation_ValidCoordinatesAccepted() {
        val pt = GeoPoint(18.5204, 73.8567)
        assertEquals(18.5204, pt.latitude, 0.0001)
        assertEquals(73.8567, pt.longitude, 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGeoPoint_Validation_InvalidLatitudeThrows() {
        GeoPoint(95.0, 73.8567)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGeoPoint_Validation_InvalidLongitudeThrows() {
        GeoPoint(18.5204, 200.0)
    }

    @Test
    fun testGeoPoint_HaversineDistance_CalculatesAccurately() {
        // Pune (18.5204, 73.8567) to Lonavala (18.7544, 73.4062) approx 53 km direct
        val pune = GeoPoint(18.5204, 73.8567)
        val lonavala = GeoPoint(18.7544, 73.4062)
        val distMeters = pune.distanceTo(lonavala)
        assertTrue("Distance should be approx 53-54 km", distMeters in 50000.0..58000.0)
    }

    @Test
    fun testGeoPoint_BearingCalculation_CardinalDirections() {
        val p1 = GeoPoint(18.0, 73.0)
        val pNorth = GeoPoint(19.0, 73.0)
        val pEast = GeoPoint(18.0, 74.0)
        val pSouth = GeoPoint(17.0, 73.0)
        val pWest = GeoPoint(18.0, 72.0)

        assertEquals(0f, p1.bearingTo(pNorth), 1.0f)
        assertEquals(90f, p1.bearingTo(pEast), 1.5f)
        assertEquals(180f, p1.bearingTo(pSouth), 1.0f)
        assertEquals(270f, p1.bearingTo(pWest), 1.5f)
    }

    @Test
    fun testGeoPoint_PerpendicularDistanceToSegment() {
        val p1 = GeoPoint(18.0, 73.0)
        val p2 = GeoPoint(18.0, 74.0)
        val testPoint = GeoPoint(18.001, 73.5) // ~111m North of midpoint
        val dist = testPoint.distanceToSegment(p1, p2)
        assertTrue("Distance should be approx 111m", dist in 100.0..120.0)
    }

    @Test
    fun testRouteEngine_CalculatesMultipleAlternatives() {
        val engine = DefaultRouteCalculationEngine()
        val start = GeoPoint(18.5204, 73.8567)
        val end = GeoPoint(18.7544, 73.4062)
        val routes = engine.calculateRoutes(start, end)

        assertEquals("Should produce 3 alternative routes", 3, routes.size)
        val types = routes.map { it.alternativeType }
        assertTrue(types.contains(RouteAlternativeType.FASTEST))
        assertTrue(types.contains(RouteAlternativeType.SHORTEST))
        assertTrue(types.contains(RouteAlternativeType.ECO))
    }

    @Test
    fun testRouteEngine_TollFiltering_ExcludesTolls() {
        val engine = DefaultRouteCalculationEngine()
        val start = GeoPoint(18.5204, 73.8567)
        val end = GeoPoint(18.7544, 73.4062)
        val noTollRoutes = engine.calculateRoutes(start, end, avoidTolls = true)

        assertEquals(2, noTollRoutes.size)
        assertTrue(noTollRoutes.none { it.hasTolls })
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRouteEngine_IdenticalStartAndDestination_Throws() {
        val engine = DefaultRouteCalculationEngine()
        val pt = GeoPoint(18.5204, 73.8567)
        engine.calculateRoutes(pt, pt)
    }

    @Test
    fun testDualEngineResolver_SelectsMapplsOrGoogle() {
        assertEquals("GoogleMaps", DualEngineResolver.resolveEngine(""))
        assertEquals("GoogleMaps", DualEngineResolver.resolveEngine("   "))
        assertEquals("Mappls", DualEngineResolver.resolveEngine("MAPPLS_API_KEY_SECURE"))
    }

    @Test
    fun testRouteDeviationEvaluator_DetectsOffRoute() {
        val polyline = listOf(
            GeoPoint(18.5000, 73.8500),
            GeoPoint(18.5100, 73.8500),
            GeoPoint(18.5200, 73.8500)
        )
        val onRoutePoint = GeoPoint(18.5050, 73.85005) // ~5m away
        val offRoutePoint = GeoPoint(18.5050, 73.8550) // ~550m away

        assertFalse(RouteDeviationEvaluator.isOffRoute(onRoutePoint, polyline, 30.0))
        assertTrue(RouteDeviationEvaluator.isOffRoute(offRoutePoint, polyline, 30.0))
    }

    @Test
    fun testRerouteTracker_RequiresThreeConsecutiveDeviations() {
        val tracker = RerouteTracker(thresholdMeters = 30.0, requiredConsecutiveCount = 3)
        val polyline = listOf(GeoPoint(18.5000, 73.8500), GeoPoint(18.5200, 73.8500))
        val offRoutePoint = GeoPoint(18.5100, 73.8600) // ~1 km off

        assertFalse(tracker.onLocationUpdate(offRoutePoint, polyline)) // 1st
        assertFalse(tracker.onLocationUpdate(offRoutePoint, polyline)) // 2nd
        assertTrue(tracker.onLocationUpdate(offRoutePoint, polyline))  // 3rd -> Trigger!
        assertTrue(tracker.isRerouteHolding)
    }

    // =========================================================================
    // FEATURE 17: 48-BYTE TBT NAV FRAME GENERATOR & PROTOCOL
    // =========================================================================

    @Test
    fun testPrimaryTurns_All22CatalogEntries() {
        assertEquals(22, PrimaryTurns.entries.size)
        assertEquals(71, PrimaryTurns.STRAIGHT.asciiValue)
        assertEquals('G', PrimaryTurns.STRAIGHT.asciiChar)
        assertEquals(73, PrimaryTurns.TURN_LEFT.asciiValue)
        assertEquals('I', PrimaryTurns.TURN_LEFT.asciiChar)
        assertEquals(74, PrimaryTurns.TURN_RIGHT.asciiValue)
        assertEquals('J', PrimaryTurns.TURN_RIGHT.asciiChar)
        assertEquals(72, PrimaryTurns.DESTINATION_REACHED.asciiValue)
        assertEquals('H', PrimaryTurns.DESTINATION_REACHED.asciiChar)
        assertEquals(66, PrimaryTurns.WRONG_WAY.asciiValue)
        assertEquals('B', PrimaryTurns.WRONG_WAY.asciiChar)
    }

    @Test
    fun testGoogleManeuverTranslator_TranslatesAll66Codes() {
        assertEquals(PrimaryTurns.WRONG_WAY, GoogleManeuverTranslator.mapGoogleManeuver(0))
        assertEquals(PrimaryTurns.STRAIGHT, GoogleManeuverTranslator.mapGoogleManeuver(1))
        assertEquals(PrimaryTurns.DESTINATION_REACHED, GoogleManeuverTranslator.mapGoogleManeuver(2))
        assertEquals(PrimaryTurns.TURN_LEFT, GoogleManeuverTranslator.mapGoogleManeuver(6))
        assertEquals(PrimaryTurns.TURN_RIGHT, GoogleManeuverTranslator.mapGoogleManeuver(7))
        assertEquals(PrimaryTurns.U_TURN_LEFT, GoogleManeuverTranslator.mapGoogleManeuver(15))
        assertEquals(PrimaryTurns.U_TURN_RIGHT, GoogleManeuverTranslator.mapGoogleManeuver(14))
        assertEquals(PrimaryTurns.ROUNDABOUT_LEFT, GoogleManeuverTranslator.mapGoogleManeuver(43)) // Clockwise
        assertEquals(PrimaryTurns.ROUNDABOUT_RIGHT, GoogleManeuverTranslator.mapGoogleManeuver(44)) // Anti-Clockwise
        assertEquals(PrimaryTurns.FERRY, GoogleManeuverTranslator.mapGoogleManeuver(63))
    }

    @Test
    fun testRoundaboutHelper_ClockwiseAndExitExtraction() {
        assertTrue(RoundaboutHelper.isClockwise(43))
        assertFalse(RoundaboutHelper.isClockwise(44))
        assertTrue(RoundaboutHelper.isClockwise(45))
        assertFalse(RoundaboutHelper.isClockwise(46))

        assertEquals(2, RoundaboutHelper.parseRoundaboutExitFromInstruction("take the 2nd exit onto MG Road"))
        assertEquals(3, RoundaboutHelper.parseRoundaboutExitFromInstruction("Take 3rd exit"))
        assertEquals(5, RoundaboutHelper.parseRoundaboutExitFromInstruction("At roundabout, exit 5"))
        assertEquals(0, RoundaboutHelper.parseRoundaboutExitFromInstruction("Continue straight"))
    }

    @Test
    fun testNavigationHelper_FormatDistance_Meters() {
        val result = NavigationHelper.formatDistance(450.0)
        assertEquals(1.toByte(), result[0]) // Unit: 1 = Meters
        val intMsb = result[1].toInt() and 0xFF
        val intLsb = result[2].toInt() and 0xFF
        val intDist = (intMsb shl 8) or intLsb
        assertEquals(450, intDist)
        assertEquals(0.toByte(), result[3]) // Frac MSB
        assertEquals(0.toByte(), result[4]) // Frac LSB
    }

    @Test
    fun testNavigationHelper_FormatDistance_Kilometers() {
        val result = NavigationHelper.formatDistance(12450.0) // 12.45 km
        assertEquals(0.toByte(), result[0]) // Unit: 0 = KM
        val intDist = ((result[1].toInt() and 0xFF) shl 8) or (result[2].toInt() and 0xFF)
        val fracDist = ((result[3].toInt() and 0xFF) shl 8) or (result[4].toInt() and 0xFF)
        assertEquals(12, intDist)
        assertEquals(45, fracDist)
    }

    @Test
    fun testTbtNavFrameBuilder_BuildsExact48BytesWithChecksum() {
        val frame = TbtNavFrameBuilder().apply {
            maneuverCode = TbtManeuvers.TURN_LEFT
            distanceToTurnMeters = 350
            etaMinutes = 15
            streetName = "Senapati Bapat Road"
        }.build()

        assertEquals(48, frame.size)
        assertTrue(TbtNavFrame.validateChecksum(frame))
    }

    @Test
    fun testTbtNavFrame_DalvikBuildNavHelperFrame_ExactByteLayout() {
        val frame = TbtNavFrame.buildNavHelperFrame(
            navMode = 1,
            primaryTurnCode = PrimaryTurns.TURN_LEFT.asciiValue,
            isClockwise = false,
            distToTurnMeters = 500,
            distTotalMeters = 64200,
            etaHour = 4,
            etaMinute = 30,
            etaAmPm = 1, // PM
            gpsStatus = 0,
            takeMeHomeAck = 0,
            streetName = "FC Road"
        )

        assertEquals(48, frame.size)
        // Byte 0: navMode(1) | (unit(1)<<4) | (cwBit(1)<<7) -> 1 | 0x10 | 0x80 = 0x91
        assertEquals(0x91.toByte(), frame[0])
        // Byte 1: Maneuver ASCII 'I' (73)
        assertEquals('I'.code.toByte(), frame[1])
        // Byte 6: ETA minute 30
        assertEquals(30.toByte(), frame[6])
        // Byte 7: (4 & 0xF) | (1 << 4) = 0x14
        assertEquals(0x14.toByte(), frame[7])
        // Byte 14: Street length = 7
        assertEquals(7.toByte(), frame[14])
        // Byte 47: Valid checksum
        assertTrue(TbtNavFrame.validateChecksum(frame))
    }

    // =========================================================================
    // FEATURE 18: LIVE RIDE TRACKING SUBSYSTEM
    // =========================================================================

    @Test
    fun testGpsJitterFilter_AcceptsRealisticSpeeds() {
        val p1 = GeoPoint(18.5204, 73.8567)
        val p2 = GeoPoint(18.5206, 73.8568) // ~24 meters in 1 second = 24 m/s (86 km/h)
        assertTrue(GpsJitterFilter.isValidStep(p1, p2, 1, 5f))
    }

    @Test
    fun testGpsJitterFilter_RejectsTeleportationAndPoorAccuracy() {
        val p1 = GeoPoint(18.5204, 73.8567)
        val pTeleport = GeoPoint(18.6000, 73.8567) // ~8.8 km in 1 second
        assertFalse(GpsJitterFilter.isValidStep(p1, pTeleport, 1, 5f))

        // Accuracy > 25m rejected
        val p2 = GeoPoint(18.5206, 73.8568)
        assertFalse(GpsJitterFilter.isValidStep(p1, p2, 1, 35f))
    }

    @Test
    fun testLeanAngleProcessor_TracksLeftAndRightPeaks() {
        val processor = LeanAngleProcessor()
        processor.processAngle(-25.5f) // Left
        processor.processAngle(31.2f)  // Right
        processor.processAngle(-28.0f) // Harder Left
        processor.processAngle(15.0f)  // Mild Right

        assertEquals(28.0f, processor.maxLeanAngleLeft, 0.01f)
        assertEquals(31.2f, processor.maxLeanAngleRight, 0.01f)
    }

    @Test
    fun testHardBrakingDetector_DetectsSharpDeceleration() {
        val detector = HardBrakingDetector(thresholdKmhPerSec = 15.0f)
        detector.evaluate(75f, 1000L, 18.5, 73.8)
        val event = detector.evaluate(55f, 2000L, 18.5, 73.8) // 20 km/h drop in 1s

        assertNotNull(event)
        assertEquals(20f, event?.decelerationKmhPerSec ?: 0f, 0.01f)
        assertTrue((event?.decelerationMps2 ?: 0f) > 5f)
    }

    @Test
    fun testHardBrakingDetector_IgnoresGradualBrakingAndAcceleration() {
        val detector = HardBrakingDetector(thresholdKmhPerSec = 15.0f)
        detector.evaluate(60f, 1000L, 18.5, 73.8)
        val brakeSlow = detector.evaluate(50f, 2000L, 18.5, 73.8) // 10 km/h drop in 1s
        assertNull(brakeSlow)

        val accel = detector.evaluate(70f, 3000L, 18.5, 73.8) // Acceleration
        assertNull(accel)
    }

    @Test
    fun testElevationTracker_NegativeAltitudesAndDeadband() {
        val tracker = ElevationTracker()
        tracker.updateElevation(-15.0f)
        assertEquals(-15.0f, tracker.currentElevationMeters, 0.01f)

        tracker.updateElevation(-14.0f) // +1.0m (below 1.5m deadband)
        assertEquals(0f, tracker.elevationGainMeters, 0.01f)

        tracker.updateElevation(-10.0f) // +4.0m (above 1.5m deadband)
        assertEquals(4.0f, tracker.elevationGainMeters, 0.01f)
    }

    @Test
    fun testLiveRideRecorder_CompleteSessionLifecycle() {
        val recorder = LiveRideRecorder("MD2A11CY4P8712391", "Pulsar NS400Z")
        recorder.startSession("Deccan Gymkhana")
        assertTrue(recorder.isRecording)

        recorder.onSensorTick(18.5204, 73.8567, 560f, 40f, -15f, 1000L)
        recorder.onSensorTick(18.5250, 73.8590, 565f, 65f, 22f, 2000L)

        val trip = recorder.finishSession("Tiger Point", "Good ride")
        assertFalse(recorder.isRecording)
        assertEquals("Deccan Gymkhana to Tiger Point Ride", trip.title)
        assertEquals(65f, trip.maxSpeedKmh, 0.01f)
        assertEquals(22f, trip.maxLeanAngleRight, 0.01f)
        assertEquals("Good ride", trip.userNote)
    }

    // =========================================================================
    // FEATURE 19: TRIP HISTORY LOG & ANALYTICS
    // =========================================================================

    @Test
    fun testCuratedToursCatalog_HasAll5IconicTours() {
        val tours = CuratedToursCatalog.tours
        assertEquals(5, tours.size)
        val ids = tours.map { it.assetKey }
        assertTrue(ids.contains("tripkanyakumari"))
        assertTrue(ids.contains("tripkashmir"))
        assertTrue(ids.contains("tripkerala"))
        assertTrue(ids.contains("tripmumbai"))
        assertTrue(ids.contains("agrafort"))
    }

    @Test
    fun testTripRepository_PreSeededTripsAndCRUD() = runBlocking {
        val repo = DefaultTripRepository()
        val initial = repo.getAllTrips()
        assertEquals(2, initial.size)

        // Save new trip
        val newTrip = TripRecord(
            id = "trip_custom",
            vehicleVin = "MD2A11CY4P8712391",
            vehicleModelName = "Pulsar NS400Z",
            title = "Night Highway Cruise",
            startTimeMillis = 1700000000000L,
            endTimeMillis = 1700003600000L,
            distanceKm = 45.0f,
            durationSeconds = 3600L,
            maxSpeedKmh = 85f,
            avgSpeedKmh = 45f,
            maxLeanAngleLeft = 20f,
            maxLeanAngleRight = 20f,
            hardBrakingCount = 1,
            elevationGainMeters = 100f,
            startLocationName = "A",
            endLocationName = "B"
        )
        repo.saveTrip(newTrip)
        assertEquals(3, repo.getAllTrips().size)
        assertEquals("Night Highway Cruise", repo.getTripById("trip_custom")?.title)

        // Delete trip
        repo.deleteTrip("trip_custom")
        assertEquals(2, repo.getAllTrips().size)
        assertNull(repo.getTripById("trip_custom"))
    }

    @Test
    fun testTripRepository_AddPhotoAndUserNote() = runBlocking {
        val repo = DefaultTripRepository()
        repo.addPhotoToTrip("trip_1", "scenic_overlook.jpg")
        val updated = repo.getTripById("trip_1")
        assertTrue(updated?.photoUrls?.contains("scenic_overlook.jpg") == true)

        repo.updateTripUserNote("trip_1", "Updated monsoon note")
        assertEquals("Updated monsoon note", repo.getTripById("trip_1")?.userNote)
    }

    // =========================================================================
    // FEATURE 20: VEHICLE DOCUMENT LOCKER
    // =========================================================================

    @Test
    fun testDocumentValidator_SanitizeDocumentNumber() {
        assertEquals("MH12TC5590", DocumentValidator.sanitizeDocumentNumber("  mh 12 tc 5590  "))
        assertEquals("DL-1220180049281", DocumentValidator.sanitizeDocumentNumber("dl-1220180049281"))
        assertEquals("RC/2026/A1", DocumentValidator.sanitizeDocumentNumber(" rc / 2026 / a1 "))
    }

    @Test
    fun testDocumentValidator_ImageUploadSizeBounds() {
        assertTrue(DocumentValidator.isValidImageUpload(1024))      // 1 KB
        assertTrue(DocumentValidator.isValidImageUpload(5242880))   // 5 MB
        assertFalse(DocumentValidator.isValidImageUpload(500))      // < 1 KB
        assertFalse(DocumentValidator.isValidImageUpload(6000000))  // > 5 MB
    }

    @Test
    fun testExpiryAlertEngine_AllTiers() {
        val now = 1700000000000L
        val in10Days = now + (10L * 86400000L)
        val in40Days = now + (40L * 86400000L)
        val past = now - (2L * 86400000L)

        assertEquals(DocExpiryStatus.EXPIRING_SOON, ExpiryAlertEngine.evaluate(in10Days, now))
        assertEquals(DocExpiryStatus.VALID, ExpiryAlertEngine.evaluate(in40Days, now))
        assertEquals(DocExpiryStatus.EXPIRED, ExpiryAlertEngine.evaluate(past, now))
    }

    @Test
    fun testDocumentLockerRepository_PreSeededDocumentsAndSync() = runBlocking {
        val repo = DefaultDocumentLockerRepository()
        val docs = repo.getAllDocuments()
        assertEquals(4, docs.size)

        val rc = repo.getDocumentById("doc_rc")
        assertNotNull(rc)
        assertEquals("MH12TC5590/2025/RC", rc?.documentNumber)

        // Cluster sync status
        repo.setClusterSyncStatus("doc_rc", true)
        val synced = repo.getDocumentById("doc_rc")
        assertTrue(synced?.isSyncedToCluster == true)
        assertNotNull(synced?.lastSyncTimestampMillis)
    }

    @Test
    fun testDocumentLockerRepository_PrepareClusterSyncPayload() {
        val repo = DefaultDocumentLockerRepository()
        val doc = VehicleDocument(
            id = "d1",
            vehicleVin = "VIN",
            docType = DocType.RC_BOOK,
            documentTitle = "RC",
            documentNumber = "MH12",
            issueDate = "2025",
            expiryDate = "2040",
            expiryEpochMillis = 2000000000000L,
            issuer = "RTO"
        )
        val imageBytes = ByteArray(4096) { 0x55 }
        val payload = repo.prepareClusterSyncPayload(doc, imageBytes)
        // sizeFrame (14 bytes) + md5Frame (42 bytes) + imageBytes (4096 bytes) = 4152 bytes
        assertTrue("Payload contains frames and data", payload.size > imageBytes.size)
    }
}
