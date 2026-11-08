package com.openride.companion.model

/**
 * Core domain entity representing the authenticated Bajaj motorcycle rider.
 * Fully compliant with Feature 8 specifications and AuthAndOnboardingE2ETest.
 */
data class RiderProfile(
    val riderId: String = "USER_DEFAULT_001",
    val riderName: String,
    val email: String,
    val phoneNumber: String,
    val bloodGroup: String,
    val avatarAsset: String = "avatar_profile_default.png",
    val customImageUri: String? = null,
    val isBajajVerified: Boolean = false,
    val preferredDistanceUnit: String = "km", // "km" or "miles"
    val themeOverride: ThemeMode = ThemeMode.SYSTEM,
    val helmetReminderEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val callSmsAlertsEnabled: Boolean = true,
    val lastUpdatedTimestampMs: Long = System.currentTimeMillis()
) {
    init {
        require(riderName.isNotBlank()) { "Rider name must not be blank" }
        require(riderName.length <= 50) { "Rider name cannot exceed 50 characters" }
    }

    fun hasValidBloodGroup(): Boolean = isValidBloodGroup(bloodGroup)

    companion object {
        val VALID_BLOOD_GROUPS = setOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun isValidBloodGroup(group: String): Boolean = group in VALID_BLOOD_GROUPS

        fun isValidEmail(email: String): Boolean = email.matches(EMAIL_REGEX)

        /**
         * Normalizes raw rider name by trimming outer whitespace and title-casing each word.
         * Example: "   rohit verma   " -> "Rohit Verma"
         */
        fun formatRiderName(rawName: String): String {
            return rawName.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar { it.uppercase() }
                }
                .take(50)
        }

        /**
         * Converts kilometers to active display unit.
         * Factor: 1 km = 0.621371 miles.
         */
        fun convertDistance(distanceKm: Float, unit: String): Float {
            return if (unit.lowercase() == "miles") distanceKm * 0.621371f else distanceKm
        }

        fun resolveUnit(unit: String): String = if (unit.lowercase() == "miles") "miles" else "km"
    }
}

/**
 * UI theme mode selection for the rider.
 */
enum class ThemeMode(val title: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode (AMOLED Navy)")
}
