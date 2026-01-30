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

        // Handle Pure Black Mode for OLED
        if (ThemeHelper.isPureBlackEnabled(this)) {
            // Check if we are physically in dark mode
            val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                window.decorView.setBackgroundColor(Color.BLACK)
                // Also could try forcing window background drawable to null or black color drawable
                window.setBackgroundDrawableResource(android.R.color.black)
            }
        }
    }
}
