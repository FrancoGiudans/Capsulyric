package com.example.islandlyrics.ui.common

import android.content.SharedPreferences
import com.example.islandlyrics.data.LyricRepository

object LyricTextDisplayMode {
    const val PREF_KEY = "lyric_text_display_mode"

    const val LYRIC = "lyric"
    const val TRANSLATION = "translation"
    const val ROMANIZATION = "romanization"

    val values = listOf(LYRIC, TRANSLATION, ROMANIZATION)

    fun read(prefs: SharedPreferences): String {
        val stored = prefs.getString(PREF_KEY, LYRIC) ?: LYRIC
        return if (stored in values) stored else LYRIC
    }

    fun write(prefs: SharedPreferences, mode: String) {
        prefs.edit().putString(PREF_KEY, if (mode in values) mode else LYRIC).apply()
    }

    fun resolve(
        mode: String,
        lyricInfo: LyricRepository.LyricInfo?,
        fallbackText: String
    ): String {
        val preferred = when (mode) {
            TRANSLATION -> lyricInfo?.translation
            ROMANIZATION -> lyricInfo?.roma
            else -> null
        }?.takeIf { it.isNotBlank() }
        return preferred ?: fallbackText
    }
}
