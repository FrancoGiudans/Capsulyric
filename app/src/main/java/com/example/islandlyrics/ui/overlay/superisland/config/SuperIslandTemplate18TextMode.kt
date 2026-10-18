package com.example.islandlyrics.ui.overlay.superisland.config

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences

/** Candidate sources for the two secondary rows in the native cover-lyrics template. */
internal enum class SuperIslandTemplate18TextMode(val preferenceValue: String) {
    TRANSLATION("translation"),
    ROMANIZATION("romanization"),
    NEXT_LYRIC("next_lyric"),
    NEXT_NEXT_LYRIC("next_next_lyric"),
    PLAYING_INFO("playing_info");

    val titleRes: Int
        get() = when (this) {
            TRANSLATION -> R.string.super_island_template18_text_translation
            ROMANIZATION -> R.string.super_island_template18_text_romanization
            NEXT_LYRIC -> R.string.super_island_template18_text_next_lyric
            NEXT_NEXT_LYRIC -> R.string.super_island_template18_text_next_next_lyric
            PLAYING_INFO -> R.string.super_island_template18_text_playing_info
        }

    companion object {
        const val PREF_KEY = AppPreferences.Keys.SUPER_ISLAND_TEMPLATE18_TEXT_MODES
        const val MIN_SELECTED = 2
        val DEFAULT = listOf(TRANSLATION, NEXT_LYRIC, PLAYING_INFO)

        fun from(value: String?): SuperIslandTemplate18TextMode? =
            entries.firstOrNull { it.preferenceValue == value }

        fun read(prefs: SharedPreferences): List<SuperIslandTemplate18TextMode> {
            val raw = prefs.getString(PREF_KEY, null)?.takeIf { it.isNotBlank() }
                ?: return DEFAULT
            val modes = LinkedHashSet<SuperIslandTemplate18TextMode>()
            raw.split(',').forEach { token -> from(token.trim())?.let(modes::add) }
            return modes.toList().takeIf { it.size >= MIN_SELECTED } ?: DEFAULT
        }

        fun write(prefs: SharedPreferences, modes: List<SuperIslandTemplate18TextMode>) {
            val normalized = LinkedHashSet(modes).toList()
                .takeIf { it.size >= MIN_SELECTED }
                ?: DEFAULT
            prefs.edit { putString(PREF_KEY, normalized.joinToString(",") { it.preferenceValue }) }
        }
    }
}
