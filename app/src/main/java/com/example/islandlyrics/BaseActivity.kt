package com.example.islandlyrics

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Base Activity to handle common UI logic like Pure Black mode.
 */
open class BaseActivity : AppCompatActivity() {

    private var currentDynamicColorEnabled: Boolean = false
    private var currentPureBlackEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Track initial state
        currentDynamicColorEnabled = ThemeHelper.isDynamicColorEnabled(this)
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

    override fun onResume() {
        super.onResume()
        
        // Check for changes that require recreation (Dynamic Color)
        val newDynamicColor = ThemeHelper.isDynamicColorEnabled(this)
        if (newDynamicColor != currentDynamicColorEnabled) {
            // Log for debugging (if possible)
            recreate()
            return
        }

        // Check for Pure Black changes (can be applied without recreate)
        updatePureBlackMode()
    }

    private fun updatePureBlackMode() {
        val isPureBlack = ThemeHelper.isPureBlackEnabled(this)
        
        // Check if we are physically in dark mode
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        
        if (isPureBlack && isDarkMode) {
            window.decorView.setBackgroundColor(Color.BLACK)
            window.setBackgroundDrawableResource(android.R.color.black)
        } else {
            // Revert to default theme background (using colorSurface to match default layout look)
            val typedValue = android.util.TypedValue()
            // Try colorSurface first, fallback to windowBackground
            val attr = com.google.android.material.R.attr.colorSurface
            if (theme.resolveAttribute(attr, typedValue, true)) {
                if (typedValue.resourceId != 0) {
                   window.setBackgroundDrawableResource(typedValue.resourceId)
                } else {
                   window.decorView.setBackgroundColor(typedValue.data)
                }
            } else {
                // Fallback to windowBackground
                theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true)
                if (typedValue.resourceId != 0) {
                    window.setBackgroundDrawableResource(typedValue.resourceId)
                } else {
                    window.decorView.setBackgroundColor(typedValue.data)
                }
            }
        }
    }
}
