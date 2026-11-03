package com.openride.companion.unit

import com.openride.companion.data.DefaultDocumentLockerRepository
import com.openride.companion.data.DefaultTripRepository
import com.openride.companion.data.DocumentLockerRepository
import com.openride.companion.data.TripRepository
import com.openride.companion.navigation.engine.ElevationTracker
import com.openride.companion.navigation.engine.GpsJitterFilter
import com.openride.companion.navigation.engine.HardBrakingDetector
import com.openride.companion.navigation.engine.LeanAngleProcessor
import com.openride.companion.navigation.engine.LiveRideRecorder
import com.openride.companion.navigation.model.BreadcrumbPoint
import com.openride.companion.navigation.model.CuratedToursCatalog
import com.openride.companion.navigation.model.DocExpiryStatus
import com.openride.companion.navigation.model.DocType
import com.openride.companion.navigation.model.DocumentValidator
import com.openride.companion.navigation.model.ExpiryAlertEngine
import com.openride.companion.navigation.model.GeoPoint
import com.openride.companion.navigation.model.HardBrakingEvent
import com.openride.companion.navigation.model.LiveTelemetry
import com.openride.companion.navigation.model.TrackingFormatters
import com.openride.companion.navigation.model.TripRecord
import com.openride.companion.navigation.model.VehicleDocument
import com.openride.companion.wifi.ClusterDocumentSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Adversarial Stress-Test Suite for Milestone 4 (Features 18, 19, 20).
 * Tests extreme boundaries, sensor jitter, NaN/Infinity inputs, zero durations,
 * massive memory loads, injection vectors, and temporal edge cases.
 */
class AdversarialM4TelemetryAndDocsTest {

    // =========================================================================
    // 1. LEAN ANGLE BOUNDARY CLAMPING & PEAK TRACKING
    // =========================================================================

    @Test
    fun testLeanAngle_ExtremeBoundaries_ClampedStrictly() {
        val processor = LeanAngleProcessor()

        // Exact boundaries
        assertEquals(-60.0f, processor.processAngle(-60.0f), 0.001f)
        assertEquals(60.0f, processor.processAngle(60.0f), 0.001f)
        assertEquals(0.0f, processor.processAngle(0.0f), 0.001f)

        // Extreme positive and negative angles
        assertEquals(-60.0f, processor.processAngle(-89.0f), 0.001f)
        assertEquals(60.0f, processor.processAngle(89.0f), 0.001f)
        assertEquals(-60.0f, processor.processAngle(-180.0f), 0.001f)
        assertEquals(60.0f, processor.processAngle(180.0f), 0.001f)
        assertEquals(-60.0f, processor.processAngle(-720.0f), 0.001f)
        assertEquals(60.0f, processor.processAngle(720.0f), 0.001f)

        // Float extreme values
        assertEquals(60.0f, processor.processAngle(Float.MAX_VALUE), 0.001f)
        assertEquals(-60.0f, processor.processAngle(-Float.MAX_VALUE), 0.001f)
        assertEquals(60.0f, processor.processAngle(Float.POSITIVE_INFINITY), 0.001f)
        assertEquals(-60.0f, processor.processAngle(Float.NEGATIVE_INFINITY), 0.001f)
    }

    @Test
    fun testLeanAngle_NanHandling_DoesNotCorruptPeakMetrics() {
        val processor = LeanAngleProcessor()
        processor.processAngle(25.0f) // Set initial right peak
        assertEquals(25.0f, processor.maxLeanAngleRight, 0.001f)
        assertEquals(0.0f, processor.maxLeanAngleLeft, 0.001f)

        // Feed Float.NaN
        val nanResult = processor.processAngle(Float.NaN)
        assertTrue("NaN input returns NaN", nanResult.isNaN())

        // Peak metrics must remain intact and must NOT be corrupted to NaN
        assertEquals(0.0f, processor.maxLeanAngleLeft, 0.001f)
        assertEquals(25.0f, processor.maxLeanAngleRight, 0.001f)

        // Subsequent valid input updates peaks normally
        processor.processAngle(-35.0f)
        assertEquals(35.0f, processor.maxLeanAngleLeft, 0.001f)
        assertEquals(25.0f, processor.maxLeanAngleRight, 0.001f)
    }

