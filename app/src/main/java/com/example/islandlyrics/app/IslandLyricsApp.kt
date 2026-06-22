package com.example.islandlyrics.app

import android.app.Application
import android.os.Build
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.core.platform.RomUtils
import com.google.android.material.color.DynamicColors
import org.lsposed.hiddenapibypass.HiddenApiBypass

class IslandLyricsApp : Application() {
    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }

        // Initialise unified logger so all AppLogger calls are persisted to file
        AppLogger.getInstance().init(this)
        
        // Initialise repository state
        com.example.islandlyrics.lyrics.state.LyricRepository.getInstance().init(this)

        // Apply saved theme preferences (Mode, Language)
        ThemeHelper.applyTheme(this)
        LabFeatureManager.ensureInitialized(this)
        
        // Debug override
        val prefs = AppPreferences.of(this)
        val forcedType = prefs.getString(AppPreferences.Keys.DEBUG_FORCED_ROM_TYPE, null)
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
