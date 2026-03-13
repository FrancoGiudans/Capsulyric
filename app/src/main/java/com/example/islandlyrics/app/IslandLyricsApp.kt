package com.example.islandlyrics.app

import android.app.Application
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.core.platform.RomUtils
import com.google.android.material.color.DynamicColors

class IslandLyricsApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Apply saved theme preferences (Mode, Language)
        ThemeHelper.applyTheme(this)
        
        // Debug override
        val prefs = getSharedPreferences("IslandLyricsPrefs", android.content.Context.MODE_PRIVATE)
        val forcedType = prefs.getString("debug_forced_rom_type", null)
        if (!forcedType.isNullOrEmpty()) {
            RomUtils.forcedRomType = forcedType
        }

        // Recommended way to handle Dynamic Color toggle for XML activities
        com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(this, 
            com.google.android.material.color.DynamicColorsOptions.Builder()
                .setPrecondition { _, _ -> ThemeHelper.isDynamicColorEnabled(this) }
                .build()
        )
    }
}
