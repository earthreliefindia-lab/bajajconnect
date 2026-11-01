package com.openride.companion.location

import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume

object LocationIntentParser {

    private const val TAG = "LocationIntentParser"

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    // Regex for latitude, longitude coordinates pair
    private val COORD_PATTERN = Pattern.compile("([-+]?\\d{1,2}\\.\\d+)[,\\s]+([-+]?\\d{1,3}\\.\\d+)")
    // Regex for URL embedded coordinates e.g. /@18.52043,73.85674,17z or !3d18.52043!4d73.85674
    private val URL_AT_COORD_PATTERN = Pattern.compile("/@([-+]?\\d{1,2}\\.\\d+),([-+]?\\d{1,3}\\.\\d+)")
    private val URL_MAPS_DATA_COORD_PATTERN = Pattern.compile("!3d([-+]?\\d{1,2}\\.\\d+)!4d([-+]?\\d{1,3}\\.\\d+)")
    // Regex for URLs inside plain text
    private val HTTP_URL_PATTERN = Pattern.compile("(https?://[\\w\\.-]+/[\\w\\.-/?=&%+,!]+)")

    /**
     * Parses an incoming Android Intent (from 'Open with' chooser or WhatsApp 'Share')
     * into a verified ParsedDestination.
     */
    suspend fun parseIntent(context: Context, intent: Intent?): ParsedDestination? = withContext(Dispatchers.IO) {
        if (intent == null) return@withContext null

        val action = intent.action
        val dataUri = intent.data

        Log.d(TAG, "Parsing intent: action=$action, dataUri=$dataUri")

        when (action) {
            Intent.ACTION_VIEW -> {
                dataUri?.let { uri ->
                    parseUri(context, uri)
                }
            }
            Intent.ACTION_SEND -> {
                // WhatsApp or message share: text/plain with URL or coordinate text
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
                if (!sharedText.isNullOrBlank()) {
                    parseText(context, sharedText, source = "WhatsApp / Shared Message")
                } else null
            }
            else -> {
                // Check if URI exists regardless of action
                dataUri?.let { parseUri(context, it) }
            }
        }
    }

    /**
     * Parses a Uri from VIEW intents: geo:, google.navigation:, or http(s)://maps...
     */
    suspend fun parseUri(context: Context, uri: Uri): ParsedDestination? {
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: ""

        Log.d(TAG, "Parsing URI: scheme=$scheme, host=$host, full=$uri")

        return when {
            scheme == "geo" -> parseGeoUri(context, uri)
            scheme == "google.navigation" -> parseGoogleNavigationUri(context, uri)
            scheme in listOf("http", "https") -> {
                parseMapsWebUrl(context, uri.toString())
            }
            else -> null
        }
    }

    /**
     * Parses geo: URIs:
     * - geo:18.52043,73.85674
     * - geo:0,0?q=18.52043,73.85674(Destination Name)
     * - geo:0,0?q=Deccan+Gymkhana+Pune
     */
    private suspend fun parseGeoUri(context: Context, uri: Uri): ParsedDestination? {
        val ssp = uri.schemeSpecificPart ?: ""
        val query = uri.getQueryParameter("q")

        // 1. Check if q parameter has coordinates e.g. q=18.52,73.85(Label)
        if (!query.isNullOrBlank()) {
            val labelMatcher = Pattern.compile("^(.*?)\\((.*?)\\)$").matcher(query)
            val (searchTarget, customLabel) = if (labelMatcher.matches()) {
                labelMatcher.group(1)?.trim() to labelMatcher.group(2)?.trim()
            } else {
                query to null
            }

            val coordMatcher = COORD_PATTERN.matcher(searchTarget ?: query)
            if (coordMatcher.find()) {
                val lat = coordMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                val lng = coordMatcher.group(2)?.toDoubleOrNull() ?: 0.0
                val title = customLabel ?: reverseGeocodeTitle(context, lat, lng) ?: "Shared Location"
                return ParsedDestination(lat, lng, title, rawSource = uri.toString(), sourceApp = "Map Link")
            } else {
                // Query is an address or place name e.g. "Deccan Gymkhana"
                val resolved = geocodeAddress(context, query)
                if (resolved != null) {
                    return resolved.copy(rawSource = uri.toString(), sourceApp = "Map Search Query")
                }
            }
        }

        // 2. Check the main coordinate part before '?'
        val mainPart = ssp.substringBefore('?')
        val coordMatcher = COORD_PATTERN.matcher(mainPart)
        if (coordMatcher.find()) {
            val lat = coordMatcher.group(1)?.toDoubleOrNull() ?: return null
            val lng = coordMatcher.group(2)?.toDoubleOrNull() ?: return null
            if (lat != 0.0 || lng != 0.0) {
                val title = reverseGeocodeTitle(context, lat, lng) ?: "Pin Drop Location"
                return ParsedDestination(lat, lng, title, rawSource = uri.toString(), sourceApp = "GPS Coordinate")
            }
        }

        return null
    }

