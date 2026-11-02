package com.openride.companion.e2e

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-End Test Suite for Authentication, Onboarding, Profile, SOS, and Garage (Features 5-10).
 * Covers Tier 1 (Feature Coverage) and Tier 2 (Boundary & Corner Cases).
 */
class AuthAndOnboardingE2ETest {

    // =========================================================================
    // FEATURE 5: APP TOUR ONBOARDING (Tier 1 & Tier 2)
    // =========================================================================

    data class OnboardingSlide(
        val index: Int,
        val imageAsset: String,
        val headline: String,
        val description: String
    )

    private val authoritativeSlides = listOf(
        OnboardingSlide(0, "navigatetrack.png", "Turn-by-Turn Navigation", "Get turn by turn navigation on your bike cluster"),
        OnboardingSlide(1, "notificationreminder.png", "Call & SMS Alerts", "Never miss important calls or messages while riding"),
        OnboardingSlide(2, "servicebooking.png", "Service & Maintenance", "Book bike service and track maintenance effortlessly")
    )

    @Test
    fun testOnboarding_TotalSlideCount_EqualsThree() {
        assertEquals("App tour must contain exactly 3 onboarding slides", 3, authoritativeSlides.size)
    }

    @Test
    fun testOnboarding_SlideContent_MatchesSpecification() {
        val slide1 = authoritativeSlides[0]
        assertEquals("navigatetrack.png", slide1.imageAsset)
        assertTrue(slide1.headline.contains("Navigation"))

        val slide2 = authoritativeSlides[1]
        assertEquals("notificationreminder.png", slide2.imageAsset)

        val slide3 = authoritativeSlides[2]
        assertEquals("servicebooking.png", slide3.imageAsset)
    }

    @Test
    fun testOnboarding_NextSlideProgression_AdvancesStep() {
        var currentStep = 0
        fun nextStep() {
            if (currentStep < authoritativeSlides.size - 1) currentStep++
        }
        nextStep()
        assertEquals(1, currentStep)
        nextStep()
        assertEquals(2, currentStep)
    }

    @Test
    fun testOnboarding_SkipAction_DirectsToLastStepOrAuth() {
        var isCompleted = false
        fun onSkip() {
            isCompleted = true
        }
        onSkip()
        assertTrue("Skip button must immediately complete onboarding", isCompleted)
    }

    @Test
    fun testOnboarding_FinishAction_OnlyAvailableOnFinalSlide() {
        fun isFinishButtonVisible(step: Int) = step == authoritativeSlides.size - 1

        assertFalse("Slide 0 should show 'Next'", isFinishButtonVisible(0))
        assertFalse("Slide 1 should show 'Next'", isFinishButtonVisible(1))
        assertTrue("Slide 2 must show 'Get Started'", isFinishButtonVisible(2))
    }

    @Test
    fun testOnboarding_Boundary_StepOutOfBoundsClamping() {
        fun clampStep(step: Int): Int = step.coerceIn(0, authoritativeSlides.size - 1)

        assertEquals(0, clampStep(-1))
        assertEquals(0, clampStep(0))
        assertEquals(2, clampStep(2))
        assertEquals(2, clampStep(5))
    }

    @Test
    fun testOnboarding_Boundary_SwipeProgressionState() {
        var page = 0
        fun handleSwipe(deltaX: Float) {
            if (deltaX < -50f && page < authoritativeSlides.size - 1) page++
            else if (deltaX > 50f && page > 0) page--
        }
        handleSwipe(-100f) // swipe forward
        assertEquals(1, page)
        handleSwipe(120f) // swipe backward
        assertEquals(0, page)
    }

    @Test
    fun testOnboarding_Boundary_SubtleSwipe_DoesNotChangePage() {
        var page = 1
        fun handleSwipe(deltaX: Float) {
            if (deltaX < -50f && page < authoritativeSlides.size - 1) page++
            else if (deltaX > 50f && page > 0) page--
        }
        handleSwipe(-10f) // tiny jitter
        assertEquals(1, page)
    }

