package com.openride.companion.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openride.companion.data.auth.AuthRepository
import com.openride.companion.model.SupportedCountry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onCountrySelected(country: SupportedCountry) {
        _uiState.update { current ->
            val reSanitized = country.sanitize(current.phoneNumberInput)
            current.copy(
                selectedCountry = country,
                phoneNumberInput = reSanitized,
                isCountryPickerOpen = false,
                errorMessage = null
            )
        }
    }

    fun onPhoneInputChanged(input: String) {
        val sanitized = _uiState.value.selectedCountry.sanitize(input)
        _uiState.update {
            it.copy(
                phoneNumberInput = sanitized,
                errorMessage = null
            )
        }
    }

    fun onTermsToggled(accepted: Boolean) {
        _uiState.update { it.copy(termsAccepted = accepted) }
    }

    fun setCountryPickerOpen(open: Boolean) {
        _uiState.update { it.copy(isCountryPickerOpen = open) }
    }

    fun onSendOtpClicked(onOtpDispatched: (dialCode: String, phoneNumber: String) -> Unit) {
        val state = _uiState.value
        if (!state.isSubmitEnabled) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.sendOtp(
                dialCode = state.selectedCountry.dialCode,
                phoneNumber = state.phoneNumberInput
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                    onOtpDispatched(state.selectedCountry.dialCode, state.phoneNumberInput)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Failed to dispatch OTP. Please try again."
                        )
                    }
                }
            )
        }
    }
}
