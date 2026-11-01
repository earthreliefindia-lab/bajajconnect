package com.openride.companion.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.JavascriptInterface
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF121418))
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
                    setBackgroundColor(0xFF121418.toInt())

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isMapLoaded = true
                            // Initial center
                            userLatitude?.let { lat ->
                                userLongitude?.let { lng ->
                                    view?.evaluateJavascript("updateUserLocation($lat, $lng, 0);", null)
                                    view?.evaluateJavascript("centerOnUser();", null)
                                }
                            }
                        }
                    }

                    val htmlContent = getLeafletMapHtml()
                    loadDataWithBaseURL("https://openride.app", htmlContent, "text/html", "UTF-8", null)
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

                    // Update route polyline
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

private fun getLeafletMapHtml(): String {
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
                    background-color: #121418;
                }
                .leaflet-control-attribution {
                    display: none !important;
                }
                /* Pulsing Rider Marker */
                .rider-marker {
                    width: 22px;
                    height: 22px;
                    background: #00E5FF;
                    border: 3px solid #FFFFFF;
                    border-radius: 50%;
                    box-shadow: 0 0 12px #00E5FF, 0 0 20px #00E5FF;
                    animation: pulse 1.8s infinite;
                }
                @keyframes pulse {
                    0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(0, 229, 255, 0.7); }
                    70% { transform: scale(1.05); box-shadow: 0 0 0 14px rgba(0, 229, 255, 0); }
                    100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(0, 229, 255, 0); }
                }
                /* Destination Pin */
                .dest-pin {
                    width: 26px;
                    height: 26px;
                    background: #FF5252;
                    border: 3px solid #FFFFFF;
                    border-radius: 50% 50% 50% 0;
                    transform: rotate(-45deg);
                    box-shadow: 0 0 10px rgba(255, 82, 82, 0.8);
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

                // CartoDB Dark Matter tiles
                L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
                    maxZoom: 19,
                    subdomains: 'abcd'
                }).addTo(map);

                var userMarker = null;
                var destMarker = null;
                var routePolyline = null;
                var currentLat = 18.5204;
                var currentLng = 73.8567;

                var riderIcon = L.divIcon({
                    className: 'rider-marker-wrapper',
                    html: '<div class="rider-marker"></div>',
                    iconSize: [22, 22],
                    iconAnchor: [11, 11]
                });

                var destIcon = L.divIcon({
                    className: 'dest-marker-wrapper',
                    html: '<div class="dest-pin"></div>',
                    iconSize: [26, 26],
                    iconAnchor: [13, 26]
                });

                function updateUserLocation(lat, lng, heading) {
                    currentLat = lat;
                    currentLng = lng;
                    if (!userMarker) {
                        userMarker = L.marker([lat, lng], { icon: riderIcon }).addTo(map);
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
                        destMarker.bindPopup('<b style="color:#000;">' + title + '</b>');
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
                    
                    routePolyline = L.polyline(coordinates, {
                        color: '#00E5FF',
                        weight: 6,
                        opacity: 0.9,
                        lineJoin: 'round',
                        lineCap: 'round'
                    }).addTo(map);

                    // Fit map bounds to show route and user
                    var bounds = L.latLngBounds(coordinates);
                    map.fitBounds(bounds, { padding: [40, 40] });
                }

                function clearRoute() {
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
