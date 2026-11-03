package com.openride.companion.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openride.companion.data.auth.AuthRepository
import com.openride.companion.data.auth.AuthSessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OtpViewModel(
    dialCode: String,
    phoneNumber: String,
    private val authRepository: AuthRepository,
    private val sessionManager: AuthSessionManager = AuthSessionManager.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OtpUiState(dialCode = dialCode, phoneNumber = phoneNumber)
    )
    val uiState: StateFlow<OtpUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        startResendTimer(30)
    }

    private fun startResendTimer(seconds: Int) {
        countdownJob?.cancel()
        _uiState.update { it.copy(remainingSeconds = seconds) }
        countdownJob = viewModelScope.launch {
            for (sec in seconds downTo 0) {
                _uiState.update { it.copy(remainingSeconds = sec) }
                if (sec > 0) delay(1000L)
            }
        }
    }

    fun onDigitEntered(index: Int, rawChar: String): Int {
        val digit = rawChar.filter { it.isDigit() }.takeLast(1)
        if (digit.isEmpty()) return index

        _uiState.update { current ->
            val updated = current.digits.toMutableList()
            updated[index] = digit
            current.copy(digits = updated, errorMessage = null)
        }
        return (index + 1).coerceAtMost(3)
    }

    fun onBackspace(index: Int): Int {
        val currentDigits = _uiState.value.digits
        if (currentDigits[index].isNotEmpty()) {
            _uiState.update { current ->
                val updated = current.digits.toMutableList()
                updated[index] = ""
                current.copy(digits = updated)
            }
            return index
        } else if (index > 0) {
            _uiState.update { current ->
                val updated = current.digits.toMutableList()
                updated[index - 1] = ""
                current.copy(digits = updated)
            }
            return index - 1
        }
        return 0
    }

    fun onVerifyClicked(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!state.isComplete || state.isLocked || state.isVerifying) return

        viewModelScope.launch {
            _uiState.update { it.copy(isVerifying = true, errorMessage = null) }
            val result = authRepository.verifyOtp(
                dialCode = state.dialCode,
                phoneNumber = state.phoneNumber,
                otp = state.otpString
            )
            result.fold(
                onSuccess = { authToken ->
                    sessionManager.setSession(
                        token = authToken.jwtToken,
                        dialCode = state.dialCode,
                        phoneNumber = state.phoneNumber
                    )
                    _uiState.update { it.copy(isVerifying = false, isVerified = true) }
                    onSuccess()
                },
                onFailure = { error ->
                    _uiState.update { current ->
                        val newAttempts = (current.attemptsLeft - 1).coerceAtLeast(0)
                        val errorMsg = if (newAttempts == 0) {
                            "Attempts exhausted. Please tap Resend OTP."
                        } else {
                            "Incorrect OTP Entered. $newAttempts attempt${if (newAttempts > 1) "s" else ""} remaining."
                        }
                        current.copy(
                            isVerifying = false,
                            attemptsLeft = newAttempts,
                            errorMessage = errorMsg,
                            digits = listOf("", "", "", "")
                        )
                    }
                }
            )
        }
    }

    fun onResendClicked() {
        val state = _uiState.value
        if (!state.isResendEnabled) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isVerifying = true,
                    digits = listOf("", "", "", ""),
                    attemptsLeft = 3,
                    errorMessage = null
                )
            }
            val result = authRepository.resendOtp(state.dialCode, state.phoneNumber)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isVerifying = false) }
                    startResendTimer(30)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isVerifying = false,
                            errorMessage = error.localizedMessage ?: "Failed to resend OTP"
                        )
                    }
                }
            )
        }
    }
}
