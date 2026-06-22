package com.example.islandlyrics.ui.overlay.config
import android.content.Context
import android.content.SharedPreferences
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.core.settings.AppPreferences

enum class CapsuleRenderMode(val prefValue: String) {
    LIVE_UPDATE("live_update"),
    XIAOMI_SUPER_ISLAND("xiaomi_super_island");

    companion object {
        const val PREF_KEY = "capsule_render_mode"

        fun read(context: Context): CapsuleRenderMode =
            read(AppPreferences.of(context))

        fun read(prefs: SharedPreferences): CapsuleRenderMode {
            val stored = prefs.getString(PREF_KEY, null)
            if (stored != null) {
                return entries.firstOrNull { it.prefValue == stored } ?: LIVE_UPDATE
            }
            return if (prefs.getBoolean(AppPreferences.Keys.SUPER_ISLAND_ENABLED_LEGACY, false)) {
                XIAOMI_SUPER_ISLAND
            } else {
                LIVE_UPDATE
            }
        }

        fun effective(context: Context): CapsuleRenderMode =
            effective(AppPreferences.of(context))

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
                .putBoolean(AppPreferences.Keys.SUPER_ISLAND_ENABLED_LEGACY, mode == XIAOMI_SUPER_ISLAND)
                .apply()
        }

        fun isSuperIslandActive(prefs: SharedPreferences): Boolean =
            effective(prefs) == XIAOMI_SUPER_ISLAND
    }
}
