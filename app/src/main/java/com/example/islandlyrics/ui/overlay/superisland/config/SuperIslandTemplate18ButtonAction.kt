package com.example.islandlyrics.ui.overlay.superisland.config

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences

internal enum class SuperIslandTemplate18ButtonAction(val preferenceValue: String) {
    PLAY_PAUSE("play_pause"),
    PREVIOUS("previous"),
    NEXT("next"),
    CONTROLS("controls");

    val titleRes: Int
        get() = when (this) {
            PLAY_PAUSE -> R.string.super_island_template18_button_play_pause
            PREVIOUS -> R.string.super_island_template18_button_previous
            NEXT -> R.string.super_island_template18_button_next
            CONTROLS -> R.string.super_island_template18_button_controls
        }

    companion object {
        const val PREF_KEY = AppPreferences.Keys.SUPER_ISLAND_TEMPLATE18_BUTTON_ACTION
        val DEFAULT = PLAY_PAUSE

        fun from(value: String?): SuperIslandTemplate18ButtonAction =
            entries.firstOrNull { it.preferenceValue == value } ?: DEFAULT

        fun read(prefs: SharedPreferences): SuperIslandTemplate18ButtonAction =
            from(prefs.getString(PREF_KEY, null))

        fun write(prefs: SharedPreferences, action: SuperIslandTemplate18ButtonAction) {
            prefs.edit { putString(PREF_KEY, action.preferenceValue) }
        }
    }
}
