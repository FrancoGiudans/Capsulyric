package com.example.islandlyrics.core.platform

import android.content.SharedPreferences

enum class XmsfBypassMode(val value: String) {
    DISABLED("disabled"),
    STANDARD("standard"),
    CUSTOM("custom"),
    AGGRESSIVE("aggressive");

    companion object {
        private const val KEY_MODE = "block_xmsf_network_mode"
        private const val KEY_LEGACY = "block_xmsf_network"
        private const val KEY_CUSTOM_DURATION_MS = "block_xmsf_network_custom_duration_ms"

        const val STANDARD_DURATION_MS = 100
        const val MIN_CUSTOM_DURATION_MS = 100
        const val MAX_CUSTOM_DURATION_MS = 500
        const val CUSTOM_DURATION_STEP_MS = 50
        const val DEFAULT_CUSTOM_DURATION_MS = STANDARD_DURATION_MS

        fun fromValue(value: String?): XmsfBypassMode {
            return entries.firstOrNull { it.value == value } ?: DISABLED
        }

        fun read(prefs: SharedPreferences): XmsfBypassMode {
            val modeValue = prefs.getString(KEY_MODE, null)
            if (modeValue != null) {
                return fromValue(modeValue)
            }
            return if (prefs.getBoolean(KEY_LEGACY, false)) STANDARD else DISABLED
        }

        fun write(prefs: SharedPreferences, mode: XmsfBypassMode) {
            prefs.edit()
                .putString(KEY_MODE, mode.value)
                .putBoolean(KEY_LEGACY, mode != DISABLED)
                .apply()
        }

        fun readCustomDurationMs(prefs: SharedPreferences): Int {
            return prefs
                .getInt(KEY_CUSTOM_DURATION_MS, DEFAULT_CUSTOM_DURATION_MS)
                .coerceIn(MIN_CUSTOM_DURATION_MS, MAX_CUSTOM_DURATION_MS)
        }

        fun writeCustomDurationMs(prefs: SharedPreferences, durationMs: Int) {
            prefs.edit()
                .putInt(KEY_CUSTOM_DURATION_MS, durationMs.coerceIn(MIN_CUSTOM_DURATION_MS, MAX_CUSTOM_DURATION_MS))
                .apply()
        }
    }

    val isEnabled: Boolean
        get() = this != DISABLED
}
