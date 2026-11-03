package com.openride.companion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openride.companion.data.TripRepository
import com.openride.companion.navigation.model.CuratedTour
import com.openride.companion.navigation.model.CuratedToursCatalog
import com.openride.companion.navigation.model.TrackingFormatters
import com.openride.companion.navigation.model.TripRecord
import com.openride.companion.ui.theme.ActionBlue
import com.openride.companion.ui.theme.BrandNavy
import com.openride.companion.ui.theme.CyanAccent
import com.openride.companion.ui.theme.ElevatedNavy
import com.openride.companion.ui.theme.SurfaceDarkBorder
import com.openride.companion.ui.theme.SurfaceDarkCard
import com.openride.companion.ui.theme.SurfaceDarkTextSec

@Composable
fun TripsScreen(
    tripRepository: TripRepository,
    onTripClick: (String) -> Unit = {}
) {
    val trips by tripRepository.tripsState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTrips = remember(trips, searchQuery) {
        if (searchQuery.isBlank()) trips
        else trips.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.startLocationName.contains(searchQuery, ignoreCase = true) ||
                    it.endLocationName.contains(searchQuery, ignoreCase = true) ||
                    (it.userNote?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val totalDistance = trips.sumOf { it.distanceKm.toDouble() }.toFloat()
    val maxSpeed = trips.maxOfOrNull { it.maxSpeedKmh } ?: 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandNavy)
            .padding(16.dp)
    ) {
        // 1. Screen Title Header
        Text(
            text = "Trips & Telematics",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 2. Search Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ElevatedNavy),
            border = BorderStroke(1.dp, SurfaceDarkBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = CyanAccent)
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search rides, locations, notes...", color = SurfaceDarkTextSec, fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = SurfaceDarkTextSec)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Segmented Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = CyanAccent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CyanAccent,
                    height = 3.dp
                )
            },
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("My Rides (${trips.size})", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Curated Tours (5)", fontWeight = FontWeight.SemiBold) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Tab Contents
        if (selectedTab == 0) {
            // Overall Stats Summary Card
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
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "%.1f km".format(totalDistance), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                        Text(text = "Total Distance", fontSize = 11.sp, color = SurfaceDarkTextSec)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${trips.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = "Recorded Rides", fontSize = 11.sp, color = SurfaceDarkTextSec)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "%.1f km/h".format(maxSpeed), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                        Text(text = "Top Speed", fontSize = 11.sp, color = SurfaceDarkTextSec)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredTrips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null, tint = SurfaceDarkTextSec, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No trips recorded yet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Connect to your Pulsar and hit the road!", color = SurfaceDarkTextSec, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredTrips) { trip ->
                        TripCardItem(trip = trip, onClick = { onTripClick(trip.id) })
                    }
                }
            }
        } else {
            // Curated Tours Catalog
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(CuratedToursCatalog.tours) { tour ->
                    CuratedTourCard(tour = tour)
                }
            }
        }
    }
}

@Composable
fun TripCardItem(trip: TripRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                Text(text = trip.dateFormatted, color = SurfaceDarkTextSec, fontSize = 12.sp)
                Icon(Icons.Default.ChevronRight, contentDescription = "View", tint = SurfaceDarkTextSec, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = trip.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics row: Distance, Duration, Top Speed, Mileage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricChip(icon = Icons.Default.Route, label = "Distance", value = "%.1f km".format(trip.distanceKm))
                MetricChip(icon = Icons.Default.Timer, label = "Duration", value = TrackingFormatters.formatDuration(trip.durationSeconds))
                MetricChip(icon = Icons.Default.Speed, label = "Top Speed", value = "%.1f km/h".format(trip.maxSpeedKmh))
                MetricChip(icon = Icons.Default.LocalGasStation, label = "Mileage", value = "%.1f km/l".format(trip.fuelEfficiencyKmpl))
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${trip.startLocationName} → ${trip.endLocationName}",
                    color = SurfaceDarkTextSec,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MetricChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(text = label, color = SurfaceDarkTextSec, fontSize = 10.sp)
    }
}

@Composable
fun CuratedTourCard(tour: CuratedTour) {
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
                Text(
                    text = tour.subtitle,
                    color = CyanAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                // Difficulty Badge
                Box(
                    modifier = Modifier
                        .background(
                            when (tour.difficulty) {
                                "Epic" -> Color(0xFFFF3B30).copy(alpha = 0.2f)
                                "Challenging" -> Color(0xFFFFA000).copy(alpha = 0.2f)
                                else -> Color(0xFF22C55E).copy(alpha = 0.2f)
                            },
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tour.difficulty,
                        color = when (tour.difficulty) {
                            "Epic" -> Color(0xFFFF3B30)
                            "Challenging" -> Color(0xFFFFA000)
                            else -> Color(0xFF22C55E)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tour.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = tour.description,
                color = SurfaceDarkTextSec,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "%.0f km • %.1f hrs".format(tour.distanceKm, tour.estimatedDurationHours), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(text = "${tour.startCity} → ${tour.destinationCity}", color = SurfaceDarkTextSec, fontSize = 12.sp)
            }
        }
    }
}