    @Test
    fun testLeanAngle_TrackingFormatters_ClampFunction() {
        assertEquals(-60.0f, TrackingFormatters.clampLeanAngle(-89.0f), 0.001f)
        assertEquals(60.0f, TrackingFormatters.clampLeanAngle(89.0f), 0.001f)
        assertEquals(-60.0f, TrackingFormatters.clampLeanAngle(-180.0f), 0.001f)
        assertEquals(60.0f, TrackingFormatters.clampLeanAngle(180.0f), 0.001f)
        assertEquals(60.0f, TrackingFormatters.clampLeanAngle(Float.POSITIVE_INFINITY), 0.001f)
        assertEquals(-60.0f, TrackingFormatters.clampLeanAngle(Float.NEGATIVE_INFINITY), 0.001f)
    }

    @Test
    fun testLeanAngle_DynamicPeakTracking_LeftAndRightPeaks() {
        val processor = LeanAngleProcessor()

        processor.processAngle(-15.0f) // Left 15
        assertEquals(15.0f, processor.maxLeanAngleLeft, 0.001f)
        assertEquals(0.0f, processor.maxLeanAngleRight, 0.001f)

        processor.processAngle(25.0f) // Right 25
        assertEquals(15.0f, processor.maxLeanAngleLeft, 0.001f)
        assertEquals(25.0f, processor.maxLeanAngleRight, 0.001f)

        processor.processAngle(-45.0f) // Left 45
        assertEquals(45.0f, processor.maxLeanAngleLeft, 0.001f)
        assertEquals(25.0f, processor.maxLeanAngleRight, 0.001f)

        processor.processAngle(55.0f) // Right 55
        assertEquals(45.0f, processor.maxLeanAngleLeft, 0.001f)
        assertEquals(55.0f, processor.maxLeanAngleRight, 0.001f)

        // Overshoot beyond 60 degrees - peak must cap at 60
        processor.processAngle(-89.0f)
        assertEquals(60.0f, processor.maxLeanAngleLeft, 0.001f)

        processor.processAngle(89.0f)
        assertEquals(60.0f, processor.maxLeanAngleRight, 0.001f)
    }

    @Test
    fun testLeanAngle_ConcurrentUpdates_SafetyUnderStress() {
        runBlocking {
            val processor = LeanAngleProcessor()
            val job1 = launch(Dispatchers.Default) {
                for (i in 1..1000) {
                    processor.processAngle((i % 70).toFloat())
                }
            }
            val job2 = launch(Dispatchers.Default) {
                for (i in 1..1000) {
                    processor.processAngle(-(i % 70).toFloat())
                }
            }
            job1.join()
            job2.join()

            // Peaks should be within clamped 0..60 bound
            assertTrue(processor.maxLeanAngleLeft in 0f..60f)
            assertTrue(processor.maxLeanAngleRight in 0f..60f)
        }
    }

    // =========================================================================
    // 2. GPS TELEPORTATION & JITTER FILTERING
    // =========================================================================

    @Test
    fun testGpsJitter_InstantJumpThresholds() {
        val origin = GeoPoint(18.5204, 73.8567)

        // Point roughly 60 meters away (valid in 1 sec, speed = 60 m/s < 70 m/s)
        val p60m = GeoPoint(18.52094, 73.8567)
        val dist60m = origin.distanceTo(p60m)
        assertTrue("Distance should be around 60m", dist60m in 55.0..65.0)
        assertTrue(GpsJitterFilter.isValidStep(origin, p60m, deltaSeconds = 1))

        // Point roughly 150 meters away (invalid in 1 sec, speed = 150 m/s > 70 m/s)
        val p150m = GeoPoint(18.52175, 73.8567)
        val dist150m = origin.distanceTo(p150m)
        assertTrue("Distance should be > 100m", dist150m > 100.0)
        assertFalse(GpsJitterFilter.isValidStep(origin, p150m, deltaSeconds = 1))

        // But 150m over 5 seconds is 30 m/s, which IS valid
        assertTrue(GpsJitterFilter.isValidStep(origin, p150m, deltaSeconds = 5))
    }

    @Test
    fun testGpsJitter_AccuracyGating() {
        val p1 = GeoPoint(18.5204, 73.8567)
        val p2 = GeoPoint(18.5205, 73.8567)

        // Exact boundary for accuracy: MAX_ACCEPTABLE_ACCURACY_METERS = 25.0f
        assertTrue(GpsJitterFilter.isValidStep(p1, p2, deltaSeconds = 1, accuracyMeters = 25.0f))
        assertFalse(GpsJitterFilter.isValidStep(p1, p2, deltaSeconds = 1, accuracyMeters = 25.1f))
        assertFalse(GpsJitterFilter.isValidStep(p1, p2, deltaSeconds = 1, accuracyMeters = 100.0f))
    }

