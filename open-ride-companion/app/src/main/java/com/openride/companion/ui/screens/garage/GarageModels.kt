package com.openride.companion.ui.screens.garage

import com.openride.companion.model.Vehicle

data class GarageUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val activeVehicle: Vehicle? = null,
    val selectedPageIndex: Int = 0,
    val isAddVehicleDialogOpen: Boolean = false,
    val newVehicleVin: String = "",
    val newVehicleReg: String = "",
    val newVehicleModelName: String = "Bajaj Dominar 400 Dual ABS",
    val addVehicleError: String? = null,
    val isLoading: Boolean = false
) {
    val displayedVehicle: Vehicle?
        get() = vehicles.getOrNull(selectedPageIndex) ?: activeVehicle
}
