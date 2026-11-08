package com.openride.companion.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openride.companion.data.profile.ProfileRepository
import com.openride.companion.model.RiderProfile
import com.openride.companion.model.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = false,
    val profile: RiderProfile? = null,
    val isAvatarPickerOpen: Boolean = false,
    val isLogoutDialogOpen: Boolean = false,
    val isBajajVerifyDialogOpen: Boolean = false,
    val isOtpSent: Boolean = false,
    val otpInput: String = "",
    val phoneInput: String = "",
    val otpError: String? = null,
    val nameInput: String = "",
    val nameError: String? = null,
    val emailInput: String = "",
    val emailError: String? = null,
    val selectedBloodGroup: String = "O+",
    val selectedDistanceUnit: String = "km",
    val selectedThemeMode: ThemeMode = ThemeMode.SYSTEM,
    val isHelmetReminderEnabled: Boolean = true,
    val isNotificationsEnabled: Boolean = true,
    val isSaveSuccessBannerVisible: Boolean = false
)

sealed interface ProfileUiEvent {
    data class ShowToast(val message: String) : ProfileUiEvent
    data object NavigateToAuth : ProfileUiEvent
    data object NavigateToSosContacts : ProfileUiEvent
}

class ProfileViewModel(
    private val repository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        repository.profileState.value.let { profile ->
            ProfileUiState(
                profile = profile,
                nameInput = profile.riderName,
                emailInput = profile.email,
                phoneInput = profile.phoneNumber,
                selectedBloodGroup = profile.bloodGroup,
                selectedDistanceUnit = profile.preferredDistanceUnit,
                selectedThemeMode = profile.themeOverride,
                isHelmetReminderEnabled = profile.helmetReminderEnabled,
                isNotificationsEnabled = profile.notificationsEnabled
            )
        }
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<ProfileUiEvent>()
    val eventFlow: SharedFlow<ProfileUiEvent> = _eventFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.profileState.collect { profile ->
                _uiState.update { current ->
                    current.copy(
                        profile = profile,
                        nameInput = profile.riderName,
                        emailInput = profile.email,
                        phoneInput = profile.phoneNumber,
                        selectedBloodGroup = profile.bloodGroup,
                        selectedDistanceUnit = profile.preferredDistanceUnit,
                        selectedThemeMode = profile.themeOverride,
                        isHelmetReminderEnabled = profile.helmetReminderEnabled,
                        isNotificationsEnabled = profile.notificationsEnabled
                    )
                }
            }
        }
    }

    fun onNameChange(newName: String) {
        val clamped = newName.take(50)
        _uiState.update {
            it.copy(
                nameInput = clamped,
                nameError = if (clamped.isBlank()) "Name cannot be empty" else null
            )
        }
    }

    fun onEmailChange(newEmail: String) {
        _uiState.update {
            it.copy(
                emailInput = newEmail.trim(),
                emailError = if (!RiderProfile.isValidEmail(newEmail.trim())) "Enter a valid email address" else null
            )
        }
    }

    fun onPhoneChange(phone: String) {
        _uiState.update { it.copy(phoneInput = phone) }
    }

    fun onOtpChange(otp: String) {
        _uiState.update { it.copy(otpInput = otp, otpError = null) }
    }

    fun onBloodGroupSelected(group: String) {
        if (RiderProfile.isValidBloodGroup(group)) {
            _uiState.update { it.copy(selectedBloodGroup = group) }
        }
    }

    fun onDistanceUnitToggled(unit: String) {
        _uiState.update { it.copy(selectedDistanceUnit = RiderProfile.resolveUnit(unit)) }
    }

    fun onThemeModeSelected(mode: ThemeMode) {
        _uiState.update { it.copy(selectedThemeMode = mode) }
    }

    fun onHelmetReminderToggled(enabled: Boolean) {
        _uiState.update { it.copy(isHelmetReminderEnabled = enabled) }
    }

    fun onNotificationsToggled(enabled: Boolean) {
        _uiState.update { it.copy(isNotificationsEnabled = enabled) }
    }

    fun openAvatarPicker() { _uiState.update { it.copy(isAvatarPickerOpen = true) } }
    fun closeAvatarPicker() { _uiState.update { it.copy(isAvatarPickerOpen = false) } }

    fun selectAvatar(assetName: String) {
        viewModelScope.launch {
            repository.updateAvatar(assetName)
            closeAvatarPicker()
        }
    }

    fun selectCustomAvatar(uriString: String) {
        viewModelScope.launch {
            repository.updateCustomAvatar(uriString)
            closeAvatarPicker()
        }
    }

    fun openBajajVerifyDialog() {
        _uiState.update {
            it.copy(
                isBajajVerifyDialogOpen = true,
                isOtpSent = false,
                otpInput = "",
                otpError = null,
                phoneInput = it.profile?.phoneNumber ?: ""
            )
        }
    }

    fun closeBajajVerifyDialog() {
        _uiState.update { it.copy(isBajajVerifyDialogOpen = false) }
    }

    fun requestBajajOtp() {
        val phone = _uiState.value.phoneInput
        if (phone.filter { it.isDigit() }.length < 10) {
            _uiState.update { it.copy(otpError = "Enter a valid 10-digit mobile number") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, otpError = null) }
            val res = repository.sendBajajOtp(phone)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isOtpSent = res.isSuccess,
                    otpError = if (res.isFailure) res.exceptionOrNull()?.message else null
                )
            }
        }
    }

    fun submitBajajOtp() {
        val phone = _uiState.value.phoneInput
        val otp = _uiState.value.otpInput
        if (otp.length < 4) {
            _uiState.update { it.copy(otpError = "Enter 4 or 6-digit OTP") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, otpError = null) }
            val res = repository.verifyBajajOtp(phone, otp)
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isBajajVerifyDialogOpen = false,
                        otpError = null
                    )
                }
                _eventFlow.emit(ProfileUiEvent.ShowToast("Bajaj Account Verified!"))
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        otpError = res.exceptionOrNull()?.message ?: "OTP verification failed"
                    )
                }
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            _eventFlow.emit(ProfileUiEvent.ShowToast("All cached data cleared"))
        }
    }

    fun openLogoutDialog() { _uiState.update { it.copy(isLogoutDialogOpen = true) } }
    fun closeLogoutDialog() { _uiState.update { it.copy(isLogoutDialogOpen = false) } }

    fun saveChanges() {
        val current = _uiState.value
        val formattedName = RiderProfile.formatRiderName(current.nameInput)
        if (formattedName.isBlank()) {
            _uiState.update { it.copy(nameError = "Name cannot be empty") }
            return
        }
        if (!RiderProfile.isValidEmail(current.emailInput)) {
            _uiState.update { it.copy(emailError = "Invalid email format") }
            return
        }

        val baseProfile = current.profile ?: repository.profileState.value
        val updated = baseProfile.copy(
            riderName = formattedName,
            email = current.emailInput,
            phoneNumber = current.phoneInput,
            bloodGroup = current.selectedBloodGroup,
            preferredDistanceUnit = current.selectedDistanceUnit,
            themeOverride = current.selectedThemeMode,
            helmetReminderEnabled = current.isHelmetReminderEnabled,
            notificationsEnabled = current.isNotificationsEnabled
        )

        viewModelScope.launch {
            val result = repository.updateProfile(updated)
            if (result.isSuccess) {
                repository.setPreferredUnit(current.selectedDistanceUnit)
                repository.setHelmetReminder(current.isHelmetReminderEnabled)
                repository.setThemeMode(current.selectedThemeMode)
                _uiState.update { it.copy(isSaveSuccessBannerVisible = true) }
            }
        }
    }

    fun dismissSuccessBanner() {
        _uiState.update { it.copy(isSaveSuccessBannerVisible = false) }
    }

    fun confirmLogout() {
        viewModelScope.launch {
            repository.logout()
            closeLogoutDialog()
            _eventFlow.emit(ProfileUiEvent.NavigateToAuth)
        }
    }
}
