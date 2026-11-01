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
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume

object LocationIntentParser {

    private const val TAG = "LocationIntentParser"

    // Non-redirecting OkHttpClient for inspecting step-by-step redirect Location headers
    private val redirectHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // 1. Direct decimal coordinates pair: 18.52043, 73.85674 or 18.52043,73.85674
    private val COORD_PATTERN = Pattern.compile("([-+]?\\d{1,2}\\.\\d+)[,\\s]+([-+]?\\d{1,3}\\.\\d+)")

    // 2. Google Maps URL path coordinate: /@18.52043,73.85674,17z or /@18.52043,73.85674
    private val URL_AT_COORD_PATTERN = Pattern.compile("/@([-+]?\\d{1,2}\\.\\d+),([-+]?\\d{1,3}\\.\\d+)")

    // 3. Google Maps Protobuf data coordinates: !3d18.52043!4d73.85674
    private val URL_DATA_COORD_PATTERN = Pattern.compile("!3d([-+]?\\d{1,2}\\.\\d+)!4d([-+]?\\d{1,3}\\.\\d+)")

    // 4. URL query parameters: ?q=18.52,73.85 or &query=18.52,73.85 or ?daddr=18.52,73.85 or &ll=18.52,73.85
    private val URL_QUERY_COORD_PATTERN = Pattern.compile("[?&](?:q|query|daddr|destination|ll)=([-+]?\\d{1,2}\\.\\d+)[,%20\\+]+([-+]?\\d{1,3}\\.\\d+)")

    // 5. Static map or OpenGraph image coordinates: center=18.52043%2C73.85674 or staticmap?center=18.52,73.85
    private val STATIC_MAP_COORD_PATTERN = Pattern.compile("center=([-+]?\\d{1,2}\\.\\d+)(?:%2C|,)([-+]?\\d{1,3}\\.\\d+)")

    // 6. Google Maps place name in path: /maps/place/Connaught+Place/@...
    private val URL_PLACE_NAME_PATTERN = Pattern.compile("/maps/place/([^/@?]+)")

    // 7. Degrees, Minutes, Seconds (DMS) coordinates: 18°31'13.5"N 73°52'27.9"E
    private val DMS_PATTERN = Pattern.compile("(\\d{1,2})°(?:(\\d{1,2})')?(?:([\\d.]+)\")?([NSEWnsew])[\\s,]+(\\d{1,3})°(?:(\\d{1,2})')?(?:([\\d.]+)\")?([NSEWnsew])")

    // 8. URLs inside text (WhatsApp / Telegram shares)
    private val HTTP_URL_PATTERN = Pattern.compile("(https?://[a-zA-Z0-9\\.-]+/[a-zA-Z0-9\\./_\\-?=&%+,!]+)")

