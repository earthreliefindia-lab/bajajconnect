package com.openride.companion.navigation.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.openride.companion.navigation.model.GeoPoint
import com.openride.companion.navigation.model.NavigationRoute
import com.openride.companion.navigation.model.RouteAlternativeType
import com.openride.companion.navigation.model.Waypoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * Camera operational mode and state.
 */
data class MapCameraState(
    val centerLat: Double = 18.5204,
    val centerLon: Double = 73.8567,
    val zoomLevel: Float = 14f,
    val isHeadingUp: Boolean = true
)

enum class MapThemeMode {
    DAY,
    NIGHT,
    AUTO
}

data class MapPalette(
    val background: Color,
    val roadGrid: Color,
    val alternativeRoute: Color,
    val traveledRoute: Color,
    val activeRouteCasing: Color,
    val activeRouteCore: Color,
    val vehiclePuckCore: Color,
    val vehiclePuckHalo: Color
)

val NightMapPalette = MapPalette(
    background = Color(0xFF002644),       // Brand Navy
    roadGrid = Color(0xFF0D3C63),         // Elevated Navy
    alternativeRoute = Color(0xFF37474F), // Slate Gray
    traveledRoute = Color(0xFF1C3B5E),    // Muted Navy
    activeRouteCasing = Color(0xFF00182C),
    activeRouteCore = Color(0xFF00E5FF),  // Cyan Accent
    vehiclePuckCore = Color(0xFF00E5FF),
    vehiclePuckHalo = Color(0x4000E5FF)
)

val DayMapPalette = MapPalette(
    background = Color(0xFFF4F6F9),
    roadGrid = Color(0xFFE2E8F0),
    alternativeRoute = Color(0xFF94A3B8),
    traveledRoute = Color(0xFFCBD5E1),
    activeRouteCasing = Color(0xFF0F172A),
    activeRouteCore = Color(0xFF1565C0),  // Action Blue
    vehiclePuckCore = Color(0xFF002644),
    vehiclePuckHalo = Color(0x331565C0)
)

/**
 * Pluggable map renderer contract for production Compose UI and automated testing.
 */
interface MapRenderer {
    @Composable
    fun RenderMap(
        modifier: Modifier,
        cameraState: MapCameraState,
        route: NavigationRoute?,
        alternateRoutes: List<NavigationRoute>,
        currentLocation: GeoPoint?,
        bearingDegrees: Float,
        traveledPoints: List<GeoPoint>,
        waypoints: List<Waypoint>,
        themeMode: MapThemeMode
    )
}

/**
 * Pure Compose Canvas offline-testable vector map renderer supporting Web Mercator projection.
 */
class ComposeCanvasMapRenderer : MapRenderer {