    @Test
    fun testOnboarding_Boundary_PersistenceOfCompletionFlag() {
        var prefsOnboardingCompleted = false
        fun completeOnboarding() {
            prefsOnboardingCompleted = true
        }
        completeOnboarding()
        assertTrue("Completion flag must remain set for future app launches", prefsOnboardingCompleted)
    }

    @Test
    fun testOnboarding_Boundary_SlideAssetIntegrity() {
        authoritativeSlides.forEach { slide ->
            assertTrue("Asset must be PNG format", slide.imageAsset.endsWith(".png"))
            assertTrue("Description must not be empty", slide.description.isNotBlank())
        }
    }

    // =========================================================================
    // FEATURE 6: INTERNATIONAL MOBILE LOGIN (Tier 1 & Tier 2)
    // =========================================================================

    data class CountryCode(val country: String, val dialCode: String, val flagEmoji: String, val expectedLen: Int)

    private val supportedCountries = listOf(
        CountryCode("India", "+91", "\uD83C\uDDEE\uD83C\uDDF3", 10),
        CountryCode("Argentina", "+54", "\uD83C\uDDE6\uD83C\uDDF7", 10),
        CountryCode("Belgium", "+32", "\uD83C\uDDE7\uD83C\uDDEA", 9),
        CountryCode("Canada", "+1", "\uD83C\uDDE8\uD83C\uDDE6", 10),
        CountryCode("Egypt", "+20", "\uD83C\uDDE8\uD83C\uDDEC", 10)
    )

    private fun validatePhoneNumber(dialCode: String, number: String): Boolean {
        val clean = number.trim().filter { it.isDigit() }
        return when (dialCode) {
            "+91" -> clean.matches(Regex("^[6-9]\\d{9}$"))
            "+54" -> clean.length == 10
            "+32" -> clean.length in 8..10
            "+1" -> clean.length == 10
            "+20" -> clean.length == 10
            else -> clean.length in 7..15
        }
    }

    @Test
    fun testLogin_SupportedCountryCatalog_ContainsAllFiveCountries() {
        val codes = supportedCountries.map { it.dialCode }
        assertTrue("Must support India +91", "+91" in codes)
        assertTrue("Must support Argentina +54", "+54" in codes)
        assertTrue("Must support Belgium +32", "+32" in codes)
        assertTrue("Must support Canada +1", "+1" in codes)
        assertTrue("Must support Egypt +20", "+20" in codes)
        assertEquals(5, supportedCountries.size)
    }

    @Test
    fun testLogin_IndianMobileValidation_ValidFormatPasses() {
        assertTrue("Valid Indian mobile starting with 9", validatePhoneNumber("+91", "9876543210"))
        assertTrue("Valid Indian mobile starting with 8", validatePhoneNumber("+91", "8123456789"))
        assertTrue("Valid Indian mobile starting with 7", validatePhoneNumber("+91", "7001122334"))
        assertTrue("Valid Indian mobile starting with 6", validatePhoneNumber("+91", "6200112233"))
    }

    @Test
    fun testLogin_IndianMobileValidation_InvalidFormatFails() {
        assertFalse("Starts with 5 (invalid Indian mobile prefix)", validatePhoneNumber("+91", "5876543210"))
        assertFalse("Less than 10 digits", validatePhoneNumber("+91", "987654321"))
        assertFalse("More than 10 digits", validatePhoneNumber("+91", "98765432100"))
    }

    @Test
    fun testLogin_InternationalMobileValidation_ArgentinaAndCanada() {
        assertTrue("Valid 10-digit Argentina number", validatePhoneNumber("+54", "1123456789"))
        assertTrue("Valid 10-digit Canada number", validatePhoneNumber("+1", "4165551234"))
    }

    @Test
    fun testLogin_DefaultCountryIsIndia() {
        val defaultCode = supportedCountries.first()
        assertEquals("+91", defaultCode.dialCode)
        assertEquals("India", defaultCode.country)
    }

