package com.example.islandlyrics

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixDebugCenterScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    // Super Island toggle state
    var superIslandEnabled by remember {
        mutableStateOf(prefs.getBoolean("super_island_enabled", false))
    }

    // Miuix UI toggle state
    var miuixEnabled by remember {
        mutableStateOf(prefs.getBoolean("ui_use_miuix", false))
    }

    // Update dialog states
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateReleaseInfo by remember { mutableStateOf<UpdateChecker.ReleaseInfo?>(null) }
    var isFetchingUpdate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "Debug Center",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // ── Debug Tools Section ──
            item {
                SmallTitle(text = "Debug Tools")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperArrow(
                        title = "Debug Lyric API",
                        summary = "Test lyric fetching with mock media data",
                        onClick = {
                            try {
                                val clazz = Class.forName("com.example.islandlyrics.DebugLyricActivity")
                                context.startActivity(Intent(context, clazz))
                            } catch (_: Exception) {
                                Toast.makeText(context, "Debug Activity not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    SuperArrow(
                        title = "Custom Settings Page",
                        summary = "Open Personalization settings",
                        onClick = {
                            context.startActivity(Intent(context, CustomSettingsActivity::class.java))
                        }
                    )
                    SuperArrow(
                        title = "Test Update Dialog",
                        summary = if (isFetchingUpdate) "Fetching..." else "Force fetch latest release info",
                        enabled = !isFetchingUpdate,
                        onClick = {
                            isFetchingUpdate = true
                            val scope = (context as? androidx.activity.ComponentActivity)
                            scope?.lifecycleScope?.launch {
                                try {
                                    val release = UpdateChecker.fetchAbsoluteLatestRelease()
                                    if (release != null) {
                                        updateReleaseInfo = release
                                        showUpdateDialog = true
                                    } else {
                                        Toast.makeText(context, "Failed to fetch release info", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isFetchingUpdate = false
                                }
                            }
                        }
                    )
                    SuperArrow(
                        title = "Media Controls",
                        summary = "Open the media control dialog",
                        onClick = {
                            try {
                                val clazz = Class.forName("com.example.islandlyrics.MediaControlDialog")
                                context.startActivity(Intent(context, clazz))
                            } catch (_: Exception) {
                                Toast.makeText(context, "MediaControlDialog not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // ── System Section ──
            item {
                SmallTitle(text = "System")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperArrow(
                        title = "System Info",
                        summary = "View device and ROM info",
                        onClick = {
                            val info = buildString {
                                appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                                appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                                appendLine("ROM: ${android.os.Build.DISPLAY}")
                            }
                            Toast.makeText(context, info, Toast.LENGTH_LONG).show()
                        }
                    )
                    SuperArrow(
                        title = "Log Console",
                        summary = "View application logs",
                        onClick = { LogViewerActivity.start(context) }
                    )
                    SuperArrow(
                        title = "Launch OOBE",
                        summary = "Re-open initial setup wizard",
                        onClick = {
                            try {
                                val clazz = Class.forName("com.example.islandlyrics.oobe.OobeActivity")
                                context.startActivity(Intent(context, clazz))
                            } catch (_: Exception) {
                                Toast.makeText(context, "OOBE Activity not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    SuperArrow(
                        title = "Service Diagnostics",
                        summary = "Check MediaMonitor service status",
                        onClick = {
                            val connected = MediaMonitorService.isConnected
                            Toast.makeText(context, "Service Connected: $connected", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // ── Toggles Section ──
            item {
                SmallTitle(text = "Feature Toggles")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperSwitch(
                        title = "Super Island",
                        summary = "Enable Super Island notification display",
                        checked = superIslandEnabled,
                        onCheckedChange = { enabled ->
                            superIslandEnabled = enabled
                            prefs.edit().putBoolean("super_island_enabled", enabled).apply()
                            if (enabled) {
                                try {
                                    val clazz = Class.forName("com.example.islandlyrics.SuperIslandHandler")
                                    val method = clazz.getDeclaredMethod("enableSuperIsland", Context::class.java)
                                    method.invoke(null, context)
                                } catch (_: Exception) {}
                            }
                        }
                    )
                    SuperSwitch(
                        title = "Miuix UI Mode",
                        summary = "Switch all pages to miuix-styled components. App will restart.",
                        checked = miuixEnabled,
                        onCheckedChange = { enabled ->
                            miuixEnabled = enabled
                            prefs.edit().putBoolean("ui_use_miuix", enabled).apply()
                            // Restart app to apply theme change
                            val intent = Intent(context, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            context.startActivity(intent)
                            (context as? android.app.Activity)?.finish()
                        }
                    )
                }
            }
        }
    }

    // Show update dialog if needed
    if (showUpdateDialog && updateReleaseInfo != null) {
        UpdateDialog(
            releaseInfo = updateReleaseInfo!!,
            onDismiss = {
                showUpdateDialog = false
                updateReleaseInfo = null
            },
            onIgnore = { tag ->
                UpdateChecker.setIgnoredVersion(context, tag)
                showUpdateDialog = false
                updateReleaseInfo = null
            }
        )
    }
}
