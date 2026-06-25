package com.example.islandlyrics.feature.main

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.state.LyricRepository

object HomeLyricPreviewDisplay {
    const val LYRIC = "lyric"
    const val TRANSLATION = "translation"
    const val ROMANIZATION = "romanization"

    val values = listOf(LYRIC, TRANSLATION, ROMANIZATION)
    val defaultModes = setOf(LYRIC)

    fun read(prefs: SharedPreferences): Set<String> {
        val stored = prefs.getStringSet(
            AppPreferences.Keys.HOME_LYRIC_PREVIEW_DISPLAY_MODES,
            defaultModes
        ) ?: defaultModes
        val sanitized = stored.filterTo(linkedSetOf()) { it in values }
        return sanitized.ifEmpty { defaultModes }
    }

    fun write(prefs: SharedPreferences, modes: Set<String>) {
        val sanitized = modes.filterTo(linkedSetOf()) { it in values }
        prefs.edit {
            putStringSet(
                AppPreferences.Keys.HOME_LYRIC_PREVIEW_DISPLAY_MODES,
                sanitized.ifEmpty { defaultModes }
            )
        }
    }

    fun toggledModes(currentModes: Set<String>, mode: String, checked: Boolean): Set<String>? {
        if (mode !in values) return currentModes
        val sanitized = currentModes.filterTo(linkedSetOf()) { it in values }
        if (sanitized.isEmpty()) {
            sanitized.addAll(defaultModes)
        }
        if (checked) {
            sanitized.add(mode)
            return sanitized
        }
        if (mode in sanitized && sanitized.size == 1) return null
        sanitized.remove(mode)
        return sanitized.ifEmpty { defaultModes }
    }

    fun previewText(
        modes: Set<String>,
        currentLine: OnlineLyricFetcher.LyricLine?,
        lyricInfo: LyricRepository.LyricInfo?
    ): String? {
        val selectedModes = values.filter { it in modes }.ifEmpty { defaultModes.toList() }
        val lines = selectedModes.mapNotNull { mode ->
            textForMode(mode, currentLine, lyricInfo)
        }
        return lines.distinct().joinToString("\n").takeIf { it.isNotBlank() }
    }

    private fun textForMode(
        mode: String,
        currentLine: OnlineLyricFetcher.LyricLine?,
        lyricInfo: LyricRepository.LyricInfo?
    ): String? {
        val text = when (mode) {
            LYRIC -> currentLine?.text ?: lyricInfo?.lyric
            TRANSLATION -> currentLine?.translation ?: lyricInfo?.translation
            ROMANIZATION -> currentLine?.roma ?: lyricInfo?.roma
            else -> null
        }
        return text?.takeIf { it.isNotBlank() }
    }
}
