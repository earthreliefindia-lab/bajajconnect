package com.openride.companion.unit

import com.openride.companion.data.profile.ProfileRepositoryImpl
import com.openride.companion.data.sos.EmergencySosRepositoryImpl
import com.openride.companion.data.vehicle.VehicleRepositoryImpl
import com.openride.companion.model.EmergencyContact
import com.openride.companion.model.GarageCatalog
import com.openride.companion.model.PowertrainType
import com.openride.companion.model.QrPairingPayload
import com.openride.companion.model.RiderProfile
import com.openride.companion.model.RtoPlateValidator
import com.openride.companion.model.SosBroadcastStatus
import com.openride.companion.model.SosIncidentPayload
import com.openride.companion.model.SosTriggerSource
import com.openride.companion.model.ThemeMode
import com.openride.companion.model.Vehicle
import com.openride.companion.model.VehicleTelemetry
import com.openride.companion.model.VinValidator
import com.openride.companion.ui.screens.sos.SosViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Adversarial and Stress Verification Suite for Milestone 2.
 * Focuses on Features 8 (Profile & Preferences), 9 (Emergency SOS), and 10 (Multi-Vehicle Garage).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdversarialMilestone2Test {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. MULTI-VEHICLE GARAGE & VEHICLE VALIDATION (Feature 10)
    // =========================================================================

    @Test
    fun testVinValidator_RejectsLetters_I_O_Q_AndInvalidLengths() {
        // Authoritative valid VINs
        assertTrue(VinValidator.isValidVin("MD2A11CY4P8712391"))
        assertTrue(VinValidator.isValidVin("MD2A11CY4P8712392"))
        assertTrue(VinValidator.isValidVin("MD2A11CY4P8712393"))

        // Strict rejection of 'I' in any position
        assertFalse(VinValidator.isValidVin("MD2A11CYIP8712391"))
        assertFalse(VinValidator.isValidVin("ID2A11CY4P8712391"))
        assertFalse(VinValidator.isValidVin("MD2A11CY4P871239I"))

        // Strict rejection of 'O' in any position
        assertFalse(VinValidator.isValidVin("MD2A11CYOP8712391"))
        assertFalse(VinValidator.isValidVin("OD2A11CY4P8712391"))
        assertFalse(VinValidator.isValidVin("MD2A11CY4P871239O"))

        // Strict rejection of 'Q' in any position
        assertFalse(VinValidator.isValidVin("MD2A11CYQP8712391"))
        assertFalse(VinValidator.isValidVin("QD2A11CY4P8712391"))
        assertFalse(VinValidator.isValidVin("MD2A11CY4P871239Q"))

        // Invalid lengths (<17 and >17)
        assertFalse(VinValidator.isValidVin(""))
        assertFalse(VinValidator.isValidVin("M"))
        assertFalse(VinValidator.isValidVin("MD2A11CY4P87123"))   // 15 chars
        assertFalse(VinValidator.isValidVin("MD2A11CY4P871239"))  // 16 chars
        assertFalse(VinValidator.isValidVin("MD2A11CY4P87123912")) // 18 chars
        assertFalse(VinValidator.isValidVin("MD2A11CY4P87123912345")) // 21 chars

        // Invalid characters (symbols, spaces)
        assertFalse(VinValidator.isValidVin("MD2A11CY4P871239!"))
        assertFalse(VinValidator.isValidVin("MD2A 11CY4P871239"))

        // Normalization handles outer whitespace and lower-case gracefully
        assertTrue(VinValidator.isValidVin("  MD2A11CY4P8712391  "))
        assertTrue(VinValidator.isValidVin("md2a11cy4p8712391"))
        assertEquals("MD2A11CY4P8712391", VinValidator.normalizeVin("  md2a11cy4p8712391  "))
    }

    @Test
    fun testRtoPlateValidator_ValidatesLengths_8To11_AndStripsFormatting() {
        // Standard formats
        assertTrue(RtoPlateValidator.isValidReg("MH 12 TC 5590")) // 10 alphanumeric
        assertTrue(RtoPlateValidator.isValidReg("MH-12-TC-5590")) // Hyphens stripped
        assertTrue(RtoPlateValidator.isValidReg("DL01AB1234"))    // 10 alphanumeric
        assertTrue(RtoPlateValidator.isValidReg("KA 05 CD 4321")) // 10 alphanumeric

        // Boundary lengths: 8 to 11 characters
        assertTrue(RtoPlateValidator.isValidReg("KA011234"))       // 8 alphanumeric (min valid)
        assertTrue(RtoPlateValidator.isValidReg("KA 01 1234"))     // 8 alphanumeric with spaces
        assertTrue(RtoPlateValidator.isValidReg("MH12TC55901"))    // 11 alphanumeric (max valid)

        // Invalid lengths: < 8 and > 11
        assertFalse(RtoPlateValidator.isValidReg(""))
        assertFalse(RtoPlateValidator.isValidReg("MH"))
        assertFalse(RtoPlateValidator.isValidReg("MH 12"))
        assertFalse(RtoPlateValidator.isValidReg("MH12TC5"))       // 7 alphanumeric
        assertFalse(RtoPlateValidator.isValidReg("MH12TC559012"))  // 12 alphanumeric
        assertFalse(RtoPlateValidator.isValidReg("DL01AB1234567")) // 13 alphanumeric

        // Normalization produces uppercase stripped string
        assertEquals("MH12TC5590", RtoPlateValidator.normalizeReg("  mh - 12 - tc - 5590  "))
    }

    @Test
    fun testQrPairingParser_ParsesValidAndRejectsMalformedPayloads() {
        // 1. Authoritative valid QR code
        val validQr = "BAJAJ:VIN=MD2A11CY4P8712391;MODEL=DOMINAR_400_DUAL_ABS;MAC=AA:BB:CC:DD:EE:FF;PIN=1234"
        val payload1 = QrPairingPayload.parse(validQr)
        assertNotNull(payload1)
        assertEquals("MD2A11CY4P8712391", payload1?.vin)
        assertEquals("DOMINAR_400_DUAL_ABS", payload1?.modelCode)
        assertEquals("AA:BB:CC:DD:EE:FF", payload1?.bluetoothMac)
        assertEquals("1234", payload1?.pairingPin)

        // 2. Malformed prefix rejection
        assertNull(QrPairingPayload.parse("KTM:VIN=MD2A11CY4P8712391;MODEL=DOMINAR_400_DUAL_ABS"))
        assertNull(QrPairingPayload.parse("VIN=MD2A11CY4P8712391;MODEL=DOMINAR_400_DUAL_ABS"))
        assertNull(QrPairingPayload.parse("BAJAJ_VIN=MD2A11CY4P8712391"))
        assertNull(QrPairingPayload.parse(""))

        // 3. Missing VIN or illegal VIN in QR
        assertNull(QrPairingPayload.parse("BAJAJ:MODEL=DOMINAR_400_DUAL_ABS;MAC=AA:BB:CC:DD:EE:FF;PIN=1234"))
        assertNull(QrPairingPayload.parse("BAJAJ:VIN=MD2A11CYIP8712391;MODEL=DOMINAR_400_DUAL_ABS")) // illegal 'I'
        assertNull(QrPairingPayload.parse("BAJAJ:VIN=MD2A11CYQP8712391;MODEL=DOMINAR_400_DUAL_ABS")) // illegal 'Q'
        assertNull(QrPairingPayload.parse("BAJAJ:VIN=SHORT;MODEL=DOMINAR_400_DUAL_ABS"))

        // 4. Missing optional fields (MAC, PIN, MODEL fallback)
        val minimalQr = "BAJAJ:VIN=MD2A11CY4P8712391"
        val minimalPayload = QrPairingPayload.parse(minimalQr)
        assertNotNull(minimalPayload)
        assertEquals("MD2A11CY4P8712391", minimalPayload?.vin)
        assertEquals("DOMINAR_400_DUAL_ABS", minimalPayload?.modelCode) // Default fallback
        assertNull(minimalPayload?.bluetoothMac)
        assertNull(minimalPayload?.pairingPin)

        // 5. Permuted tokens with extra whitespace
        val permutedQr = "BAJAJ: PIN = 4491 ; MODEL = PULSAR_NS400Z ; VIN = MD2A11CY4P8712391 ; MAC = 11:22:33:44:55:66"
        val permutedPayload = QrPairingPayload.parse(permutedQr)
        assertNotNull(permutedPayload)
        assertEquals("MD2A11CY4P8712391", permutedPayload?.vin)
        assertEquals("PULSAR_NS400Z", permutedPayload?.modelCode)
        assertEquals("11:22:33:44:55:66", permutedPayload?.bluetoothMac)
        assertEquals("4491", permutedPayload?.pairingPin)
    }

    @Test
    fun testActiveVehicleSwitch_IceAndEvPlatforms_AndFallbackOnInvalidId() = runTest {
        val repo = VehicleRepositoryImpl()

        // Initial default active vehicle is NS400Z (ICE)
        val initialActive = repo.getActiveVehicle()
        assertNotNull(initialActive)
        assertEquals("BIKE_PULSAR_NS400Z", initialActive?.id)
        assertFalse(initialActive!!.modelSpec.isElectric)
        assertEquals(PowertrainType.ICE, initialActive.modelSpec.powertrainType)

        // Switch to Dominar 400 (ICE)
        val dominarResult = repo.selectActiveVehicle("BIKE_DOMINAR_400")
        assertTrue(dominarResult.isSuccess)
        assertEquals("BIKE_DOMINAR_400", repo.getActiveVehicle()?.id)
        assertEquals("Bajaj Dominar 400 Dual ABS", repo.getActiveVehicle()?.modelSpec?.modelName)
        assertFalse(repo.getActiveVehicle()!!.modelSpec.isElectric)

        // Switch to Chetak EV Premium (EV)
        val chetakResult = repo.selectActiveVehicle("BIKE_CHETAK_EV")
        assertTrue(chetakResult.isSuccess)
        val chetakVehicle = repo.getActiveVehicle()
        assertNotNull(chetakVehicle)
        assertEquals("BIKE_CHETAK_EV", chetakVehicle?.id)
        assertTrue(chetakVehicle!!.modelSpec.isElectric)
        assertEquals(PowertrainType.ELECTRIC, chetakVehicle.modelSpec.powertrainType)
        assertEquals("Battery Level", chetakVehicle.primaryEnergyLabel)

        // Switch by Model Name (Pulsar N160)
        val n160Result = repo.selectActiveVehicle("Bajaj Pulsar N160")
        assertTrue(n160Result.isSuccess)
        assertEquals("BIKE_PULSAR_N160", repo.getActiveVehicle()?.id)

        // Switch to Pulsar N250
        val n250Result = repo.selectActiveVehicle("BIKE_PULSAR_N250")
        assertTrue(n250Result.isSuccess)
        assertEquals("BIKE_PULSAR_N250", repo.getActiveVehicle()?.id)

        // Fallback Behavior: Non-existent vehicle ID or random string
        val currentBeforeInvalid = repo.getActiveVehicle()
        val invalidResult = repo.selectActiveVehicle("NON_EXISTENT_VEHICLE_999")
        assertTrue("Fallback must succeed without crashing", invalidResult.isSuccess)
        assertEquals("Non-existent vehicle switch must retain previously active vehicle",
            currentBeforeInvalid?.id, repo.getActiveVehicle()?.id)
    }

    // =========================================================================
    // 2. TELEMETRY WARNING THRESHOLDS
    // =========================================================================

    @Test
    fun testTelemetryWarningThresholds_Fuel_EvBattery_12VBattery_AndServiceCountdown() {
        // Fuel Level Warnings: Low at <= 15%, Critical at <= 8%
        val telNormalFuel = VehicleTelemetry(vehicleId = "V1", fuelLevelPercent = 16)
        assertFalse(telNormalFuel.isFuelLow)
        assertFalse(telNormalFuel.isFuelCritical)

        val telLowFuel = VehicleTelemetry(vehicleId = "V1", fuelLevelPercent = 15)
        assertTrue(telLowFuel.isFuelLow)
        assertFalse(telLowFuel.isFuelCritical)

        val telBorderFuel = VehicleTelemetry(vehicleId = "V1", fuelLevelPercent = 9)
        assertTrue(telBorderFuel.isFuelLow)
        assertFalse(telBorderFuel.isFuelCritical)

        val telCriticalFuel = VehicleTelemetry(vehicleId = "V1", fuelLevelPercent = 8)
        assertTrue(telCriticalFuel.isFuelLow)
        assertTrue(telCriticalFuel.isFuelCritical)

        val telEmptyFuel = VehicleTelemetry(vehicleId = "V1", fuelLevelPercent = 0)
        assertTrue(telEmptyFuel.isFuelLow)
        assertTrue(telEmptyFuel.isFuelCritical)

        // EV Battery Warnings: Low at <= 20%, Critical at <= 10%
        val telNormalEv = VehicleTelemetry(vehicleId = "V1", batterySocPercent = 21)
        assertFalse(telNormalEv.isEvBatteryLow)
        assertFalse(telNormalEv.isEvBatteryCritical)

        val telLowEv = VehicleTelemetry(vehicleId = "V1", batterySocPercent = 20)
        assertTrue(telLowEv.isEvBatteryLow)
        assertFalse(telLowEv.isEvBatteryCritical)

        val telBorderEv = VehicleTelemetry(vehicleId = "V1", batterySocPercent = 11)
        assertTrue(telBorderEv.isEvBatteryLow)
        assertFalse(telBorderEv.isEvBatteryCritical)

        val telCriticalEv = VehicleTelemetry(vehicleId = "V1", batterySocPercent = 10)
        assertTrue(telCriticalEv.isEvBatteryLow)
        assertTrue(telCriticalEv.isEvBatteryCritical)

        val telZeroEv = VehicleTelemetry(vehicleId = "V1", batterySocPercent = 0)
        assertTrue(telZeroEv.isEvBatteryLow)
        assertTrue(telZeroEv.isEvBatteryCritical)

        // 12V Starter Battery Warning: Critical at < 11.8V
        val telNormal12V = VehicleTelemetry(vehicleId = "V1", battery12VVoltage = 12.8f)
        assertFalse(telNormal12V.isStarterBatteryCritical)

        val telThreshold12V = VehicleTelemetry(vehicleId = "V1", battery12VVoltage = 11.8f)
        assertFalse(telThreshold12V.isStarterBatteryCritical) // Exactly 11.8 is not strictly < 11.8

        val telCritical12V = VehicleTelemetry(vehicleId = "V1", battery12VVoltage = 11.79f)
        assertTrue(telCritical12V.isStarterBatteryCritical)

        val telDead12V = VehicleTelemetry(vehicleId = "V1", battery12VVoltage = 10.5f)
        assertTrue(telDead12V.isStarterBatteryCritical)

        // Service countdown calculation: 5000 - (odometer % 5000)
        assertEquals(5000, Vehicle.calculateRemainingServiceKm(0))
        assertEquals(4999, Vehicle.calculateRemainingServiceKm(1))
        assertEquals(4000, Vehicle.calculateRemainingServiceKm(1000))
        assertEquals(880, Vehicle.calculateRemainingServiceKm(4120))
        assertEquals(1, Vehicle.calculateRemainingServiceKm(4999))
        assertEquals(5000, Vehicle.calculateRemainingServiceKm(5000)) // Exact interval resets to 5000
        assertEquals(4999, Vehicle.calculateRemainingServiceKm(5001))
        assertEquals(5000, Vehicle.calculateRemainingServiceKm(10000))
        assertEquals(2550, Vehicle.calculateRemainingServiceKm(12450))
        assertEquals(5000, Vehicle.calculateRemainingServiceKm(-50)) // Negative odometer handling
    }

    // =========================================================================
    // 3. EMERGENCY SOS SAFETY CONTRACTS (Feature 9)
    // =========================================================================

    @Test
    fun testEmergencySos_CapOfFive_AndDuplicateRejection() = runTest {
        val repo = EmergencySosRepositoryImpl()
        // Repository comes pre-seeded with 2 contacts
        val initialContacts = repo.getContacts()
        assertEquals(2, initialContacts.size)

        // Rejection of duplicate phone number (+919876543210 exists)
        val duplicateContact = EmergencyContact(
            name = "Imposter Anita",
            phone = "+919876543210",
            relationship = "Friend"
        )
        val dupResult = repo.addContact(duplicateContact)
        assertFalse("Duplicate phone number must be rejected", dupResult.isSuccess)

        // Duplicate with formatting (whitespace)
        val formattedDup = EmergencyContact(
            name = "Formatted Dup",
            phone = "+91 98765 43210",
            relationship = "Friend"
        )
        val formattedDupResult = repo.addContact(formattedDup)
        assertFalse("Formatted duplicate phone number must also be rejected", formattedDupResult.isSuccess)

        // Add 3 unique contacts to reach cap of 5
        assertTrue(repo.addContact(EmergencyContact(name = "C3", phone = "+919876543213", relationship = "Sister")).isSuccess)
        assertTrue(repo.addContact(EmergencyContact(name = "C4", phone = "+919876543214", relationship = "Colleague")).isSuccess)
        assertTrue(repo.addContact(EmergencyContact(name = "C5", phone = "+919876543215", relationship = "Doctor")).isSuccess)
        assertEquals(5, repo.getContacts().size)

        // Attempting to add 6th contact must be rejected
        val overflowContact = EmergencyContact(
            name = "C6 Overflow",
            phone = "+919876543216",
            relationship = "Friend"
        )
        val overflowResult = repo.addContact(overflowContact)
        assertFalse("Attempting to add a 6th emergency contact must fail", overflowResult.isSuccess)
        assertEquals(5, repo.getContacts().size)
    }

    @Test
    fun testEmergencySos_RsaFallback_WhenContactListIsEmpty() {
        val emptyPayload = SosIncidentPayload(
            triggerSource = SosTriggerSource.MANUAL_BUTTON,
            latitude = 18.5204,
            longitude = 73.8567,
            riderName = "Ketan",
            recipients = emptyList()
        )

        // Must resolve to official Bajaj 24x7 RSA Hotline
        assertEquals("1800 233 2453", emptyPayload.resolvePrimaryRecipient())
        assertEquals("1800 233 2453", EmergencyContact.AUTHORITATIVE_RSA_PHONE)
    }

    @Test
    fun testEmergencySos_FiveSecondGracefulAbort_AndDispatchPayloadGeneration() = runTest {
        val sosRepo = EmergencySosRepositoryImpl()
        val profileRepo = ProfileRepositoryImpl()
        val viewModel = SosViewModel(sosRepo, profileRepo)

        // 1. Trigger manual SOS
        viewModel.triggerManualSos()
        testScheduler.advanceTimeBy(100) // Start coroutine

        assertTrue(viewModel.uiState.value.isCountdownActive)
        assertEquals(5, viewModel.uiState.value.countdownRemaining)

        // 2. Advance time by 2 seconds
        testScheduler.advanceTimeBy(2000)
        assertEquals(3, viewModel.uiState.value.countdownRemaining)
        assertTrue(viewModel.uiState.value.isCountdownActive)

        // 3. User cancels SOS gracefully within 5-second countdown
        viewModel.cancelSos()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCountdownActive)
        assertFalse(viewModel.uiState.value.isDispatchedBannerVisible)
        assertEquals(SosBroadcastStatus.CANCELLED, sosRepo.activeSosState.value?.status)

        // 4. Trigger again and let full 5 seconds elapse
        viewModel.triggerManualSos()
        testScheduler.advanceTimeBy(100)
        assertTrue(viewModel.uiState.value.isCountdownActive)

        testScheduler.advanceTimeBy(5500)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCountdownActive)
        assertTrue(viewModel.uiState.value.isDispatchedBannerVisible)
        assertEquals(SosBroadcastStatus.DISPATCHED, sosRepo.activeSosState.value?.status)

        // 5. GPS payload URL generation check
        val payload = sosRepo.activeSosState.value!!
        val smsText = payload.buildSmsText()
        assertTrue(smsText.contains("https://maps.google.com/?q=18.5204,73.8567"))
    }

    @Test
    fun testEmergencySos_MidRideBleDisconnectAlert_TriggerSource() {
        val bleDisconnectPayload = SosIncidentPayload(
            triggerSource = SosTriggerSource.BLE_DISCONNECTED_MID_RIDE,
            latitude = 18.5204,
            longitude = 73.8567,
            riderName = "Ketan",
            recipients = listOf("+919876543210")
        )

        val smsText = bleDisconnectPayload.buildSmsText()
        assertTrue(smsText.contains("Breakdown detected"))
        assertTrue(smsText.contains("https://maps.google.com/?q=18.5204,73.8567"))
    }

    // =========================================================================
    // 4. PROFILE PREFERENCES (Feature 8)
    // =========================================================================

    @Test
    fun testProfilePreferences_EightMedicalBloodGroupsValidation() {
        // Must contain exactly 8 medical blood groups
        assertEquals(8, RiderProfile.VALID_BLOOD_GROUPS.size)

        val expectedGroups = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        for (bg in expectedGroups) {
            assertTrue("Blood group $bg must be valid", RiderProfile.isValidBloodGroup(bg))
        }

        // Invalid blood groups
        val invalidGroups = listOf("A", "B", "AB", "O", "C+", "H-", "Z+", "", "   ", "O Positive")
        for (invalid in invalidGroups) {
            assertFalse("Invalid blood group $invalid must be rejected", RiderProfile.isValidBloodGroup(invalid))
        }
    }

    @Test
    fun testProfilePreferences_EmailRfcRegex_AndNameFormattingTrimming() {
        // Valid RFC compliant email addresses
        assertTrue(RiderProfile.isValidEmail("ketan.sharma@example.com"))
        assertTrue(RiderProfile.isValidEmail("rider+tag@bajajconnect.in"))
        assertTrue(RiderProfile.isValidEmail("support@openride.companion.org"))

        // Invalid email addresses based on contract regex
        assertFalse(RiderProfile.isValidEmail("plainaddress"))
        assertFalse(RiderProfile.isValidEmail("@missingusername.com"))
        assertFalse(RiderProfile.isValidEmail("username@domain"))
        assertFalse(RiderProfile.isValidEmail("user@"))
        assertFalse(RiderProfile.isValidEmail("username@domain.c")) // Single letter TLD rejected by {2,}
        assertFalse(RiderProfile.isValidEmail(""))

        // Name trimming, capitalization, and 50 chars clamping
        assertEquals("Rohit Verma", RiderProfile.formatRiderName("   rohit   verma   "))
        assertEquals("Dr Anita Sen", RiderProfile.formatRiderName("dr anita sen"))

        val veryLongName = "Alexander".repeat(10) // 90 characters
        val formattedLong = RiderProfile.formatRiderName(veryLongName)
        assertTrue(formattedLong.length <= 50)
    }

    @Test
    fun testProfilePreferences_DistanceUnitConversionFactor() {
        // Factor: 1 km = 0.621371 miles
        val factor = 0.621371f

        assertEquals(62.1371f, RiderProfile.convertDistance(100f, "miles"), 0.001f)
        assertEquals(factor, RiderProfile.convertDistance(1f, "miles"), 0.0001f)
        assertEquals(0f, RiderProfile.convertDistance(0f, "miles"), 0.0001f)
        assertEquals(155.34275f, RiderProfile.convertDistance(250f, "miles"), 0.001f)

        // When unit is km, returns unchanged
        assertEquals(100f, RiderProfile.convertDistance(100f, "km"), 0.0001f)
        assertEquals(250f, RiderProfile.convertDistance(250f, "km"), 0.0001f)
    }
}