    @Test
    fun testGpsJitter_ZeroDuration_DivideByZeroProtection() {
        val p1 = GeoPoint(18.5204, 73.8567)
        val p2 = GeoPoint(18.5205, 73.8567)

        // deltaSeconds = 0 coerced to 1 in GpsJitterFilter
        val valid = GpsJitterFilter.isValidStep(p1, p2, deltaSeconds = 0)
        // Distance is ~11m in 1 sec = 11 m/s < 70 m/s, so step is valid without ArithmeticException
        assertTrue(valid)

        // Negative deltaSeconds also coerced to at least 1
        val validNegative = GpsJitterFilter.isValidStep(p1, p2, deltaSeconds = -5)
        assertTrue(validNegative)
    }

    @Test
    fun testTracking_ZeroAndNegativeDuration_AverageSpeedProtection() {
        // Zero duration returns 0
        assertEquals(0f, TrackingFormatters.calculateAverageSpeed(1000f, 0L), 0.001f)
        // Negative duration returns 0
        assertEquals(0f, TrackingFormatters.calculateAverageSpeed(1000f, -10L), 0.001f)
        // Zero distance returns 0
        assertEquals(0f, TrackingFormatters.calculateAverageSpeed(0f, 3600L), 0.001f)
    }

    @Test
    fun testElevation_NegativeElevationBelowSeaLevel() {
        val tracker = ElevationTracker()

        // Dead Sea / coastal depression: -50m
        tracker.updateElevation(-50.0f)
        assertEquals(-50.0f, tracker.currentElevationMeters, 0.001f)
        assertEquals(0.0f, tracker.elevationGainMeters, 0.001f)

        // Climb from -50m to -40m (+10m gain, above 1.5m deadband)
        tracker.updateElevation(-40.0f)
        assertEquals(-40.0f, tracker.currentElevationMeters, 0.001f)
        assertEquals(10.0f, tracker.elevationGainMeters, 0.001f)

        // Micro-jitter from -40m to -39.0m (+1.0m, within 1.5m deadband) -> no gain added
        tracker.updateElevation(-39.0f)
        assertEquals(10.0f, tracker.elevationGainMeters, 0.001f)

        // Descent does not increase elevation gain
        tracker.updateElevation(-60.0f)
        assertEquals(-60.0f, tracker.currentElevationMeters, 0.001f)
        assertEquals(10.0f, tracker.elevationGainMeters, 0.001f)
    }

    @Test
    fun testGps_ExtremeCoordinateBoundaries() {
        // Equator and Prime Meridian
        val p0 = GeoPoint(0.0, 0.0)
        assertEquals(0.0, p0.latitude, 0.0001)
        assertEquals(0.0, p0.longitude, 0.0001)

        // North and South Poles
        val northPole = GeoPoint(90.0, 0.0)
        val southPole = GeoPoint(-90.0, 0.0)
        val poleDist = northPole.distanceTo(southPole)
        // Distance roughly 20,015 km (half circumference)
        assertTrue(poleDist > 20_000_000.0)

        // Antimeridian (-180.0 and +180.0)
        val west180 = GeoPoint(0.0, -180.0)
        val east180 = GeoPoint(0.0, 180.0)
        val antiDist = west180.distanceTo(east180)
        assertEquals(0.0, antiDist, 0.1) // Same meridian
    }

    // =========================================================================
    // 3. HARD BRAKING THRESHOLD & TRANSIENT DETECTIONS
    // =========================================================================

    @Test
    fun testHardBraking_BorderlineDecelerations() {
        val detector = HardBrakingDetector(thresholdKmhPerSec = 15.0f)

        // Initial reference speed: 60 km/h at t=1000ms
        detector.evaluate(60.0f, 1000L, 18.5204, 73.8567)

        // Case A: 14.9 km/h drop in 1.0s (deceleration = 14.9 km/h/s <= 15.0f) -> NO event
        val eventSub = detector.evaluate(45.1f, 2000L, 18.5204, 73.8567)
        assertNull("14.9 km/h/s should NOT trigger hard braking", eventSub)

        // Reset
        detector.reset()
        detector.evaluate(60.0f, 1000L, 18.5204, 73.8567)

        // Case B: Exactly 15.0 km/h drop in 1.0s (deceleration = 15.0 km/h/s <= 15.0f) -> NO event (> is required)
        val eventExact = detector.evaluate(45.0f, 2000L, 18.5204, 73.8567)
        assertNull("15.0 km/h/s boundary should NOT trigger hard braking (> 15 required)", eventExact)

        // Reset
        detector.reset()
        detector.evaluate(60.0f, 1000L, 18.5204, 73.8567)

        // Case C: 15.1 km/h drop in 1.0s (deceleration = 15.1 km/h/s > 15.0f) -> TRIGGERS event
        val eventSuper = detector.evaluate(44.9f, 2000L, 18.5204, 73.8567)
        assertNotNull("15.1 km/h/s MUST trigger hard braking event", eventSuper)
        assertEquals(15.1f, eventSuper?.decelerationKmhPerSec ?: 0f, 0.05f)
    }

