package com.openride.companion.ui.screens.profile

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateToSos: () -> Unit,
    onNavigateToAuth: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BajajGradients.HeroNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Screen Header
            Text(
                text = "Rider Profile",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            )

            // Avatar Section
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(ElevatedNavy)
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
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(ActionBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Avatar",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 1. Personal Identification Card ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "RIDER DETAILS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ElevatedNavy.copy(alpha = 0.5f),
                            unfocusedContainerColor = ElevatedNavy.copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ElevatedNavy.copy(alpha = 0.5f),
                            unfocusedContainerColor = ElevatedNavy.copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.profile?.phoneNumber ?: "",
                        onValueChange = {},
                        enabled = false,
                        label = { Text("Phone Number") },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledContainerColor = ElevatedNavy.copy(alpha = 0.3f),
                            disabledTextColor = Color.White.copy(alpha = 0.7f),
                            disabledBorderColor = SurfaceDarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 2. Medical Blood Group Card ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "EMERGENCY BLOOD GROUP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(RiderProfile.VALID_BLOOD_GROUPS.toList()) { group ->
                            val isSelected = state.selectedBloodGroup == group
                            Box(
                                modifier = Modifier
                                    .size(width = 64.dp, height = 36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ActionBlue else ElevatedNavy)
                                    .border(1.dp, if (isSelected) CyanAccent else SurfaceDarkBorder, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.onBloodGroupSelected(group) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = group,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else SurfaceDarkTextSec
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 3. Ride Preferences Card ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PREFERENCES & SAFETY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Distance Units Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Distance Units", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(text = "Cluster and app metric units", fontSize = 11.sp, color = SurfaceDarkTextSec)
                        }

                        Row {
                            FilterChip(
                                selected = state.selectedDistanceUnit == "km",
                                onClick = { viewModel.onDistanceUnitToggled("km") },
                                label = { Text("KM") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ActionBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FilterChip(
                                selected = state.selectedDistanceUnit == "miles",
                                onClick = { viewModel.onDistanceUnitToggled("miles") },
                                label = { Text("MILES") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ActionBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Theme Selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "App Theme", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(text = state.selectedThemeMode.title, fontSize = 11.sp, color = SurfaceDarkTextSec)
                        }

                        Row {
                            FilterChip(
                                selected = state.selectedThemeMode == ThemeMode.DARK,
                                onClick = { viewModel.onThemeModeSelected(ThemeMode.DARK) },
                                label = { Text("Dark") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ActionBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FilterChip(
                                selected = state.selectedThemeMode == ThemeMode.LIGHT,
                                onClick = { viewModel.onThemeModeSelected(ThemeMode.LIGHT) },
                                label = { Text("Light") }
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
                            Text(text = "Helmet Safety Reminder", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(text = "Prompt to wear helmet before ride launch", fontSize = 11.sp, color = SurfaceDarkTextSec)
                        }
                        Switch(
                            checked = state.isHelmetReminderEnabled,
                            onCheckedChange = { viewModel.onHelmetReminderToggled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SuccessGreen
                            )
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
                            Text(text = "Cluster Notifications", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(text = "Relay calls & SMS alerts to cluster display", fontSize = 11.sp, color = SurfaceDarkTextSec)
                        }
                        Switch(
                            checked = state.isNotificationsEnabled,
                            onCheckedChange = { viewModel.onNotificationsToggled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SuccessGreen
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 4. Emergency Contacts Quick Summary Row ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSos() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "SOS Contacts",
                            tint = CriticalRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Emergency SOS & RSA",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Manage broadcast contacts & 24x7 RSA hotline",
                                fontSize = 11.sp,
                                color = SurfaceDarkTextSec
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Navigate to SOS",
                        tint = SurfaceDarkTextSec,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 5. Action Buttons ---
            Button(
                onClick = { viewModel.saveChanges() },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (state.isLoading) "SAVING..." else "SAVE CHANGES",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { viewModel.openLogoutDialog() },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CriticalRed),
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
                    fontSize = 13.sp,
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
                containerColor = SurfaceDarkCard,
                title = { Text("Select Avatar", color = Color.White, fontWeight = FontWeight.Bold) },
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
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, ActionBlue, CircleShape)
                                    .clickable { viewModel.selectAvatar(assetName) }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.closeAvatarPicker() }) {
                        Text("CLOSE", color = CyanAccent)
                    }
                }
            )
        }

        // --- Logout Confirmation Dialog ---
        if (state.isLogoutDialogOpen) {
            AlertDialog(
                onDismissRequest = { viewModel.closeLogoutDialog() },
                containerColor = SurfaceDarkCard,
                title = { Text("Log Out?", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Logging out will disconnect your motorcycle cluster and require mobile OTP re-authentication.",
                        color = SurfaceDarkTextSec,
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
                        Text("CANCEL", color = SurfaceDarkTextSec)
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