    @Composable
    override fun RenderMap(
        modifier: Modifier,
        cameraState: MapCameraState,
        route: NavigationRoute?,
        alternateRoutes: List<NavigationRoute>,
        currentLocation: GeoPoint?,
        bearingDegrees: Float,
        traveledPoints: List<GeoPoint>,
        waypoints: List<Waypoint>,
        themeMode: MapThemeMode
    ) {
        val isDark = when (themeMode) {
            MapThemeMode.DAY -> false
            MapThemeMode.NIGHT -> true
            MapThemeMode.AUTO -> isSystemInDarkTheme()
        }
        val palette = if (isDark) NightMapPalette else DayMapPalette

        val effectiveCenter = if (cameraState.isHeadingUp && currentLocation != null) {
            currentLocation
        } else {
            GeoPoint(cameraState.centerLat, cameraState.centerLon)
        }

        Canvas(modifier = modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val scale = (1 shl cameraState.zoomLevel.toInt()) * 2.0

            fun project(point: GeoPoint): Offset {
                val x = (point.longitude + 180.0) / 360.0 * scale
                val latRad = Math.toRadians(point.latitude)
                val mercN = ln(tan((PI / 4.0) + (latRad / 2.0)))
                val y = (1.0 - (mercN / PI)) / 2.0 * scale

                val centerX = (effectiveCenter.longitude + 180.0) / 360.0 * scale
                val centerLatRad = Math.toRadians(effectiveCenter.latitude)
                val centerMercN = ln(tan((PI / 4.0) + (centerLatRad / 2.0)))
                val centerY = (1.0 - (centerMercN / PI)) / 2.0 * scale

                val screenX = (x - centerX + width / 2.0).toFloat()
                val screenY = (y - centerY + height / 2.0).toFloat()
                return Offset(screenX, screenY)
            }

            // Layer 0: Map Background
            drawRect(color = palette.background)

            // Layer 1: Road Grid lines
            val gridStep = 60f
            var gx = 0f
            while (gx < width) {
                drawLine(
                    color = palette.roadGrid,
                    start = Offset(gx, 0f),
                    end = Offset(gx, height),
                    strokeWidth = 1.5f
                )
                gx += gridStep
            }
            var gy = 0f
            while (gy < height) {
                drawLine(
                    color = palette.roadGrid,
                    start = Offset(0f, gy),
                    end = Offset(width, gy),
                    strokeWidth = 1.5f
                )
                gy += gridStep
            }

            // Rotation wrapper for Heading-Up navigation
            val rotationAngle = if (cameraState.isHeadingUp) -bearingDegrees else 0f
            val pivotOffset = if (currentLocation != null) project(currentLocation) else Offset(width / 2f, height / 2f)

            rotate(degrees = rotationAngle, pivot = pivotOffset) {
                // Layer 2: Alternate Routes
                for (alt in alternateRoutes) {
                    if (alt.polyline.size >= 2) {
                        drawPolyline(alt.polyline, ::project, palette.alternativeRoute, strokeWidth = 12f)
                    }
                }

                // Layer 3: Traveled Route Polyline (breadcrumbs)
                if (traveledPoints.size >= 2) {
                    drawPolyline(traveledPoints, ::project, palette.traveledRoute, strokeWidth = 16f)
                }

                // Layer 4: Planned Route Polyline
                if (route != null && route.polyline.size >= 2) {
                    val coreColor = if (route.alternativeType == RouteAlternativeType.ECO) {
                        Color(0xFF22C55E) // Green for Eco
                    } else {
                        palette.activeRouteCore // Cyan for Fastest
                    }
                    // Outer Casing
                    drawPolyline(route.polyline, ::project, palette.activeRouteCasing, strokeWidth = 24f)
                    // Inner Core
                    drawPolyline(route.polyline, ::project, coreColor, strokeWidth = 16f)
                }

                // Layer 5 & 6: Start & Destination pins
                route?.polyline?.firstOrNull()?.let { startPt ->
                    val offset = project(startPt)
                    drawCircle(color = Color(0xFF22C55E), radius = 18f, center = offset)
                    drawCircle(color = Color.White, radius = 8f, center = offset)
                }
                route?.polyline?.lastOrNull()?.let { endPt ->
                    val offset = project(endPt)
                    drawCircle(color = Color(0xFFFF3B30), radius = 22f, center = offset)
                    drawCircle(color = Color.White, radius = 9f, center = offset)
                }
            }

            // Layer 7: Current Vehicle Puck (drawn in unrotated screen coords or facing forward)
            if (currentLocation != null) {
                val puckPos = project(currentLocation)
                // Pulsating halo
                drawCircle(color = palette.vehiclePuckHalo, radius = 38f, center = puckPos)
                // Puck body
                drawCircle(color = Color.White, radius = 20f, center = puckPos)
                drawCircle(color = palette.vehiclePuckCore, radius = 16f, center = puckPos)

                // Directional Chevron
                val chevronAngle = if (cameraState.isHeadingUp) 0f else bearingDegrees
                rotate(degrees = chevronAngle, pivot = puckPos) {
                    val path = Path().apply {
                        moveTo(puckPos.x, puckPos.y - 12f)
                        lineTo(puckPos.x + 8f, puckPos.y + 8f)
                        lineTo(puckPos.x, puckPos.y + 4f)
                        lineTo(puckPos.x - 8f, puckPos.y + 8f)
                        close()
                    }
                    drawPath(path = path, color = Color.White)
                }
            }
        }
    }

    private fun DrawScope.drawPolyline(
        points: List<GeoPoint>,
        project: (GeoPoint) -> Offset,
        color: Color,
        strokeWidth: Float
    ) {
        if (points.size < 2) return
        val path = Path()
        val first = project(points.first())
        path.moveTo(first.x, first.y)
        for (i in 1 until points.size) {
            val pt = project(points[i])
            path.lineTo(pt.x, pt.y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