    @Test
    fun testHardBraking_SuddenAccelerationSpikes_Ignored() {
        val detector = HardBrakingDetector(thresholdKmhPerSec = 15.0f)

        detector.evaluate(20.0f, 1000L, 18.5204, 73.8567)
        // Sudden acceleration from 20 km/h to 120 km/h in 1.0s
        val accelEvent = detector.evaluate(120.0f, 2000L, 18.5204, 73.8567)
        assertNull("Rapid acceleration must never trigger hard braking", accelEvent)
    }

    @Test
    fun testHardBraking_RapidVelocityOscillation() {
        val detector = HardBrakingDetector(thresholdKmhPerSec = 15.0f)
        var hardBrakeCount = 0

        var currentTime = 1000L
        detector.evaluate(60.0f, currentTime, 18.5204, 73.8567)

        // Oscillate between 60 km/h and 30 km/h every 500ms
        // Drop of 30 km/h in 0.5s = 60 km/h/s (> 15) -> Triggers brake
        // Rise of 30 km/h in 0.5s = Accel -> No event
        for (i in 1..6) {
            currentTime += 500L
            val speed = if (i % 2 == 1) 30.0f else 60.0f
            val event = detector.evaluate(speed, currentTime, 18.5204, 73.8567)
            if (event != null) {
                hardBrakeCount++
            }
        }

        // 3 deceleration cycles occurred
        assertEquals(3, hardBrakeCount)
    }

    @Test
    fun testHardBraking_TimeDiscontinuityGuards() {
        val detector = HardBrakingDetector(thresholdKmhPerSec = 15.0f)

        detector.evaluate(80.0f, 1000L, 18.5204, 73.8567)
        // Long pause: 10 seconds later, speed is 20 km/h
        // deltaSeconds = 10.0s > 2.0s pause guard
        val pauseEvent = detector.evaluate(20.0f, 11000L, 18.5204, 73.8567)
        assertNull("Gaps longer than 2.0s must not trigger hard braking due to pause discontinuity", pauseEvent)

        // Zero delta time (same timestamp)
        val zeroTimeEvent = detector.evaluate(10.0f, 11000L, 18.5204, 73.8567)
        assertNull("Zero delta time must not produce arithmetic anomaly", zeroTimeEvent)
    }

    // =========================================================================
    // 4. CURATED RIDES & TRIP HISTORY STRESS
    // =========================================================================

    @Test
    fun testTripLog_MassiveBreadcrumbTrail_50kPointsMemorySafety() {
        val massiveBreadcrumbs = ArrayList<BreadcrumbPoint>(50_000)
        val startTime = 1700000000000L

        for (i in 0 until 50_000) {
            massiveBreadcrumbs.add(
                BreadcrumbPoint(
                    latitude = 18.5204 + (i * 0.00001),
                    longitude = 73.8567 + (i * 0.00001),
                    timestampMillis = startTime + (i * 1000L),
                    speedKmh = 45.0f + (i % 30),
                    leanAngleDegrees = (i % 40) - 20f,
                    elevationMeters = 550f + (i % 100)
                )
            )
        }

        assertEquals(50_000, massiveBreadcrumbs.size)

        val massiveTrip = TripRecord(
            id = "trip_massive_50k",
            vehicleVin = "MD2A11CY4P8712391",
            vehicleModelName = "Pulsar NS400Z",
            title = "Endurance 50,000 Points Tour",
            startTimeMillis = startTime,
            endTimeMillis = startTime + (50_000 * 1000L),
            distanceKm = 520.5f,
            durationSeconds = 50_000L,
            maxSpeedKmh = 118.0f,
            avgSpeedKmh = 37.5f,
            maxLeanAngleLeft = 32.0f,
            maxLeanAngleRight = 35.0f,
            hardBrakingCount = 12,
            elevationGainMeters = 1450f,
            startLocationName = "Pune",
            endLocationName = "Goa",
            breadcrumbPoints = massiveBreadcrumbs
        )

        assertEquals(50_000, massiveTrip.breadcrumbPoints.size)
        val stored = massiveTrip.toStoredTrip()
        assertEquals(520.5f, stored.distanceKm, 0.001f)
    }