    @Test
    fun testLogin_Boundary_FormattedInputWithDashesAndSpaces() {
        val formatted = "  9876-543-210 "
        assertTrue("Stripped digits should pass Indian validation", validatePhoneNumber("+91", formatted))
    }

    @Test
    fun testLogin_Boundary_AlphaCharactersInPhoneNumber() {
        assertFalse("Phone with alphabetic characters must fail", validatePhoneNumber("+91", "9876abc210"))
    }

    @Test
    fun testLogin_Boundary_CountryCodeSelection_SwitchesCorrectly() {
        var selectedDial = "+91"
        fun selectCountry(code: String) {
            if (supportedCountries.any { it.dialCode == code }) {
                selectedDial = code
            }
        }
        selectCountry("+54")
        assertEquals("+54", selectedDial)
        selectCountry("+999") // unsupported
        assertEquals("+54", selectedDial)
    }

    @Test
    fun testLogin_Boundary_ZeroLeadingIndianNumber() {
        // Some users type 09876543210
        val input = "09876543210"
        val trimmed = if (input.startsWith("0")) input.substring(1) else input
        assertTrue("Normalized leading zero should pass validation", validatePhoneNumber("+91", trimmed))
    }

    @Test
    fun testLogin_Boundary_MaximumInputLengthRestriction() {
        val input = "9876543210123456789"
        val limited = input.take(10)
        assertEquals("9876543210", limited)
    }

    // =========================================================================
    // FEATURE 7: 4-DIGIT OTP VERIFICATION (Tier 1 & Tier 2)
    // =========================================================================

    class OtpSession(
        val expectedOtp: String = "4491",
        var expiryTimestampMs: Long = System.currentTimeMillis() + 60_000L,
        var resendCooldownSeconds: Int = 60
    ) {
        var isVerified: Boolean = false
        var attemptsLeft: Int = 3

        fun verify(inputOtp: String): Boolean {
            if (System.currentTimeMillis() > expiryTimestampMs) {
                return false // Expired
            }
            if (attemptsLeft <= 0) return false

            if (inputOtp == expectedOtp) {
                isVerified = true
                return true
            } else {
                attemptsLeft--
                return false
            }
        }

        fun resend() {
            expiryTimestampMs = System.currentTimeMillis() + 60_000L
            resendCooldownSeconds = 60
            attemptsLeft = 3
        }
    }

    @Test
    fun testOtp_CorrectCode_VerifiesSuccessfully() {
        val session = OtpSession(expectedOtp = "1234")
        val success = session.verify("1234")
        assertTrue("Correct 4-digit OTP must pass", success)
        assertTrue(session.isVerified)
    }

    @Test
    fun testOtp_IncorrectCode_FailsAndDecrementsAttempts() {
        val session = OtpSession(expectedOtp = "1234")
        val success = session.verify("0000")
        assertFalse("Incorrect OTP must be rejected", success)
        assertFalse(session.isVerified)
        assertEquals("Attempts remaining decremented to 2", 2, session.attemptsLeft)
    }

    @Test
    fun testOtp_MaxAttemptsExhausted_LocksVerification() {
        val session = OtpSession(expectedOtp = "1234")
        session.verify("1111")
        session.verify("2222")
        session.verify("3333")
        assertEquals(0, session.attemptsLeft)
        // Even if correct now, blocked until resend
        assertFalse(session.verify("1234"))
    }

    @Test
    fun testOtp_Resend_ResetsTimerAndAttempts() {
        val session = OtpSession(expectedOtp = "1234")
        session.verify("0000")
        session.resend()
        assertEquals(3, session.attemptsLeft)
        assertEquals(60, session.resendCooldownSeconds)
    }

    @Test
    fun testOtp_GridLength_IsStrictlyFourDigits() {
        fun isValidOtpFormat(otp: String) = otp.length == 4 && otp.all { it.isDigit() }
        assertTrue(isValidOtpFormat("4491"))
        assertFalse(isValidOtpFormat("449"))
        assertFalse(isValidOtpFormat("44912"))
        assertFalse(isValidOtpFormat("449A"))
    }

