package com.openride.companion.ui.screens

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.openride.companion.location.ParsedDestination
import org.json.JSONArray

/**
 * Ultra-lightweight, zero-friction Navigation Map Component.
 * Never prompts for API keys, loads instantaneously, and supports smooth 60fps
 * drag, pan, pinch-zoom, and 1-tap GPS centering.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun NavigationMapView(
    userLatitude: Double?,
    userLongitude: Double?,
    destination: ParsedDestination?,
    routeCoordinates: List<Pair<Double, Double>>,
    onFetchCurrentLocation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }
    var isSatelliteMode by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    // Reload HTML only when Theme or Map Style changes
    LaunchedEffect(isDark, isSatelliteMode) {
        if (isMapLoaded) {
            webViewInstance?.loadDataWithBaseURL(
                "https://openride.local",
                getLightweightMapHtml(isDark, isSatelliteMode),
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
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    isClickable = true
                    isFocusable = true
                    isFocusableInTouchMode = true

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = false
                        cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                    setBackgroundColor(if (isDark) 0xFF121418.toInt() else 0xFFF1F3F4.toInt())

                    // Crucial: Disallow parent Compose from stealing touch gestures
                    setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        v.onTouchEvent(event)
                    }

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isMapLoaded = true
                            val lat = userLatitude ?: 18.5204
                            val lng = userLongitude ?: 73.8567
                            view?.evaluateJavascript("updateUserLocation($lat, $lng, 0);", null)
                            view?.evaluateJavascript("centerOnUser();", null)
                        }
                    }

                    val htmlContent = getLightweightMapHtml(isDark, isSatelliteMode)
                    loadDataWithBaseURL("https://openride.local", htmlContent, "text/html", "UTF-8", null)
                    webViewInstance = this
                }
            },
            update = { webView ->
                if (isMapLoaded) {
                    if (userLatitude != null && userLongitude != null) {
                        webView.evaluateJavascript("updateUserLocation($userLatitude, $userLongitude, 0);", null)
                    }

                    if (destination != null) {
                        val safeTitle = destination.title.replace("'", "\\'")
                        webView.evaluateJavascript(
                            "setDestination(${destination.latitude}, ${destination.longitude}, '$safeTitle');",
                            null
                        )
                    } else {
                        webView.evaluateJavascript("clearDestination();", null)
                    }

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

        // Floating Map Controls: Layer Switcher, Zoom In, Zoom Out, My Location
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1-Tap Map Layer Switcher (Vector vs Satellite/Outdoor)
            FloatingActionButton(
                onClick = { isSatelliteMode = !isSatelliteMode },
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = if (isSatelliteMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Default.Layers, contentDescription = "Toggle Map Layer", modifier = Modifier.size(20.dp))
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

            // 1-Tap Fetch Current Location Button (Prominent GPS crosshair)
            FloatingActionButton(
                onClick = {
                    onFetchCurrentLocation()
                    if (userLatitude != null && userLongitude != null) {
                        webViewInstance?.evaluateJavascript("updateUserLocation($userLatitude, $userLongitude, 0);", null)
                        webViewInstance?.evaluateJavascript("centerOnUser();", null)
                    }
                },
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Fetch Current Location", modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * Generates an ultra-lightweight HTML/Leaflet map with zero API keys required.
 * Uses high-speed cached raster tiles tuned to stock Android light/dark themes.
 */
