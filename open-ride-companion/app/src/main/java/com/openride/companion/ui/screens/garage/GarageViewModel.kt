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

    fun onModelSelected(modelName: String) {
        _uiState.update { it.copy(newVehicleModelName = modelName) }
    }

    fun submitAddVehicle() {
        val state = _uiState.value
        val cleanVin = VinValidator.normalizeVin(state.newVehicleVin)
        val cleanReg = RtoPlateValidator.normalizeReg(state.newVehicleReg)

        if (!VinValidator.isValidVin(cleanVin)) {
            _uiState.update { it.copy(addVehicleError = "Invalid VIN: Must be 17 chars (excluding I, O, Q)") }
            return
        }
        if (!RtoPlateValidator.isValidReg(cleanReg)) {
            _uiState.update { it.copy(addVehicleError = "Invalid registration number: Must be 8-11 alphanumeric characters") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = vehicleRepository.addVehicle(
                vin = cleanVin,
                registrationNumber = cleanReg,
                modelName = state.newVehicleModelName
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, isAddVehicleDialogOpen = false) }
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
}
