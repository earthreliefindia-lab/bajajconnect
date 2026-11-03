package com.openride.companion.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.openride.companion.location.ParsedDestination
import org.json.JSONArray

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NavigationMapView(
    userLatitude: Double?,
    userLongitude: Double?,
    destination: ParsedDestination?,
    routeCoordinates: List<Pair<Double, Double>>,
    mapplsApiKey: String = "",
    onConfigureMapplsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    // Reload HTML when API key or Theme changes
    LaunchedEffect(mapplsApiKey, isDark) {
        if (isMapLoaded) {
            webViewInstance?.loadDataWithBaseURL(
                "https://mappls.com",
                getMapplsStyleMapHtml(mapplsApiKey, isDark),
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isDark) Color(0xFF121418) else Color(0xFFF1F3F4))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    setBackgroundColor(if (isDark) 0xFF121418.toInt() else 0xFFF1F3F4.toInt())

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isMapLoaded = true
                            userLatitude?.let { lat ->
                                userLongitude?.let { lng ->
                                    view?.evaluateJavascript("updateUserLocation($lat, $lng, 0);", null)
                                    view?.evaluateJavascript("centerOnUser();", null)
                                }
                            }
                        }
                    }

                    val htmlContent = getMapplsStyleMapHtml(mapplsApiKey, isDark)
                    loadDataWithBaseURL("https://mappls.com", htmlContent, "text/html", "UTF-8", null)
                    webViewInstance = this
                }
            },
            update = { webView ->
                if (isMapLoaded) {
                    // Update user location
                    if (userLatitude != null && userLongitude != null) {
                        webView.evaluateJavascript("updateUserLocation($userLatitude, $userLongitude, 0);", null)
                    }

                    // Update destination pin
                    if (destination != null) {
                        val safeTitle = destination.title.replace("'", "\\'")
                        webView.evaluateJavascript(
                            "setDestination(${destination.latitude}, ${destination.longitude}, '$safeTitle');",
                            null
                        )
                    } else {
                        webView.evaluateJavascript("clearDestination();", null)
                    }

                    // Update bike route polyline
                    if (routeCoordinates.isNotEmpty()) {
                        val jsonArr = JSONArray()
                        for (pt in routeCoordinates) {
                            val pairArr = JSONArray()
                            pairArr.put(pt.first)
                            pairArr.put(pt.second)
                            jsonArr.put(pairArr)
                        }
                        webView.evaluateJavascript("drawRoute($jsonArr);", null)
                    } else {
                        webView.evaluateJavascript("clearRoute();", null)
                    }
                }
            }
        )

        // Floating Map Controls: My Location, Zoom In, Zoom Out, Mappls Setup
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mappls Mode Indicator Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TwoWheeler,
                        contentDescription = "Bike Mode",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (mapplsApiKey.isBlank()) "Mappls Free" else "Mappls Pro",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Mappls Configure Button
            FloatingActionButton(
                onClick = onConfigureMapplsClick,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = "Mappls Setup", modifier = Modifier.size(20.dp))
            }

            // Zoom In Button
            FloatingActionButton(
                onClick = { webViewInstance?.evaluateJavascript("map.zoomIn();", null) },
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(20.dp))
            }

            // Zoom Out Button
            FloatingActionButton(
                onClick = { webViewInstance?.evaluateJavascript("map.zoomOut();", null) },
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(20.dp))
            }

            // My Location Button
            FloatingActionButton(
                onClick = {
                    if (userLatitude != null && userLongitude != null) {
                        webViewInstance?.evaluateJavascript("updateUserLocation($userLatitude, $userLongitude, 0);", null)
                        webViewInstance?.evaluateJavascript("centerOnUser();", null)
                    }
                },
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location", modifier = Modifier.size(22.dp))
            }
        }
    }
}

