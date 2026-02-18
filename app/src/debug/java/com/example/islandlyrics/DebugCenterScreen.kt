package com.example.islandlyrics

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.livedata.observeAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugCenterScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Center") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Debug Lyric API ──
            DebugMenuButton(
                text = "Debug Lyric API",
                description = "Test lyric fetching and parsing",
                onClick = {
                    val intent = Intent(context, DebugLyricActivity::class.java)
                    context.startActivity(intent)
                }
            )

            // ── Custom Settings Page ──
            DebugMenuButton(
                text = "Custom Settings Page",
                description = "Grouped settings: App Body, Capsule, Notification",
                onClick = {
                    val intent = Intent(context, CustomSettingsActivity::class.java)
                    context.startActivity(intent)
                }
            )

            // ── Test Update Dialog ──
            DebugMenuButton(
                text = "Test Update Dialog",
                description = "Show a dummy update available dialog",
                onClick = { showUpdateDialog = true }
            )

            // ── Media Controls ──
            var showMediaControlDialog by remember { mutableStateOf(false) }
            DebugMenuButton(
                text = "Media Controls",
                description = "Control playback, open app, or launch Mi Play",
                onClick = { showMediaControlDialog = true }
            )

            if (showMediaControlDialog) {
                MediaControlDialog(onDismiss = { showMediaControlDialog = false })
            }

            // ── System Info ──
            var showSystemInfoDialog by remember { mutableStateOf(false) }
            DebugMenuButton(
                text = "Show System Info",
                description = "Display Android version, ROM, and device info",
                onClick = { showSystemInfoDialog = true }
            )

            if (showSystemInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showSystemInfoDialog = false },
                    title = { Text("System Info") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Android Version: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                            // Extended ROM Info
                            val romInfo = remember { RomUtils.getRomInfo() }
                            if (romInfo.isNotEmpty()) {
                                Text("ROM Version: $romInfo", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            // Device Type
                            val deviceType = remember { "${android.os.Build.MANUFACTURER}/${RomUtils.getRomType()}" }
                            Text("Device Type: $deviceType", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                            
                            Text("Build ID: ${android.os.Build.DISPLAY}")
                            Text("Manufacturer: ${android.os.Build.MANUFACTURER}")
                            Text("Model: ${android.os.Build.MODEL} (${android.os.Build.DEVICE})")
                            Text("Brand: ${android.os.Build.BRAND}")
                            Text("Product: ${android.os.Build.PRODUCT}")
                            Text("Hardware: ${android.os.Build.HARDWARE}")
                            Text("Fingerprint:\n${android.os.Build.FINGERPRINT}", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSystemInfoDialog = false }) {
                            Text("Generic OK") // Matches user's typical style if any, but "OK" is fine
                        }
                    }
                )
            }
            // ── OOBE ──
            DebugMenuButton(
                text = "Launch OOBE",
                description = "Open Onboarding Screen directly",
                onClick = {
                    context.startActivity(Intent(context, com.example.islandlyrics.oobe.OobeActivity::class.java))
                }
            )

            // ── Service Diagnostics ──
            DebugMenuButton(
                text = "Service Diagnostics",
                description = "View internal state of Notification Listener Service",
                onClick = { showDiagnosticsDialog = true }
            )
        }
    }

    if (showUpdateDialog) {
        val dummyRelease = UpdateChecker.ReleaseInfo(
            tagName = "v9.9.9_DEBUG",
            name = "Debug Test Update",
            body = "### New Features\n- This is a test update dialog.\n- It verifies that the dialog renders correctly.",
            htmlUrl = "https://github.com/FrancoGiudans/Capsulyric/releases",
            publishedAt = "2026-01-01T00:00:00Z",
            prerelease = true
        )

        UpdateDialog(
            releaseInfo = dummyRelease,
            onDismiss = { showUpdateDialog = false },
            onIgnore = { /* No-op in debug */ }
        )
    }



    // ── Diagnostics Monitor Dialog ──
    val diagnostics by LyricRepository.getInstance().liveDiagnostics.observeAsState()
    
    if (showDiagnosticsDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = { Text("Service Diagnostics") },
            text = {
                Column {
                    if (diagnostics == null) {
                        Text("Waiting for data...", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Is Connected: ${diagnostics?.isConnected}")
                        Text("Total Controllers: ${diagnostics?.totalControllers}")
                        Text("Whitelisted Controllers: ${diagnostics?.whitelistedControllers}")
                        Text("Primary Package: ${diagnostics?.primaryPackage}")
                        Text("Whitelist Size: ${diagnostics?.whitelistSize}")
                        Text("Last Setup: ${diagnostics?.lastUpdateParams}")
                        Text("Time: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(diagnostics?.timestamp ?: 0))}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagnosticsDialog = false }) {
                        Text("Close")
                }
            }
        )
    }
}


@Composable
private fun DebugMenuButton(
    text: String,
    description: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


