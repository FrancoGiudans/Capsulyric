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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable

private fun MediaControlDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var activeControllers by remember { mutableStateOf<List<MediaController>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("Scanning...") }

    // Load controllers & Listen for changes
    DisposableEffect(Unit) {
        val mediaSessionManager = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = android.content.ComponentName(context, MediaMonitorService::class.java)

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            activeControllers = controllers ?: emptyList()
            statusMessage = "Found ${activeControllers.size} raw sessions"
        }

        try {
            // Initial load
            activeControllers = mediaSessionManager.getActiveSessions(componentName)
            statusMessage = "Found ${activeControllers.size} raw sessions"
            // Register listener
            mediaSessionManager.addOnActiveSessionsChangedListener(listener, componentName)
        } catch (e: SecurityException) {
            statusMessage = "Permission Required (Notification Listener)"
        } catch (e: Exception) {
            statusMessage = "Error: ${e.message}"
        }

        onDispose {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(listener)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Filter by Whitelist
    val whitelistedControllers = remember(activeControllers) {
        val enabledPackages = ParserRuleHelper.getEnabledPackages(context)
        activeControllers.filter { enabledPackages.contains(it.packageName) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Media Controls") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Status
                Text(
                    text = "$statusMessage (Whitelisted: ${whitelistedControllers.size})",
                    style = MaterialTheme.typography.bodySmall
                )

                if (whitelistedControllers.isNotEmpty()) {
                    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { whitelistedControllers.size })
                    
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        pageSpacing = 8.dp
                    ) { page ->
                        // Use a key to ensure we don't reuse state incorrectly when the list changes
                        key(whitelistedControllers[page].packageName) {
                            MediaSessionCard(controller = whitelistedControllers[page], context = context)
                        }
                    }
                    
                    // Simple Pager Indicator
                    if (whitelistedControllers.size > 1) {
                         Row(
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(whitelistedControllers.size) { iteration ->
                                val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .size(8.dp)
                                        .background(color, androidx.compose.foundation.shape.CircleShape)
                                )
                            }
                        }
                    }

                } else {
                    Text("No whitelisted media sessions found.\nCheck Parser Rules or play music in a supported app.", color = MaterialTheme.colorScheme.error)
                }

                Divider()

                // Mi Play (Always available)
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent()
                            intent.component = android.content.ComponentName(
                                "miui.systemui.plugin",
                                "miui.systemui.miplay.MiPlayDetailActivity"
                            )
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Mi Play Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Launch Xiaomi Mi Play")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun MediaSessionCard(controller: MediaController, context: android.content.Context) {
    // Observable state for the controller
    var playbackState by remember(controller) { mutableStateOf(controller.playbackState) }
    var metadata by remember(controller) { mutableStateOf(controller.metadata) }

    DisposableEffect(controller) {
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                playbackState = state
            }
            override fun onMetadataChanged(meta: MediaMetadata?) {
                metadata = meta
            }
        }
        controller.registerCallback(callback)
        onDispose {
            controller.unregisterCallback(callback)
        }
    }

    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title"
    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
    val pkg = controller.packageName
    val appName = remember(pkg) { ParserRuleHelper.getAppNameForPackage(context, pkg) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "$appName ($pkg)", style = MaterialTheme.typography.labelSmall)
            Text(text = title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(text = artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            
            Spacer(modifier = Modifier.height(8.dp))

            // Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { controller.transportControls.skipToPrevious() }) {
                    Icon(painterResource(R.drawable.ic_skip_previous), "Prev")
                }
                IconButton(onClick = {
                    val state = playbackState?.state
                    if (state == PlaybackState.STATE_PLAYING) {
                        controller.transportControls.pause()
                    } else {
                        controller.transportControls.play()
                    }
                }) {
                     val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
                     Icon(painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow), "Toggle")
                }
                IconButton(onClick = { controller.transportControls.skipToNext() }) {
                    Icon(painterResource(R.drawable.ic_skip_next), "Next")
                }
            }
            
            // Actions
            Button(
                onClick = {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            android.widget.Toast.makeText(context, "Cannot open $pkg", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open App")
            }
        }
    }
}
