package com.openride.companion.ui.screens.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openride.companion.data.profile.ProfileRepository
import com.openride.companion.data.sos.EmergencySosRepository
import com.openride.companion.model.EmergencyContact
import com.openride.companion.model.SosBroadcastStatus
import com.openride.companion.model.SosIncidentPayload
import com.openride.companion.model.SosTriggerSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SosUiState(
    val contacts: List<EmergencyContact> = emptyList(),
    val isAddContactDialogOpen: Boolean = false,
    val newContactName: String = "",
    val newContactPhone: String = "",
    val newContactRelation: String = "Family",
    val addContactError: String? = null,
    val activeSosPayload: SosIncidentPayload? = null,
    val countdownRemaining: Int = 5,
    val isCountdownActive: Boolean = false,
    val isDispatchedBannerVisible: Boolean = false,
    val isTriggerConfirmDialogOpen: Boolean = false
)

class SosViewModel(
    private val sosRepository: EmergencySosRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SosUiState())
    val uiState: StateFlow<SosUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            sosRepository.contactsState.collect { list ->
                _uiState.update { it.copy(contacts = list) }
            }
        }
        viewModelScope.launch {
            sosRepository.activeSosState.collect { payload ->
                _uiState.update { it.copy(activeSosPayload = payload) }
            }
        }
    }

    fun openAddContactDialog() {
        _uiState.update {
            it.copy(
                isAddContactDialogOpen = true,
                newContactName = "",
                newContactPhone = "",
                newContactRelation = "Family",
                addContactError = null
            )
        }
    }

    fun closeAddContactDialog() {
        _uiState.update { it.copy(isAddContactDialogOpen = false, addContactError = null) }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(newContactName = name, addContactError = null) }
    }

    fun onPhoneChange(phone: String) {
        _uiState.update { it.copy(newContactPhone = phone, addContactError = null) }
    }

    fun onRelationChange(relation: String) {
        _uiState.update { it.copy(newContactRelation = relation) }
    }

    fun submitAddContact() {
        val state = _uiState.value
        if (state.newContactName.isBlank()) {
            _uiState.update { it.copy(addContactError = "Contact name cannot be blank") }
            return
        }
        if (state.newContactPhone.isBlank()) {
            _uiState.update { it.copy(addContactError = "Phone number cannot be blank") }
            return
        }

        val newContact = EmergencyContact(
            name = state.newContactName.trim(),
            phone = state.newContactPhone.trim(),
            relationship = state.newContactRelation,
            isPrimary = state.contacts.isEmpty()
        )

        viewModelScope.launch {
            val result = sosRepository.addContact(newContact)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isAddContactDialogOpen = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(addContactError = error.localizedMessage ?: "Failed to add contact")
                    }
                }
            )
        }
    }

    fun removeContact(contactId: String) {
        viewModelScope.launch {
            sosRepository.removeContact(contactId)
        }
    }

    fun setPrimaryContact(contactId: String) {
        viewModelScope.launch {
            sosRepository.setPrimaryContact(contactId)
        }
    }

    fun triggerManualSos() {
        viewModelScope.launch {
            val profile = profileRepository.getProfile()
            val payload = sosRepository.initiateSos(
                source = SosTriggerSource.MANUAL_BUTTON,
                riderName = profile.riderName,
                latitude = 18.5204, // Default telemetry baseline GPS
                longitude = 73.8567,
                batteryVoltage = 12.6f,
                vehicleModel = "Bajaj Motorcycle"
            )

            startCountdown(5, payload)
        }
    }

    private fun startCountdown(seconds: Int, payload: SosIncidentPayload) {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                isCountdownActive = true,
                countdownRemaining = seconds,
                isDispatchedBannerVisible = false
            )
        }

        countdownJob = viewModelScope.launch {
            for (sec in seconds downTo 1) {
                _uiState.update { it.copy(countdownRemaining = sec) }
                delay(1000L)
            }
            // Dispatched after countdown
            sosRepository.dispatchSos(payload)
            _uiState.update {
                it.copy(
                    isCountdownActive = false,
                    isDispatchedBannerVisible = true
                )
            }
        }
    }

    fun cancelSos() {
        countdownJob?.cancel()
        viewModelScope.launch {
            sosRepository.cancelSos()
            _uiState.update {
                it.copy(
                    isCountdownActive = false,
                    countdownRemaining = 5,
                    isDispatchedBannerVisible = false
                )
            }
        }
    }
}