    /**
     * Parses an incoming Android Intent into a verified ParsedDestination.
     * Supports:
     * - ACTION_VIEW with geo:, google.navigation:, or http(s):// links (including WhatsApp Google Maps links)
     * - ACTION_SEND with text/plain (WhatsApp / Telegram Sharesheet)
     */
    suspend fun parseIntent(context: Context, intent: Intent?): ParsedDestination? = withContext(Dispatchers.IO) {
        if (intent == null) return@withContext null

        val action = intent.action
        val dataUri = intent.data

        Log.d(TAG, "Parsing intent: action=$action, dataUri=$dataUri")

        when (action) {
            Intent.ACTION_VIEW -> {
                dataUri?.let { uri -> parseUri(context, uri) }
            }
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
                Log.d(TAG, "ACTION_SEND sharedText: $sharedText")
                if (!sharedText.isNullOrBlank()) {
                    val res = parseText(context, sharedText, source = "WhatsApp / Shared Message")
                    Log.i(TAG, "Parsed ACTION_SEND result: $res")
                    res
                } else null
            }
            else -> {
                dataUri?.let { parseUri(context, it) }
            }
        }
    }

    /**
     * Parses a Uri from VIEW intents: geo:, google.navigation:, or http(s)://...
     */
    suspend fun parseUri(context: Context, uri: Uri): ParsedDestination? {
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null

        Log.d(TAG, "Parsing URI: scheme=$scheme, full=$uri")

        return when (scheme) {
            "geo" -> parseGeoUri(context, uri)
            "google.navigation" -> parseGoogleNavigationUri(context, uri)
            "http", "https" -> parseMapsWebUrl(context, uri.toString())
            else -> null
        }
    }

    /**
     * Parses geo: URIs:
     * - geo:18.52043,73.85674
     * - geo:0,0?q=18.52043,73.85674(Destination Name)
     * - geo:18.52043,73.85674?q=Shivajinagar
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

            val targetStr = searchTarget ?: query
            val coordMatcher = COORD_PATTERN.matcher(targetStr)
            if (coordMatcher.find()) {
                val lat = coordMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                val lng = coordMatcher.group(2)?.toDoubleOrNull() ?: 0.0
                val title = customLabel ?: reverseGeocodeTitle(context, lat, lng) ?: "Shared Location"
                return ParsedDestination(lat, lng, title, rawSource = uri.toString(), sourceApp = "Google Maps Pin")
            } else {
                // Address query e.g. "Deccan Gymkhana"
                val resolved = geocodeAddress(context, targetStr)
                if (resolved != null) {
                    val finalTitle = customLabel ?: resolved.title
                    return resolved.copy(title = finalTitle, rawSource = uri.toString(), sourceApp = "Map Search Query")
                }
            }
        }

        // 2. Check the main coordinate part before '?' e.g. geo:18.52043,73.85674
        val mainPart = ssp.substringBefore('?')
        val coordMatcher = COORD_PATTERN.matcher(mainPart)
        if (coordMatcher.find()) {
            val lat = coordMatcher.group(1)?.toDoubleOrNull() ?: return null
            val lng = coordMatcher.group(2)?.toDoubleOrNull() ?: return null
            if (lat != 0.0 || lng != 0.0) {
                val title = reverseGeocodeTitle(context, lat, lng) ?: "Pin Drop Location"
                return ParsedDestination(lat, lng, title, rawSource = uri.toString(), sourceApp = "Google Maps Pin")
            }
        }

        return null
    }

    /**
     * Parses google.navigation:q=18.5204,73.8567&mode=l (two-wheeler bike mode)
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
     * Parses Google Maps web links (including maps.app.goo.gl, goo.gl/maps, maps.google.com, mappls.com).
     * Follows HTTP 301/302 redirects safely to extract destination coordinates and place details.
     */
    suspend fun parseMapsWebUrl(context: Context, rawUrl: String): ParsedDestination? = withContext(Dispatchers.IO) {
        try {
            // First attempt to extract directly from the provided URL (fast path)
            val immediateResult = extractFromUrlString(context, rawUrl)
            if (immediateResult != null) {
                return@withContext immediateResult
            }

            // If it's a shortened link (maps.app.goo.gl, goo.gl/maps, mmi.in, etc.) or place link, unshorten it
            val resolvedUrls = followRedirectsAndExtract(rawUrl)
            for (url in resolvedUrls) {
                val result = extractFromUrlString(context, url)
                if (result != null) {
                    return@withContext result.copy(rawSource = rawUrl)
                }
            }

            // If coordinates were not in URLs, check if place name was extracted from path
            for (url in resolvedUrls) {
                val placeName = extractPlaceNameFromUrl(url)
                if (!placeName.isNullOrBlank()) {
                    val geocoded = geocodeAddress(context, placeName)
                    if (geocoded != null) {
                        return@withContext geocoded.copy(title = placeName, rawSource = rawUrl, sourceApp = "Google Maps Place")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Maps web URL: $rawUrl", e)
        }
        null
    }

    /**
     * Extracts destination coordinates and place title directly from a URL string.
     */
    private suspend fun extractFromUrlString(context: Context, url: String): ParsedDestination? {
        val extractedPlaceName = extractPlaceNameFromUrl(url)

        // 1. Check for /@lat,lng e.g. /@18.52043,73.85674,17z
        val atMatcher = URL_AT_COORD_PATTERN.matcher(url)
        if (atMatcher.find()) {
            val lat = atMatcher.group(1)?.toDoubleOrNull()
            val lng = atMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                val title = extractedPlaceName ?: reverseGeocodeTitle(context, lat, lng) ?: "Shared Location"
                return ParsedDestination(lat, lng, title, rawSource = url, sourceApp = "Google Maps Link")
            }
        }

        // 2. Check for Protobuf data parameters: !3d18.52043!4d73.85674
        val dataMatcher = URL_DATA_COORD_PATTERN.matcher(url)
        if (dataMatcher.find()) {
            val lat = dataMatcher.group(1)?.toDoubleOrNull()
            val lng = dataMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                val title = extractedPlaceName ?: reverseGeocodeTitle(context, lat, lng) ?: "Shared Location"
                return ParsedDestination(lat, lng, title, rawSource = url, sourceApp = "Google Maps Link")
            }
        }

        // 3. Check for query parameters: ?q=lat,lng or &daddr=lat,lng or ?destination=lat,lng
        val queryMatcher = URL_QUERY_COORD_PATTERN.matcher(url)
        if (queryMatcher.find()) {
            val lat = queryMatcher.group(1)?.toDoubleOrNull()
            val lng = queryMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                val title = extractedPlaceName ?: reverseGeocodeTitle(context, lat, lng) ?: "Shared Location"
                return ParsedDestination(lat, lng, title, rawSource = url, sourceApp = "Google Maps Link")
            }
        }

        // 4. Check for Static map or center coordinates
        val staticMatcher = STATIC_MAP_COORD_PATTERN.matcher(url)
        if (staticMatcher.find()) {
            val lat = staticMatcher.group(1)?.toDoubleOrNull()
            val lng = staticMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                val title = extractedPlaceName ?: reverseGeocodeTitle(context, lat, lng) ?: "Shared Location"
                return ParsedDestination(lat, lng, title, rawSource = url, sourceApp = "Google Maps Link")
            }
        }

        // 5. Check if query is an address in ?q= or &query=
        try {
            val parsedUri = Uri.parse(url)
            val queryText = parsedUri.getQueryParameter("q")
                ?: parsedUri.getQueryParameter("query")
                ?: parsedUri.getQueryParameter("destination")
                ?: parsedUri.getQueryParameter("daddr")

            if (!queryText.isNullOrBlank()) {
                val coordMatch = COORD_PATTERN.matcher(queryText)
                if (coordMatch.find()) {
                    val lat = coordMatch.group(1)?.toDoubleOrNull()
                    val lng = coordMatch.group(2)?.toDoubleOrNull()
                    if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                        val title = extractedPlaceName ?: reverseGeocodeTitle(context, lat, lng) ?: "Shared Location"
                        return ParsedDestination(lat, lng, title, rawSource = url, sourceApp = "Google Maps Link")
                    }
                } else {
                    return geocodeAddress(context, queryText)?.copy(
                        rawSource = url,
                        sourceApp = "Google Maps Link"
                    )
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Extracts and decodes human-readable place name from Google Maps URLs.
     * e.g. /maps/place/Pune+Railway+Station/@18.5284... -> "Pune Railway Station"
     */
    private fun extractPlaceNameFromUrl(url: String): String? {
        val matcher = URL_PLACE_NAME_PATTERN.matcher(url)
        if (matcher.find()) {
            val rawName = matcher.group(1) ?: return null
            return try {
                val decoded = URLDecoder.decode(rawName, StandardCharsets.UTF_8.name())
                    .replace('+', ' ')
                    .trim()

                // If the decoded place name is actually DMS coords like 18°31'13.5"N 73°52'27.9"E, keep it clean
                decoded
            } catch (e: Exception) {
                rawName.replace('+', ' ')
            }
        }
        return null
    }

    /**
     * Follows HTTP 301/302 redirects step-by-step up to 6 hops to resolve shortened URLs
     * without blocking or losing intermediate query parameters.
     */
    private fun followRedirectsAndExtract(initialUrl: String): List<String> {
        val visitedUrls = mutableListOf<String>()
        var currentUrl = initialUrl
        var hops = 0
        val maxHops = 6

        while (hops < maxHops) {
            visitedUrls.add(currentUrl)
            hops++

            try {
                val request = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
                    .build()

                val response = redirectHttpClient.newCall(request).execute()
                val statusCode = response.code
                val locationHeader = response.header("Location")

                // If redirected (301, 302, 303, 307, 308)
                if (statusCode in 300..399 && !locationHeader.isNullOrBlank()) {
                    response.close()
                    val nextUrl = if (locationHeader.startsWith("http")) {
                        locationHeader
                    } else {
                        // Relative redirect
                        val baseUri = Uri.parse(currentUrl)
                        "${baseUri.scheme}://${baseUri.host}$locationHeader"
                    }
                    currentUrl = nextUrl
                    continue
                }

                // If 200 OK, also inspect the body for canonical link or meta refresh
                if (statusCode == 200) {
                    val bodySnippet = response.body?.string()?.take(5000) ?: ""
                    response.close()

                    // Look for og:url or staticmap in HTML body
                    val ogUrlMatcher = Pattern.compile("<meta property=\"og:url\" content=\"([^\"]+)\"").matcher(bodySnippet)
                    if (ogUrlMatcher.find()) {
                        ogUrlMatcher.group(1)?.let { visitedUrls.add(it) }
                    }

                    val staticMapMatcher = Pattern.compile("(https?://[^\"]+staticmap[^\"]+)").matcher(bodySnippet)
                    if (staticMapMatcher.find()) {
                        staticMapMatcher.group(1)?.let { visitedUrls.add(it) }
                    }
                    break
                }

                response.close()
                break
            } catch (e: Exception) {
                Log.w(TAG, "Redirect resolution stopped at hop $hops for $currentUrl: ${e.message}")
                break
            }
        }

        return visitedUrls
    }

    /**
     * Parses raw message text (e.g. from WhatsApp or Telegram) which may contain links, coords, or addresses.
     */
    suspend fun parseText(context: Context, text: String, source: String = "WhatsApp / Shared Message"): ParsedDestination? {
        val trimmed = text.trim()

        // 1. Search for embedded HTTP URL first (e.g. https://maps.app.goo.gl/...)
        val urlMatcher = HTTP_URL_PATTERN.matcher(trimmed)
        if (urlMatcher.find()) {
            val extractedUrl = urlMatcher.group(1)
            if (extractedUrl != null) {
                val parsed = parseMapsWebUrl(context, extractedUrl)
                if (parsed != null) return parsed.copy(sourceApp = source)
            }
        }

        // 2. Search for DMS (Degrees Minutes Seconds) coordinates: 18°31'13.5"N 73°52'27.9"E
        val dmsParsed = parseDmsCoordinates(context, trimmed)
        if (dmsParsed != null) {
            return dmsParsed.copy(rawSource = text, sourceApp = source)
        }

        // 3. Search for direct decimal coordinates in text: 18.52043, 73.85674
        val coordMatcher = COORD_PATTERN.matcher(trimmed)
        if (coordMatcher.find()) {
            val lat = coordMatcher.group(1)?.toDoubleOrNull()
            val lng = coordMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                val title = reverseGeocodeTitle(context, lat, lng) ?: "Shared Location"
                return ParsedDestination(lat, lng, title, rawSource = text, sourceApp = source)
            }
        }

        // 4. Fallback: treat text as a place name or address
        return geocodeAddress(context, trimmed)?.copy(rawSource = text, sourceApp = source)
    }

    /**
     * Parses Degrees, Minutes, Seconds (DMS) coordinates format.
     */
    private suspend fun parseDmsCoordinates(context: Context, text: String): ParsedDestination? {
        val matcher = DMS_PATTERN.matcher(text)
        if (matcher.find()) {
            try {
                val latDeg = matcher.group(1)?.toDoubleOrNull() ?: 0.0
                val latMin = matcher.group(2)?.toDoubleOrNull() ?: 0.0
                val latSec = matcher.group(3)?.toDoubleOrNull() ?: 0.0
                val latDir = matcher.group(4)?.uppercase(Locale.ROOT) ?: "N"

                val lngDeg = matcher.group(5)?.toDoubleOrNull() ?: 0.0
                val lngMin = matcher.group(6)?.toDoubleOrNull() ?: 0.0
                val lngSec = matcher.group(7)?.toDoubleOrNull() ?: 0.0
                val lngDir = matcher.group(8)?.uppercase(Locale.ROOT) ?: "E"

                var lat = latDeg + (latMin / 60.0) + (latSec / 3600.0)
                if (latDir == "S") lat = -lat

                var lng = lngDeg + (lngMin / 60.0) + (lngSec / 3600.0)
                if (lngDir == "W") lng = -lng

                if (isValidCoordinate(lat, lng)) {
                    val title = reverseGeocodeTitle(context, lat, lng) ?: "Shared Coordinates"
                    return ParsedDestination(lat, lng, title)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing DMS coordinates", e)
            }
        }
        return null
    }

    /**
     * Geocodes an address or place name into coordinates using Android Geocoder.
     */
    suspend fun geocodeAddress(context: Context, query: String): ParsedDestination? = withContext(Dispatchers.IO) {
        if (query.isBlank() || !Geocoder.isPresent()) return@withContext null

        withTimeoutOrNull(2500L) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine<ParsedDestination?> { continuation ->
                        geocoder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val addr = addresses.firstOrNull()
                                if (addr != null && isValidCoordinate(addr.latitude, addr.longitude)) {
                                    val title = addr.featureName ?: addr.thoroughfare ?: query
                                    val fullAddress = (0..addr.maxAddressLineIndex).joinToString(", ") { addr.getAddressLine(it) }
                                    if (continuation.isActive) continuation.resume(ParsedDestination(addr.latitude, addr.longitude, title, fullAddress))
                                } else {
                                    if (continuation.isActive) continuation.resume(null)
                                }
                            }
                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val results = geocoder.getFromLocationName(query, 1)
                    val addr = results?.firstOrNull() ?: return@withTimeoutOrNull null
                    if (isValidCoordinate(addr.latitude, addr.longitude)) {
                        val title = addr.featureName ?: addr.thoroughfare ?: query
                        val fullAddress = (0..addr.maxAddressLineIndex).joinToString(", ") { addr.getAddressLine(it) }
                        ParsedDestination(addr.latitude, addr.longitude, title, fullAddress)
                    } else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Geocoding failed for: $query", e)
                null
            }
        }
    }

    /**
     * Reverse-geocodes coordinates into a readable place title.
     */
    suspend fun reverseGeocodeTitle(context: Context, lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent() || !isValidCoordinate(lat, lng)) return@withContext null
        withTimeoutOrNull(2500L) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine<String?> { continuation ->
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val addr = addresses.firstOrNull()
                                val title = addr?.featureName ?: addr?.locality ?: addr?.subLocality
                                if (continuation.isActive) continuation.resume(title)
                            }
                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(null)
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

    private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        return lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)
    }
}
