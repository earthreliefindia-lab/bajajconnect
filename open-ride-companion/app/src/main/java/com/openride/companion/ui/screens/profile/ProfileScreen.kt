package com.openride.companion.ui.screens.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openride.companion.R
import com.openride.companion.model.RiderProfile
import com.openride.companion.model.ThemeMode
import com.openride.companion.ui.theme.CriticalRed
import com.openride.companion.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    vehicleRepository: com.openride.companion.data.vehicle.VehicleRepository? = null,
    tripRepository: com.openride.companion.data.TripRepository? = null,
    documentRepository: com.openride.companion.data.DocumentLockerRepository? = null,
    onNavigateToAuth: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showResetConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Screen Header
            Text(
                text = "Rider Profile",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
            )

            // Avatar Section
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { viewModel.openAvatarPicker() },
                contentAlignment = Alignment.Center
            ) {
                val avatarRes = resolveAvatarDrawable(state.profile?.avatarAsset)
                Image(
                    painter = painterResource(id = avatarRes),
                    contentDescription = "Profile Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Avatar",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- 1. Personal Identification Card ---
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "RIDER DETAILS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = state.nameInput,
                        onValueChange = { viewModel.onNameChange(it) },
                        label = { Text("Full Name") },
                        isError = state.nameError != null,
                        supportingText = state.nameError?.let { { Text(it, color = CriticalRed) } },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.emailInput,
                        onValueChange = { viewModel.onEmailChange(it) },
                        label = { Text("Email Address") },
                        isError = state.emailError != null,
                        supportingText = state.emailError?.let { { Text(it, color = CriticalRed) } },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.profile?.phoneNumber ?: "",
                        onValueChange = {},
                        enabled = false,
                        label = { Text("Phone Number") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- 2. Medical Blood Group Card ---
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "EMERGENCY BLOOD GROUP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(RiderProfile.VALID_BLOOD_GROUPS.toList()) { group ->
                            val isSelected = state.selectedBloodGroup == group
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onBloodGroupSelected(group) },
                                label = { Text(group, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- 3. App Theme & Styling (Dynamic Wallpaper / Light / Dark) ---
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "APP THEME & PALETTE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose stock Android wallpaper colors or manual light/dark mode",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3-way Theme Selection Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Option 1: System / Wallpaper
                        val isSystem = state.selectedThemeMode == ThemeMode.SYSTEM
                        FilterChip(
                            selected = isSystem,
                            onClick = { viewModel.onThemeModeSelected(ThemeMode.SYSTEM) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = "System Wallpaper",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("System", fontSize = 12.sp, fontWeight = if (isSystem) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )

                        // Option 2: Light Mode
                        val isLight = state.selectedThemeMode == ThemeMode.LIGHT
                        FilterChip(
                            selected = isLight,
                            onClick = { viewModel.onThemeModeSelected(ThemeMode.LIGHT) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LightMode,
                                    contentDescription = "Light Mode",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Light", fontSize = 12.sp, fontWeight = if (isLight) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )

                        // Option 3: Dark Mode
                        val isDark = state.selectedThemeMode == ThemeMode.DARK
                        FilterChip(
                            selected = isDark,
                            onClick = { viewModel.onThemeModeSelected(ThemeMode.DARK) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DarkMode,
                                    contentDescription = "Dark Mode",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Dark", fontSize = 12.sp, fontWeight = if (isDark) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Distance Units Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Distance Units", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            Text(text = "Cluster and app metric units", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row {
                            FilterChip(
                                selected = state.selectedDistanceUnit == "km",
                                onClick = { viewModel.onDistanceUnitToggled("km") },
                                label = { Text("KM") },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FilterChip(
                                selected = state.selectedDistanceUnit == "miles",
                                onClick = { viewModel.onDistanceUnitToggled("miles") },
                                label = { Text("MILES") },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Helmet Reminder Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Helmet Safety Reminder", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            Text(text = "Prompt to wear helmet before ride launch", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.isHelmetReminderEnabled,
                            onCheckedChange = { viewModel.onHelmetReminderToggled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Notifications Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Cluster Notifications", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            Text(text = "Relay calls & SMS alerts to cluster display", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.isNotificationsEnabled,
                            onCheckedChange = { viewModel.onNotificationsToggled(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- Action Buttons ---
            Button(
                onClick = { viewModel.saveChanges() },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = if (state.isLoading) "SAVING..." else "SAVE PROFILE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { showResetConfirm = true },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Fresh Start",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RESET TO FRESH APP",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { viewModel.openLogoutDialog() },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CriticalRed),
                border = BorderStroke(1.dp, CriticalRed.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Logout",
                    tint = CriticalRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LOG OUT OF VEHICLE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CriticalRed
                )
            }
        }

        // --- Avatar Picker Dialog ---
        if (state.isAvatarPickerOpen) {
            val avatars = listOf(
                "avatar_profile_default.png" to R.drawable.avatar_profile_default,
                "avatar_emergency_contact_1.png" to R.drawable.avatar_emergency_contact_1,
                "avatar_emergency_contact_2.png" to R.drawable.avatar_emergency_contact_2
            )

            AlertDialog(
                onDismissRequest = { viewModel.closeAvatarPicker() },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                title = { Text("Select Avatar", fontWeight = FontWeight.Bold) },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        avatars.forEach { (assetName, resId) ->
                            Image(
                                painter = painterResource(id = resId),
                                contentDescription = assetName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    .clickable { viewModel.selectAvatar(assetName) }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.closeAvatarPicker() }) {
                        Text("CLOSE")
                    }
                }
            )
        }

        // --- Reset Data Confirmation Dialog ---
        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                title = { Text("Reset to Fresh App?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "This will clear all preloaded demo data (trips, notes, documents, and vehicle status) so your app is completely fresh.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                com.openride.companion.search.repository.RideNotesRepository.resetToFresh()
                                tripRepository?.resetToFresh()
                                documentRepository?.resetToFresh()
                                vehicleRepository?.resetToFresh()
                                showResetConfirm = false
                                android.widget.Toast.makeText(context, "App reset to fresh state! Enjoy your new ride.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("RESET TO FRESH", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) {
                        Text("CANCEL")
                    }
                }
            )
        }

        // --- Logout Confirmation Dialog ---
        if (state.isLogoutDialogOpen) {
            AlertDialog(
                onDismissRequest = { viewModel.closeLogoutDialog() },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                title = { Text("Log Out?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Logging out will disconnect your motorcycle cluster and require mobile OTP re-authentication.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmLogout(onNavigateToAuth) }) {
                        Text("LOG OUT", color = CriticalRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.closeLogoutDialog() }) {
                        Text("CANCEL")
                    }
                }
            )
        }
    }
}

fun resolveAvatarDrawable(assetName: String?): Int {
    return when (assetName) {
        "avatar_emergency_contact_1.png" -> R.drawable.avatar_emergency_contact_1
        "avatar_emergency_contact_2.png" -> R.drawable.avatar_emergency_contact_2
        else -> R.drawable.avatar_profile_default
    }
}
