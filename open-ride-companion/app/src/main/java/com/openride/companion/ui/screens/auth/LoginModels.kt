package com.openride.companion.ui.screens.auth

import com.openride.companion.model.CountryCatalog
import com.openride.companion.model.SupportedCountry

data class LoginUiState(
    val selectedCountry: SupportedCountry = CountryCatalog.defaultCountry,
    val phoneNumberInput: String = "",
    val termsAccepted: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCountryPickerOpen: Boolean = false
) {
    val isPhoneValid: Boolean = selectedCountry.validateNumber(phoneNumberInput)
    val isSubmitEnabled: Boolean = isPhoneValid && termsAccepted && !isLoading
}
