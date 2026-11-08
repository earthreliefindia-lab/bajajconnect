package com.openride.companion.data.profile

import com.openride.companion.model.RiderProfile
import com.openride.companion.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface ProfileRepository {
    val profileState: StateFlow<RiderProfile>

    suspend fun getProfile(): RiderProfile
    suspend fun updateProfile(updated: RiderProfile): Result<Unit>
    suspend fun updateAvatar(avatarAssetName: String): Result<Unit>
    suspend fun setHelmetReminder(enabled: Boolean): Result<Unit>
    suspend fun setPreferredUnit(unit: String): Result<Unit>
    suspend fun setThemeMode(mode: ThemeMode): Result<Unit>
    suspend fun logout(): Result<Unit>
}

/**
 * Production-ready Profile Repository implementation.
 */
class ProfileRepositoryImpl : ProfileRepository {

    private val defaultProfile = RiderProfile(
        riderId = "USER_DEFAULT_001",
        riderName = "Ketan Sharma",
        email = "ketan.sharma@example.com",
        phoneNumber = "+919876543210",
        bloodGroup = "O+",
        avatarAsset = "avatar_profile_default.png",
        preferredDistanceUnit = "km",
        themeOverride = ThemeMode.SYSTEM,
        helmetReminderEnabled = true,
        notificationsEnabled = true,
        callSmsAlertsEnabled = true
    )

    private val _profileState = MutableStateFlow(defaultProfile)
    override val profileState: StateFlow<RiderProfile> = _profileState.asStateFlow()

    override suspend fun getProfile(): RiderProfile = _profileState.value

    override suspend fun updateProfile(updated: RiderProfile): Result<Unit> {
        val sanitizedName = RiderProfile.formatRiderName(updated.riderName)
        if (sanitizedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Rider name cannot be blank"))
        }
        if (!RiderProfile.isValidBloodGroup(updated.bloodGroup)) {
            return Result.failure(IllegalArgumentException("Invalid blood group: ${updated.bloodGroup}"))
        }
        if (!RiderProfile.isValidEmail(updated.email)) {
            return Result.failure(IllegalArgumentException("Invalid email format: ${updated.email}"))
        }

        val cleaned = updated.copy(
            riderName = sanitizedName,
            preferredDistanceUnit = RiderProfile.resolveUnit(updated.preferredDistanceUnit),
            lastUpdatedTimestampMs = System.currentTimeMillis()
        )
        _profileState.value = cleaned
        return Result.success(Unit)
    }

    override suspend fun updateAvatar(avatarAssetName: String): Result<Unit> {
        _profileState.update { it.copy(avatarAsset = avatarAssetName) }
        return Result.success(Unit)
    }

    override suspend fun setHelmetReminder(enabled: Boolean): Result<Unit> {
        _profileState.update { it.copy(helmetReminderEnabled = enabled) }
        return Result.success(Unit)
    }

    override suspend fun setPreferredUnit(unit: String): Result<Unit> {
        val validUnit = RiderProfile.resolveUnit(unit)
        _profileState.update { it.copy(preferredDistanceUnit = validUnit) }
        return Result.success(Unit)
    }

    override suspend fun setThemeMode(mode: ThemeMode): Result<Unit> {
        _profileState.update { it.copy(themeOverride = mode) }
        return Result.success(Unit)
    }

    override suspend fun logout(): Result<Unit> {
        _profileState.value = defaultProfile.copy(
            riderId = "",
            riderName = "Guest Rider",
            email = "",
            phoneNumber = ""
        )
        return Result.success(Unit)
    }
}
