package com.openride.companion.ui.screens.auth

data class OtpUiState(
    val dialCode: String,
    val phoneNumber: String,
    val digits: List<String> = listOf("", "", "", ""),
    val remainingSeconds: Int = 30,
    val attemptsLeft: Int = 3,
    val isVerifying: Boolean = false,
    val errorMessage: String? = null,
    val isVerified: Boolean = false
) {
    val otpString: String = digits.joinToString("")
    val isComplete: Boolean = otpString.length == 4 && otpString.all { it.isDigit() }
    val isResendEnabled: Boolean = remainingSeconds == 0 && !isVerifying
    val isLocked: Boolean = attemptsLeft <= 0
}
