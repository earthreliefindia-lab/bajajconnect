package com.openride.companion.challenger

import com.openride.companion.data.auth.AuthRepositoryImpl
import com.openride.companion.data.auth.AuthSessionManager
import com.openride.companion.data.preferences.InMemorySessionPreferences
import com.openride.companion.model.CountryCatalog
import com.openride.companion.model.SessionState
import com.openride.companion.ui.screens.auth.LoginViewModel
import com.openride.companion.ui.screens.auth.OtpViewModel
import com.openride.companion.ui.screens.onboarding.OnboardingCatalog
import com.openride.companion.ui.screens.onboarding.OnboardingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
 * Empirical Adversarial Test Suite authored by m2_challenger_1.
 * Stress-tests Phone Validation, 4-Digit Discrete OTP Security, Lockout Enforcement,
 * and Onboarding Pager Bounds / Persistence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthAndOnboardingAdversarialTest {

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
    // 1. PHONE NUMBER SANITIZATION & VALIDATION (Feature 6)
    // =========================================================================

    @Test
    fun probeCountryCatalog_AuthoritativeFiveCountries() {
        val expectedCodes = listOf("+91", "+54", "+32", "+1", "+20")
        val catalogCodes = CountryCatalog.supportedCountries.map { it.dialCode }
        assertEquals(expectedCodes, catalogCodes)

        // Verify default country is India
        assertEquals("+91", CountryCatalog.defaultCountry.dialCode)
        assertEquals("India", CountryCatalog.defaultCountry.name)

        // Verify findByDialCode
        assertNotNull(CountryCatalog.findByDialCode("+91"))
        assertNotNull(CountryCatalog.findByDialCode("+54"))
        assertNotNull(CountryCatalog.findByDialCode("+32"))
        assertNotNull(CountryCatalog.findByDialCode("+1"))
        assertNotNull(CountryCatalog.findByDialCode("+20"))
        assertNull(CountryCatalog.findByDialCode("+44"))
        assertNull(CountryCatalog.findByDialCode("+999"))
    }

    @Test
    fun probePhoneValidation_India_ValidPrefixesAndLength() {
        val india = CountryCatalog.findByDialCode("+91")!!

        // Valid starting digits 6, 7, 8, 9 with exactly 10 digits
        assertTrue("Starting with 6", india.validateNumber("6123456789"))
        assertTrue("Starting with 7", india.validateNumber("7123456789"))
        assertTrue("Starting with 8", india.validateNumber("8123456789"))
        assertTrue("Starting with 9", india.validateNumber("9123456789"))
    }

    @Test
    fun probePhoneValidation_India_InvalidStartingDigits() {
        val india = CountryCatalog.findByDialCode("+91")!!

        // Invalid starting digits 0, 1, 2, 3, 4, 5
        assertFalse("Starting with 0", india.validateNumber("0123456789"))
        assertFalse("Starting with 1", india.validateNumber("1123456789"))
        assertFalse("Starting with 2", india.validateNumber("2123456789"))
        assertFalse("Starting with 3", india.validateNumber("3123456789"))
        assertFalse("Starting with 4", india.validateNumber("4123456789"))
        assertFalse("Starting with 5", india.validateNumber("5123456789"))
    }

    @Test
    fun probePhoneSanitization_LeadingZerosAndFormatting() {
        val india = CountryCatalog.findByDialCode("+91")!!

        // Single leading zero normalization
        val singleZero = "09876543210"
        val sanitizedSingle = india.sanitize(singleZero)
        assertEquals("9876543210", sanitizedSingle)
        assertTrue(india.validateNumber(singleZero))

        // Embedded spaces
        val withSpaces = " 9876 543 210 "
        assertEquals("9876543210", india.sanitize(withSpaces))
        assertTrue(india.validateNumber(withSpaces))

        // Embedded hyphens and symbols
        val pureFormatted = "9876-543-210"
        assertEquals("9876543210", india.sanitize(pureFormatted))
        assertTrue(india.validateNumber(pureFormatted))

        // Non-numeric alpha characters
        val withAlpha = "9876abc210"
        assertEquals("9876210", india.sanitize(withAlpha)) // 7 digits
        assertFalse(india.validateNumber(withAlpha))

        // Under-length (< 10)
        assertFalse("9 digits", india.validateNumber("987654321"))
        assertFalse("1 digit", india.validateNumber("9"))
        assertFalse("Empty", india.validateNumber(""))
    }

    @Test
    fun probePhoneValidation_InternationalPrefixRules() {
        val argentina = CountryCatalog.findByDialCode("+54")!!
        val belgium = CountryCatalog.findByDialCode("+32")!!
        val canada = CountryCatalog.findByDialCode("+1")!!
        val egypt = CountryCatalog.findByDialCode("+20")!!

        // Argentina (+54): exactly 10 digits
        assertTrue("Argentina 10 digits", argentina.validateNumber("1123456789"))
        assertFalse("Argentina 9 digits", argentina.validateNumber("112345678"))

        // Belgium (+32): 8..10 digits
        assertTrue("Belgium 8 digits", belgium.validateNumber("12345678"))
        assertTrue("Belgium 9 digits", belgium.validateNumber("123456789"))
        assertTrue("Belgium 10 digits", belgium.validateNumber("1234567890"))
        assertFalse("Belgium 7 digits", belgium.validateNumber("1234567"))

        // Canada (+1): exactly 10 digits
        assertTrue("Canada 10 digits", canada.validateNumber("4165551234"))
        assertFalse("Canada 9 digits", canada.validateNumber("416555123"))

        // Egypt (+20): exactly 10 digits
        assertTrue("Egypt 10 digits", egypt.validateNumber("1012345678"))
        assertFalse("Egypt 9 digits", egypt.validateNumber("101234567"))
    }

    /**
     * EMPIRICAL FINDING:
     * Over-length input (>10 digits) validation behavior.
     * In SupportedCountry.validateNumber(rawNumber), it calls sanitize(rawNumber) which truncates
     * excess digits with `digits.take(maxLength)`. Consequently, raw over-length phone numbers
     * (e.g. 11-15 digits) are accepted as valid rather than rejected.
     */
    @Test
    fun probePhoneValidation_OverLengthInputTruncationBehavior() {
        val india = CountryCatalog.findByDialCode("+91")!!
        val argentina = CountryCatalog.findByDialCode("+54")!!

        // Raw input with 14 digits
        val overLengthIndia = "98765432109999"
        val sanitized = india.sanitize(overLengthIndia)
        assertEquals("9876543210", sanitized)
        // Note: validateNumber returns true because sanitize() took only first 10 digits
        val isValidated = india.validateNumber(overLengthIndia)
        assertTrue("Over-length input is truncated to 10 chars by sanitize and thus validates", isValidated)

        val overLengthArg = "112345678901"
        assertTrue("Argentina 12 digits truncated to 10 chars and validates", argentina.validateNumber(overLengthArg))
    }

    @Test
    fun probeLoginViewModel_CountrySwitchingAndSubmitEnabling() = runTest {
        val sessionManager = AuthSessionManager()
        val repository = AuthRepositoryImpl(sessionManager)
        val viewModel = LoginViewModel(repository)

        // Initially submit is disabled
        assertFalse(viewModel.uiState.value.isSubmitEnabled)

        // Enter valid phone but no terms
        viewModel.onPhoneInputChanged("9876543210")
        assertTrue(viewModel.uiState.value.isPhoneValid)
        assertFalse(viewModel.uiState.value.isSubmitEnabled)

        // Accept terms -> submit enabled
        viewModel.onTermsToggled(true)
        assertTrue(viewModel.uiState.value.isSubmitEnabled)

        // Switch to Belgium (+32) with 10 digits -> still valid
        val belgium = CountryCatalog.findByDialCode("+32")!!
        viewModel.onCountrySelected(belgium)
        assertEquals("+32", viewModel.uiState.value.selectedCountry.dialCode)
        assertTrue(viewModel.uiState.value.isPhoneValid)

        // Shorten to 7 digits for Belgium -> invalid
        viewModel.onPhoneInputChanged("1234567")
        assertFalse(viewModel.uiState.value.isPhoneValid)
        assertFalse(viewModel.uiState.value.isSubmitEnabled)
    }

    // =========================================================================
    // 2. 4-DIGIT DISCRETE OTP VERIFICATION & LOCKOUT (Feature 7)
    // =========================================================================

    @Test
    fun probeOtpViewModel_DiscreteBoxFocusTraversalAndBackspace() = runTest {
        val sessionManager = AuthSessionManager()
        val repository = AuthRepositoryImpl(sessionManager)
        repository.sendOtp("+91", "9876543210")

        val viewModel = OtpViewModel("+91", "9876543210", repository, sessionManager)

        // Forward entry
        assertEquals(1, viewModel.onDigitEntered(0, "4"))
        assertEquals(2, viewModel.onDigitEntered(1, "4"))
        assertEquals(3, viewModel.onDigitEntered(2, "9"))
        assertEquals(3, viewModel.onDigitEntered(3, "1")) // capped at 3
        assertEquals("4491", viewModel.uiState.value.otpString)
        assertTrue(viewModel.uiState.value.isComplete)

        // Backspace on filled box 3 -> clears box 3 and returns focus 3
        val backFocus1 = viewModel.onBackspace(3)
        assertEquals(3, backFocus1)
        assertEquals("", viewModel.uiState.value.digits[3])
        assertEquals("449", viewModel.uiState.value.otpString)
        assertFalse(viewModel.uiState.value.isComplete)

        // Backspace on empty box 3 -> clears box 2 and returns focus 2 (focus regression)
        val backFocus2 = viewModel.onBackspace(3)
        assertEquals(2, backFocus2)
        assertEquals("", viewModel.uiState.value.digits[2])
        assertEquals("44", viewModel.uiState.value.otpString)

        // Rapid entry with non-numeric / empty characters
        val ignored = viewModel.onDigitEntered(2, "x")
        assertEquals(2, ignored) // index didn't advance
        assertEquals("", viewModel.uiState.value.digits[2])
    }

    @Test
    fun probeOtp_LockoutEnforcementAfterThreeConsecutiveFailures() = runTest {
        val sessionManager = AuthSessionManager()
        val repository = AuthRepositoryImpl(sessionManager)
        repository.sendOtp("+91", "9876543210")

        val viewModel = OtpViewModel("+91", "9876543210", repository, sessionManager)

        // Attempt 1: wrong code 0000
        viewModel.onDigitEntered(0, "0")
        viewModel.onDigitEntered(1, "0")
        viewModel.onDigitEntered(2, "0")
        viewModel.onDigitEntered(3, "0")
        viewModel.onVerifyClicked {}
        testScheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.attemptsLeft)
        assertFalse(viewModel.uiState.value.isLocked)
        assertFalse(viewModel.uiState.value.isVerified)

        // Attempt 2: wrong code 1111
        viewModel.onDigitEntered(0, "1")
        viewModel.onDigitEntered(1, "1")
        viewModel.onDigitEntered(2, "1")
        viewModel.onDigitEntered(3, "1")
        viewModel.onVerifyClicked {}
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.attemptsLeft)
        assertFalse(viewModel.uiState.value.isLocked)

        // Attempt 3: wrong code 2222 -> triggers Lockout
        viewModel.onDigitEntered(0, "2")
        viewModel.onDigitEntered(1, "2")
        viewModel.onDigitEntered(2, "2")
        viewModel.onDigitEntered(3, "2")
        viewModel.onVerifyClicked {}
        testScheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.attemptsLeft)
        assertTrue("Must be locked out after 3 failures", viewModel.uiState.value.isLocked)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("exhausted") == true)

        // Attempt 4: entering correct bypass code 1234 while locked out must be blocked!
        viewModel.onDigitEntered(0, "1")
        viewModel.onDigitEntered(1, "2")
        viewModel.onDigitEntered(2, "3")
        viewModel.onDigitEntered(3, "4")
        var bypassed = false
        viewModel.onVerifyClicked { bypassed = true }
        testScheduler.advanceUntilIdle()

        assertFalse("Verification must not execute when locked", bypassed)
        assertFalse(sessionManager.isAuthenticated)
    }

    @Test
    fun probeOtp_BypassCodesVsUnauthorizedCodes() = runTest {
        // Test bypass code 1234
        val session1 = AuthSessionManager()
        val repo1 = AuthRepositoryImpl(session1)
        repo1.sendOtp("+91", "9876543210")
        val vm1 = OtpViewModel("+91", "9876543210", repo1, session1)

        vm1.onDigitEntered(0, "1")
        vm1.onDigitEntered(1, "2")
        vm1.onDigitEntered(2, "3")
        vm1.onDigitEntered(3, "4")
        var success1 = false
        vm1.onVerifyClicked { success1 = true }
        testScheduler.advanceUntilIdle()
        assertTrue("Bypass code 1234 must verify", success1)
        assertTrue(session1.isAuthenticated)

        // Test bypass code 4491
        val session2 = AuthSessionManager()
        val repo2 = AuthRepositoryImpl(session2)
        repo2.sendOtp("+91", "9876543210")
        val vm2 = OtpViewModel("+91", "9876543210", repo2, session2)

        vm2.onDigitEntered(0, "4")
        vm2.onDigitEntered(1, "4")
        vm2.onDigitEntered(2, "9")
        vm2.onDigitEntered(3, "1")
        var success2 = false
        vm2.onVerifyClicked { success2 = true }
        testScheduler.advanceUntilIdle()
        assertTrue("Bypass code 4491 must verify", success2)
        assertTrue(session2.isAuthenticated)

        // Test unauthorized code 8888
        val session3 = AuthSessionManager()
        val repo3 = AuthRepositoryImpl(session3)
        repo3.sendOtp("+91", "9876543210")
        val vm3 = OtpViewModel("+91", "9876543210", repo3, session3)

        vm3.onDigitEntered(0, "8")
        vm3.onDigitEntered(1, "8")
        vm3.onDigitEntered(2, "8")
        vm3.onDigitEntered(3, "8")
        var success3 = false
        vm3.onVerifyClicked { success3 = true }
        testScheduler.advanceUntilIdle()
        assertFalse("Unauthorized code 8888 must fail", success3)
        assertFalse(session3.isAuthenticated)
    }

    @Test
    fun probeOtp_CooldownTimerAndResendUnlock() = runTest {
        val sessionManager = AuthSessionManager()
        val repository = AuthRepositoryImpl(sessionManager)
        repository.sendOtp("+91", "9876543210")

        val viewModel = OtpViewModel("+91", "9876543210", repository, sessionManager)

        // Initially remainingSeconds = 30, resend is disabled
        assertEquals(30, viewModel.uiState.value.remainingSeconds)
        assertFalse(viewModel.uiState.value.isResendEnabled)

        // Calling onResendClicked before cooldown finishes has no effect
        viewModel.onResendClicked()
        assertFalse(viewModel.uiState.value.isResendEnabled)

        // Advance virtual time by 15 seconds
        testScheduler.advanceTimeBy(15_000L)
        testScheduler.runCurrent()
        assertEquals(15, viewModel.uiState.value.remainingSeconds)
        assertFalse(viewModel.uiState.value.isResendEnabled)

        // Advance remaining 15 seconds
        testScheduler.advanceTimeBy(15_000L)
        testScheduler.runCurrent()
        assertEquals(0, viewModel.uiState.value.remainingSeconds)
        assertTrue("Resend must be enabled when countdown reaches 0", viewModel.uiState.value.isResendEnabled)

        // Lock out attempts first (re-entering digits each time because failed attempt clears digits)
        fun enterWrongCode() {
            viewModel.onDigitEntered(0, "0")
            viewModel.onDigitEntered(1, "0")
            viewModel.onDigitEntered(2, "0")
            viewModel.onDigitEntered(3, "0")
            viewModel.onVerifyClicked {}
            testScheduler.runCurrent()
        }
        enterWrongCode()
        enterWrongCode()
        enterWrongCode()
        assertTrue("Must be locked out after 3 wrong attempts", viewModel.uiState.value.isLocked)

        // Now trigger Resend
        viewModel.onResendClicked()
        testScheduler.runCurrent() // execute resend coroutine without advancing 30s countdown

        // Attempts restored, timer reset, lockout cleared
        assertEquals("attemptsLeft restored to 3", 3, viewModel.uiState.value.attemptsLeft)
        assertFalse("Lockout cleared", viewModel.uiState.value.isLocked)
        assertEquals("Timer reset to 30", 30, viewModel.uiState.value.remainingSeconds)

        // Drain pending countdown coroutine so runTest completes cleanly
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun probeOtp_Boundary_NonDigitAndPartialInputs() = runTest {
        val sessionManager = AuthSessionManager()
        val repository = AuthRepositoryImpl(sessionManager)
        repository.sendOtp("+91", "9876543210")

        // Partial 2-digit OTP directly to repository
        val partialResult = repository.verifyOtp("+91", "9876543210", "12")
        assertTrue(partialResult.isFailure)
        assertTrue(partialResult.exceptionOrNull() is IllegalArgumentException)

        // Non-digit input to repository (e.g. "abcd")
        val alphaResult = repository.verifyOtp("+91", "9876543210", "abcd")
        assertTrue(alphaResult.isFailure)
        assertTrue(alphaResult.exceptionOrNull() is IllegalArgumentException)

        // 5-digit OTP directly to repository
        val overLengthOtp = repository.verifyOtp("+91", "9876543210", "12345")
        assertTrue(overLengthOtp.isFailure)
    }

    // =========================================================================
    // 3. ONBOARDING NAVIGATION & PAGER BOUNDS (Feature 5)
    // =========================================================================

    @Test
    fun probeOnboarding_PagerBoundaryClamping() = runTest {
        val prefs = InMemorySessionPreferences()
        val viewModel = OnboardingViewModel(prefs)

        assertEquals(3, OnboardingCatalog.slides.size)
        assertEquals(0, viewModel.uiState.value.currentPage)

        // Negative page clamping (should clamp to 0)
        viewModel.onPageChanged(-1)
        assertEquals(0, viewModel.uiState.value.currentPage)
        viewModel.onPageChanged(-99)
        assertEquals(0, viewModel.uiState.value.currentPage)

        // Positive page clamping (should clamp to max index 2)
        viewModel.onPageChanged(2)
        assertEquals(2, viewModel.uiState.value.currentPage)
        viewModel.onPageChanged(3)
        assertEquals(2, viewModel.uiState.value.currentPage)
        viewModel.onPageChanged(100)
        assertEquals(2, viewModel.uiState.value.currentPage)
    }

    @Test
    fun probeOnboarding_StandardNextProgression() = runTest {
        val prefs = InMemorySessionPreferences()
        val viewModel = OnboardingViewModel(prefs)

        var navigated = false
        val onNavigate = { navigated = true }

        // Slide 0 -> Next -> Slide 1
        viewModel.onNextClicked(onNavigate)
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.currentPage)
        assertFalse(navigated)
        assertFalse(prefs.isOnboardingCompleted())

        // Slide 1 -> Next -> Slide 2
        viewModel.onNextClicked(onNavigate)
        testScheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.currentPage)
        assertTrue(viewModel.uiState.value.isFinalPage)
        assertEquals("GET STARTED", viewModel.uiState.value.actionButtonText)
        assertFalse(navigated)
        assertFalse(prefs.isOnboardingCompleted())

        // Slide 2 -> Get Started -> Completed
        viewModel.onNextClicked(onNavigate)
        testScheduler.advanceUntilIdle()
        assertTrue(navigated)
        assertTrue(viewModel.uiState.value.isCompleted)
        assertTrue(prefs.isOnboardingCompleted())
    }

    @Test
    fun probeOnboarding_SkipActionCompletesImmediately() = runTest {
        val prefs = InMemorySessionPreferences()
        val viewModel = OnboardingViewModel(prefs)

        assertFalse(prefs.isOnboardingCompleted())

        var navigated = false
        viewModel.onSkipClicked { navigated = true }
        testScheduler.advanceUntilIdle()

        assertTrue("Navigation must trigger immediately on skip", navigated)
        assertTrue("Onboarding completed flag must be true", prefs.isOnboardingCompleted())
        assertTrue("UI state completed must be true", viewModel.uiState.value.isCompleted)
    }

    @Test
    fun probeSessionPreferences_PersistenceInStateFlow() {
        val prefs = InMemorySessionPreferences()
        assertFalse(prefs.isOnboardingCompleted())
        assertFalse(prefs.isOnboardingCompletedFlow.value)

        prefs.setOnboardingCompleted(true)
        assertTrue(prefs.isOnboardingCompleted())
        assertTrue(prefs.isOnboardingCompletedFlow.value)

        prefs.setOnboardingCompleted(false)
        assertFalse(prefs.isOnboardingCompleted())
        assertFalse(prefs.isOnboardingCompletedFlow.value)
    }
}