private fun getMapplsStyleMapHtml(apiKey: String, isDark: Boolean): String {
    val defaultFreeUrl = if (isDark) {
        "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
    } else {
        "https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png"
    }
    val activeTileUrl = if (apiKey.isNotBlank()) {
        "https://apis.mappls.com/advancedmaps/v1/$apiKey/still_image?center={lat},{lon}&zoom={z}"
    } else {
        defaultFreeUrl
    }
    val isCustomKey = apiKey.isNotBlank()
    val bgColor = if (isDark) "#121418" else "#F8F9FA"
    val routeColor = if (isDark) "#00E5FF" else "#1A73E8"
    val glowColor = if (isDark) "#0066FF" else "#90CAF9"

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                html, body, #map {
                    height: 100%;
                    width: 100%;
                    margin: 0;
                    padding: 0;
                    background-color: $bgColor;
                }
                .leaflet-control-attribution {
                    display: none !important;
                }
                /* Pulsing Motorcycle Rider Marker */
                .bike-rider-marker {
                    width: 32px;
                    height: 32px;
                    background: #1A73E8;
                    border: 2px solid #FFFFFF;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    box-shadow: 0 0 12px rgba(26, 115, 232, 0.7);
                    animation: bikePulse 2s infinite;
                }
                .bike-rider-marker svg {
                    width: 18px;
                    height: 18px;
                    fill: #FFFFFF;
                }
                @keyframes bikePulse {
                    0% { transform: scale(0.96); box-shadow: 0 0 0 0 rgba(26, 115, 232, 0.6); }
                    70% { transform: scale(1.04); box-shadow: 0 0 0 12px rgba(26, 115, 232, 0); }
                    100% { transform: scale(0.96); box-shadow: 0 0 0 0 rgba(26, 115, 232, 0); }
                }
                /* Mappls Destination Pin */
                .mappls-dest-pin {
                    width: 28px;
                    height: 28px;
                    background: #EA4335;
                    border: 2.5px solid #FFFFFF;
                    border-radius: 50% 50% 50% 0;
                    transform: rotate(-45deg);
                    box-shadow: 0 2px 8px rgba(234, 67, 53, 0.6);
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var isCustom = $isCustomKey;
                var activeTileUrl = '$activeTileUrl';
                var defaultFreeUrl = '$defaultFreeUrl';

                var map = L.map('map', {
                    zoomControl: false,
                    attributionControl: false,
                    dragging: true,
                    touchZoom: true,
                    scrollWheelZoom: true,
                    doubleClickZoom: true,
                    boxZoom: false
                }).setView([18.5204, 73.8567], 14);

                var mainTileLayer = L.tileLayer(activeTileUrl, {
                    maxZoom: 19,
                    subdomains: isCustom ? '' : 'abcd'
                }).addTo(map);

                if (isCustom) {
                    mainTileLayer.on('tileerror', function() {
                        map.removeLayer(mainTileLayer);
                        L.tileLayer(defaultFreeUrl, {
                            maxZoom: 19,
                            subdomains: 'abcd'
                        }).addTo(map);
                    });
                }

                var userMarker = null;
                var destMarker = null;
                var routePolyline = null;
                var routeGlow = null;
                var currentLat = 18.5204;
                var currentLng = 73.8567;

                var bikeIcon = L.divIcon({
                    className: 'bike-marker-wrapper',
                    html: '<div class="bike-rider-marker"><svg viewBox="0 0 24 24"><path d="M19 17c1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3 1.34 3 3 3m0-4c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1M5 17c1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3 1.34 3 3 3m0-4c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1m14.5-8H16l-2.7 4.1-1.3-2.1H14V5H9v2h2.2l2.3 3.7L10.7 15H7.8c-.4-1.2-1.5-2-2.8-2H5c-1.7 0-3 1.3-3 3s1.3 3 3 3c1.3 0 2.4-.8 2.8-2h3.9l3.5-5.5 2.1 3.5h1.7c.4 1.2 1.5 2 2.8 2 1.7 0 3-1.3 3-3s-1.3-3-3-3h-.8l2.2-3.8c.2-.4 0-.9-.4-1.1-.1-.1-.3-.1-.4-.1z"/></svg></div>',
                    iconSize: [32, 32],
                    iconAnchor: [16, 16]
                });

                var destIcon = L.divIcon({
                    className: 'dest-marker-wrapper',
                    html: '<div class="mappls-dest-pin"></div>',
                    iconSize: [28, 28],
                    iconAnchor: [14, 28]
                });

                function updateUserLocation(lat, lng, heading) {
                    currentLat = lat;
                    currentLng = lng;
                    if (!userMarker) {
                        userMarker = L.marker([lat, lng], { icon: bikeIcon }).addTo(map);
                        map.setView([lat, lng], 15);
                    } else {
                        userMarker.setLatLng([lat, lng]);
                    }
                }

                function setDestination(lat, lng, title) {
                    if (destMarker) {
                        map.removeLayer(destMarker);
                    }
                    destMarker = L.marker([lat, lng], { icon: destIcon }).addTo(map);
                    if (title) {
                        destMarker.bindPopup('<b style="color:#000;">' + title + '</b>').openPopup();
                    }
                    if (!routePolyline) {
                        map.flyTo([lat, lng], 16, { animate: true, duration: 0.8 });
                    }
                }

                function clearDestination() {
                    if (destMarker) {
                        map.removeLayer(destMarker);
                        destMarker = null;
                    }
                }

                function drawRoute(coordinates) {
                    clearRoute();
                    if (!coordinates || coordinates.length === 0) return;
                    
                    routeGlow = L.polyline(coordinates, {
                        color: '$glowColor',
                        weight: 9,
                        opacity: 0.5,
                        lineJoin: 'round',
                        lineCap: 'round'
                    }).addTo(map);

                    routePolyline = L.polyline(coordinates, {
                        color: '$routeColor',
                        weight: 5,
                        opacity: 1.0,
                        lineJoin: 'round',
                        lineCap: 'round'
                    }).addTo(map);

                    var bounds = L.latLngBounds(coordinates);
                    map.fitBounds(bounds, { padding: [40, 40] });
                }

                function clearRoute() {
                    if (routeGlow) {
                        map.removeLayer(routeGlow);
                        routeGlow = null;
                    }
                    if (routePolyline) {
                        map.removeLayer(routePolyline);
                        routePolyline = null;
                    }
                }

                function centerOnUser() {
                    map.flyTo([currentLat, currentLng], 16, { animate: true, duration: 0.8 });
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}
