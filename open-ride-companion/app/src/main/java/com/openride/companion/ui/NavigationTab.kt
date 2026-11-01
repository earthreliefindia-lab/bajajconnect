package com.openride.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openride.companion.location.LocalSearchManager
import com.openride.companion.location.NearbyCategory
import com.openride.companion.location.ParsedDestination
import com.openride.companion.model.LiveNavInstruction
import com.openride.companion.model.ManeuverType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationTab(
    activeInstruction: LiveNavInstruction,
    isNavigating: Boolean,
    currentDestination: ParsedDestination?,
    searchResults: List<ParsedDestination>,
    isSearching: Boolean,
    userLatitude: Double?,
    userLongitude: Double?,
    routeCoordinates: List<Pair<Double, Double>>,
    mapplsApiKey: String = "",
    onSaveMapplsApiKey: (String) -> Unit = {},
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (NearbyCategory) -> Unit,
    onDestinationSelected: (ParsedDestination) -> Unit,
    onStartNavigation: (ParsedDestination) -> Unit,
    onStopNavigation: () -> Unit,
    onExternalMapsFallback: (ParsedDestination) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showMapplsSetupDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // --- 1. Live Turn-by-Turn Maneuver HUD (Active Navigation) ---
        if (isNavigating) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = ButtonDefaults.outlinedButtonBorder
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "RELAYING TO CLUSTER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        // Real-time GPS Speed
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${activeInstruction.currentSpeedKmh}",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "km/h",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val icon = when (activeInstruction.maneuver) {
                                    ManeuverType.TURN_LEFT, ManeuverType.SLIGHT_LEFT -> Icons.AutoMirrored.Filled.ArrowBack
                                    ManeuverType.TURN_RIGHT, ManeuverType.SLIGHT_RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
                                    ManeuverType.U_TURN -> Icons.Default.Refresh
                                    ManeuverType.ROUNDABOUT -> Icons.Default.Sync
                                    ManeuverType.ARRIVE -> Icons.Default.Place
                                    else -> Icons.Default.ArrowUpward
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeInstruction.formattedManeuverDistance,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = activeInstruction.instructionText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (activeInstruction.nextStreet.isNotBlank()) {
                                Text(
                                    text = "Onto ${activeInstruction.nextStreet}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${activeInstruction.formattedRemainingTime} remaining · ${activeInstruction.formattedRemainingDistance}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Row {
                            currentDestination?.let { dest ->
                                IconButton(onClick = { onExternalMapsFallback(dest) }) {
                                    Icon(
                                        imageVector = Icons.Default.Map,
                                        contentDescription = "Open in Google Maps",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            TextButton(
                                onClick = onStopNavigation,
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
                            ) {
                                Text("End Nav", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Interactive In-App Map ---
        NavigationMapView(
            userLatitude = userLatitude,
            userLongitude = userLongitude,
            destination = currentDestination,
            routeCoordinates = routeCoordinates,
            mapplsApiKey = mapplsApiKey,
            onConfigureMapplsClick = { showMapplsSetupDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isNavigating) 240.dp else 220.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // --- 3. Search Bar for Places & Addresses ---
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onSearchQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text("Search places, shops, fuel, restaurants...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        onSearchQueryChange("")
                    }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(6.dp))

        // --- 4. Nearby Category Quick Chips ---
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(LocalSearchManager.QUICK_CATEGORIES) { category ->
                FilterChip(
                    selected = false,
                    onClick = {
                        searchQuery = category.name
                        onCategorySelected(category)
                    },
                    label = { Text(category.name, fontSize = 12.sp) },
                    leadingIcon = {
                        val icon = when (category.id) {
                            "fuel" -> Icons.Default.LocalGasStation
                            "ev" -> Icons.Default.ElectricBike
                            "repair" -> Icons.Default.Build
                            "food" -> Icons.Default.Restaurant
                            else -> Icons.Default.LocalHospital
                        }
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 5. Selected Destination / Incoming WhatsApp Location Card ---
        if (!isNavigating && currentDestination != null) {
            val dest = currentDestination
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = ButtonDefaults.outlinedButtonBorder
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "DESTINATION: ${dest.sourceApp.uppercase()}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        dest.formattedDistance?.let { dist ->
                            Text(
                                text = dist,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = dest.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    dest.address?.let { addr ->
                        Text(
                            text = addr,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onStartNavigation(dest) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Bike Nav (Mappls)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { onExternalMapsFallback(dest) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = "Two-Wheeler Maps", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bike Maps", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- 6. Search Results List ---
        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (searchResults.isNotEmpty()) {
            Text(
                text = "SEARCH RESULTS (${searchResults.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(searchResults) { result ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onDestinationSelected(result) },
                        color = MaterialTheme.colorScheme.surface,
                        border = ButtonDefaults.outlinedButtonBorder
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                result.address?.let { addr ->
                                    Text(
                                        text = addr,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            result.formattedDistance?.let { dist ->
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = dist,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 7. Mappls Maps (MapmyIndia) Setup Dialog ---
        if (showMapplsSetupDialog) {
            MapplsSetupDialog(
                currentApiKey = mapplsApiKey,
                onSaveApiKey = { newKey ->
                    onSaveMapplsApiKey(newKey)
                    showMapplsSetupDialog = false
                },
                onUseFreeMode = {
                    onSaveMapplsApiKey("")
                    showMapplsSetupDialog = false
                },
                onDismiss = { showMapplsSetupDialog = false }
            )
        }
    }
}
