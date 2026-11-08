package com.openride.companion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.openride.companion.location.LocationIntentParser
import com.openride.companion.location.RouteNavigationManager
import com.openride.companion.navigation.model.GeoPoint
import com.openride.companion.navigation.model.NavigationRoute
import com.openride.companion.navigation.model.NavigationStep
import com.openride.companion.navigation.model.RouteAlternativeType
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openride.companion.ui.theme.BajajGradients
import com.openride.companion.ui.theme.BajajTheme

import com.openride.companion.data.auth.AuthRepositoryImpl
import com.openride.companion.data.DefaultDocumentLockerRepository
import com.openride.companion.data.DefaultTripRepository
import com.openride.companion.data.preferences.InMemorySessionPreferences
import com.openride.companion.data.profile.ProfileRepositoryImpl
import com.openride.companion.data.sos.EmergencySosRepositoryImpl
import com.openride.companion.data.vehicle.VehicleRepositoryImpl
import com.openride.companion.navigation.AppNavGraph
import com.openride.companion.navigation.state.NavigationManager

class MainActivity : ComponentActivity() {

    private val sessionPreferences by lazy { InMemorySessionPreferences() }
    private val authRepository by lazy { AuthRepositoryImpl() }
    private val vehicleRepository by lazy { VehicleRepositoryImpl() }
    private val profileRepository by lazy { ProfileRepositoryImpl() }
    private val sosRepository by lazy { EmergencySosRepositoryImpl() }
    private val tripRepository by lazy { DefaultTripRepository() }
    private val documentRepository by lazy { DefaultDocumentLockerRepository() }
    private val navigationManager by lazy { NavigationManager() }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Request location permissions if not already granted
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        handleIncomingIntent(intent)

        setContent {
            val profile by profileRepository.profileState.collectAsState()
            BajajTheme(themeMode = profile.themeOverride) {
                AppNavGraph(
                    sessionPreferences = sessionPreferences,
                    authRepository = authRepository,
                    vehicleRepository = vehicleRepository,
                    profileRepository = profileRepository,
                    tripRepository = tripRepository,
                    documentRepository = documentRepository,
                    navigationManager = navigationManager
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        lifecycleScope.launch {
            val destination = LocationIntentParser.parseIntent(this@MainActivity, intent)
            if (destination != null) {
                val startLat = 18.5204
                val startLng = 73.8567
                val route = RouteNavigationManager.calculateRoute(startLat, startLng, destination.latitude, destination.longitude)
                if (route != null) {
                    val polyGeo = route.coordinates.map { GeoPoint(it.first, it.second) }
                    val steps = route.steps.mapIndexed { idx, s ->
                        NavigationStep(
                            stepIndex = idx,
                            maneuverCode = s.maneuverCode,
                            streetName = s.streetName,
                            startCoordinate = polyGeo.getOrElse(idx) { GeoPoint(startLat, startLng) },
                            endCoordinate = polyGeo.getOrElse(idx + 1) { GeoPoint(destination.latitude, destination.longitude) },
                            distanceMeters = s.distanceMeters,
                            durationSeconds = s.durationSeconds.toInt(),
                            voiceInstruction = s.instruction
                        )
                    }
                    val navRoute = NavigationRoute(
                        id = "shared_${System.currentTimeMillis()}",
                        name = destination.title,
                        alternativeType = RouteAlternativeType.FASTEST,
                        polyline = polyGeo,
                        distanceMeters = route.totalDistanceMeters.toInt(),
                        durationSeconds = route.totalDurationSeconds.toInt(),
                        steps = steps,
                        hasTolls = false
                    )
                    navigationManager.selectRoute(navRoute)
                    Toast.makeText(this@MainActivity, "Opened shared location: ${destination.title}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BajajGradients.HeroNavy),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_bajaj_logo_white),
                contentDescription = "Bajaj Logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "RIDE CONNECT",
                style = MaterialTheme.typography.headlineLarge.copy(
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
        }
    }
}