    @Test
    fun testTripLog_MassiveBreadcrumbTrail_100kPointsMemorySafety() {
        val breadcrumbs100k = ArrayList<BreadcrumbPoint>(100_000)
        val startTime = 1700000000000L

        for (i in 0 until 100_000) {
            breadcrumbs100k.add(
                BreadcrumbPoint(
                    latitude = 18.0 + (i * 0.000005),
                    longitude = 73.0 + (i * 0.000005),
                    timestampMillis = startTime + (i * 1000L),
                    speedKmh = 60.0f,
                    leanAngleDegrees = 15.0f,
                    elevationMeters = 600f
                )
            )
        }

        assertEquals(100_000, breadcrumbs100k.size)

        val trip100k = TripRecord(
            id = "trip_100k",
            vehicleVin = "MD2A11CY4P8712391",
            vehicleModelName = "Pulsar NS400Z",
            title = "100k Mega Trail",
            startTimeMillis = startTime,
            endTimeMillis = startTime + (100_000 * 1000L),
            distanceKm = 1000f,
            durationSeconds = 100_000L,
            maxSpeedKmh = 120f,
            avgSpeedKmh = 45f,
            maxLeanAngleLeft = 25f,
            maxLeanAngleRight = 25f,
            hardBrakingCount = 5,
            elevationGainMeters = 800f,
            startLocationName = "Origin",
            endLocationName = "Destination",
            breadcrumbPoints = breadcrumbs100k
        )

        assertEquals(100_000, trip100k.breadcrumbPoints.size)
    }

    @Test
    fun testTripLog_EmptyTripCreationAndDefaulting() {
        runBlocking {
            val repo = DefaultTripRepository()
            val initialCount = repo.getAllTrips().size

            // Start session and immediately finish with no breadcrumbs
            repo.startRideSession("MD2A11CY4P8712391", "Pulsar NS400Z", "Home Garage")
            val finishResult = repo.finishRideSession("Workplace Office", null)
            assertTrue(finishResult.isSuccess)

            val createdTrip = finishResult.getOrThrow()
            assertEquals("Home Garage to Workplace Office Ride", createdTrip.title)
            assertEquals(0f, createdTrip.distanceKm, 0.001f)
            assertEquals(0, createdTrip.breadcrumbPoints.size)
            assertEquals(0, createdTrip.hardBrakingCount)

            val updatedTrips = repo.getAllTrips()
            assertEquals(initialCount + 1, updatedTrips.size)
        }
    }

    @Test
    fun testTripLog_TitleDefaulting_EdgeCases() {
        assertEquals("Goa Highway Ride", TripRecord.resolveTitle("Goa Highway Ride", "Pune", "Goa"))
        assertEquals("Pune to Goa Ride", TripRecord.resolveTitle(null, "Pune", "Goa"))
        assertEquals("Pune to Goa Ride", TripRecord.resolveTitle("", "Pune", "Goa"))
        assertEquals("Pune to Goa Ride", TripRecord.resolveTitle("   ", "Pune", "Goa"))
        assertEquals("A to B Ride", TripRecord.resolveTitle("\t\n", "A", "B"))
    }

    @Test
    fun testCuratedToursCatalog_IntegrityAndUniqueness() {
        val tours = CuratedToursCatalog.tours
        assertEquals(5, tours.size)

        // All IDs must be unique
        val ids = tours.map { it.id }
        assertEquals(ids.distinct().size, tours.size)

        // All asset keys must match survey specification
        val expectedKeys = setOf("tripkanyakumari", "tripkashmir", "tripkerala", "tripmumbai", "agrafort")
        assertEquals(expectedKeys, CuratedToursCatalog.tourKeys.toSet())

        // Positive distances and durations
        tours.forEach { tour ->
            assertTrue("${tour.id} distance must be positive", tour.distanceKm > 0f)
            assertTrue("${tour.id} duration must be positive", tour.estimatedDurationHours > 0f)
            assertFalse("${tour.id} scenic highlights must not be empty", tour.scenicHighlights.isEmpty())
        }
    }

