package com.openride.companion.ui.screens.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openride.companion.data.vehicle.VehicleRepository
import com.openride.companion.model.RtoPlateValidator
import com.openride.companion.model.Vehicle
import com.openride.companion.model.VinValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GarageViewModel(
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GarageUiState())
    val uiState: StateFlow<GarageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            vehicleRepository.vehiclesState.collect { list ->
                _uiState.update { current ->
                    val active = list.find { it.isActive } ?: list.firstOrNull()
                    val activeIndex = list.indexOfFirst { it.id == active?.id }.coerceAtLeast(0)
                    current.copy(
                        vehicles = list,
                        activeVehicle = active,
                        selectedPageIndex = if (current.selectedPageIndex in list.indices) current.selectedPageIndex else activeIndex
                    )
                }
            }
        }
    }

    fun onPageChanged(page: Int) {
        _uiState.update { it.copy(selectedPageIndex = page) }
    }

    fun onSelectActiveBike(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleRepository.selectActiveVehicle(vehicle.id)
        }
    }

    fun openAddVehicleDialog() {
        _uiState.update {
            it.copy(
                isAddVehicleDialogOpen = true,
                newVehicleVin = "",
                newVehicleReg = "",
                addVehicleError = null
            )
        }
    }

    fun closeAddVehicleDialog() {
        _uiState.update { it.copy(isAddVehicleDialogOpen = false, addVehicleError = null) }
    }

    fun onVinChanged(vin: String) {
        _uiState.update { it.copy(newVehicleVin = vin.uppercase(), addVehicleError = null) }
    }

    fun onRegChanged(reg: String) {
        _uiState.update { it.copy(newVehicleReg = reg.uppercase(), addVehicleError = null) }
    }

    fun openManageBikesDialog() {
        _uiState.update { it.copy(isManageBikesDialogOpen = true, manageBikesMessage = null) }
    }

    fun closeManageBikesDialog() {
        _uiState.update { it.copy(isManageBikesDialogOpen = false, manageBikesMessage = null) }
    }

    fun removeVehicle(vehicleId: String) {
        val currentList = _uiState.value.vehicles
        if (currentList.size <= 1) {
            _uiState.update { it.copy(manageBikesMessage = "Cannot delete the only motorcycle in your garage") }
            return
        }
        viewModelScope.launch {
            val result = vehicleRepository.removeVehicle(vehicleId)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(manageBikesMessage = "Motorcycle removed successfully") }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(manageBikesMessage = error.localizedMessage ?: "Failed to delete") }
                }
            )
        }
    }

    fun onModelSelected(modelName: String) {
        _uiState.update { it.copy(newVehicleModelName = modelName) }
    }

    fun submitAddVehicle() {
        val state = _uiState.value
        val rawVin = state.newVehicleVin.trim()
        val effectiveVin = if (rawVin.isNotBlank() && VinValidator.isValidVin(VinValidator.normalizeVin(rawVin))) {
            VinValidator.normalizeVin(rawVin)
        } else if (rawVin.isBlank()) {
            "MD2A" + (1000000000000L..9999999999999L).random().toString()
        } else {
            _uiState.update { it.copy(addVehicleError = "Invalid VIN: Must be 17 chars (excluding I, O, Q) or leave blank to auto-generate") }
            return
        }

        val rawReg = state.newVehicleReg.trim().uppercase()
        val effectiveReg = if (rawReg.isNotBlank()) {
            val normalized = RtoPlateValidator.normalizeReg(rawReg)
            if (!RtoPlateValidator.isValidReg(normalized)) {
                _uiState.update { it.copy(addVehicleError = "Invalid plate: Must be 8-11 alphanumeric chars (e.g. MH 12 AB 1234)") }
                return
            }
            normalized
        } else {
            "MH " + (10..99).random() + " TC " + (1000..9999).random()
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = vehicleRepository.addVehicle(
                vin = effectiveVin,
                registrationNumber = effectiveReg,
                modelName = state.newVehicleModelName
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAddVehicleDialogOpen = false,
                            newVehicleVin = "",
                            newVehicleReg = "",
                            addVehicleError = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            addVehicleError = error.localizedMessage ?: "Failed to add vehicle"
                        )
                    }
                }
            )
        }
    }

    fun resetToFresh() {
        viewModelScope.launch {
            vehicleRepository.resetToFresh()
        }
    }
}
