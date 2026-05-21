package com.example.islandlyrics.ui.common

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.service.MediaMonitorService
import android.app.ActivityManager

open class BaseActivity : AppCompatActivity() {

    companion object {
        /** Tracks the number of started (visible) activities across the app. */
        private var startedActivityCount = 0
    }

    private var currentPureBlackEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Track initial state
        currentPureBlackEnabled = ThemeHelper.isPureBlackEnabled(this)

        // Enforce Edge-to-Edge globally to prevent layout jumps/trembling during state changes
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // Apply Pure Black Mode
        updatePureBlackMode()
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        updatePureBlackMode()
    }

    override fun setContentView(view: android.view.View?) {
        super.setContentView(view)
        updatePureBlackMode()
    }

    override fun onStart() {
        super.onStart()
        startedActivityCount++
        // When app comes to foreground, re-include in recents
        if (startedActivityCount == 1) {
            // App is entering foreground: proactively request notification listener rebind
            MediaMonitorService.markForeground()
            MediaMonitorService.requestRebind(this)

            val prefs = getSharedPreferences("IslandLyricsPrefs", MODE_PRIVATE)
            if (prefs.getBoolean("hide_recents_enabled", false)) {
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                am.appTasks.firstOrNull()?.setExcludeFromRecents(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Check for Pure Black changes (can be applied without recreate)
        updatePureBlackMode()
    }

    override fun onStop() {
        super.onStop()
        startedActivityCount--
        // When ALL activities are stopped, the app is truly in the background
        if (startedActivityCount == 0) {
            val prefs = getSharedPreferences("IslandLyricsPrefs", MODE_PRIVATE)
            if (prefs.getBoolean("hide_recents_enabled", false)) {
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                am.appTasks.firstOrNull()?.setExcludeFromRecents(true)
            }
        }
    }

    private fun updatePureBlackMode() {
        val isPureBlack = ThemeHelper.isPureBlackEnabled(this)

        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES

        if (isPureBlack && isDarkMode) {
            window.decorView.setBackgroundColor(Color.BLACK)
        }
    }
}