    @Test
    fun testTripRepository_NonExistentTripDeletion_FailsCleanly() {
        runBlocking {
            val repo = DefaultTripRepository()
            val deleteResult = repo.deleteTrip("non_existent_id_9999")
            assertTrue(deleteResult.isFailure)
        }
    }

    // =========================================================================
    // 5. DOCUMENT LOCKER VALIDATION & PAYLOAD INTEGRITY
    // =========================================================================

    @Test
    fun testDocLocker_PayloadSizeBounds() {
        // Zero-byte
        assertFalse(DocumentValidator.isValidImageUpload(0L))

        // Negative size
        assertFalse(DocumentValidator.isValidImageUpload(-1L))
        assertFalse(DocumentValidator.isValidImageUpload(-1024L))

        // Lower boundary: 1024 bytes (1 KB)
        assertFalse(DocumentValidator.isValidImageUpload(1023L))
        assertTrue(DocumentValidator.isValidImageUpload(1024L))
        assertTrue(DocumentValidator.isValidImageUpload(1025L))

        // Upper boundary: 5,242,880 bytes (5 MB)
        assertTrue(DocumentValidator.isValidImageUpload(5_242_879L))
        assertTrue(DocumentValidator.isValidImageUpload(5_242_880L))
        assertFalse(DocumentValidator.isValidImageUpload(5_242_881L))
        assertFalse(DocumentValidator.isValidImageUpload(10_000_000L))
    }

    @Test
    fun testDocLocker_Md5ChecksumVerification() {
        runBlocking {
            val repo = DefaultDocumentLockerRepository()
            val doc = repo.getDocumentById("doc_rc")!!

            val sampleData = "BAJAJ_PULSAR_RC_OFFICIAL_DOCUMENT_PAYLOAD".toByteArray(Charsets.UTF_8)
            // Pad to valid size (2048 bytes)
            val validPayload = ByteArray(2048) { idx ->
                if (idx < sampleData.size) sampleData[idx] else 0x55.toByte()
            }

            val syncFrame = repo.prepareClusterSyncPayload(doc, validPayload)
            assertNotNull(syncFrame)
            assertTrue(syncFrame.size > validPayload.size)

            // Verify MD5 calculated by ClusterDocumentSync matches standard Java MessageDigest
            val expectedMd5 = MessageDigest.getInstance("MD5").digest(validPayload).joinToString("") {
                "%02x".format(it)
            }
            val calculatedMd5 = ClusterDocumentSync.calculateMd5Hash(validPayload)
            assertEquals(expectedMd5, calculatedMd5)
        }
    }

