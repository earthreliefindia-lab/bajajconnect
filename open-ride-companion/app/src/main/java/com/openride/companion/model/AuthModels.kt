package com.openride.companion.model

import androidx.annotation.DrawableRes
import com.openride.companion.R

/**
 * Domain entity representing a supported country for international mobile login.
 */
data class SupportedCountry(
    val name: String,
    val dialCode: String,
    @DrawableRes val flagDrawableRes: Int,
    val flagEmoji: String,
    val minLength: Int,
    val maxLength: Int
) {
    /**
     * Validates phone number according to country specific telephony rules.
     */
    fun validateNumber(rawNumber: String): Boolean {
        val clean = sanitize(rawNumber)
        return when (dialCode) {
            "+91" -> clean.matches(Regex("^[6-9]\\d{9}$"))
            "+54" -> clean.length == 10
            "+32" -> clean.length in 8..10
            "+1" -> clean.length == 10
            "+20" -> clean.length == 10
            else -> clean.length in 7..15
        }
    }

    /**
     * Sanitizes user input by stripping formatting and normalizing country prefixes.
     */
    fun sanitize(input: String): String {
        var digits = input.filter { it.isDigit() }
        if (dialCode == "+91" && digits.startsWith("0")) {
            digits = digits.substring(1)
        }
        return digits.take(maxLength)
    }
}

/**
 * Authoritative catalog of the 5 APK-verified countries.
 */
object CountryCatalog {
    val supportedCountries: List<SupportedCountry> = listOf(
        SupportedCountry(
            name = "India",
            dialCode = "+91",
            flagDrawableRes = R.drawable.flag_india,
            flagEmoji = "\uD83C\uDDEE\uD83C\uDDF3",
            minLength = 10,
            maxLength = 10
        ),
        SupportedCountry(
            name = "Argentina",
            dialCode = "+54",
            flagDrawableRes = R.drawable.flag_argentina,
            flagEmoji = "\uD83C\uDDE6\uD83C\uDDF7",
            minLength = 10,
            maxLength = 10
        ),
        SupportedCountry(
            name = "Belgium",
            dialCode = "+32",
            flagDrawableRes = R.drawable.flag_belgium,
            flagEmoji = "\uD83C\uDDE7\uD83C\uDDEA",
            minLength = 8,
            maxLength = 10
        ),
        SupportedCountry(
            name = "Canada",
            dialCode = "+1",
            flagDrawableRes = R.drawable.flag_canada,
            flagEmoji = "\uD83C\uDDE8\uD83C\uDDE6",
            minLength = 10,
            maxLength = 10
        ),
        SupportedCountry(
            name = "Egypt",
            dialCode = "+20",
            flagDrawableRes = R.drawable.flag_egypt,
            flagEmoji = "\uD83C\uDDE8\uD83C\uDDEC",
            minLength = 10,
            maxLength = 10
        )
    )

    val defaultCountry: SupportedCountry = supportedCountries.first()

    fun findByDialCode(dialCode: String): SupportedCountry? {
        return supportedCountries.find { it.dialCode == dialCode }
    }
}

/**
 * Authentication JWT session token.
 */
data class AuthToken(
    val jwtToken: String,
    val issuedAtMs: Long = System.currentTimeMillis(),
    val expiryTimestampMs: Long = System.currentTimeMillis() + 30L * 24 * 3600 * 1000 // 30 days
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiryTimestampMs
}

/**
 * Reactive state of the rider authentication session.
 */
sealed interface SessionState {
    data object Unauthenticated : SessionState

    data class Authenticated(
        val token: String,
        val dialCode: String,
        val phoneNumber: String,
        val activeVehicleId: String? = null,
        val authenticatedAtMs: Long = System.currentTimeMillis()
    ) : SessionState
}
