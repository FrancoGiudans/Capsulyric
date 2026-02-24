package com.example.islandlyrics

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugCenterScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateReleaseInfo by remember { mutableStateOf<UpdateChecker.ReleaseInfo?>(null) }
    var isFetchingUpdate by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
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
                text = if (isFetchingUpdate) "Fetching latest release..." else "Test Update Dialog",
                description = "Show update dialog with absolute latest release (incl. prerelease)",
                onClick = {
                    if (isFetchingUpdate) return@DebugMenuButton
                    isFetchingUpdate = true
                    coroutineScope.launch {
                        val release = UpdateChecker.fetchAbsoluteLatestRelease()
                        isFetchingUpdate = false
                        if (release != null) {
                            updateReleaseInfo = release
                            showUpdateDialog = true
                        } else {
                            android.widget.Toast.makeText(context, "Failed to fetch release", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
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
            
            // ── Device Identifier Override ──
            var showDeviceIdentifierDialog by remember { mutableStateOf(false) }
            val currentForced = prefs.getString("debug_forced_rom_type", null) ?: "Auto"
            DebugMenuButton(
                text = "Override Device Identifier",
                description = "Force ROM type (Current: $currentForced)",
                onClick = { showDeviceIdentifierDialog = true }
            )

            if (showDeviceIdentifierDialog) {
                AlertDialog(
                    onDismissRequest = { showDeviceIdentifierDialog = false },
                    title = { Text("Select Device Identifier") },
                    text = {
                        Column {
                            val options = listOf("Auto", "HyperOS", "OneUI", "ColorOS", "OriginOS/FuntouchOS", "Flyme", "AOSP")
                            options.forEach { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val value = if (option == "Auto") null else option
                                            prefs.edit().putString("debug_forced_rom_type", value).apply()
                                            RomUtils.forcedRomType = value
                                            showDeviceIdentifierDialog = false
                                            android.widget.Toast.makeText(context, "Restart app to apply fully", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 12.dp)
                                ) {
                                    Text(text = option)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDeviceIdentifierDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            // ── Log Console ──
            DebugMenuButton(
                text = "Log Console",
                description = "View and export application logs",
                onClick = {
                    LogViewerActivity.start(context)
                }
            )
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

            // ── Super Island Toggle ──
            var superIslandEnabled by remember {
                mutableStateOf(prefs.getBoolean("debug_super_island_enabled", false))
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "发送小米超级岛通知",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "替代 Live Update，使用小米超级岛模版发送歌词通知",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = superIslandEnabled,
                        onCheckedChange = { enabled ->
                            superIslandEnabled = enabled
                            prefs.edit().putBoolean("debug_super_island_enabled", enabled).apply()

                            val action = if (enabled) {
                                "ACTION_ENABLE_SUPER_ISLAND"
                            } else {
                                "ACTION_DISABLE_SUPER_ISLAND"
                            }
                            val intent = Intent(context, LyricService::class.java).setAction(action)
                            context.startService(intent)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Miuix UI Toggle ──
            var miuixEnabled by remember {
                mutableStateOf(prefs.getBoolean("ui_use_miuix", false))
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Switch to Miuix UI",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Switch all pages to miuix-styled components. App will restart.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = miuixEnabled,
                        onCheckedChange = { enabled ->
                            miuixEnabled = enabled
                            prefs.edit().putBoolean("ui_use_miuix", enabled).apply()
                            // Restart app to apply theme change
                            val restartIntent = Intent(context, MainActivity::class.java)
                            restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            context.startActivity(restartIntent)
                            (context as? android.app.Activity)?.finish()
                        }
                    )
                }
            }
        }
    }

    if (showUpdateDialog && updateReleaseInfo != null) {
        UpdateDialog(
            releaseInfo = updateReleaseInfo!!,
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


