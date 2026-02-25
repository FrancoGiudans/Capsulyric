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
import androidx.compose.runtime.livedata.observeAsState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
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

    // Media Control Dialog state
    val showMediaControlDialog = remember { mutableStateOf(false) }
    
    // System Info and Service Diagnostics states
    val showSystemInfoDialog = remember { mutableStateOf(false) }
    val showDiagnosticsDialog = remember { mutableStateOf(false) }

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
                                context.startActivity(Intent(context, DebugLyricActivity::class.java))
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
                        onClick = { showMediaControlDialog.value = true }
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
                        onClick = { showSystemInfoDialog.value = true }
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
                                context.startActivity(Intent(context, com.example.islandlyrics.oobe.OobeActivity::class.java))
                            } catch (_: Exception) {
                                Toast.makeText(context, "OOBE Activity not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    SuperArrow(
                        title = "Service Diagnostics",
                        summary = "Check MediaMonitor service status",
                        onClick = { showDiagnosticsDialog.value = true }
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
                            context.startService(Intent(context, LyricService::class.java))
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
                    
                    val romOptions = listOf("Auto", "HyperOS", "OneUI", "ColorOS", "OriginOS/FuntouchOS", "Flyme", "AOSP")
                    val currentRom = prefs.getString("debug_forced_rom_type", null) ?: "Auto"
                    val currentIndex = romOptions.indexOf(currentRom).coerceAtLeast(0)
                    top.yukonga.miuix.kmp.extra.SuperDropdown(
                        title = "Device Identifier Override",
                        items = romOptions,
                        selectedIndex = currentIndex,
                        onSelectedIndexChange = { index ->
                            val selected = romOptions[index]
                            val newType = if (selected == "Auto") null else selected
                            prefs.edit().putString("debug_forced_rom_type", newType).apply()
                            RomUtils.forcedRomType = newType
                            Toast.makeText(context, "Identifier set to $selected. Restart app to apply fully.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // Show update dialog if needed
    if (showUpdateDialog && updateReleaseInfo != null) {
        val dialogState = remember(updateReleaseInfo) { mutableStateOf(true) }
        if (dialogState.value) {
            MiuixUpdateDialog(
                show = dialogState,
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
        } else {
            showUpdateDialog = false
            updateReleaseInfo = null
        }
    }

    if (showMediaControlDialog.value) {
        MiuixMediaControlDialog(
            show = showMediaControlDialog,
            onDismiss = { showMediaControlDialog.value = false }
        )
    }

    if (showSystemInfoDialog.value) {
        SuperDialog(
            title = "System Info",
            show = showSystemInfoDialog,
            onDismissRequest = { showSystemInfoDialog.value = false }
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Android Version: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})",
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    color = MiuixTheme.colorScheme.onSurface
                )
                
                val romInfo = remember { RomUtils.getRomInfo() }
                if (romInfo.isNotEmpty()) {
                    Text(
                        "ROM Version: $romInfo", 
                        fontSize = MiuixTheme.textStyles.title4.fontSize, 
                        color = MiuixTheme.colorScheme.primary
                    )
                }
                
                val deviceType = remember { "${android.os.Build.MANUFACTURER}/${RomUtils.getRomType()}" }
                Text(
                    "Device Type: $deviceType", 
                    fontSize = MiuixTheme.textStyles.body2.fontSize, 
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                
                Text("Build ID: ${android.os.Build.DISPLAY}", fontSize = MiuixTheme.textStyles.body2.fontSize)
                Text("Manufacturer: ${android.os.Build.MANUFACTURER}", fontSize = MiuixTheme.textStyles.body2.fontSize)
                Text("Model: ${android.os.Build.MODEL} (${android.os.Build.DEVICE})", fontSize = MiuixTheme.textStyles.body2.fontSize)
                Text("Brand: ${android.os.Build.BRAND}", fontSize = MiuixTheme.textStyles.body2.fontSize)
                Text("Product: ${android.os.Build.PRODUCT}", fontSize = MiuixTheme.textStyles.body2.fontSize)
                Text("Hardware: ${android.os.Build.HARDWARE}", fontSize = MiuixTheme.textStyles.body2.fontSize)
                Text(
                    "Fingerprint:\n${android.os.Build.FINGERPRINT}", 
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
    }

    val diagnostics by LyricRepository.getInstance().liveDiagnostics.observeAsState()
    
    if (showDiagnosticsDialog.value) {
        SuperDialog(
            title = "Service Diagnostics",
            show = showDiagnosticsDialog,
            onDismissRequest = { showDiagnosticsDialog.value = false }
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (diagnostics == null) {
                    Text(
                        "Waiting for data...", 
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                } else {
                    Text("Is Connected: ${diagnostics?.isConnected}", fontSize = MiuixTheme.textStyles.body2.fontSize)
                    Text("Total Controllers: ${diagnostics?.totalControllers}", fontSize = MiuixTheme.textStyles.body2.fontSize)
                    Text("Whitelisted Controllers: ${diagnostics?.whitelistedControllers}", fontSize = MiuixTheme.textStyles.body2.fontSize)
                    Text("Primary Package: ${diagnostics?.primaryPackage}", fontSize = MiuixTheme.textStyles.body2.fontSize)
                    Text("Whitelist Size: ${diagnostics?.whitelistSize}", fontSize = MiuixTheme.textStyles.body2.fontSize)
                    Text("Last Setup: ${diagnostics?.lastUpdateParams}", fontSize = MiuixTheme.textStyles.body2.fontSize)
                    Text("Time: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(diagnostics?.timestamp ?: 0))}", fontSize = MiuixTheme.textStyles.body2.fontSize)
                }
            }
        }
    }
}