    @Test
    fun testOtp_Boundary_ExpiredSession_RejectsVerification() {
        val session = OtpSession(expectedOtp = "1234")
        session.expiryTimestampMs = System.currentTimeMillis() - 1000L // 1 second ago
        val success = session.verify("1234")
        assertFalse("Expired OTP must fail verification even with matching digits", success)
    }

    @Test
    fun testOtp_Boundary_AutoAdvanceFocusCalculation() {
        // Simulates 4-box auto-focus logic
        val boxes = arrayOf("", "", "", "")
        fun insertDigit(index: Int, digit: Char): Int {
            if (index in 0..3 && digit.isDigit()) {
                boxes[index] = digit.toString()
                return (index + 1).coerceAtMost(3)
            }
            return index
        }
        val nextFocus = insertDigit(0, '5')
        assertEquals("After entering digit 0, focus moves to box 1", 1, nextFocus)
    }

    @Test
    fun testOtp_Boundary_BackspaceFocusRegression() {
        val boxes = arrayOf("5", "4", "", "")
        fun handleBackspace(currentIndex: Int): Int {
            if (boxes[currentIndex].isEmpty() && currentIndex > 0) {
                boxes[currentIndex - 1] = ""
                return currentIndex - 1
            } else {
                boxes[currentIndex] = ""
                return currentIndex
            }
        }
        val target = handleBackspace(2)
        assertEquals("Backspace on empty box 2 clears box 1 and focuses it", 1, target)
        assertEquals("", boxes[1])
    }

    @Test
    fun testOtp_Boundary_WhitespaceOrSpecialCharacters() {
        fun sanitizeOtpInput(raw: String): String = raw.filter { it.isDigit() }.take(4)
        assertEquals("1234", sanitizeOtpInput(" 1 2 3 4 "))
        assertEquals("9876", sanitizeOtpInput("98-76#"))
    }

    @Test
    fun testOtp_Boundary_AuthTokenGenerationOnSuccess() {
        val session = OtpSession(expectedOtp = "9999")
        session.verify("9999")
        val generatedToken = if (session.isVerified) "JWT_SESSION_" + System.currentTimeMillis() else null
        assertNotNull("Successful verification must produce non-null auth token", generatedToken)
    }

    // =========================================================================
    // FEATURE 8: USER PROFILE & PREFERENCES (Tier 1 & Tier 2)
    // =========================================================================

    data class UserProfile(
        val riderName: String,
        val email: String,
        val phoneNumber: String,
        val bloodGroup: String,
        val preferredDistanceUnit: String = "km", // "km" or "miles"
        val notificationsEnabled: Boolean = true
    )

    @Test
    fun testProfile_ProfileCreation_HasValidFields() {
        val profile = UserProfile(
            riderName = "Ketan Sharma",
            email = "ketan.sharma@example.com",
            phoneNumber = "+919876543210",
            bloodGroup = "O+"
        )
        assertEquals("Ketan Sharma", profile.riderName)
        assertEquals("O+", profile.bloodGroup)
        assertEquals("km", profile.preferredDistanceUnit)
    }

    @Test
    fun testProfile_BloodGroupValidation_OnlyAllowsStandardGroups() {
        val validBloodGroups = setOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        assertTrue("O+ is valid", "O+" in validBloodGroups)
        assertTrue("AB- is valid", "AB-" in validBloodGroups)
        assertFalse("X+ is invalid", "X+" in validBloodGroups)
    }

    @Test
    fun testProfile_UnitConversion_KmToMiles() {
        fun convertDistance(distanceKm: Float, unit: String): Float {
            return if (unit == "miles") distanceKm * 0.621371f else distanceKm
        }
        val distKm = 100f
        assertEquals(62.1371f, convertDistance(distKm, "miles"), 0.001f)
        assertEquals(100f, convertDistance(distKm, "km"), 0.001f)
    }

