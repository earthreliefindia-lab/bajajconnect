package com.openride.companion.ui.screens.profile

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    vehicleRepository: com.openride.companion.data.vehicle.VehicleRepository? = null,
    tripRepository: com.openride.companion.data.TripRepository? = null,
    documentRepository: com.openride.companion.data.DocumentLockerRepository? = null,
    onNavigateToAuth: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Custom Profile Photo Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val file = File(context.filesDir, "custom_rider_avatar.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.selectCustomAvatar(file.absolutePath)
                android.widget.Toast.makeText(context, "Profile photo updated!", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to load image: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

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

            // Custom / Preset Avatar Section
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { viewModel.openAvatarPicker() },
                contentAlignment = Alignment.Center
            ) {
                val customFile = state.profile?.customImageUri?.let { File(it) }
                val customBitmap = remember(state.profile?.customImageUri) {
                    if (customFile != null && customFile.exists()) {
                        BitmapFactory.decodeFile(customFile.absolutePath)
                    } else null
                }

                if (customBitmap != null) {
                    Image(
                        bitmap = customBitmap.asImageBitmap(),
                        contentDescription = "Profile Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val avatarRes = resolveAvatarDrawable(state.profile?.avatarAsset)
                    Image(
                        painter = painterResource(id = avatarRes),
                        contentDescription = "Profile Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Camera / Edit Overlay Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Avatar",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 0. Official Bajaj Connect Account Verification Banner ---
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (state.profile?.isBajajVerified == true) SuccessGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (state.profile?.isBajajVerified == true) SuccessGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.profile?.isBajajVerified == true) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = SuccessGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Official Bajaj Verified Account",
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen,
                                fontSize = 13.sp
                            )
                            Text(
                                text = state.profile?.phoneNumber?.ifBlank { "Mobile Verified" } ?: "Mobile Verified",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Unverified",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Link Official Bajaj Account",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Configure mobile OTP from official Bajaj app",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { viewModel.openBajajVerifyDialog() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("VERIFY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

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
                        value = state.phoneInput.ifBlank { state.profile?.phoneNumber ?: "" },
                        onValueChange = { viewModel.onPhoneChange(it) },
                        label = { Text("Registered Mobile Number") },
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(RiderProfile.VALID_BLOOD_GROUPS.toList()) { group ->
                            FilterChip(
                                selected = state.selectedBloodGroup == group,
                                onClick = { viewModel.onBloodGroupSelected(group) },
                                label = { Text(group, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
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

            // --- 3. App Appearance & Dynamic Theme Card ---
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "APPEARANCE & THEME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.selectedThemeMode == ThemeMode.SYSTEM,
                            onClick = { viewModel.onThemeModeSelected(ThemeMode.SYSTEM) },
                            leadingIcon = { Icon(Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("System Wallpaper", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = state.selectedThemeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.onThemeModeSelected(ThemeMode.LIGHT) },
                            leadingIcon = { Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Light", fontSize = 11.sp) },
                            modifier = Modifier.weight(0.7f)
                        )
                        FilterChip(
                            selected = state.selectedThemeMode == ThemeMode.DARK,
                            onClick = { viewModel.onThemeModeSelected(ThemeMode.DARK) },
                            leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Dark", fontSize = 11.sp) },
                            modifier = Modifier.weight(0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- 4. Safety & Cluster Alerts Card ---
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "RIDE ALERTS & PREFERENCES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Smart Helmet Reminder", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            Text(text = "Show pre-ride safety reminder on cluster", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.isHelmetReminderEnabled,
                            onCheckedChange = { viewModel.onHelmetReminderToggled(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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

        // --- Avatar Picker Dialog (Preset Avatars + Custom Photo Upload) ---
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
                title = { Text("Profile Photo", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Option 1: Custom Photo from Device Gallery
                        Button(
                            onClick = {
                                viewModel.closeAvatarPicker()
                                imagePickerLauncher.launch("image/*")
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload Custom Photo", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "— OR SELECT PRESET AVATAR —",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Option 2: Presets
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
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        .clickable { viewModel.selectAvatar(assetName) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.closeAvatarPicker() }) {
                        Text("CANCEL")
                    }
                }
            )
        }

        // --- Official Bajaj Connect Mobile Verification Dialog ---
        if (state.isBajajVerifyDialogOpen) {
            AlertDialog(
                onDismissRequest = { viewModel.closeBajajVerifyDialog() },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bajaj Mobile Verification", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (!state.isOtpSent)
                                "Enter your registered Indian mobile number to sync with the official Bajaj Connect account."
                            else
                                "Enter the OTP sent to +91 ${state.phoneInput}.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!state.isOtpSent) {
                            OutlinedTextField(
                                value = state.phoneInput,
                                onValueChange = { viewModel.onPhoneChange(it) },
                                label = { Text("10-Digit Mobile Number") },
                                prefix = { Text("+91 ") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedTextField(
                                value = state.otpInput,
                                onValueChange = { viewModel.onOtpChange(it) },
                                label = { Text("4 or 6-Digit OTP") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (state.otpError != null) {
                            Text(
                                text = state.otpError ?: "",
                                color = CriticalRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (!state.isOtpSent) {
                                viewModel.requestBajajOtp()
                            } else {
                                viewModel.submitBajajOtp()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        enabled = !state.isLoading
                    ) {
                        Text(
                            text = if (state.isLoading) "WAIT..." else if (!state.isOtpSent) "SEND OTP" else "VERIFY OTP",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.closeBajajVerifyDialog() }) {
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
                    TextButton(onClick = { viewModel.confirmLogout() }) {
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