private fun getLightweightMapHtml(isDark: Boolean, isSatellite: Boolean): String {
    val tileUrl = when {
        isSatellite -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
        isDark -> "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}@2x.png"
        else -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    }

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
                    touch-action: pan-x pan-y pinch-zoom;
                    -webkit-user-select: none;
                    user-select: none;
                }
                .leaflet-control-attribution {
                    display: none !important;
                }
                /* Pulsing Motorcycle Rider Marker */
                .bike-rider-marker {
                    width: 34px;
                    height: 34px;
                    background: #1A73E8;
                    border: 2.5px solid #FFFFFF;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    box-shadow: 0 0 14px rgba(26, 115, 232, 0.8);
                    animation: bikePulse 2s infinite;
                }
                .bike-rider-marker svg {
                    width: 20px;
                    height: 20px;
                    fill: #FFFFFF;
                }
                @keyframes bikePulse {
                    0% { transform: scale(0.96); box-shadow: 0 0 0 0 rgba(26, 115, 232, 0.6); }
                    70% { transform: scale(1.04); box-shadow: 0 0 0 14px rgba(26, 115, 232, 0); }
                    100% { transform: scale(0.96); box-shadow: 0 0 0 0 rgba(26, 115, 232, 0); }
                }
                /* Destination Pin */
                .mappls-dest-pin {
                    width: 30px;
                    height: 30px;
                    background: #EA4335;
                    border: 2.5px solid #FFFFFF;
                    border-radius: 50% 50% 50% 0;
                    transform: rotate(-45deg);
                    box-shadow: 0 2px 10px rgba(234, 67, 53, 0.6);
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var tileUrl = '$tileUrl';

                var map = L.map('map', {
                    zoomControl: false,
                    attributionControl: false,
                    dragging: true,
                    touchZoom: true,
                    scrollWheelZoom: true,
                    doubleClickZoom: true,
                    boxZoom: false,
                    tap: false
                }).setView([18.5204, 73.8567], 15);

                L.tileLayer(tileUrl, {
                    maxZoom: 19,
                    subdomains: 'abc'
                }).addTo(map);

                var userMarker = null;
                var destMarker = null;
                var routePolyline = null;
                var routeGlow = null;
                var currentLat = 18.5204;
                var currentLng = 73.8567;

                var bikeIcon = L.divIcon({
                    className: 'bike-marker-wrapper',
                    html: '<div class="bike-rider-marker"><svg viewBox="0 0 24 24"><path d="M19 17c1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3 1.34 3 3 3m0-4c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1M5 17c1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3 1.34 3 3 3m0-4c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1m14.5-8H16l-2.7 4.1-1.3-2.1H14V5H9v2h2.2l2.3 3.7L10.7 15H7.8c-.4-1.2-1.5-2-2.8-2H5c-1.7 0-3 1.3-3 3s1.3 3 3 3c1.3 0 2.4-.8 2.8-2h3.9l3.5-5.5 2.1 3.5h1.7c.4 1.2 1.5 2 2.8 2 1.7 0 3-1.3 3-3s-1.3-3-3-3h-.8l2.2-3.8c.2-.4 0-.9-.4-1.1-.1-.1-.3-.1-.4-.1z"/></svg></div>',
                    iconSize: [34, 34],
                    iconAnchor: [17, 17]
                });

                var destIcon = L.divIcon({
                    className: 'dest-marker-wrapper',
                    html: '<div class="mappls-dest-pin"></div>',
                    iconSize: [30, 30],
                    iconAnchor: [15, 30]
                });

                function updateUserLocation(lat, lng, heading) {
                    currentLat = lat;
                    currentLng = lng;
                    if (!userMarker) {
                        userMarker = L.marker([lat, lng], { icon: bikeIcon }).addTo(map);
                        map.setView([lat, lng], 16);
                    } else {
                        userMarker.setLatLng([lat, lng]);
                    }
                }

                function centerOnUser() {
                    if (userMarker) {
                        map.flyTo([currentLat, currentLng], 16, { animate: true, duration: 0.6 });
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
                        map.flyTo([lat, lng], 16, { animate: true, duration: 0.6 });
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

                    map.fitBounds(routePolyline.getBounds(), { padding: [50, 50] });
                }

                function clearRoute() {
                    if (routePolyline) {
                        map.removeLayer(routePolyline);
                        routePolyline = null;
                    }
                    if (routeGlow) {
                        map.removeLayer(routeGlow);
                        routeGlow = null;
                    }
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}
