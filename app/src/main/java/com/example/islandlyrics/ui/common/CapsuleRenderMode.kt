package com.example.islandlyrics.ui.common

import android.content.Context
import android.content.SharedPreferences
import com.example.islandlyrics.core.platform.RomUtils

enum class CapsuleRenderMode(val prefValue: String) {
    LIVE_UPDATE("live_update"),
    XIAOMI_SUPER_ISLAND("xiaomi_super_island"),
    COLOROS_FLUID_CLOUD("coloros_fluid_cloud");

    companion object {
        const val PREF_KEY = "capsule_render_mode"
        private const val LEGACY_SUPER_ISLAND_KEY = "super_island_enabled"

        fun read(context: Context): CapsuleRenderMode =
            read(context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE))

        fun read(prefs: SharedPreferences): CapsuleRenderMode {
            val stored = prefs.getString(PREF_KEY, null)
            if (stored != null) {
                return entries.firstOrNull { it.prefValue == stored } ?: LIVE_UPDATE
            }
            return if (prefs.getBoolean(LEGACY_SUPER_ISLAND_KEY, false)) {
                XIAOMI_SUPER_ISLAND
            } else {
                LIVE_UPDATE
            }
        }

        fun effective(context: Context): CapsuleRenderMode =
            effective(context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE))

        fun effective(prefs: SharedPreferences): CapsuleRenderMode {
            val selected = read(prefs)
            if (!RomUtils.isLiveUpdateSupported() && selected == LIVE_UPDATE) {
                return XIAOMI_SUPER_ISLAND
            }
            return selected
        }

        fun write(prefs: SharedPreferences, mode: CapsuleRenderMode) {
            prefs.edit()
                .putString(PREF_KEY, mode.prefValue)
                .putBoolean(LEGACY_SUPER_ISLAND_KEY, mode == XIAOMI_SUPER_ISLAND)
                .apply()
        }

        fun isSuperIslandActive(prefs: SharedPreferences): Boolean =
            effective(prefs) == XIAOMI_SUPER_ISLAND
    }
}
