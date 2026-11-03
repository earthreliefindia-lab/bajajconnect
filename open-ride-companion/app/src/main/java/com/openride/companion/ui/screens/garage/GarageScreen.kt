package com.openride.companion.ui.screens.garage

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ElectricBike
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openride.companion.R
import com.openride.companion.model.EmergencyContact
import com.openride.companion.model.GarageCatalog
import com.openride.companion.model.Vehicle
import com.openride.companion.ui.theme.ActionBlue
import com.openride.companion.ui.theme.BajajGradients
import com.openride.companion.ui.theme.CriticalRed
import com.openride.companion.ui.theme.CyanAccent
import com.openride.companion.ui.theme.ElevatedNavy
import com.openride.companion.ui.theme.LightBlueAccent
import com.openride.companion.ui.theme.SuccessGreen
import com.openride.companion.ui.theme.SurfaceDarkBorder
import com.openride.companion.ui.theme.SurfaceDarkCard
import com.openride.companion.ui.theme.SurfaceDarkTextSec
import com.openride.companion.ui.theme.WarningAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GarageScreen(
    viewModel: GarageViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToSos: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val vehicles = uiState.vehicles
    val pagerState = rememberPagerState(
        initialPage = uiState.selectedPageIndex.coerceIn(0, (vehicles.size - 1).coerceAtLeast(0)),
        pageCount = { vehicles.size }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.onPageChanged(page)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BajajGradients.HeroNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp)
        ) {
            // --- Top App Bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "GARAGE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "My Motorcycles",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = { viewModel.openAddVehicleDialog() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ElevatedNavy)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Vehicle",
                        tint = Color.White
                    )
                }
            }

            // --- Horizontal Bike Pager ---
            if (vehicles.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) { page ->
                    val bike = vehicles[page]
                    val isSelected = bike.isActive

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(250.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkCard),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, CyanAccent) else null
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Top row: Active Badge & Cluster hardware
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(SuccessGreen.copy(alpha = 0.2f))
                                                .border(1.dp, SuccessGreen, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "ACTIVE RIDE",
                                                color = SuccessGreen,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        TextButton(
                                            onClick = { viewModel.onSelectActiveBike(bike) },
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(
                                                text = "SET AS ACTIVE",
                                                color = LightBlueAccent,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Text(
                                        text = bike.modelSpec.clusterHardware,
                                        color = SurfaceDarkTextSec,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Central Bike Image Render
                                Image(
                                    painter = painterResource(id = bike.modelSpec.primaryDrawableRes),
                                    contentDescription = bike.modelSpec.modelName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .height(130.dp)
                                        .fillMaxWidth()
                                )

                                // Bottom Details: Model Name & Reg Plate
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = bike.modelSpec.modelName,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = bike.registrationNumber,
                                        color = SurfaceDarkTextSec,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // Pager Indicators
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(vehicles.size) { index ->
                        val isCurrent = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(6.dp)
                                .width(if (isCurrent) 18.dp else 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isCurrent) CyanAccent else SurfaceDarkBorder)
                        )
                    }
                }
            }

            // Display active or selected bike details
            val currentBike = uiState.displayedVehicle
            if (currentBike != null) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {

                    // 12V Starter Battery Critical Alert Warning Banner
                    if (currentBike.isStarterBatteryCritical) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CriticalRed.copy(alpha = 0.2f)),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, CriticalRed)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Battery Warning",
                                        tint = CriticalRed,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Starter Battery Critical (< 11.8V)",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Alternator voltage low (${currentBike.batteryVoltage12V}V). Dial 24x7 RSA.",
                                            color = SurfaceDarkTextSec,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:18002332453")
                                        }
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRed),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = "Call RSA",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("RSA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // --- Dual Powertrain Energy Gauge Card ---
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkCard)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (currentBike.modelSpec.isElectric) Icons.Default.BatteryChargingFull else Icons.Default.LocalGasStation,
                                        contentDescription = "Energy Icon",
                                        tint = if (currentBike.modelSpec.isElectric) CyanAccent else LightBlueAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = currentBike.primaryEnergyLabel,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                val energyPct = currentBike.effectiveEnergyPercentage
                                Text(
                                    text = "$energyPct%",
                                    color = when {
                                        energyPct <= 10 -> CriticalRed
                                        energyPct <= 20 -> WarningAmber
                                        else -> SuccessGreen
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { currentBike.effectiveEnergyPercentage / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = when {
                                    currentBike.effectiveEnergyPercentage <= 10 -> CriticalRed
                                    currentBike.effectiveEnergyPercentage <= 20 -> WarningAmber
                                    else -> SuccessGreen
                                },
                                trackColor = SurfaceDarkBorder
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (currentBike.modelSpec.isElectric) "3.2 kWh IP67 Battery" else "Tank Capacity: ${currentBike.modelSpec.fuelCapacityLitres}L",
                                    color = SurfaceDarkTextSec,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Estimated Range: ${currentBike.modelSpec.defaultDteKm} km",
                                    color = LightBlueAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // --- 2x2 Quick Status Grid ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Card 1: Odometer & Service Countdown
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(110.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceDarkCard)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "ODOMETER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SurfaceDarkTextSec,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "${currentBike.odometerKm} km",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Service in ${currentBike.remainingServiceKm} km",
                                    fontSize = 11.sp,
                                    color = if (currentBike.remainingServiceKm <= 50) CriticalRed else CyanAccent,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Card 2: Cluster & Wi-Fi Mirroring
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(110.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceDarkCard)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "CLUSTER DISPLAY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SurfaceDarkTextSec,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = currentBike.modelSpec.clusterHardware,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                val isWifiSupported = currentBike.modelSpec.clusterHardware == "Cluster0265"
                                Text(
                                    text = if (isWifiSupported) "Wi-Fi Secondary TFT" else "BLE Inverted LCD",
                                    fontSize = 11.sp,
                                    color = if (isWifiSupported) SuccessGreen else SurfaceDarkTextSec,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Card 3: 12V Electrical Bus
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(110.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceDarkCard)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "12V BUS VOLTAGE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SurfaceDarkTextSec,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "${currentBike.batteryVoltage12V} V",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentBike.isStarterBatteryCritical) CriticalRed else Color.White
                                )
                                Text(
                                    text = if (currentBike.isStarterBatteryCritical) "Critical - Dial RSA" else "Healthy Bus Voltage",
                                    fontSize = 11.sp,
                                    color = if (currentBike.isStarterBatteryCritical) CriticalRed else SuccessGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Card 4: Powertrain Displacement
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(110.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceDarkCard)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "POWERTRAIN",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SurfaceDarkTextSec,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = if (currentBike.modelSpec.isElectric) "Electric PMSM" else "${currentBike.modelSpec.engineDisplacementCc} cc",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (currentBike.modelSpec.isElectric) "Zero Emission EV" else "Liquid/Oil Cooled",
                                    fontSize = 11.sp,
                                    color = LightBlueAccent,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Add Vehicle Modal Dialog ---
        if (uiState.isAddVehicleDialogOpen) {
            AlertDialog(
                onDismissRequest = { viewModel.closeAddVehicleDialog() },
                containerColor = SurfaceDarkCard,
                title = {
                    Text(
                        text = "Add Motorcycle",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Enter your motorcycle VIN and Registration plate number.",
                            color = SurfaceDarkTextSec,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = uiState.newVehicleVin,
                            onValueChange = { viewModel.onVinChanged(it) },
                            label = { Text("17-Character VIN") },
                            placeholder = { Text("MD2A11CY4P8712391") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.newVehicleReg,
                            onValueChange = { viewModel.onRegChanged(it) },
                            label = { Text("Registration Plate") },
                            placeholder = { Text("MH 12 TC 5590") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (uiState.addVehicleError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.addVehicleError!!,
                                color = CriticalRed,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.submitAddVehicle() },
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                    ) {
                        Text("ADD BIKE")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.closeAddVehicleDialog() }) {
                        Text("CANCEL", color = SurfaceDarkTextSec)
                    }
                }
            )
        }
    }
}
