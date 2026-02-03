package com.example.islandlyrics

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Base Activity to handle common UI logic like Pure Black mode.
 */
open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enforce Edge-to-Edge globally to prevent layout jumps/trembling during state changes
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // Apply Pure Black Mode
        updatePureBlackMode()
    }

    override fun onResume() {
        super.onResume()
        // Re-check Pure Black mode on resume (e.g. returning from Settings)
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
            // Revert to default theme background
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true)
            if (typedValue.resourceId != 0) {
                window.setBackgroundDrawableResource(typedValue.resourceId)
            } else {
                window.decorView.setBackgroundColor(typedValue.data)
            }
        }
    }
}
