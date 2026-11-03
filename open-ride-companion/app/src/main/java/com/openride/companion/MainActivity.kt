package com.openride.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            BajajTheme {
                AppNavGraph(
                    sessionPreferences = sessionPreferences,
                    authRepository = authRepository,
                    vehicleRepository = vehicleRepository,
                    profileRepository = profileRepository,
                    sosRepository = sosRepository,
                    tripRepository = tripRepository,
                    documentRepository = documentRepository,
                    navigationManager = navigationManager
                )
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
