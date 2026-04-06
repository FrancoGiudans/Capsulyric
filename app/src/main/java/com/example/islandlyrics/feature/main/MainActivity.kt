package com.example.islandlyrics.feature.main

import com.example.islandlyrics.ui.common.BaseActivity
import android.content.BroadcastReceiver
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.update.UpdateChecker
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.service.MediaMonitorService
import com.example.islandlyrics.feature.customsettings.CustomSettingsActivity
import com.example.islandlyrics.feature.navigation.MaterialTopLevelNavigationBar
import com.example.islandlyrics.feature.navigation.MiuixTopLevelFloatingNavigationBar
import com.example.islandlyrics.feature.navigation.TopLevelDestination
import com.example.islandlyrics.feature.main.miuix.MiuixMainScreen
import com.example.islandlyrics.feature.parserrule.material.ParserRuleScreen
import com.example.islandlyrics.feature.settings.material.SettingsScreen
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.LocalMiuixBlurBackdrop
import com.example.islandlyrics.ui.miuix.LocalMiuixBlurEnabled
import com.example.islandlyrics.feature.update.material.UpdateDialog
import com.example.islandlyrics.feature.main.material.MainScreen
import com.example.islandlyrics.ui.theme.material.AppTheme
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

class MainActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())

    // Compose state for API dashboard (driven by BroadcastReceiver + reflection checks)
    private var versionText by mutableStateOf("...")
    private var updateReleaseInfo by mutableStateOf<UpdateChecker.ReleaseInfo?>(null)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OOBE Check
        val prefs = getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_setup_complete", false)) {
            startActivity(Intent(this, com.example.islandlyrics.feature.oobe.OobeActivity::class.java))
            finish()
            return
        }

        updateVersionInfo()

        // Auto-check for updates on startup
        if (!OfflineModeManager.isEnabled(this) && UpdateChecker.isAutoUpdateEnabled(this) && !hasCheckedForUpdates) {
            hasCheckedForUpdates = true
            lifecycleScope.launch {
                try {
                    val release = UpdateChecker.checkForUpdate(this@MainActivity)
                    if (release != null) {
                        updateReleaseInfo = release
                        AppLogger.getInstance().log("MainActivity", "Auto-update found: ${release.tagName}")
                    }
                } catch (e: Exception) {
                    AppLogger.getInstance().log("MainActivity", "Auto-update check failed: ${e.message}")
                }
            }
        }

        val useMiuix = isMiuixEnabled(this)

        setContent {
            if (useMiuix) {
                MiuixAppTheme {
                    MiuixTopLevelPager()
                }
            } else {
                MaterialThemeHost()
            }
        }

        checkPromotedNotificationPermission()
    }

    // API 36 Permission Check (Standard Runtime Permission)
    private fun checkPromotedNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 36) {
            if (checkSelfPermission("android.permission.POST_PROMOTED_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf("android.permission.POST_PROMOTED_NOTIFICATIONS"), 102)
            }
        }
    }

    private fun openPromotedSettings() {
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                val intent = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback to standard settings
            }
        }
        
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(intent)
        if (Build.VERSION.SDK_INT >= 36) {
            Toast.makeText(this, "Promoted Settings not found, opening Notification Settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateVersionInfo() {
        try {
            val version = packageManager.getPackageInfo(packageName, 0).versionName
            val type = if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) "Debug" else "Release"
            versionText = getString(R.string.app_version_fmt, version, type)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }


    override fun onResume() {
        super.onResume()

        // DIAGNOSTIC: Check actual permission state
        val listenerString = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val isPermissionGranted = listenerString?.contains(packageName) == true
        val serviceConnected = MediaMonitorService.isConnected

        AppLogger.getInstance().log("MainActivity", "=== Service Status ===")
        AppLogger.getInstance().log("MainActivity", "Permission Granted: $isPermissionGranted")
        AppLogger.getInstance().log("MainActivity", "Service Connected: $serviceConnected")
        AppLogger.getInstance().log("MainActivity", "Enabled Listeners: $listenerString")

        // ENHANCED: Auto-Rebind Check with detailed logging
        if (isPermissionGranted && !serviceConnected) {
            AppLogger.getInstance().log("MainActivity", "⚠️ Permission OK but service disconnected - attempting rebind")
            MediaMonitorService.requestRebind(this)

            handler.postDelayed({
                if (!MediaMonitorService.isConnected) {
                    AppLogger.getInstance().log("MainActivity", "⚠️ Rebind slow. Attempting FORCE REBIND (Component Toggle)")
                    MediaMonitorService.forceRebind(this)

                    handler.postDelayed({
                        if (!MediaMonitorService.isConnected) {
                            AppLogger.getInstance().log("MainActivity", "❌ ALL REBIND ATTEMPTS FAILED - Please toggle permission manually")
                        } else {
                            AppLogger.getInstance().log("MainActivity", "✅ Service connected after FORCE REBIND")
                        }
                    }, 1000) // Give force rebind time (1s)
                } else {
                    AppLogger.getInstance().log("MainActivity", "✅ Service connected after first retry")
                }
            }, 500) // Faster fallback to force rebind
        } else if (!isPermissionGranted) {
            AppLogger.getInstance().log("MainActivity", "❌ Notification Listener Permission NOT granted!")
        } else {
            AppLogger.getInstance().log("MainActivity", "✅ Service already connected")
        }

    }


    companion object {
        private const val TAG = "IslandLyrics"
        private const val PREFS_NAME = "IslandLyricsPrefs"
        private var hasCheckedForUpdates = false
    }

    @Composable
    private fun MaterialThemeHost() {
        val prefs = remember { getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
        var followSystem by remember { mutableStateOf(prefs.getBoolean("theme_follow_system", true)) }
        var darkMode by remember { mutableStateOf(prefs.getBoolean("theme_dark_mode", false)) }
        var pureBlack by remember { mutableStateOf(prefs.getBoolean("theme_pure_black", false)) }
        var dynamicColor by remember { mutableStateOf(prefs.getBoolean("theme_dynamic_color", true)) }

        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    "theme_follow_system" -> followSystem = prefs.getBoolean("theme_follow_system", true)
                    "theme_dark_mode" -> darkMode = prefs.getBoolean("theme_dark_mode", false)
                    "theme_pure_black" -> pureBlack = prefs.getBoolean("theme_pure_black", false)
                    "theme_dynamic_color" -> dynamicColor = prefs.getBoolean("theme_dynamic_color", true)
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }

        val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
        val useDarkTheme = if (followSystem) isSystemDark else darkMode

        AppTheme(
            darkTheme = useDarkTheme,
            dynamicColor = dynamicColor,
            pureBlack = pureBlack && useDarkTheme
        ) {
            MaterialTopLevelPager()

            if (updateReleaseInfo != null) {
                UpdateDialog(
                    releaseInfo = updateReleaseInfo!!,
                    onDismiss = { updateReleaseInfo = null },
                    onIgnore = { tag ->
                        UpdateChecker.setIgnoredVersion(this@MainActivity, tag)
                        AppLogger.getInstance().log("Update", "Ignored version: $tag")
                    }
                )
            }
        }
    }

    @Composable
    private fun MaterialTopLevelPager() {
        val pagerState = rememberPagerState(pageCount = { TopLevelDestination.entries.size })
        val scope = rememberCoroutineScope()

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                MaterialTopLevelNavigationBar(
                    currentDestination = TopLevelDestination.entries[pagerState.currentPage],
                    onNavigate = { destination ->
                        scope.launch { pagerState.animateScrollToPage(TopLevelDestination.entries.indexOf(destination)) }
                    }
                )
            }
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) { page ->
                when (TopLevelDestination.entries[page]) {
                    TopLevelDestination.HOME -> MainScreen(
                        versionText = versionText,
                        isDebugBuild = BuildConfig.DEBUG,
                        onOpenSettings = {},
                        onOpenPersonalization = { startActivity(Intent(this@MainActivity, CustomSettingsActivity::class.java)) },
                        onOpenWhitelist = {},
                        onOpenDebug = { openDebugCenter() },
                        onOpenPromotedSettings = { openPromotedSettings() },
                        onStatusCardTap = {
                            MediaMonitorService.requestRebind(this@MainActivity)
                            Toast.makeText(this@MainActivity, "Requesting Rebind...", Toast.LENGTH_SHORT).show()
                        }
                    )

                    TopLevelDestination.PARSER_RULES -> ParserRuleScreen(
                        showBackButton = false
                    )

                    TopLevelDestination.SETTINGS -> SettingsScreen(
                        onCheckUpdate = { performUpdateCheckFromMain() },
                        onShowDiagnostics = { showDiagnosticsFromMain() },
                        updateVersionText = getVersionNameForUi(),
                        updateCodenameText = BuildConfig.VERSION_CODENAME,
                        updateBuildText = BuildConfig.GIT_COMMIT_HASH,
                        onOpenCustomSettings = {
                            startActivity(Intent(this@MainActivity, CustomSettingsActivity::class.java))
                        },
                        showBackButton = false
                    )
                }
            }
        }
    }

    @Composable
    private fun MiuixTopLevelPager() {
        val pagerState = rememberPagerState(pageCount = { TopLevelDestination.entries.size })
        val scope = rememberCoroutineScope()
        var bottomBarVisible by androidx.compose.runtime.remember { mutableStateOf(true) }
        val backdrop = rememberLayerBackdrop()

        androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
            bottomBarVisible = true
        }

        CompositionLocalProvider(
            LocalMiuixBlurBackdrop provides backdrop,
            LocalMiuixBlurEnabled provides true
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (TopLevelDestination.entries[page]) {
                            TopLevelDestination.HOME -> MiuixMainScreen(
                                versionText = versionText,
                                isDebugBuild = BuildConfig.DEBUG,
                                onOpenSettings = {},
                                onOpenPersonalization = { startActivity(Intent(this@MainActivity, CustomSettingsActivity::class.java)) },
                                onOpenWhitelist = {},
                                onOpenDebug = { openDebugCenter() },
                                onOpenPromotedSettings = { openPromotedSettings() },
                                onStatusCardTap = {
                                    MediaMonitorService.requestRebind(this@MainActivity)
                                    Toast.makeText(this@MainActivity, "Requesting Rebind...", Toast.LENGTH_SHORT).show()
                                },
                                onBottomBarVisibilityChange = { bottomBarVisible = it },
                                updateReleaseInfo = updateReleaseInfo,
                                onUpdateDismiss = { updateReleaseInfo = null },
                                onUpdateIgnore = { tag ->
                                    UpdateChecker.setIgnoredVersion(this@MainActivity, tag)
                                    AppLogger.getInstance().log("Update", "Ignored version: $tag")
                                }
                            )

                            TopLevelDestination.PARSER_RULES -> com.example.islandlyrics.feature.parserrule.miuix.MiuixParserRuleScreen(
                                showBackButton = false,
                                onBottomBarVisibilityChange = { bottomBarVisible = it }
                            )

                            TopLevelDestination.SETTINGS -> com.example.islandlyrics.feature.settings.miuix.MiuixSettingsScreen(
                                onCheckUpdate = { performUpdateCheckFromMain() },
                                onShowDiagnostics = { showDiagnosticsFromMain() },
                                updateVersionText = getVersionNameForUi(),
                                updateCodenameText = BuildConfig.VERSION_CODENAME,
                                updateBuildText = BuildConfig.GIT_COMMIT_HASH,
                                onOpenCustomSettings = {
                                    startActivity(Intent(this@MainActivity, CustomSettingsActivity::class.java))
                                },
                                showBackButton = false,
                                onBottomBarVisibilityChange = { bottomBarVisible = it },
                                updateReleaseInfo = updateReleaseInfo,
                                onUpdateDismiss = { updateReleaseInfo = null },
                                onUpdateIgnore = { tag ->
                                    UpdateChecker.setIgnoredVersion(this@MainActivity, tag)
                                    AppLogger.getInstance().log("Update", "Ignored version: $tag")
                                }
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = bottomBarVisible,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp)
                ) {
                    MiuixTopLevelFloatingNavigationBar(
                        currentDestination = TopLevelDestination.entries[pagerState.currentPage],
                        onNavigate = { destination ->
                            scope.launch { pagerState.animateScrollToPage(TopLevelDestination.entries.indexOf(destination)) }
                        }
                    )
                }
            }
        }
    }

    private fun openDebugCenter() {
        try {
            val clazz = Class.forName("com.example.islandlyrics.DebugCenterActivity")
            startActivity(Intent(this@MainActivity, clazz))
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "Debug Activity not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getVersionNameForUi(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    private fun performUpdateCheckFromMain() {
        if (OfflineModeManager.isEnabled(this)) {
            Toast.makeText(this, R.string.offline_mode_network_blocked, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val release = UpdateChecker.checkForUpdate(this@MainActivity)
                if (release != null) {
                    updateReleaseInfo = release
                    AppLogger.getInstance().log("MainActivity", "Update found from settings page: ${release.tagName}")
                } else {
                    Toast.makeText(this@MainActivity, R.string.update_no_update, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.update_check_failed, Toast.LENGTH_SHORT).show()
                AppLogger.getInstance().log("MainActivity", "Settings-page update check failed: ${e.message}")
            }
        }
    }

    private fun showDiagnosticsFromMain() {
        startActivity(Intent(this, com.example.islandlyrics.feature.diagnostics.DiagnosticsActivity::class.java))
    }
}
