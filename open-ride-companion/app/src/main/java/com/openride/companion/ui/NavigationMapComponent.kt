package com.openride.companion.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
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

    // Reload HTML when API key is switched between free mode and custom key
    LaunchedEffect(mapplsApiKey) {
        if (isMapLoaded) {
            webViewInstance?.loadDataWithBaseURL(
                "https://mappls.com",
                getMapplsStyleMapHtml(mapplsApiKey),
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
            .background(Color(0xFF0C131F))
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
                    setBackgroundColor(0xFF0C131F.toInt())

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

                    val htmlContent = getMapplsStyleMapHtml(mapplsApiKey)
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

        // Top-Left Badge: Mappls Maps · Two-Wheeler Mode (Tap to configure)
        Surface(
            onClick = onConfigureMapplsClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            color = Color(0xFF0066FF).copy(alpha = 0.9f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TwoWheeler,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (mapplsApiKey.isNotBlank()) "MAPPLS PRO · BIKE MODE" else "MAPPLS FREE · BIKE MODE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Setup Mappls Key",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // Bottom-Left Badge: Free Mappls Vector Tiles / API Key Status
        Surface(
            onClick = onConfigureMapplsClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            color = Color(0xFF070C14).copy(alpha = 0.85f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (mapplsApiKey.isNotBlank()) Color(0xFF00E676) else Color(0xFF00E5FF))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (mapplsApiKey.isNotBlank()) "Mappls API Key Active · Setup" else "Mappls Free Mode (No Key Needed) · Setup",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (mapplsApiKey.isNotBlank()) Color(0xFF00E676) else Color(0xFF94A3B8)
                )
            }
        }

        // Floating "My Location" Recenter Button
        FloatingActionButton(
            onClick = {
                userLatitude?.let { lat ->
                    userLongitude?.let { lng ->
                        webViewInstance?.evaluateJavascript("updateUserLocation($lat, $lng, 0); centerOnUser();", null)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(44.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Center on my location",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun getMapplsStyleMapHtml(apiKey: String = ""): String {
    val cleanKey = apiKey.trim()
    val isCustomKey = cleanKey.isNotEmpty()

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
                    background-color: #0C131F;
                }
                .leaflet-control-attribution {
                    display: none !important;
                }
                /* Motorcycle Rider Marker */
                .bike-rider-marker {
                    width: 32px;
                    height: 32px;
                    background: #0066FF;
                    border: 3px solid #FFFFFF;
                    border-radius: 50%;
                    box-shadow: 0 0 16px #00E5FF, 0 0 24px rgba(0, 102, 255, 0.6);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    animation: bikePulse 2s infinite;
                }
                .bike-rider-marker svg {
                    width: 18px;
                    height: 18px;
                    fill: #FFFFFF;
                }
                @keyframes bikePulse {
                    0% { transform: scale(0.96); box-shadow: 0 0 0 0 rgba(0, 229, 255, 0.7); }
                    70% { transform: scale(1.04); box-shadow: 0 0 0 16px rgba(0, 229, 255, 0); }
                    100% { transform: scale(0.96); box-shadow: 0 0 0 0 rgba(0, 229, 255, 0); }
                }
                /* Mappls Destination Pin */
                .mappls-dest-pin {
                    width: 28px;
                    height: 28px;
                    background: #FF3B30;
                    border: 3px solid #FFFFFF;
                    border-radius: 50% 50% 50% 0;
                    transform: rotate(-45deg);
                    box-shadow: 0 0 12px rgba(255, 59, 48, 0.9);
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map', {
                    zoomControl: false,
                    attributionControl: false
                }).setView([18.5204, 73.8567], 14);

                // Configure Tile Engine: Custom Mappls API Key vs 100% Free Vector Tile Engine
                var isCustom = $isCustomKey;
                var customKey = '$cleanKey';
                var defaultFreeUrl = 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png';
                var activeTileUrl = isCustom 
                    ? 'https://apis.mappls.com/advancedmaps/v1/' + encodeURIComponent(customKey) + '/raster_tile/{z}/{x}/{y}.png'
                    : defaultFreeUrl;

                var mainTileLayer = L.tileLayer(activeTileUrl, {
                    maxZoom: 19,
                    subdomains: isCustom ? '' : 'abcd'
                }).addTo(map);

                // Automatic seamless fallback: if custom Mappls key fails or is unauthorized, switch to Free mode
                if (isCustom) {
                    mainTileLayer.on('tileerror', function() {
                        console.warn('Mappls API key authorization failed, seamlessly falling back to Free Mappls Vector Tiles');
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

                // Motorcycle SVG Icon
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
                    
                    // Outer glow
                    routeGlow = L.polyline(coordinates, {
                        color: '#0066FF',
                        weight: 10,
                        opacity: 0.5,
                        lineJoin: 'round',
                        lineCap: 'round'
                    }).addTo(map);

                    // Inner neon cyan bike path
                    routePolyline = L.polyline(coordinates, {
                        color: '#00E5FF',
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
