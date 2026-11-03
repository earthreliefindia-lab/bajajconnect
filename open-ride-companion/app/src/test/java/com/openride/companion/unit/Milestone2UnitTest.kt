package com.openride.companion.unit

import com.openride.companion.data.auth.AuthRepositoryImpl
import com.openride.companion.data.auth.AuthSessionManager
import com.openride.companion.data.preferences.InMemorySessionPreferences
import com.openride.companion.data.profile.ProfileRepositoryImpl
import com.openride.companion.data.sos.EmergencySosRepositoryImpl
import com.openride.companion.data.vehicle.VehicleRepositoryImpl
import com.openride.companion.model.CountryCatalog
import com.openride.companion.model.EmergencyContact
import com.openride.companion.model.GarageCatalog
import com.openride.companion.model.QrPairingPayload
import com.openride.companion.model.RiderProfile
import com.openride.companion.model.RtoPlateValidator
import com.openride.companion.model.SessionState
import com.openride.companion.model.SosBroadcastStatus
import com.openride.companion.model.SosIncidentPayload
import com.openride.companion.model.SosTriggerSource
import com.openride.companion.model.ThemeMode
import com.openride.companion.model.Vehicle
import com.openride.companion.model.VinValidator
import com.openride.companion.ui.screens.auth.LoginViewModel
import com.openride.companion.ui.screens.auth.OtpViewModel
import com.openride.companion.ui.screens.garage.GarageViewModel
import com.openride.companion.ui.screens.onboarding.OnboardingCatalog
import com.openride.companion.ui.screens.onboarding.OnboardingViewModel
import com.openride.companion.ui.screens.profile.ProfileViewModel
import com.openride.companion.ui.screens.sos.SosViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class Milestone2UnitTest {

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
    // 1. FEATURE 5: APP TOUR ONBOARDING
    // =========================================================================

    @Test
    fun testOnboardingCatalog_ContainsThreeVerifiedSlides() {
        assertEquals(3, OnboardingCatalog.slides.size)
        assertEquals("navigatetrack.png", OnboardingCatalog.slides[0].imageAsset)
        assertEquals("notificationreminder.png", OnboardingCatalog.slides[1].imageAsset)
        assertEquals("servicebooking.png", OnboardingCatalog.slides[2].imageAsset)
    }

    @Test
    fun testOnboardingViewModel_NavigationProgressionAndCompletion() = runTest {
        val prefs = InMemorySessionPreferences()
        val viewModel = OnboardingViewModel(prefs)

        assertEquals(0, viewModel.uiState.value.currentPage)
        assertEquals("NEXT", viewModel.uiState.value.actionButtonText)

        viewModel.onPageChanged(1)
        assertEquals(1, viewModel.uiState.value.currentPage)

        viewModel.onPageChanged(2)
        assertEquals(2, viewModel.uiState.value.currentPage)
        assertEquals("GET STARTED", viewModel.uiState.value.actionButtonText)

        var navigated = false
        viewModel.onNextClicked { navigated = true }
        testScheduler.advanceUntilIdle()

        assertTrue(navigated)
        assertTrue(prefs.isOnboardingCompleted())
    }

    @Test
    fun testOnboardingViewModel_SkipMarksCompletedImmediately() = runTest {
        val prefs = InMemorySessionPreferences()
        val viewModel = OnboardingViewModel(prefs)

        var navigated = false
        viewModel.onSkipClicked { navigated = true }
        testScheduler.advanceUntilIdle()

        assertTrue(navigated)
        assertTrue(prefs.isOnboardingCompleted())
    }

    // =========================================================================
    // 2. FEATURE 6: INTERNATIONAL MOBILE LOGIN
    // =========================================================================

    @Test
    fun testCountryCatalog_AllFiveCountriesPresent() {
        assertEquals(5, CountryCatalog.supportedCountries.size)
        val india = CountryCatalog.findByDialCode("+91")
        assertNotNull(india)
        assertEquals("India", india?.name)

        val argentina = CountryCatalog.findByDialCode("+54")
        assertNotNull(argentina)
        assertEquals("Argentina", argentina?.name)
    }

    @Test
    fun testCountrySanitization_IndianLeadingZeroRemoved() {
        val india = CountryCatalog.defaultCountry
        val sanitized = india.sanitize("09876543210")
        assertEquals("9876543210", sanitized)
        assertTrue(india.validateNumber(sanitized))
    }

    @Test
    fun testLoginViewModel_TermsValidationAndSendOtp() = runTest {
        val sessionManager = AuthSessionManager()
        val repository = AuthRepositoryImpl(sessionManager)
        val viewModel = LoginViewModel(repository)

        viewModel.onPhoneInputChanged("9876543210")
        assertFalse(viewModel.uiState.value.isSubmitEnabled) // Terms not accepted yet

        viewModel.onTermsToggled(true)
        assertTrue(viewModel.uiState.value.isSubmitEnabled)

        var dispatched = false
        viewModel.onSendOtpClicked { dialCode, phone ->
            dispatched = true
            assertEquals("+91", dialCode)
            assertEquals("9876543210", phone)
        }
        testScheduler.advanceUntilIdle()
        assertTrue(dispatched)
    }

    // =========================================================================
    // 3. FEATURE 7: 4-DIGIT OTP VERIFICATION
    // =========================================================================

    @Test
    fun testOtpViewModel_AutoAdvanceAndBypassVerification() = runTest {
        val sessionManager = AuthSessionManager()
        val repository = AuthRepositoryImpl(sessionManager)
        repository.sendOtp("+91", "9876543210")

        val viewModel = OtpViewModel("+91", "9876543210", repository, sessionManager)

        val focus1 = viewModel.onDigitEntered(0, "1")
        assertEquals(1, focus1)
        val focus2 = viewModel.onDigitEntered(1, "2")
        assertEquals(2, focus2)
        val focus3 = viewModel.onDigitEntered(2, "3")
        assertEquals(3, focus3)
        val focus4 = viewModel.onDigitEntered(3, "4")
        assertEquals(3, focus4)

        assertTrue(viewModel.uiState.value.isComplete)

        var verified = false
        viewModel.onVerifyClicked { verified = true }
        testScheduler.advanceUntilIdle()

        assertTrue(verified)
        assertTrue(sessionManager.isAuthenticated)
    }

    @Test
    fun testOtpViewModel_InvalidCodeDecrementsAttempts() = runTest {
        val sessionManager = AuthSessionManager()
        val repository = AuthRepositoryImpl(sessionManager)
        repository.sendOtp("+91", "9876543210")

        val viewModel = OtpViewModel("+91", "9876543210", repository, sessionManager)

        viewModel.onDigitEntered(0, "9")
        viewModel.onDigitEntered(1, "9")
        viewModel.onDigitEntered(2, "9")
        viewModel.onDigitEntered(3, "8")

        viewModel.onVerifyClicked {}
        testScheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.attemptsLeft)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    // =========================================================================
    // 4. FEATURE 10: MULTI-VEHICLE GARAGE & VALIDATORS
    // =========================================================================

    @Test
    fun testGarageCatalog_ContainsAllAuthoritativeModels() {
        assertEquals(5, GarageCatalog.authoritativeSpecs.size)
        val chetak = GarageCatalog.findModelByName("Bajaj Chetak EV (Premium)")
        assertNotNull(chetak)
        assertTrue(chetak!!.isElectric)
        assertEquals(0.0f, chetak.fuelCapacityLitres, 0.01f)

        val dominar = GarageCatalog.findModelByName("Bajaj Dominar 400 Dual ABS")
        assertNotNull(dominar)
        assertFalse(dominar!!.isElectric)
        assertEquals("Cluster0265", dominar.clusterHardware)
    }

    @Test
    fun testVinValidator_AcceptsValidAndRejectsIllegal() {
        assertTrue(VinValidator.isValidVin("MD2A11CY4P8712391"))
        assertFalse(VinValidator.isValidVin("MD2A11CYIP8712391")) // Contains 'I'
        assertFalse(VinValidator.isValidVin("SHORT"))
    }

    @Test
    fun testRtoPlateValidator_FormatsAndValidatesPlates() {
        assertTrue(RtoPlateValidator.isValidReg("MH 12 TC 5590"))
        assertTrue(RtoPlateValidator.isValidReg("DL01AB1234"))
        assertFalse(RtoPlateValidator.isValidReg("MH"))
    }

    @Test
    fun testQrPairingPayload_ParsesChassisQrCode() {
        val qr = "BAJAJ:VIN=MD2A11CY4P8712391;MODEL=DOMINAR_400_DUAL_ABS;MAC=AA:BB:CC:DD:EE:FF;PIN=1234"
        val parsed = QrPairingPayload.parse(qr)
        assertNotNull(parsed)
        assertEquals("MD2A11CY4P8712391", parsed?.vin)
        assertEquals("DOMINAR_400_DUAL_ABS", parsed?.modelCode)
        assertEquals("AA:BB:CC:DD:EE:FF", parsed?.bluetoothMac)
        assertEquals("1234", parsed?.pairingPin)
    }

    @Test
    fun testVehicleRepository_ActiveVehicleSwitchingAndServiceCountdown() = runTest {
        val repository = VehicleRepositoryImpl()
        val vehicles = repository.getVehicles()
        assertEquals(5, vehicles.size)

        val active = repository.getActiveVehicle()
        assertNotNull(active)

        val chetakResult = repository.selectActiveVehicle("Bajaj Chetak EV (Premium)")
        assertTrue(chetakResult.isSuccess)
        assertEquals("Bajaj Chetak EV (Premium)", repository.getActiveVehicle()?.modelSpec?.modelName)

        val remainingService = Vehicle.calculateRemainingServiceKm(4120, 5000)
        assertEquals(880, remainingService)
    }

    // =========================================================================
    // 5. FEATURE 8: RIDER PROFILE & PREFERENCES
    // =========================================================================

    @Test
    fun testRiderProfile_ValidationAndNameFormatting() {
        val formatted = RiderProfile.formatRiderName("   rohit verma   ")
        assertEquals("Rohit Verma", formatted)

        assertTrue(RiderProfile.isValidBloodGroup("O+"))
        assertTrue(RiderProfile.isValidBloodGroup("AB-"))
        assertFalse(RiderProfile.isValidBloodGroup("Z+"))

        val convertedMiles = RiderProfile.convertDistance(100f, "miles")
        assertEquals(62.1371f, convertedMiles, 0.001f)
    }

    @Test
    fun testProfileViewModel_UpdatesRiderPreferences() = runTest {
        val repository = ProfileRepositoryImpl()
        val viewModel = ProfileViewModel(repository)

        viewModel.onNameChange("Vikram Malhotra")
        viewModel.onEmailChange("vikram.m@example.com")
        viewModel.onBloodGroupSelected("B+")
        viewModel.onDistanceUnitToggled("miles")
        viewModel.onThemeModeSelected(ThemeMode.DARK)

        viewModel.saveChanges()
        testScheduler.advanceUntilIdle()

        val updated = repository.getProfile()
        assertEquals("Vikram Malhotra", updated.riderName)
        assertEquals("vikram.m@example.com", updated.email)
        assertEquals("B+", updated.bloodGroup)
        assertEquals("miles", updated.preferredDistanceUnit)
    }

    // =========================================================================
    // 6. FEATURE 9: EMERGENCY SOS CONTACTS & BROADCAST SYSTEM
    // =========================================================================

    @Test
    fun testEmergencyContact_EnforcesFiveContactCapAndDuplicateRejection() = runTest {
        val repository = EmergencySosRepositoryImpl()
        val initialList = repository.getContacts()
        assertEquals(2, initialList.size)

        // Try adding duplicate
        val duplicate = EmergencyContact(name = "Anita Clone", phone = "+919876543210", relationship = "Spouse")
        val addDuplicateResult = repository.addContact(duplicate)
        assertFalse(addDuplicateResult.isSuccess)

        // Add 3 unique contacts to hit cap of 5
        repository.addContact(EmergencyContact(name = "C3", phone = "+919876543213", relationship = "Friend"))
        repository.addContact(EmergencyContact(name = "C4", phone = "+919876543214", relationship = "Friend"))
        repository.addContact(EmergencyContact(name = "C5", phone = "+919876543215", relationship = "Friend"))

        assertEquals(5, repository.getContacts().size)

        // 6th contact should be rejected
        val overflow = EmergencyContact(name = "C6", phone = "+919876543216", relationship = "Friend")
        val overflowResult = repository.addContact(overflow)
        assertFalse(overflowResult.isSuccess)
    }

    @Test
    fun testSosIncidentPayload_BuildsSmsCoordinates() {
        val payload = SosIncidentPayload(
            triggerSource = SosTriggerSource.MANUAL_BUTTON,
            latitude = 18.5204,
            longitude = 73.8567,
            riderName = "Ketan",
            recipients = listOf("+919876543210")
        )

        val sms = payload.buildSmsText()
        assertTrue(sms.contains("EMERGENCY SOS"))
        assertTrue(sms.contains("https://maps.google.com/?q=18.5204,73.8567"))
    }

    @Test
    fun testSosIncidentPayload_FallbackToOfficialRsa() {
        val emptyPayload = SosIncidentPayload(
            triggerSource = SosTriggerSource.CRITICAL_BATTERY_DROP,
            latitude = 18.5204,
            longitude = 73.8567,
            riderName = "Ketan",
            recipients = emptyList()
        )

        assertEquals("1800 233 2453", emptyPayload.resolvePrimaryRecipient())
    }
}
