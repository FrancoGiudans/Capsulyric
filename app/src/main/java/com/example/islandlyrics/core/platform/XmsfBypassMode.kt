package com.example.islandlyrics.core.platform

import android.content.SharedPreferences

enum class XmsfBypassMode(val value: String) {
    DISABLED("disabled"),
    STANDARD("standard"),
    AGGRESSIVE("aggressive");

    companion object {
        private const val KEY_MODE = "block_xmsf_network_mode"
        private const val KEY_LEGACY = "block_xmsf_network"

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
    }

    val isEnabled: Boolean
        get() = this != DISABLED
}
