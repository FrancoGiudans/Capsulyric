/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Build
import com.example.islandlyrics.core.cache.AppImageCacheManager
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.rules.ParserRuleHelper
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

        // Initialise fair memory mechanism (HyperOS/ColorOS/OriginOS/MagicOS, Android 16+)
        com.example.islandlyrics.runtime.memory.FairMemoryManager.getInstance().initialize(this)

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

    // ── Universal Android memory pressure callback ──────────────────────────
    // Complements FairMemoryManager (OEM-specific, Android 16+) with standard
    // onTrimMemory that works on ALL Android versions and ALL devices.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            // Foreground — moderate pressure: light cleanup
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                AppLogger.getInstance().log(TAG, "onTrimMemory: RUNNING_MODERATE — clearing Coil memory")
                AppImageCacheManager.getImageLoader(this).memoryCache?.clear()
            }
            // Foreground — low memory: clear image cache + parser cache
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                AppLogger.getInstance().log(TAG, "onTrimMemory: RUNNING_LOW — clearing caches")
                releaseMemoryCaches()
            }
            // Foreground — critical: aggressive cleanup + backup state
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                AppLogger.getInstance().log(TAG, "onTrimMemory: RUNNING_CRITICAL — backing up state")
                releaseMemoryCaches()
                Runtime.getRuntime().gc()
                com.example.islandlyrics.runtime.memory.FairMemoryManager.getInstance().backupState(this)
            }
            // UI hidden (app went to background): clear image memory cache
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                AppLogger.getInstance().log(TAG, "onTrimMemory: UI_HIDDEN — clearing Coil memory")
                AppImageCacheManager.getImageLoader(this).memoryCache?.clear()
            }
            // Background levels: aggressive cleanup
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                AppLogger.getInstance().log(TAG, "onTrimMemory: BACKGROUND/MODERATE/COMPLETE ($level) — full cleanup")
                releaseMemoryCaches()
                Runtime.getRuntime().gc()
            }
        }
    }

    /**
     * Release non-essential memory caches.
     * Safe to call at any time; shared by onTrimMemory and FairMemoryManager.
     */
    private fun releaseMemoryCaches() {
        AppImageCacheManager.getImageLoader(this).memoryCache?.clear()
        ParserRuleHelper.invalidateCache()
    }

    companion object {
        private const val TAG = "IslandLyricsApp"
    }
}
