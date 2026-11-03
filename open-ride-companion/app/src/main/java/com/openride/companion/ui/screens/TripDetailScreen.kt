package com.openride.companion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openride.companion.data.TripRepository
import com.openride.companion.navigation.model.TrackingFormatters
import com.openride.companion.navigation.model.TripRecord
import com.openride.companion.ui.theme.ActionBlue
import com.openride.companion.ui.theme.BrandNavy
import com.openride.companion.ui.theme.CyanAccent
import com.openride.companion.ui.theme.ElevatedNavy
import com.openride.companion.ui.theme.SurfaceDarkBorder
import com.openride.companion.ui.theme.SurfaceDarkTextSec
import kotlinx.coroutines.launch

@Composable
fun TripDetailScreen(
    tripId: String,
    tripRepository: TripRepository,
    onNavigateBack: () -> Unit = {}
) {
    var trip by remember { mutableStateOf<TripRecord?>(null) }
    var userNoteText by remember { mutableStateOf("") }
    var isEditingNote by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tripId) {
        val loaded = tripRepository.getTripById(tripId)
        trip = loaded
        userNoteText = loaded?.userNote ?: ""
    }

    val currentTrip = trip

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandNavy)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Ride Telematics Detail",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (currentTrip == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Loading trip data...", color = SurfaceDarkTextSec)
            }
            return
        }

        // 1. Trip Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ElevatedNavy),
            border = BorderStroke(1.dp, SurfaceDarkBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = currentTrip.dateFormatted, color = SurfaceDarkTextSec, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentTrip.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${currentTrip.vehicleModelName} • ${currentTrip.vehicleVin}",
                    color = CyanAccent,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${currentTrip.startLocationName} → ${currentTrip.endLocationName}",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Metrics Grid
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ElevatedNavy),
            border = BorderStroke(1.dp, SurfaceDarkBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Ride Performance Metrics", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailMetricItem(label = "Distance", value = "%.1f km".format(currentTrip.distanceKm), icon = Icons.Default.Route)
                    DetailMetricItem(label = "Duration", value = TrackingFormatters.formatDuration(currentTrip.durationSeconds), icon = Icons.Default.Timer)
                    DetailMetricItem(label = "Top Speed", value = "%.1f km/h".format(currentTrip.maxSpeedKmh), icon = Icons.Default.Speed)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailMetricItem(label = "Avg Speed", value = "%.1f km/h".format(currentTrip.avgSpeedKmh), icon = Icons.Default.Navigation)
                    DetailMetricItem(label = "Fuel Economy", value = "%.1f km/l".format(currentTrip.fuelEfficiencyKmpl), icon = Icons.Default.LocalGasStation)
                    DetailMetricItem(label = "Elevation Gain", value = "%.0f m".format(currentTrip.elevationGainMeters), icon = Icons.Default.Landscape)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Lean Angle Cornering Gauge
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ElevatedNavy),
            border = BorderStroke(1.dp, SurfaceDarkBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Peak Cornering Lean Angles", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "%.1f°".format(currentTrip.maxLeanAngleLeft), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                        Text(text = "Max Left Lean", fontSize = 12.sp, color = SurfaceDarkTextSec)
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                        contentDescription = "Bike",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "%.1f°".format(currentTrip.maxLeanAngleRight), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                        Text(text = "Max Right Lean", fontSize = 12.sp, color = SurfaceDarkTextSec)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Hard Braking Events
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ElevatedNavy),
            border = BorderStroke(1.dp, SurfaceDarkBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Braking",
                    tint = if (currentTrip.hardBrakingCount > 0) Color(0xFFFFA000) else Color(0xFF22C55E),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "${currentTrip.hardBrakingCount} Harsh Braking Events",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (currentTrip.hardBrakingCount > 0) "Deceleration exceeded 15 km/h per second" else "Smooth riding! No abrupt decelerations detected",
                        color = SurfaceDarkTextSec,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Rider Journal / Note Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ElevatedNavy),
            border = BorderStroke(1.dp, SurfaceDarkBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Rider Journal Note", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    IconButton(onClick = { isEditingNote = !isEditingNote }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = CyanAccent)
                    }
                }

                if (isEditingNote) {
                    OutlinedTextField(
                        value = userNoteText,
                        onValueChange = { userNoteText = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = SurfaceDarkBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                tripRepository.updateTripUserNote(currentTrip.id, userNoteText)
                                trip = currentTrip.copy(userNote = userNoteText)
                                isEditingNote = false
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Note", color = CyanAccent)
                    }
                } else {
                    Text(
                        text = if (userNoteText.isNotBlank()) userNoteText else "No notes added for this ride. Tap edit to log your thoughts.",
                        color = if (userNoteText.isNotBlank()) Color.White else SurfaceDarkTextSec,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DetailMetricItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text = label, color = SurfaceDarkTextSec, fontSize = 10.sp)
    }
}
