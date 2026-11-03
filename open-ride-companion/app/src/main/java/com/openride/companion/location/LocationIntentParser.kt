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
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume

object LocationIntentParser {

    private const val TAG = "LocationIntentParser"

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

    // 5. Static map or OpenGraph image coordinates: center=18.52043%2C73.85674
    private val STATIC_MAP_COORD_PATTERN = Pattern.compile("center=([-+]?\\d{1,2}\\.\\d+)(?:%2C|,)([-+]?\\d{1,3}\\.\\d+)")

    // 6. Google Maps place name in path: /maps/place/Connaught+Place/@...
    private val URL_PLACE_NAME_PATTERN = Pattern.compile("/maps/place/([^/@?]+)")

    // 7. Degrees, Minutes, Seconds (DMS) coordinates: 18°31'13.5"N 73°52'27.9"E
    private val DMS_PATTERN = Pattern.compile("(\\d{1,2})°(?:(\\d{1,2})')?(?:([\\d.]+)\")?([NSEWnsew])[\\s,]+(\\d{1,3})°(?:(\\d{1,2})')?(?:([\\d.]+)\")?([NSEWnsew])")

    // 8. URLs inside text (WhatsApp / Telegram shares)
    private val HTTP_URL_PATTERN = Pattern.compile("(https?://[a-zA-Z0-9\\.-]+/[a-zA-Z0-9\\./_\\-?=&%+,!]+)")

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
                    parseText(context, sharedText, source = "WhatsApp / Shared Message")
                } else null
            }
            else -> {
                dataUri?.let { parseUri(context, it) }
            }
        }
    }

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

    private fun getSafeQueryParameter(uri: Uri, paramName: String): String? {
        return try {
            if (uri.isHierarchical) {
                uri.getQueryParameter(paramName)
            } else {
                val ssp = uri.schemeSpecificPart ?: return null
                val queryIndex = ssp.indexOf('?')
                if (queryIndex >= 0 && queryIndex < ssp.length - 1) {
                    val queryString = ssp.substring(queryIndex + 1)
                    val pairs = queryString.split("&")
                    for (pair in pairs) {
                        val parts = pair.split("=")
                        if (parts.isNotEmpty() && parts[0] == paramName) {
                            return if (parts.size > 1) {
                                URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
                            } else ""
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "getSafeQueryParameter failed for uri=$uri param=$paramName", e)
            null
        }
    }

    private suspend fun parseGeoUri(context: Context, uri: Uri): ParsedDestination? {
        val ssp = uri.schemeSpecificPart ?: return null
        val queryPart = getSafeQueryParameter(uri, "q")

        if (!queryPart.isNullOrBlank()) {
            val coordMatch = COORD_PATTERN.matcher(queryPart)
            if (coordMatch.find()) {
                val lat = coordMatch.group(1)?.toDoubleOrNull() ?: 0.0
                val lng = coordMatch.group(2)?.toDoubleOrNull() ?: 0.0
                if (isValidCoordinate(lat, lng)) {
                    val title = extractLabelFromQuery(queryPart) ?: reverseGeocodeTitle(context, lat, lng) ?: "Shared Location"
                    return ParsedDestination(lat, lng, title, rawSource = uri.toString(), sourceApp = "Google Maps Geo Link")
                }
            } else {
                val geocoded = geocodeQuery(context, queryPart)
                if (geocoded != null) {
                    return geocoded.copy(rawSource = uri.toString(), sourceApp = "Google Maps Geo Search")
                }
            }
        }

        val baseCoordPart = if (ssp.contains("?")) ssp.substringBefore("?") else ssp
        val match = COORD_PATTERN.matcher(baseCoordPart)
        if (match.find()) {
            val lat = match.group(1)?.toDoubleOrNull() ?: 0.0
            val lng = match.group(2)?.toDoubleOrNull() ?: 0.0
            if (isValidCoordinate(lat, lng)) {
                val title = reverseGeocodeTitle(context, lat, lng) ?: "Shared Pin"
                return ParsedDestination(lat, lng, title, rawSource = uri.toString(), sourceApp = "Geo Intent")
            }
        }

        return null
    }

    private suspend fun parseGoogleNavigationUri(context: Context, uri: Uri): ParsedDestination? {
        val q = getSafeQueryParameter(uri, "q") ?: return null

        val coordMatch = COORD_PATTERN.matcher(q)
        if (coordMatch.find()) {
            val lat = coordMatch.group(1)?.toDoubleOrNull() ?: 0.0
            val lng = coordMatch.group(2)?.toDoubleOrNull() ?: 0.0
            if (isValidCoordinate(lat, lng)) {
                val title = reverseGeocodeTitle(context, lat, lng) ?: "Navigation Destination"
                return ParsedDestination(lat, lng, title, rawSource = uri.toString(), sourceApp = "Google Navigation Link")
            }
        }

        val geocoded = geocodeQuery(context, q)
        if (geocoded != null) {
            return geocoded.copy(rawSource = uri.toString(), sourceApp = "Google Navigation Search")
        }

        return null
    }

    suspend fun parseMapsWebUrl(context: Context, initialUrl: String): ParsedDestination? {
        var url = initialUrl
        if (url.contains("goo.gl") || url.contains("maps.app.goo.gl") || url.contains("bit.ly")) {
            url = resolveRedirectUrl(url) ?: url
        }

        val atMatch = URL_AT_COORD_PATTERN.matcher(url)
        if (atMatch.find()) {
            val lat = atMatch.group(1)?.toDoubleOrNull() ?: 0.0
            val lng = atMatch.group(2)?.toDoubleOrNull() ?: 0.0
            if (isValidCoordinate(lat, lng)) {
                val placeTitle = extractPlaceNameFromUrl(url) ?: reverseGeocodeTitle(context, lat, lng) ?: "Google Maps Destination"
                return ParsedDestination(lat, lng, placeTitle, rawSource = initialUrl, sourceApp = "Google Maps URL")
            }
        }

        val dataMatch = URL_DATA_COORD_PATTERN.matcher(url)
        if (dataMatch.find()) {
            val lat = dataMatch.group(1)?.toDoubleOrNull() ?: 0.0
            val lng = dataMatch.group(2)?.toDoubleOrNull() ?: 0.0
            if (isValidCoordinate(lat, lng)) {
                val placeTitle = extractPlaceNameFromUrl(url) ?: reverseGeocodeTitle(context, lat, lng) ?: "Google Maps Place"
                return ParsedDestination(lat, lng, placeTitle, rawSource = initialUrl, sourceApp = "Google Maps Web Link")
            }
        }

        val queryMatch = URL_QUERY_COORD_PATTERN.matcher(url)
        if (queryMatch.find()) {
            val lat = queryMatch.group(1)?.toDoubleOrNull() ?: 0.0
            val lng = queryMatch.group(2)?.toDoubleOrNull() ?: 0.0
            if (isValidCoordinate(lat, lng)) {
                val title = reverseGeocodeTitle(context, lat, lng) ?: "Shared Location"
                return ParsedDestination(lat, lng, title, rawSource = initialUrl, sourceApp = "Google Maps Query Link")
            }
        }

        val staticMatch = STATIC_MAP_COORD_PATTERN.matcher(url)
        if (staticMatch.find()) {
            val lat = staticMatch.group(1)?.toDoubleOrNull() ?: 0.0
            val lng = staticMatch.group(2)?.toDoubleOrNull() ?: 0.0
            if (isValidCoordinate(lat, lng)) {
                val title = reverseGeocodeTitle(context, lat, lng) ?: "Location Pin"
                return ParsedDestination(lat, lng, title, rawSource = initialUrl, sourceApp = "Static Map Link")
            }
        }

        val placeName = extractPlaceNameFromUrl(url)
        if (!placeName.isNullOrBlank()) {
            val geocoded = geocodeQuery(context, placeName)
            if (geocoded != null) {
                return geocoded.copy(rawSource = initialUrl, sourceApp = "Google Maps Named Place")
            }
        }

        return null
    }

    suspend fun parseText(context: Context, text: String, source: String = "Text Share"): ParsedDestination? {
        val urlMatch = HTTP_URL_PATTERN.matcher(text)
        if (urlMatch.find()) {
            val extractedUrl = urlMatch.group(1)
            if (!extractedUrl.isNullOrBlank()) {
                val res = parseMapsWebUrl(context, extractedUrl)
                if (res != null) return res
            }
        }

        val coordMatch = COORD_PATTERN.matcher(text)
        if (coordMatch.find()) {
            val lat = coordMatch.group(1)?.toDoubleOrNull() ?: 0.0
            val lng = coordMatch.group(2)?.toDoubleOrNull() ?: 0.0
            if (isValidCoordinate(lat, lng)) {
                val title = reverseGeocodeTitle(context, lat, lng) ?: "Shared Coordinates"
                return ParsedDestination(lat, lng, title, rawSource = text, sourceApp = source)
            }
        }

        val dmsMatch = DMS_PATTERN.matcher(text)
        if (dmsMatch.find()) {
            val dmsResult = parseDmsCoordinates(dmsMatch)
            if (dmsResult != null) {
                val title = reverseGeocodeTitle(context, dmsResult.first, dmsResult.second) ?: "DMS Location"
                return ParsedDestination(dmsResult.first, dmsResult.second, title, rawSource = text, sourceApp = source)
            }
        }

        return null
    }

    private fun resolveRedirectUrl(shortUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url(shortUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/109.0")
                .build()

            val response = redirectHttpClient.newCall(request).execute()
            val locationHeader = response.header("Location")
            if (!locationHeader.isNullOrBlank()) {
                if (locationHeader.contains("goo.gl") || locationHeader.contains("maps.app.goo.gl")) {
                    resolveRedirectUrl(locationHeader) ?: locationHeader
                } else {
                    locationHeader
                }
            } else {
                response.request.url.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving short URL: $shortUrl", e)
            null
        }
    }

    private fun extractPlaceNameFromUrl(url: String): String? {
        return try {
            val match = URL_PLACE_NAME_PATTERN.matcher(url)
            if (match.find()) {
                val raw = match.group(1)
                raw?.let { URLDecoder.decode(it.replace("+", " "), "UTF-8") }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractLabelFromQuery(query: String): String? {
        val start = query.indexOf('(')
        val end = query.indexOf(')')
        return if (start != -1 && end != -1 && end > start) {
            query.substring(start + 1, end).trim()
        } else null
    }

    private fun parseDmsCoordinates(matcher: java.util.regex.Matcher): Pair<Double, Double>? {
        return try {
            val latDeg = matcher.group(1)?.toDoubleOrNull() ?: return null
            val latMin = matcher.group(2)?.toDoubleOrNull() ?: 0.0
            val latSec = matcher.group(3)?.toDoubleOrNull() ?: 0.0
            val latDir = matcher.group(4)?.uppercase(Locale.ROOT) ?: "N"

            val lonDeg = matcher.group(5)?.toDoubleOrNull() ?: return null
            val lonMin = matcher.group(6)?.toDoubleOrNull() ?: 0.0
            val lonSec = matcher.group(7)?.toDoubleOrNull() ?: 0.0
            val lonDir = matcher.group(8)?.uppercase(Locale.ROOT) ?: "E"

            var lat = latDeg + (latMin / 60.0) + (latSec / 3600.0)
            if (latDir == "S") lat = -lat

            var lon = lonDeg + (lonMin / 60.0) + (lonSec / 3600.0)
            if (lonDir == "W") lon = -lon

            if (isValidCoordinate(lat, lon)) Pair(lat, lon) else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun geocodeQuery(context: Context, query: String): ParsedDestination? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        withTimeoutOrNull(3000L) {
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
                                val title = when {
                                    !addr?.subLocality.isNullOrBlank() && !addr?.locality.isNullOrBlank() -> "${addr.subLocality}, ${addr.locality}"
                                    !addr?.locality.isNullOrBlank() -> addr.locality
                                    !addr?.thoroughfare.isNullOrBlank() -> addr.thoroughfare
                                    !addr?.featureName.isNullOrBlank() && !addr.featureName.all { it.isDigit() } -> addr.featureName
                                    else -> addr?.subLocality ?: addr?.locality ?: addr?.featureName
                                }
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
                    when {
                        !addr?.subLocality.isNullOrBlank() && !addr?.locality.isNullOrBlank() -> "${addr.subLocality}, ${addr.locality}"
                        !addr?.locality.isNullOrBlank() -> addr.locality
                        !addr?.thoroughfare.isNullOrBlank() -> addr.thoroughfare
                        !addr?.featureName.isNullOrBlank() && !addr.featureName.all { it.isDigit() } -> addr.featureName
                        else -> addr?.subLocality ?: addr?.locality ?: addr?.featureName
                    }
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
