package com.example.islandlyrics.ui.overlay.floating

import android.content.SharedPreferences
import com.example.islandlyrics.core.settings.AppPreferences

internal data class FloatingLyricsDisplayConfig(
    val displayModes: Set<FloatingLyricsDisplayMode>,
    val showNeighborLine: Boolean,
    val neighborAlignment: FloatingLyricsNeighborAlignment,
    val wordHighlight: Boolean
) {
    companion object {
        const val KEY_DISPLAY_MODE = AppPreferences.Keys.FLOATING_DISPLAY_MODE
        const val KEY_SHOW_NEIGHBOR_LINE = "floating_show_neighbor_line"
        const val KEY_NEIGHBOR_ALIGNMENT = AppPreferences.Keys.FLOATING_NEIGHBOR_ALIGNMENT
        const val KEY_WORD_HIGHLIGHT = AppPreferences.Keys.FLOATING_WORD_HIGHLIGHT

        fun from(prefs: SharedPreferences): FloatingLyricsDisplayConfig {
            return FloatingLyricsDisplayConfig(
                displayModes = readDisplayModes(prefs),
                showNeighborLine = readShowNeighborLine(prefs),
                neighborAlignment = FloatingLyricsNeighborAlignment.from(
                    prefs.getString(KEY_NEIGHBOR_ALIGNMENT, FloatingLyricsNeighborAlignment.CENTER.value)
                ),
                wordHighlight = prefs.getBoolean(KEY_WORD_HIGHLIGHT, true)
            )
        }

        fun readDisplayModes(prefs: SharedPreferences): Set<FloatingLyricsDisplayMode> {
            return FloatingLyricsDisplayMode.from(prefs.all[KEY_DISPLAY_MODE])
        }

        fun readShowNeighborLine(prefs: SharedPreferences): Boolean {
            return if (prefs.contains(KEY_SHOW_NEIGHBOR_LINE)) {
                prefs.all[KEY_SHOW_NEIGHBOR_LINE] as? Boolean ?: false
            } else {
                FloatingLyricsDisplayMode.hasLegacyNeighborLine(prefs.all[KEY_DISPLAY_MODE])
            }
        }
    }
}

internal enum class FloatingLyricsDisplayMode(val value: String) {
    LYRIC("lyric"),
    TRANSLATION("translation"),
    ROMANIZATION("romanization");

    companion object {
        const val LEGACY_SINGLE_LINE = "single_line"
        const val LEGACY_NEIGHBOR_LINE = "neighbor_line"

        val defaultModes = setOf(LYRIC)
        val optionOrder = listOf(LYRIC, TRANSLATION, ROMANIZATION)

        fun from(value: Any?): Set<FloatingLyricsDisplayMode> {
            val modes = when (value) {
                null -> defaultModes
                is String -> fromString(value)
                is Set<*> -> fromTokens(value.mapNotNull { it as? String })
                else -> defaultModes
            }
            return sanitize(modes)
        }

        fun preferenceValue(modes: Set<FloatingLyricsDisplayMode>): String {
            val values = optionOrder
                .filter { it in sanitize(modes) }
                .map { it.value }
            return if (values.size == 1 && values.first() != LYRIC.value) {
                values.first() + ","
            } else {
                values.joinToString(",")
            }
        }

        fun hasLegacyNeighborLine(value: Any?): Boolean {
            return when (value) {
                LEGACY_NEIGHBOR_LINE -> true
                is Set<*> -> value.any { it == LEGACY_NEIGHBOR_LINE }
                else -> false
            }
        }

        fun toggledModes(
            currentModes: Set<FloatingLyricsDisplayMode>,
            mode: FloatingLyricsDisplayMode,
            checked: Boolean
        ): Set<FloatingLyricsDisplayMode>? {
            val next = sanitize(currentModes).toMutableSet()
            if (checked) {
                next.add(mode)
                return sanitize(next)
            }
            if (mode in next && next.size == 1) return null
            next.remove(mode)
            return sanitize(next)
        }

        private fun sanitize(modes: Set<FloatingLyricsDisplayMode>): Set<FloatingLyricsDisplayMode> {
            val sanitized = optionOrder.filterTo(linkedSetOf()) { it in modes }
            return sanitized.ifEmpty { defaultModes }
        }

        private fun fromString(value: String): Set<FloatingLyricsDisplayMode> {
            return when (value) {
                "", LEGACY_SINGLE_LINE -> defaultModes
                ROMANIZATION.value -> linkedSetOf(LYRIC, ROMANIZATION)
                TRANSLATION.value -> linkedSetOf(LYRIC, TRANSLATION)
                LEGACY_NEIGHBOR_LINE -> defaultModes
                else -> fromTokens(value.split(",", "+"))
            }
        }

        private fun fromTokens(tokens: Iterable<String>): Set<FloatingLyricsDisplayMode> {
            val modes = linkedSetOf<FloatingLyricsDisplayMode>()
            tokens.forEach { item ->
                when (val normalized = item.trim()) {
                    LEGACY_SINGLE_LINE -> modes.add(LYRIC)
                    LEGACY_NEIGHBOR_LINE, "" -> Unit
                    else -> entries.firstOrNull { it.value == normalized }?.let(modes::add)
                }
            }
            return modes
        }
    }
}

internal enum class FloatingLyricsNeighborAlignment(val value: String) {
    CENTER("center"),
    SPLIT_START_END("split_start_end");

    companion object {
        fun from(value: String?): FloatingLyricsNeighborAlignment {
            return entries.firstOrNull { it.value == value } ?: CENTER
        }
    }
}