    @Test
    fun testDocLocker_CorruptedMd5Verification_Rejected() {
        val sampleData = ByteArray(2048) { 0x42.toByte() }
        val validMd5 = ClusterDocumentSync.calculateMd5Hash(sampleData)

        // Exact match passes
        assertTrue(ClusterDocumentSync.verifyDocumentSyncHash(sampleData, validMd5))
        // Case-insensitivity passes
        assertTrue(ClusterDocumentSync.verifyDocumentSyncHash(sampleData, validMd5.uppercase()))

        // 1-character corrupted MD5 rejected
        val corruptedMd5 = validMd5.replaceRange(0, 1, if (validMd5[0] == 'a') "b" else "a")
        assertFalse(ClusterDocumentSync.verifyDocumentSyncHash(sampleData, corruptedMd5))

        // Truncated MD5 rejected
        assertFalse(ClusterDocumentSync.verifyDocumentSyncHash(sampleData, validMd5.substring(0, 16)))

        // Empty MD5 rejected
        assertFalse(ClusterDocumentSync.verifyDocumentSyncHash(sampleData, ""))

        // Tampered payload rejected against original MD5
        val tamperedData = sampleData.clone()
        tamperedData[100] = 0x99.toByte()
        assertFalse(ClusterDocumentSync.verifyDocumentSyncHash(tamperedData, validMd5))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDocLocker_OversizedPayloadRejection_Throws() {
        runBlocking {
            val repo = DefaultDocumentLockerRepository()
            val doc = repo.getDocumentById("doc_rc")!!
            val oversized = ByteArray(5_242_881) // 5MB + 1 byte
            repo.prepareClusterSyncPayload(doc, oversized)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDocLocker_UndersizedPayloadRejection_Throws() {
        runBlocking {
            val repo = DefaultDocumentLockerRepository()
            val doc = repo.getDocumentById("doc_rc")!!
            val undersized = ByteArray(500) // < 1024 bytes
            repo.prepareClusterSyncPayload(doc, undersized)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDocLocker_ZeroBytePayloadRejection_Throws() {
        runBlocking {
            val repo = DefaultDocumentLockerRepository()
            val doc = repo.getDocumentById("doc_rc")!!
            val zeroBytes = ByteArray(0)
            repo.prepareClusterSyncPayload(doc, zeroBytes)
        }
    }

    // =========================================================================
    // 6. DOCUMENT NUMBER SANITIZATION & INJECTION ATTACKS
    // =========================================================================

    @Test
    fun testDocNumber_Sanitization_SqlInjectionVectors() {
        // Classic SQL injection attempts
        assertEquals("OR11", DocumentValidator.sanitizeDocumentNumber("' OR '1'='1"))
        assertEquals("ADMIN--", DocumentValidator.sanitizeDocumentNumber("admin'--"))
        assertEquals("DROPTABLEDOCUMENTS--", DocumentValidator.sanitizeDocumentNumber("DROP TABLE documents; --"))
        assertEquals("UNIONSELECT123--", DocumentValidator.sanitizeDocumentNumber("UNION SELECT 1, 2, 3; --"))
        assertEquals("1OR11", DocumentValidator.sanitizeDocumentNumber("1' OR '1' = '1"))
    }

    @Test
    fun testDocNumber_Sanitization_UnicodeAndControlCharacters() {
        // Null bytes, bell characters, newlines, carriage returns, tabs
        val nastyString = "\u0000\u0007\t\n\rMH-12/2026/RC\u001B\u200B"
        assertEquals("MH-12/2026/RC", DocumentValidator.sanitizeDocumentNumber(nastyString))
    }

    @Test
    fun testDocNumber_Sanitization_SpecialPunctuationAndWhitespace() {
        val messyInput = "  !@#$%^&*()_+{}[]|\":;?><,.~`  DL-14 2011 0012345  "
        // Retains only A-Z, 0-9, /, - (space is stripped)
        assertEquals("DL-1420110012345", DocumentValidator.sanitizeDocumentNumber(messyInput))
    }

    @Test
    fun testDocNumber_Sanitization_EmptyAndBlankStrings() {
        assertEquals("", DocumentValidator.sanitizeDocumentNumber(""))
        assertEquals("", DocumentValidator.sanitizeDocumentNumber("     "))
        assertEquals("", DocumentValidator.sanitizeDocumentNumber("\n\t\r"))
        assertEquals("", DocumentValidator.sanitizeDocumentNumber("!@#$%^&*()"))
    }

    // =========================================================================
    // 7. EXPIRY ALERT ENGINE BOUNDARY TIMES & LEAP YEARS
    // =========================================================================

    @Test
    fun testExpiryAlertEngine_EpochZeroAndPastDates() {
        val now = 1772600000000L // 2026 epoch

        // Epoch 0 (1970-01-01) -> EXPIRED
        assertEquals(DocExpiryStatus.EXPIRED, ExpiryAlertEngine.evaluate(0L, now))
        assertTrue(ExpiryAlertEngine.calculateRemainingDays(0L, now) < 0)

        // Negative epoch (-1000L) -> EXPIRED
        assertEquals(DocExpiryStatus.EXPIRED, ExpiryAlertEngine.evaluate(-1000L, now))

        // Exactly now - 1ms -> EXPIRED
        assertEquals(DocExpiryStatus.EXPIRED, ExpiryAlertEngine.evaluate(now - 1L, now))
    }

    @Test
    fun testExpiryAlertEngine_ExactThresholdBoundaries() {
        val now = 1772600000000L // arbitrary fixed point in time
        val oneDayMillis = 86_400_000L

        // Exactly 30 days remaining (boundary of EXPIRING_SOON)
        val exactly30Days = now + (30L * oneDayMillis)
        assertEquals(DocExpiryStatus.EXPIRING_SOON, ExpiryAlertEngine.evaluate(exactly30Days, now))
        assertEquals(30L, ExpiryAlertEngine.calculateRemainingDays(exactly30Days, now))

        // Exactly 31 days remaining (boundary of VALID)
        val exactly31Days = now + (31L * oneDayMillis)
        assertEquals(DocExpiryStatus.VALID, ExpiryAlertEngine.evaluate(exactly31Days, now))
        assertEquals(31L, ExpiryAlertEngine.calculateRemainingDays(exactly31Days, now))

        // Exactly 0 days remaining (expires in a few hours today)
        val laterToday = now + (3L * 3600 * 1000L) // +3 hours
        assertEquals(DocExpiryStatus.EXPIRING_SOON, ExpiryAlertEngine.evaluate(laterToday, now))
        assertEquals(0L, ExpiryAlertEngine.calculateRemainingDays(laterToday, now))

        // 1 day remaining
        val tomorrow = now + (1L * oneDayMillis)
        assertEquals(DocExpiryStatus.EXPIRING_SOON, ExpiryAlertEngine.evaluate(tomorrow, now))
        assertEquals(1L, ExpiryAlertEngine.calculateRemainingDays(tomorrow, now))
    }

    @Test
    fun testExpiryAlertEngine_LeapYearHandling() {
        // Leap year 2024: Feb 28 to Feb 29 to Mar 1
        // 2024-02-28 00:00:00 UTC = 1709078400000L
        val feb28_2024 = 1709078400000L
        val feb29_2024 = feb28_2024 + 86_400_000L
        val mar01_2024 = feb29_2024 + 86_400_000L

        // Remaining days across leap day
        assertEquals(1L, ExpiryAlertEngine.calculateRemainingDays(feb29_2024, feb28_2024))
        assertEquals(2L, ExpiryAlertEngine.calculateRemainingDays(mar01_2024, feb28_2024))

        // Leap year 2028: Feb 29, 2028
        // 2028-02-29 00:00:00 UTC = 1835395200000L
        val feb29_2028 = 1835395200000L
        val feb01_2028 = feb29_2028 - (28L * 86_400_000L)
        assertEquals(28L, ExpiryAlertEngine.calculateRemainingDays(feb29_2028, feb01_2028))
        assertEquals(DocExpiryStatus.EXPIRING_SOON, ExpiryAlertEngine.evaluate(feb29_2028, feb01_2028))
    }

    @Test
    fun testExpiryAlertEngine_NegativeRemainingDays() {
        val now = 1772600000000L
        val oneDay = 86_400_000L

        // 1 day expired
        val expired1Day = now - (1L * oneDay)
        assertEquals(-1L, ExpiryAlertEngine.calculateRemainingDays(expired1Day, now))
        assertEquals(DocExpiryStatus.EXPIRED, ExpiryAlertEngine.evaluate(expired1Day, now))

        // 30 days expired
        val expired30Days = now - (30L * oneDay)
        assertEquals(-30L, ExpiryAlertEngine.calculateRemainingDays(expired30Days, now))
        assertEquals(DocExpiryStatus.EXPIRED, ExpiryAlertEngine.evaluate(expired30Days, now))

        // 365 days expired
        val expired1Year = now - (365L * oneDay)
        assertEquals(-365L, ExpiryAlertEngine.calculateRemainingDays(expired1Year, now))
        assertEquals(DocExpiryStatus.EXPIRED, ExpiryAlertEngine.evaluate(expired1Year, now))
    }

    @Test
    fun testExpiryAlertEngine_MillisecondPrecisionAroundBoundaries() {
        val now = 1772600000000L
        val oneDay = 86_400_000L

        // Exactly 30 days + 1 millisecond -> 30 days remaining -> EXPIRING_SOON
        val thirtyDaysPlus1Ms = now + (30L * oneDay) + 1L
        assertEquals(30L, ExpiryAlertEngine.calculateRemainingDays(thirtyDaysPlus1Ms, now))
        assertEquals(DocExpiryStatus.EXPIRING_SOON, ExpiryAlertEngine.evaluate(thirtyDaysPlus1Ms, now))

        // Exactly 31 days - 1 millisecond -> 30 days remaining -> EXPIRING_SOON
        val thirtyOneDaysMinus1Ms = now + (31L * oneDay) - 1L
        assertEquals(30L, ExpiryAlertEngine.calculateRemainingDays(thirtyOneDaysMinus1Ms, now))
        assertEquals(DocExpiryStatus.EXPIRING_SOON, ExpiryAlertEngine.evaluate(thirtyOneDaysMinus1Ms, now))

        // Exactly 31 days + 1 millisecond -> 31 days remaining -> VALID
        val thirtyOneDaysPlus1Ms = now + (31L * oneDay) + 1L
        assertEquals(31L, ExpiryAlertEngine.calculateRemainingDays(thirtyOneDaysPlus1Ms, now))
        assertEquals(DocExpiryStatus.VALID, ExpiryAlertEngine.evaluate(thirtyOneDaysPlus1Ms, now))
    }
}
