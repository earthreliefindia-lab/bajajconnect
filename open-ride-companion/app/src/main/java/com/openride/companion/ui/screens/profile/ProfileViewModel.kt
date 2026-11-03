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
    val nameInput: String = "",
    val nameError: String? = null,
    val emailInput: String = "",
    val emailError: String? = null,
    val selectedBloodGroup: String = "O+",
    val selectedDistanceUnit: String = "km",
    val selectedThemeMode: ThemeMode = ThemeMode.DARK,
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
            bloodGroup = current.selectedBloodGroup,
            preferredDistanceUnit = current.selectedDistanceUnit,
            themeOverride = current.selectedThemeMode,
            helmetReminderEnabled = current.isHelmetReminderEnabled,
            notificationsEnabled = current.isNotificationsEnabled
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.updateProfile(updated)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isSaveSuccessBannerVisible = result.isSuccess
                )
            }
            if (result.isSuccess) {
                _eventFlow.emit(ProfileUiEvent.ShowToast("Profile updated successfully"))
            }
        }
    }

    fun confirmLogout(onNavigateToAuth: () -> Unit) {
        viewModelScope.launch {
            closeLogoutDialog()
            repository.logout()
            onNavigateToAuth()
        }
    }
}