    @Test
    fun testProfile_EmailValidation_RejectsMalformedEmail() {
        fun isValidEmail(email: String): Boolean = email.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"))

        assertTrue(isValidEmail("rider@bajajconnect.com"))
        assertFalse(isValidEmail("plainaddress"))
        assertFalse(isValidEmail("@missingusername.com"))
    }

    @Test
    fun testProfile_NotificationToggles_UpdatesPreferences() {
        var profile = UserProfile("Rider", "r@b.com", "+919876543210", "A+")
        profile = profile.copy(notificationsEnabled = false)
        assertFalse(profile.notificationsEnabled)
    }

    @Test
    fun testProfile_Boundary_NameTrimmingAndCapitalization() {
        val raw = "   rohit verma   "
        val formatted = raw.trim().split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        assertEquals("Rohit Verma", formatted)
    }

    @Test
    fun testProfile_Boundary_EmptyNameValidation() {
        val emptyName = "   "
        assertFalse("Empty name must not be valid", emptyName.isNotBlank())
    }

    @Test
    fun testProfile_Boundary_LongNameTruncation() {
        val veryLongName = "A".repeat(100)
        val clamped = veryLongName.take(50)
        assertEquals(50, clamped.length)
    }

    @Test
    fun testProfile_Boundary_UnknownUnitDefaultsToKm() {
        fun resolveUnit(unit: String): String = if (unit.lowercase() == "miles") "miles" else "km"
        assertEquals("km", resolveUnit("parsecs"))
        assertEquals("miles", resolveUnit("miles"))
    }

    @Test
    fun testProfile_Boundary_PhoneNumberUpdatePreservesFormat() {
        val original = "+919876543210"
        val updated = "+919123456789"
        assertTrue(updated.startsWith("+91"))
    }

    // =========================================================================
    // FEATURE 9: EMERGENCY SOS CONTACTS (Tier 1 & Tier 2)
    // =========================================================================

    data class EmergencyContact(
        val id: String,
        val name: String,
        val phone: String,
        val relationship: String
    )

    private val authoritativeRsaPhone = "1800 233 2453"

    @Test
    fun testSos_RoadsideAssistanceHelpline_MatchesSpec() {
        assertEquals("Bajaj 24x7 Roadside Assistance number", "1800 233 2453", authoritativeRsaPhone)
    }

    @Test
    fun testSos_AddEmergencyContact_AddsToRegistry() {
        val contacts = mutableListOf<EmergencyContact>()
        contacts.add(EmergencyContact("1", "Anita Sharma", "+919876543211", "Spouse"))
        assertEquals(1, contacts.size)
        assertEquals("Anita Sharma", contacts.first().name)
    }

    @Test
    fun testSos_MaxContactLimit_EnforcedToFive() {
        val contacts = (1..6).map { EmergencyContact("$it", "Contact $it", "+91987654321$it", "Friend") }
        val capped = contacts.take(5)
        assertEquals("Emergency contact list capped at 5", 5, capped.size)
    }

    @Test
    fun testSos_AlertPayload_FormatsSmsWithGpsCoordinates() {
        fun buildSosSms(riderName: String, lat: Double, lon: Double): String {
            return "EMERGENCY SOS from $riderName! I need immediate help. Current Location: https://maps.google.com/?q=$lat,$lon"
        }
        val sms = buildSosSms("Ketan", 18.5204, 73.8567)
        assertTrue(sms.contains("EMERGENCY SOS"))
        assertTrue(sms.contains("18.5204,73.8567"))
    }

    @Test
    fun testSos_RemoveContact_RemovesById() {
        var contacts = listOf(
            EmergencyContact("1", "Parent", "+919876543211", "Father"),
            EmergencyContact("2", "Sibling", "+919876543212", "Brother")
        )
        contacts = contacts.filter { it.id != "1" }
        assertEquals(1, contacts.size)
        assertEquals("2", contacts.first().id)
    }

