package com.example.islandlyrics

import android.content.BroadcastReceiver
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

class MainActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())

    // Compose state for API dashboard (driven by BroadcastReceiver + reflection checks)
    private var apiPermissionText by mutableStateOf("Permission: Checking...")
    private var apiCapabilityText by mutableStateOf("Notif.hasPromotable: Waiting...")
    private var apiFlagText by mutableStateOf("Flag PROMOTED_ONGOING: Waiting...")
    private var apiPermissionActive by mutableStateOf(false)
    private var apiCapabilityActive by mutableStateOf(false)
    private var apiFlagActive by mutableStateOf(false)
    private var showApiCard by mutableStateOf(false)
    private var versionText by mutableStateOf("...")

    private val diagReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if ("com.example.islandlyrics.STATUS_UPDATE" == intent.action) {
                if (intent.hasExtra("hasPromotable")) {
                    val hasPromotable = intent.getBooleanExtra("hasPromotable", false)
                    apiCapabilityText = "Notif.hasPromotable: $hasPromotable"
                    apiCapabilityActive = hasPromotable
                }
                if (intent.hasExtra("isPromoted")) {
                    val isPromoted = intent.getBooleanExtra("isPromoted", false)
                    apiFlagText = "Flag PROMOTED_ONGOING: $isPromoted"
                    apiFlagActive = isPromoted
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OOBE Check
        val prefs = getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_setup_complete", false)) {
            startActivity(Intent(this, com.example.islandlyrics.oobe.OobeActivity::class.java))
            finish()
            return
        }

        updateVersionInfo()

        setContent {
            AppTheme {
                MainScreen(
                    versionText = versionText,
                    isDebugBuild = BuildConfig.DEBUG,
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onOpenDebug = {
                        try {
                            val clazz = Class.forName("com.example.islandlyrics.DebugCenterActivity")
                            startActivity(Intent(this, clazz))
                        } catch (e: Exception) {
                            Toast.makeText(this, "Debug Activity not found", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenPromotedSettings = { openPromotedSettings() },
                    onStatusCardTap = {
                        MediaMonitorService.requestRebind(this)
                        Toast.makeText(this, "Requesting Rebind...", Toast.LENGTH_SHORT).show()
                    },
                    apiPermissionText = apiPermissionText,
                    apiCapabilityText = apiCapabilityText,
                    apiFlagText = apiFlagText,
                    apiPermissionActive = apiPermissionActive,
                    apiCapabilityActive = apiCapabilityActive,
                    apiFlagActive = apiFlagActive,
                    showApiCard = showApiCard,
                )
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

    // Check API Status (Reflection)
    private fun checkApiStatusForDashboard() {
        if (!BuildConfig.DEBUG) {
            showApiCard = false
            return
        }
        showApiCard = true

        if (Build.VERSION.SDK_INT < 36) {
            apiPermissionText = "Permission: N/A (Pre-API 36)"
            apiCapabilityText = "Notif.hasPromotable: N/A"
            apiFlagText = "Flag PROMOTED: N/A"
            return
        }

        try {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            val m = nm.javaClass.getMethod("canPostPromotedNotifications")
            val granted = m.invoke(nm) as Boolean

            if (granted) {
                apiPermissionText = "Permission (canPost): GRANTED ✅"
                apiPermissionActive = true
            } else {
                apiPermissionText = "Permission (canPost): DENIED ❌"
                apiPermissionActive = false
            }
        } catch (e: Exception) {
            apiPermissionText = "Permission Check Failed: ${e.message}"
            apiPermissionActive = false
        }
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
                    AppLogger.getInstance().log("MainActivity", "⚠️ Second rebind attempt (still disconnected)")
                    MediaMonitorService.requestRebind(this)

                    handler.postDelayed({
                        if (!MediaMonitorService.isConnected) {
                            AppLogger.getInstance().log("MainActivity", "❌ REBIND FAILED - Manual intervention may be needed")
                        } else {
                            AppLogger.getInstance().log("MainActivity", "✅ Service connected after retry")
                        }
                    }, 1000)
                } else {
                    AppLogger.getInstance().log("MainActivity", "✅ Service connected after first retry")
                }
            }, 500)
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
    }
}
