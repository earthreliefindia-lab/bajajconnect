package com.openride.companion.ble

import java.util.UUID

/**
 * Authoritative GATT constants mined from Bajaj Ride Connect APK (classes3.dex)
 * and verified against BleTelematicsE2ETest.kt.
 */
object OctGattConstants {

    // --- 128-bit OCT Engineering GATT Service ---
    val SERVICE_OCT_UUID: UUID = UUID.fromString("0010676e-6972-6565-6e69-676e4543544f")

    // --- 9 Defined Characteristic UUIDs (skipping 0710 and 0810 by design) ---
    val CHAR_TBT_NAV_UUID: UUID = UUID.fromString("0110676e-6972-6565-6e69-676e4543544f")
    val CHAR_GENERAL_PACKET_UUID: UUID = UUID.fromString("0210676e-6972-6565-6e69-676e4543544f")
    val CHAR_MISSED_CALL_UUID: UUID = UUID.fromString("0310676e-6972-6565-6e69-676e4543544f")
    val CHAR_ALERTS_UUID: UUID = UUID.fromString("0410676e-6972-6565-6e69-676e4543544f")
    val CHAR_MUSIC_UUID: UUID = UUID.fromString("0510676e-6972-6565-6e69-676e4543544f")
    val CHAR_HANDLEBAR_UUID: UUID = UUID.fromString("0610676e-6972-6565-6e69-676e4543544f")
    val CHAR_FAVORITES_UUID: UUID = UUID.fromString("0910676e-6972-6565-6e69-676e4543544f")
    val CHAR_RECENT_CALLS_UUID: UUID = UUID.fromString("0a10676e-6972-6565-6e69-676e4543544f")
    val CHAR_DIAGNOSTICS_UUID: UUID = UUID.fromString("0b10676e-6972-6565-6e69-676e4543544f")

    // --- Standard CCCD Descriptor ---
    val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // --- MTU Negotiation Constants ---
    const val MTU_REQUESTED: Int = 512
    const val MTU_FALLBACK_APK: Int = 256
    const val MTU_FALLBACK_LEGACY: Int = 185
    const val MTU_MINIMUM_ATT: Int = 23
    const val MTU_WATCHDOG_TIMEOUT_MS: Long = 2500L

    // --- Timing & Retry Constants ---
    const val SCAN_PERIOD_MS: Long = 10000L
    const val WRITE_RETRY_MAX: Int = 5
    const val MIN_OUTGOING_DISPLAY_MS: Long = 2000L
    const val OUTGOING_ANSWER_POLL_MS: Long = 500L
    const val END_CALL_HOLD_MS: Long = 500L
    const val TBT_REROUTE_HOLD_MS: Long = 12000L
    const val CPU_WAKE_LOCK_TAG: String = "BajajRideConnect:BleServiceCpuWakeLock"

    // --- Authoritative Reconnect Backoff Delays ---
    val RECONNECT_BACKOFF_MS = longArrayOf(1500L, 2500L, 4000L, 6000L, 8000L)

    // --- Allowed Scan Prefixes ---
    val ALLOWED_CLUSTER_PREFIXES = listOf("Bajaj", "Pulsar", "Dominar", "Chetak")

    // --- All 9 Characteristics List ---
    val ALL_OCT_CHARACTERISTICS = listOf(
        CHAR_TBT_NAV_UUID,
        CHAR_GENERAL_PACKET_UUID,
        CHAR_MISSED_CALL_UUID,
        CHAR_ALERTS_UUID,
        CHAR_MUSIC_UUID,
        CHAR_HANDLEBAR_UUID,
        CHAR_FAVORITES_UUID,
        CHAR_RECENT_CALLS_UUID,
        CHAR_DIAGNOSTICS_UUID
    )

    /**
     * Exponential backoff calculation as tested in BleTelematicsE2ETest.kt
     */
    fun calculateExponentialBackoff(attempt: Int): Long {
        val base = 1000L
        val factor = 1 shl attempt.coerceIn(0, 5)
        return (base * factor).coerceAtMost(30000L)
    }

    /**
     * Exact array backoff calculation from Bajaj Ride Connect APK
     */
    fun calculateArrayBackoff(attempt: Int): Long {
        val index = attempt.coerceIn(0, RECONNECT_BACKOFF_MS.size - 1)
        return RECONNECT_BACKOFF_MS[index]
    }

    /**
     * Maps RSSI dBm values (-100 to -60 dBm) to 0..5 dashboard signal bars
     */
    fun calculateRssiBars(rssi: Int): Int {
        return when {
            rssi >= -60 -> 5
            rssi >= -70 -> 4
            rssi >= -80 -> 3
            rssi >= -90 -> 2
            rssi >= -100 -> 1
            else -> 0
        }
    }

    /**
     * Filters advertisement device names
     */
    fun isBajajCluster(advertisedName: String?): Boolean {
        if (advertisedName.isNullOrBlank()) return false
        return ALLOWED_CLUSTER_PREFIXES.any { advertisedName.startsWith(it, ignoreCase = true) }
    }
}