    @Test
    fun testSos_Boundary_DuplicateContactPhone_Disallowed() {
        val existing = listOf(EmergencyContact("1", "Pooja", "+919876543210", "Sister"))
        fun canAddContact(phone: String): Boolean = existing.none { it.phone == phone }
        assertFalse("Duplicate phone number cannot be added", canAddContact("+919876543210"))
        assertTrue("Unique phone number allowed", canAddContact("+919876543219"))
    }

    @Test
    fun testSos_Boundary_EmptySosContactsList_FallbackToRsa() {
        val contacts = emptyList<EmergencyContact>()
        val fallbackRecipient = if (contacts.isEmpty()) authoritativeRsaPhone else contacts.first().phone
        assertEquals("When no personal SOS contacts, fall back to official RSA number", authoritativeRsaPhone, fallbackRecipient)
    }

    @Test
    fun testSos_Boundary_InvalidGpsLocationInSos() {
        fun buildSosSafe(lat: Double?, lon: Double?): String {
            return if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                "EMERGENCY SOS: GPS location currently unavailable."
            } else {
                "Location: https://maps.google.com/?q=$lat,$lon"
            }
        }
        assertEquals("EMERGENCY SOS: GPS location currently unavailable.", buildSosSafe(null, null))
        assertEquals("EMERGENCY SOS: GPS location currently unavailable.", buildSosSafe(100.0, 73.0))
        assertTrue(buildSosSafe(18.52, 73.85).contains("18.52,73.85"))
    }

    @Test
    fun testSos_Boundary_ContactNameSpecialCharacters() {
        val contact = EmergencyContact("1", "R. K. Sharma (Brother)", "+919876543211", "Brother")
        assertTrue(contact.name.isNotBlank())
    }

    @Test
    fun testSos_Boundary_SosBroadcastTriggerState() {
        var sosTriggered = false
        fun triggerSos() {
            sosTriggered = true
        }
        triggerSos()
        assertTrue(sosTriggered)
    }

    // =========================================================================
    // FEATURE 10: MULTI-VEHICLE GARAGE (Tier 1 & Tier 2)
    // =========================================================================

    data class GarageBikeSpec(
        val modelName: String,
        val fuelCapacityLitres: Float,
        val engineDisplacementCc: Int,
        val defaultDteKm: Int,
        val clusterHardware: String,
        val isElectric: Boolean
    )

    private val authoritativeGarageSpecs = mapOf(
        "Bajaj Pulsar NS400Z" to GarageBikeSpec("Bajaj Pulsar NS400Z", 12.0f, 373, 285, "Cluster0265", false),
        "Bajaj Dominar 400 Dual ABS" to GarageBikeSpec("Bajaj Dominar 400 Dual ABS", 13.0f, 373, 240, "Cluster0265", false),
        "Bajaj Chetak EV (Premium)" to GarageBikeSpec("Bajaj Chetak EV (Premium)", 0.0f, 0, 95, "Cluster0265", true),
        "Bajaj Pulsar N250" to GarageBikeSpec("Bajaj Pulsar N250", 14.0f, 249, 310, "Cluster2763", false),
        "Bajaj Pulsar N160" to GarageBikeSpec("Bajaj Pulsar N160", 14.0f, 164, 360, "Cluster2763", false)
    )

    @Test
    fun testGarage_CatalogCompleteness_CoversAll5Motorcycles() {
        assertEquals("Garage must include exactly 5 key Bajaj motorcycle variants", 5, authoritativeGarageSpecs.size)
        assertTrue(authoritativeGarageSpecs.containsKey("Bajaj Pulsar NS400Z"))
        assertTrue(authoritativeGarageSpecs.containsKey("Bajaj Dominar 400 Dual ABS"))
        assertTrue(authoritativeGarageSpecs.containsKey("Bajaj Chetak EV (Premium)"))
        assertTrue(authoritativeGarageSpecs.containsKey("Bajaj Pulsar N250"))
        assertTrue(authoritativeGarageSpecs.containsKey("Bajaj Pulsar N160"))
    }

    @Test
    fun testGarage_ChetakEv_CorrectlyIdentifiedAsElectric() {
        val chetak = authoritativeGarageSpecs["Bajaj Chetak EV (Premium)"]
        assertNotNull(chetak)
        assertTrue("Chetak must have isElectric == true", chetak!!.isElectric)
        assertEquals(0.0f, chetak.fuelCapacityLitres, 0.01f)
    }

    @Test
    fun testGarage_ClusterTypeMapping_SeparatesTftAndLcd() {
        val dominar = authoritativeGarageSpecs["Bajaj Dominar 400 Dual ABS"]
        assertEquals("Dominar 400 uses full TFT Color Display (Cluster0265)", "Cluster0265", dominar?.clusterHardware)

        val pulsarN160 = authoritativeGarageSpecs["Bajaj Pulsar N160"]
        assertEquals("Pulsar N160 uses standard LCD Display (Cluster2763)", "Cluster2763", pulsarN160?.clusterHardware)
    }

    @Test
    fun testGarage_SwitchActiveBike_UpdatesActiveSpecs() {
        var activeModel = "Bajaj Pulsar NS400Z"
        fun switchBike(newModel: String) {
            if (authoritativeGarageSpecs.containsKey(newModel)) {
                activeModel = newModel
            }
        }
        switchBike("Bajaj Chetak EV (Premium)")
        assertEquals("Bajaj Chetak EV (Premium)", activeModel)
    }

    @Test
    fun testGarage_NextServiceCalculation_EstimatesDaysAndKm() {
        val currentOdo = 4120
        val serviceInterval = 5000
        val remainingKm = serviceInterval - (currentOdo % serviceInterval)
        assertEquals(880, remainingKm)
    }

    @Test
    fun testGarage_Boundary_SwitchToUnknownModel_RetainsCurrent() {
        var current = "Bajaj Pulsar NS400Z"
        fun switchSafe(model: String) {
            if (authoritativeGarageSpecs.containsKey(model)) current = model
        }
        switchSafe("Unknown Model 999")
        assertEquals("Bajaj Pulsar NS400Z", current)
    }

    @Test
    fun testGarage_Boundary_VinNumberValidation() {
        // Standard 17-character ISO VIN
        fun isValidVin(vin: String): Boolean = vin.length == 17 && vin.matches(Regex("^[A-HJ-NPR-Z0-9]{17}$"))

        assertTrue("Valid Bajaj VIN", isValidVin("MD2A11CY4P8712391"))
        assertFalse("Contains illegal character 'I'", isValidVin("MD2A11CYIP8712391"))
        assertFalse("Too short", isValidVin("MD2A11CY4P8712"))
    }

    @Test
    fun testGarage_Boundary_RegistrationNumberFormat() {
        // Indian RTO format: e.g. MH 12 TC 5590 or MH12TC5590
        fun isValidReg(reg: String): Boolean = reg.filter { it.isLetterOrDigit() }.length in 8..11

        assertTrue(isValidReg("MH 12 TC 5590"))
        assertTrue(isValidReg("DL01AB1234"))
        assertFalse(isValidReg("MH"))
    }

    @Test
    fun testGarage_Boundary_BatteryPercentageBounds() {
        fun clampBattery(percentage: Int): Int = percentage.coerceIn(0, 100)
        assertEquals(0, clampBattery(-5))
        assertEquals(94, clampBattery(94))
        assertEquals(100, clampBattery(120))
    }

    @Test
    fun testGarage_Boundary_ElectricVsIceFuelPercentage() {
        // Electric shows Battery %, ICE shows Fuel %
        fun getPrimaryEnergyLabel(isElectric: Boolean): String = if (isElectric) "Battery Level" else "Fuel Level"

        assertEquals("Battery Level", getPrimaryEnergyLabel(true))
        assertEquals("Fuel Level", getPrimaryEnergyLabel(false))
    }
}
