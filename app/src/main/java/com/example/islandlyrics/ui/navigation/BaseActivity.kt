package com.example.islandlyrics.ui.navigation

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.animation.PathInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.runtime.service.MediaMonitorService
import android.app.ActivityManager

open class BaseActivity : AppCompatActivity() {

    companion object {
        /** Tracks the number of started (visible) activities across the app. */
        private var startedActivityCount = 0
        private const val PAGE_UNDERLAY_SHIFT_FRACTION = -0.05f
        private const val PAGE_UNDERLAY_SCALE = 0.97f
        private const val PAGE_UNDERLAY_ALPHA = 0.97f
        private const val PAGE_UNDERLAY_OPEN_DURATION_MS = 430L
        private const val PAGE_UNDERLAY_CLOSE_DURATION_MS = 360L
    }

    private var currentPureBlackEnabled: Boolean = false
    private var pageUnderlayTransformActive: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Track initial state
        currentPureBlackEnabled = ThemeHelper.isPureBlackEnabled(this)

        // Enforce Edge-to-Edge globally to prevent layout jumps/trembling during state changes
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // Apply Pure Black Mode
        updatePureBlackMode()
        applyOpenTransition()
    }

    override fun finish() {
        super.finish()
        applyCloseTransition()
    }

    override fun startActivity(intent: Intent) {
        val style = activityTransitionStyleForLaunch(intent)
        super.startActivity(intent)
        animatePageUnderlayIfNeeded(style)
        applyOpenTransition(style)
    }

    override fun startActivity(intent: Intent, options: Bundle?) {
        val style = activityTransitionStyleForLaunch(intent)
        super.startActivity(intent, options)
        animatePageUnderlayIfNeeded(style)
        applyOpenTransition(style)
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

            val prefs = AppPreferences.of(this)
            if (prefs.getBoolean(AppPreferences.Keys.HIDE_RECENTS_ENABLED, false)) {
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                am.appTasks.firstOrNull()?.setExcludeFromRecents(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Check for Pure Black changes (can be applied without recreate)
        updatePureBlackMode()
        restorePageUnderlayTransform()
    }

    override fun onStop() {
        super.onStop()
        startedActivityCount--
        // When ALL activities are stopped, the app is truly in the background
        if (startedActivityCount == 0) {
            val prefs = AppPreferences.of(this)
            if (prefs.getBoolean(AppPreferences.Keys.HIDE_RECENTS_ENABLED, false)) {
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                am.appTasks.firstOrNull()?.setExcludeFromRecents(true)
            }
        }
    }

    private fun updatePureBlackMode() {
        if (isWindowTranslucent()) {
            window.decorView.setBackgroundColor(Color.TRANSPARENT)
            return
        }

        val isPureBlack = ThemeHelper.isPureBlackEnabled(this)

        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES

        if (isPureBlack && isDarkMode) {
            window.decorView.setBackgroundColor(Color.BLACK)
        }
    }

    private fun isWindowTranslucent(): Boolean {
        val typedArray = obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent))
        return try {
            typedArray.getBoolean(0, false)
        } finally {
            typedArray.recycle()
        }
    }

    protected open fun activityTransitionStyle(): ActivityTransitionStyle {
        return if (isWindowTranslucent()) ActivityTransitionStyle.Page else ActivityTransitionStyle.None
    }

    private fun applyOpenTransition(style: ActivityTransitionStyle = activityTransitionStyle()) {
        when (style) {
            ActivityTransitionStyle.None -> Unit
            ActivityTransitionStyle.Page -> overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.page_open_enter,
                R.anim.page_open_exit
            )
            ActivityTransitionStyle.OverlaySheet -> overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.overlay_sheet_open_enter,
                R.anim.overlay_sheet_open_exit
            )
        }
    }

    private fun applyCloseTransition(style: ActivityTransitionStyle = activityTransitionStyle()) {
        when (style) {
            ActivityTransitionStyle.None -> Unit
            ActivityTransitionStyle.Page -> overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.page_close_enter,
                R.anim.page_close_exit
            )
            ActivityTransitionStyle.OverlaySheet -> overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.overlay_sheet_close_enter,
                R.anim.overlay_sheet_close_exit
            )
        }
    }

    private fun animatePageUnderlayIfNeeded(style: ActivityTransitionStyle) {
        if (style != ActivityTransitionStyle.Page) return
        val view = window.decorView
        val shift = view.width * PAGE_UNDERLAY_SHIFT_FRACTION
        view.animate().cancel()
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        view.animate()
            .translationX(shift)
            .scaleX(PAGE_UNDERLAY_SCALE)
            .scaleY(PAGE_UNDERLAY_SCALE)
            .alpha(PAGE_UNDERLAY_ALPHA)
            .setDuration(PAGE_UNDERLAY_OPEN_DURATION_MS)
            .setInterpolator(pageTransitionInterpolator())
            .start()
        pageUnderlayTransformActive = true
    }

    private fun restorePageUnderlayTransform() {
        if (!pageUnderlayTransformActive) return
        val view = window.decorView
        view.animate().cancel()
        view.animate()
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(PAGE_UNDERLAY_CLOSE_DURATION_MS)
            .setInterpolator(pageTransitionInterpolator())
            .withEndAction {
                pageUnderlayTransformActive = false
            }
            .start()
    }

    private fun pageTransitionInterpolator() = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    private fun activityTransitionStyleForLaunch(intent: Intent): ActivityTransitionStyle {
        val component = intent.component ?: return ActivityTransitionStyle.None
        val className = component.className
        if (component.packageName != packageName) return ActivityTransitionStyle.None

        return when (className) {
            "com.example.islandlyrics.feature.parserrule.ParserRuleEditorActivity" -> ActivityTransitionStyle.OverlaySheet
            "com.example.islandlyrics.feature.main.MainActivity",
            "com.example.islandlyrics.feature.oobe.OobeActivity",
            "com.example.islandlyrics.feature.mediacontrol.MediaControlActivity",
            "com.example.islandlyrics.DebugCenterActivity",
            "com.example.islandlyrics.DebugLyricActivity",
            "com.example.islandlyrics.feature.qqroman.QqRomanDebugActivity" -> ActivityTransitionStyle.None
            else -> ActivityTransitionStyle.Page
        }
    }
}

enum class ActivityTransitionStyle {
    None,
    Page,
    OverlaySheet
}
