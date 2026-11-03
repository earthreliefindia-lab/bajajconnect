package com.openride.companion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openride.companion.data.DocumentLockerRepository
import com.openride.companion.navigation.model.DocExpiryStatus
import com.openride.companion.navigation.model.DocType
import com.openride.companion.navigation.model.VehicleDocument
import com.openride.companion.ui.theme.ActionBlue
import com.openride.companion.ui.theme.BrandNavy
import com.openride.companion.ui.theme.CyanAccent
import com.openride.companion.ui.theme.ElevatedNavy
import com.openride.companion.ui.theme.SurfaceDarkBorder
import com.openride.companion.ui.theme.SurfaceDarkTextSec
import kotlinx.coroutines.launch

@Composable
fun DocumentLockerScreen(
    documentRepository: DocumentLockerRepository,
    onNavigateBack: () -> Unit = {}
) {
    val documents by documentRepository.documentsState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BrandNavy
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Screen Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Vehicle Document Locker",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Bajaj Pulsar NS400Z • MH12TC5590",
                        fontSize = 12.sp,
                        color = CyanAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Verification Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ElevatedNavy),
                border = BorderStroke(1.dp, SurfaceDarkBorder)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Verified, contentDescription = "Verified", tint = Color(0xFF22C55E), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${documents.count { it.isVerified }}/${documents.size} Documents Verified",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "DigiLocker integrated with Ministry of Road Transport",
                            color = SurfaceDarkTextSec,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Document List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(documents) { doc ->
                    DocumentCard(
                        doc = doc,
                        onSyncToCluster = {
                            scope.launch {
                                // Simulate 12KB sample document image payload
                                val sampleImage = ByteArray(12288) { 0x5A }
                                val payload = documentRepository.prepareClusterSyncPayload(doc, sampleImage)
                                documentRepository.setClusterSyncStatus(doc.id, true)
                                snackbarHostState.showSnackbar("Synced ${doc.documentTitle} to Bike TFT Cluster (${payload.size} bytes)")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentCard(
    doc: VehicleDocument,
    onSyncToCluster: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedNavy),
        border = BorderStroke(1.dp, SurfaceDarkBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (doc.docType) {
                            DocType.INSURANCE -> Icons.Default.Security
                            else -> Icons.Default.Description
                        },
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = doc.documentTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Status Badge
                ExpiryBadge(status = doc.expiryStatus)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Monospace Document Number
            Text(
                text = doc.documentNumber,
                color = CyanAccent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = doc.issuer, color = SurfaceDarkTextSec, fontSize = 12.sp)
            Text(text = "Expiry: ${doc.expiryDate}", color = Color.White, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (doc.isSyncedToCluster) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, contentDescription = "Synced", tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Synced to Cluster", color = Color(0xFF22C55E), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = onSyncToCluster,
                    colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = "Sync", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Sync to TFT", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ExpiryBadge(status: DocExpiryStatus) {
    val (bgColor, textColor, label) = when (status) {
        DocExpiryStatus.VALID -> Triple(Color(0xFF22C55E).copy(alpha = 0.2f), Color(0xFF22C55E), "VALID")
        DocExpiryStatus.EXPIRING_SOON -> Triple(Color(0xFFFFA000).copy(alpha = 0.2f), Color(0xFFFFA000), "EXPIRING SOON")
        DocExpiryStatus.EXPIRED -> Triple(Color(0xFFFF3B30).copy(alpha = 0.2f), Color(0xFFFF3B30), "EXPIRED")
    }

    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = label, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
