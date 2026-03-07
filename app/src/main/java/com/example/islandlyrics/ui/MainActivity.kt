package com.example.islandlyrics.ui

import android.content.BroadcastReceiver
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.utils.UpdateChecker
import com.example.islandlyrics.utils.AppLogger
import com.example.islandlyrics.service.MediaMonitorService
import com.example.islandlyrics.ui.miuix.MiuixUpdateDialog
import com.example.islandlyrics.ui.miuix.MiuixMainScreen
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.material.UpdateDialog
import com.example.islandlyrics.ui.material.MainScreen
import com.example.islandlyrics.ui.material.AppTheme
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private var versionText by mutableStateOf("...")
    private var updateReleaseInfo by mutableStateOf<UpdateChecker.ReleaseInfo?>(null)

    private val diagReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Placeholder for future diagnostics
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OOBE Check
        val prefs = getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_setup_complete", false)) {
            startActivity(Intent(this, com.example.islandlyrics.ui.OobeActivity::class.java))
            finish()
            return
        }

        updateVersionInfo()

        // Auto-check for updates on startup
        if (UpdateChecker.isAutoUpdateEnabled(this) && !hasCheckedForUpdates) {
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
                    MiuixMainScreen(
                        versionText = versionText,
                        isDebugBuild = BuildConfig.DEBUG,
                        onOpenSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                        onOpenPersonalization = { startActivity(Intent(this@MainActivity, CustomSettingsActivity::class.java)) },
                        onOpenWhitelist = { startActivity(Intent(this@MainActivity, ParserRuleActivity::class.java)) },
                        onOpenDebug = {
                            try {
                                val clazz = Class.forName("com.example.islandlyrics.DebugCenterActivity")
                                startActivity(Intent(this@MainActivity, clazz))
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Debug Activity not found", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenPromotedSettings = { openPromotedSettings() },
                        onStatusCardTap = {
                            MediaMonitorService.requestRebind(this@MainActivity)
                            Toast.makeText(this@MainActivity, "Requesting Rebind...", Toast.LENGTH_SHORT).show()
                        }
                    )
                    
                    if (updateReleaseInfo != null) {
                        val showDialog = androidx.compose.runtime.remember(updateReleaseInfo) { androidx.compose.runtime.mutableStateOf(true) }
                        if (showDialog.value) {
                            MiuixUpdateDialog(
                                show = showDialog,
                                releaseInfo = updateReleaseInfo!!,
                                onDismiss = { updateReleaseInfo = null },
                                onIgnore = { tag ->
                                    UpdateChecker.setIgnoredVersion(this@MainActivity, tag)
                                    AppLogger.getInstance().log("Update", "Ignored version: $tag")
                                }
                            )
                        } else {
                            updateReleaseInfo = null
                        }
                    }
                }
            } else {
                AppTheme {
                    MainScreen(
                        versionText = versionText,
                        isDebugBuild = BuildConfig.DEBUG,
                        onOpenSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                        onOpenPersonalization = { startActivity(Intent(this@MainActivity, CustomSettingsActivity::class.java)) },
                        onOpenWhitelist = { startActivity(Intent(this@MainActivity, ParserRuleActivity::class.java)) },
                        onOpenDebug = {
                            try {
                                val clazz = Class.forName("com.example.islandlyrics.DebugCenterActivity")
                                startActivity(Intent(this@MainActivity, clazz))
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Debug Activity not found", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenPromotedSettings = { openPromotedSettings() },
                        onStatusCardTap = {
                            MediaMonitorService.requestRebind(this@MainActivity)
                            Toast.makeText(this@MainActivity, "Requesting Rebind...", Toast.LENGTH_SHORT).show()
                        }
                    )
                    
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
        }
    }

    // Standard Notification Permission Check
    private fun checkPromotedNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 102)
            }
        }
    }

    private fun openPromotedSettings() {
        try {
            val intent = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
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

    // Check API Status
    private fun checkApiStatusForDashboard() {
        // Disabled for Android 15
    }

    override fun onResume() {
        super.onResume()
        checkApiStatusForDashboard()

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
                    AppLogger.getInstance().log("MainActivity", "⚠️ Standard rebind failed. Attempting FORCE REBIND (Component Toggle)")
                    MediaMonitorService.forceRebind(this)

                    handler.postDelayed({
                        if (!MediaMonitorService.isConnected) {
                            AppLogger.getInstance().log("MainActivity", "❌ ALL REBIND ATTEMPTS FAILED - Please toggle permission manually")
                        } else {
                            AppLogger.getInstance().log("MainActivity", "✅ Service connected after FORCE REBIND")
                        }
                    }, 2000) // Give force rebind more time (2s)
                } else {
                    AppLogger.getInstance().log("MainActivity", "✅ Service connected after first retry")
                }
            }, 1000) // Increase first wait to 1s to be safe
        } else if (!isPermissionGranted) {
            AppLogger.getInstance().log("MainActivity", "❌ Notification Listener Permission NOT granted!")
        } else {
            AppLogger.getInstance().log("MainActivity", "✅ Service already connected")
        }

        checkApiStatusForDashboard()

        // Register Diag Receiver
        val filter = IntentFilter()
        filter.addAction("com.example.islandlyrics.DIAG_UPDATE")
        filter.addAction("com.example.islandlyrics.STATUS_UPDATE")

        registerReceiver(diagReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(diagReceiver)
        } catch (e: Exception) {
        }
    }

    companion object {
        private const val TAG = "IslandLyrics"
        private const val PREFS_NAME = "IslandLyricsPrefs"
        private var hasCheckedForUpdates = false
    }
}
