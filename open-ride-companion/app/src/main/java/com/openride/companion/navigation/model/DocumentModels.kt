package com.openride.companion.navigation.model

/**
 * Supported vehicle document types.
 */
enum class DocType(val displayName: String, val clusterDocIndex: Int) {
    RC_BOOK("Registration Certificate (RC)", 1),
    INSURANCE("Comprehensive Bike Insurance", 2),
    PUC_CERTIFICATE("Pollution Under Control (PUC)", 3),
    DRIVING_LICENSE("Driving License", 4),
    CUSTOM("Custom / Warranty / Service", 5)
}

/**
 * 3-tier document expiration alert status.
 */
enum class DocExpiryStatus {
    VALID,          // > 30 days until expiration
    EXPIRING_SOON,  // 0 <= days <= 30 until expiration
    EXPIRED         // < 0 days (past expiration)
}

/**
 * Lightweight stored document representation matching E2E test assertions.
 */
data class StoredDocument(
    val id: String,
    val type: String,
    val documentNumber: String,
    val expiryDate: String,
    val isVerified: Boolean,
    val issuer: String
)

/**
 * Authoritative vehicle document domain model.
 */
data class VehicleDocument(
    val id: String,
    val vehicleVin: String,
    val docType: DocType,
    val documentTitle: String,
    val documentNumber: String,
    val issueDate: String,
    val expiryDate: String,
    val expiryEpochMillis: Long,
    val issuer: String,
    val isVerified: Boolean = true,
    val documentUri: String? = null,
    val fileSizeBytes: Long = 0L,
    val md5Checksum: String? = null,
    val isSyncedToCluster: Boolean = false,
    val lastSyncTimestampMillis: Long? = null
) {
    val expiryStatus: DocExpiryStatus get() = ExpiryAlertEngine.evaluate(expiryEpochMillis)
    val remainingDays: Long get() = ExpiryAlertEngine.calculateRemainingDays(expiryEpochMillis)

    fun toStoredDocument(): StoredDocument {
        return StoredDocument(
            id = id,
            type = documentTitle,
            documentNumber = documentNumber,
            expiryDate = expiryDate,
            isVerified = isVerified,
            issuer = issuer
        )
    }

    fun toSummaryLine(): String {
        return "$documentTitle: $documentNumber ($expiryDate)"
    }
}

/**
 * Expiration evaluation engine and validation algorithms.
 */
object ExpiryAlertEngine {
    const val EXPIRING_SOON_THRESHOLD_DAYS = 30L

    fun calculateRemainingDays(expiryEpochMillis: Long, nowMillis: Long = System.currentTimeMillis()): Long {
        return (expiryEpochMillis - nowMillis) / (1000L * 60L * 60L * 24L)
    }

    fun evaluate(expiryEpochMillis: Long, nowMillis: Long = System.currentTimeMillis()): DocExpiryStatus {
        if (expiryEpochMillis < nowMillis) {
            return DocExpiryStatus.EXPIRED
        }
        val days = calculateRemainingDays(expiryEpochMillis, nowMillis)
        return if (days <= EXPIRING_SOON_THRESHOLD_DAYS) {
            DocExpiryStatus.EXPIRING_SOON
        } else {
            DocExpiryStatus.VALID
        }
    }

    fun isExpired(expiryYear: Int, currentYear: Int): Boolean {
        return expiryYear < currentYear
    }
}

/**
 * Document input validation and sanitization helpers.
 */
object DocumentValidator {

    /**
     * Sanitizes document numbers: uppercase, trims whitespace, retains alphanumeric, slashes, and dashes.
     */
    fun sanitizeDocumentNumber(raw: String): String {
        return raw.trim().uppercase().replace(Regex("[^A-Z0-9/-]"), "")
    }

    /**
     * Validates image payload byte size: between 1 KB (1024 bytes) and 5 MB (5,242,880 bytes).
     */
    fun isValidImageUpload(sizeBytes: Long): Boolean {
        return sizeBytes in 1024..5_242_880
    }
}