    /**
     * Parses google.navigation:q=18.5204,73.8567&mode=b
     */
    private suspend fun parseGoogleNavigationUri(context: Context, uri: Uri): ParsedDestination? {
        val q = uri.getQueryParameter("q") ?: return null
        val matcher = COORD_PATTERN.matcher(q)
        return if (matcher.find()) {
            val lat = matcher.group(1)?.toDoubleOrNull() ?: return null
            val lng = matcher.group(2)?.toDoubleOrNull() ?: return null
            val title = reverseGeocodeTitle(context, lat, lng) ?: "Navigation Target"
            ParsedDestination(lat, lng, title, rawSource = uri.toString(), sourceApp = "Google Navigation")
        } else {
            geocodeAddress(context, q)?.copy(rawSource = uri.toString(), sourceApp = "Google Navigation")
        }
    }

    /**
     * Parses Google Maps web links (including maps.app.goo.gl and maps.google.com).
     * Follows HTTP redirects asynchronously to extract final destination coordinates.
     */
    suspend fun parseMapsWebUrl(context: Context, rawUrl: String): ParsedDestination? = withContext(Dispatchers.IO) {
        try {
            var targetUrl = rawUrl

            // If it is a shortened link (e.g. maps.app.goo.gl or goo.gl/maps), resolve redirects
            if (rawUrl.contains("goo.gl", ignoreCase = true)) {
                targetUrl = resolveFinalRedirect(rawUrl) ?: rawUrl
            }

            Log.d(TAG, "Resolved URL: $targetUrl")

            // Check URL for /@lat,lng or !3dlat!4dlng
            val atMatcher = URL_AT_COORD_PATTERN.matcher(targetUrl)
            if (atMatcher.find()) {
                val lat = atMatcher.group(1)?.toDoubleOrNull()
                val lng = atMatcher.group(2)?.toDoubleOrNull()
                if (lat != null && lng != null) {
                    val title = reverseGeocodeTitle(context, lat, lng) ?: "Shared Location"
                    return@withContext ParsedDestination(lat, lng, title, rawSource = rawUrl, sourceApp = "Google Maps Link")
                }
            }

            val dataMatcher = URL_MAPS_DATA_COORD_PATTERN.matcher(targetUrl)
            if (dataMatcher.find()) {
                val lat = dataMatcher.group(1)?.toDoubleOrNull()
                val lng = dataMatcher.group(2)?.toDoubleOrNull()
                if (lat != null && lng != null) {
                    val title = reverseGeocodeTitle(context, lat, lng) ?: "Shared Location"
                    return@withContext ParsedDestination(lat, lng, title, rawSource = rawUrl, sourceApp = "Google Maps Link")
                }
            }

            // Check for query parameters like ?q=lat,lng or &query=lat,lng
            val uri = Uri.parse(targetUrl)
            val query = uri.getQueryParameter("q")
                ?: uri.getQueryParameter("query")
                ?: uri.getQueryParameter("destination")

            if (!query.isNullOrBlank()) {
                val coordMatcher = COORD_PATTERN.matcher(query)
                if (coordMatcher.find()) {
                    val lat = coordMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                    val lng = coordMatcher.group(2)?.toDoubleOrNull() ?: 0.0
                    val title = reverseGeocodeTitle(context, lat, lng) ?: "Shared Location"
                    return@withContext ParsedDestination(lat, lng, title, rawSource = rawUrl, sourceApp = "Google Maps Link")
                } else {
                    return@withContext geocodeAddress(context, query)?.copy(
                        rawSource = rawUrl,
                        sourceApp = "Google Maps Link"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Maps web URL: $rawUrl", e)
        }
        null
    }

    /**
     * Parses raw message text (e.g. from WhatsApp) which may contain links or coords.
     */
    suspend fun parseText(context: Context, text: String, source: String = "Shared Text"): ParsedDestination? {
        // 1. Search for embedded HTTP URL first
        val urlMatcher = HTTP_URL_PATTERN.matcher(text)
        if (urlMatcher.find()) {
            val extractedUrl = urlMatcher.group(1)
            if (extractedUrl != null) {
                val parsed = parseMapsWebUrl(context, extractedUrl)
                if (parsed != null) return parsed.copy(sourceApp = source)
            }
        }

        // 2. Search for direct coordinates in text
        val coordMatcher = COORD_PATTERN.matcher(text)
        if (coordMatcher.find()) {
            val lat = coordMatcher.group(1)?.toDoubleOrNull()
            val lng = coordMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                val title = reverseGeocodeTitle(context, lat, lng) ?: "Shared Location"
                return ParsedDestination(lat, lng, title, rawSource = text, sourceApp = source)
            }
        }

        // 3. Fallback: treat cleaned text as an address/landmark
        return geocodeAddress(context, text.trim())?.copy(sourceApp = source)
    }

    /**
     * Resolves HTTP 301/302 redirects to find the canonical Google Maps destination URL.
     */
    private fun resolveFinalRedirect(shortUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url(shortUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0)")
                .build()
            val response = httpClient.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            response.close()
            finalUrl
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve redirect for $shortUrl: ${e.message}")
            null
        }
    }

    /**
     * Geocodes an address or place name into coordinates using Android Geocoder.
     */
    suspend fun geocodeAddress(context: Context, query: String): ParsedDestination? = withContext(Dispatchers.IO) {
        if (query.isBlank() || !Geocoder.isPresent()) return@withContext null

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine<ParsedDestination?> { continuation ->
                    geocoder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            val addr = addresses.firstOrNull()
                            if (addr != null) {
                                val title = addr.featureName ?: addr.thoroughfare ?: query
                                val fullAddress = (0..addr.maxAddressLineIndex).joinToString(", ") { addr.getAddressLine(it) }
                                continuation.resume(ParsedDestination(addr.latitude, addr.longitude, title, fullAddress))
                            } else {
                                continuation.resume(null)
                            }
                        }
                        override fun onError(errorMessage: String?) {
                            continuation.resume(null)
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName(query, 1)
                val addr = results?.firstOrNull() ?: return@withContext null
                val title = addr.featureName ?: addr.thoroughfare ?: query
                val fullAddress = (0..addr.maxAddressLineIndex).joinToString(", ") { addr.getAddressLine(it) }
                ParsedDestination(addr.latitude, addr.longitude, title, fullAddress)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed for: $query", e)
            null
        }
    }

    /**
     * Reverse-geocodes coordinates into a readable place title.
     */
    suspend fun reverseGeocodeTitle(context: Context, lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine<String?> { continuation ->
                    geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            val addr = addresses.firstOrNull()
                            val title = addr?.featureName ?: addr?.locality ?: addr?.subLocality
                            continuation.resume(title)
                        }
                        override fun onError(errorMessage: String?) {
                            continuation.resume(null)
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(lat, lng, 1)
                val addr = results?.firstOrNull()
                addr?.featureName ?: addr?.locality ?: addr?.subLocality
            }
        } catch (e: Exception) {
            null
        }
    }
}
