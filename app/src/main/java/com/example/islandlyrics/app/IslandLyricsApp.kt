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
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandPreferenceMigration
import com.google.android.material.color.DynamicColors
import org.lsposed.hiddenapibypass.HiddenApiBypass

class IslandLyricsApp : Application() {
    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("")
            } catch (t: Throwable) {
                // Android 17 (API 37) may further restrict hidden API access;
                // log and continue — Shizuku/FirewallCompat paths have their own fallbacks.
                android.util.Log.w("IslandLyricsApp", "HiddenApiBypass failed, some reflective paths may be unavailable", t)
            }
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
        // 旧版本「通知按键」→ 新「播放按键布局 + 显示进度条」偏好迁移
        SuperIslandPreferenceMigration.migrate(prefs)
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
            // UI hidden (app went to background): clear image memory cache
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                AppLogger.getInstance().log(TAG, "onTrimMemory: UI_HIDDEN — clearing Coil memory")
                AppImageCacheManager.getImageLoader(this).memoryCache?.clear()
            }
            // Background: aggressive cleanup
            // (Foreground trim levels RUNNING_*, MODERATE and COMPLETE were deprecated in API 35
            //  and are no longer delivered since API 34; minSdk is 35, so they were removed.)
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                AppLogger.getInstance().log(TAG, "onTrimMemory: BACKGROUND ($level) — full cleanup")
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
