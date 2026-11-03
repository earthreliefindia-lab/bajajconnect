package com.openride.companion.model

import java.util.UUID

/**
 * Verified emergency contact eligible for manual or automated SOS payload broadcast.
 * Max limit: 5 contacts. Rejects duplicates by phone number.
 */
data class EmergencyContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val relationship: String,
    val isPrimary: Boolean = false,
    val avatarAsset: String = "avatar_emergency_contact_1.png"
) {
    init {
        require(name.isNotBlank()) { "Contact name cannot be blank" }
        require(phone.isNotBlank()) { "Contact phone cannot be blank" }
    }

    companion object {
        const val MAX_CONTACT_LIMIT = 5
        const val AUTHORITATIVE_RSA_PHONE = "1800 233 2453"
        const val RSA_HOTLINE_NAME = "Bajaj 24x7 Roadside Assistance"

        /**
         * Checks if a new phone number can be added to an existing list.
         * Disallows duplicate normalized phone numbers and enforces 5-contact cap.
         */
        fun canAddContact(existing: List<EmergencyContact>, candidatePhone: String): Boolean {
            if (existing.size >= MAX_CONTACT_LIMIT) return false
            val cleanCandidate = candidatePhone.filter { it.isDigit() || it == '+' }
            return existing.none {
                val cleanExisting = it.phone.filter { c -> c.isDigit() || c == '+' }
                cleanExisting == cleanCandidate
            }
        }
    }
}

/**
 * Source trigger event for emergency alerts.
 */
enum class SosTriggerSource {
    MANUAL_BUTTON,
    BLE_DISCONNECTED_MID_RIDE,
    CRITICAL_BATTERY_DROP,
    CRASH_TELEMETRY
}

/**
 * Broadcast status of an SOS incident.
 */
enum class SosBroadcastStatus {
    IDLE,
    COUNTDOWN_ACTIVE,
    DISPATCHING,
    DISPATCHED,
    CANCELLED
}

/**
 * Complete payload structure for emergency SMS and network broadcast.
 */
data class SosIncidentPayload(
    val incidentId: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val triggerSource: SosTriggerSource,
    val latitude: Double?,
    val longitude: Double?,
    val batteryVoltage: Float? = null,
    val riderName: String,
    val vehicleModel: String? = null,
    val recipients: List<String> = emptyList(),
    val status: SosBroadcastStatus = SosBroadcastStatus.DISPATCHING
) {
    /**
     * Constructs the standard emergency SMS string.
     * Complies with testSos_AlertPayload_FormatsSmsWithGpsCoordinates and testScenario2_EmergencySosTriggerAndRsaFallback.
     */
    fun buildSmsText(): String {
        val hasValidGps = latitude != null && longitude != null &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0

        return if (hasValidGps) {
            when (triggerSource) {
                SosTriggerSource.BLE_DISCONNECTED_MID_RIDE, SosTriggerSource.CRITICAL_BATTERY_DROP -> {
                    "EMERGENCY SOS: Breakdown detected at https://maps.google.com/?q=$latitude,$longitude"
                }
                else -> {
                    "EMERGENCY SOS from $riderName! I need immediate help. Current Location: https://maps.google.com/?q=$latitude,$longitude"
                }
            }
        } else {
            "EMERGENCY SOS: GPS location currently unavailable."
        }
    }

    /**
     * Fallback recipient resolution: if no personal contacts exist, returns official Bajaj RSA hotline.
     */
    fun resolvePrimaryRecipient(): String {
        return recipients.firstOrNull() ?: EmergencyContact.AUTHORITATIVE_RSA_PHONE
    }
}
