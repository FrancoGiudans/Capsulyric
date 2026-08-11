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

package com.example.islandlyrics.ui.overlay.floating

import android.content.SharedPreferences
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.ui.overlay.model.SecondaryTextMode

internal data class FloatingLyricsDisplayConfig(
    val secondaryTextModes: List<SecondaryTextMode>,
    val showSecondLine: Boolean,
    val neighborAlignment: FloatingLyricsNeighborAlignment,
    val wordHighlight: Boolean
) {
    companion object {
        const val KEY_DISPLAY_MODE = AppPreferences.Keys.FLOATING_DISPLAY_MODE
        const val KEY_SECONDARY_TEXT_MODES = AppPreferences.Keys.FLOATING_SECONDARY_TEXT_MODES
        const val KEY_SHOW_NEIGHBOR_LINE = "floating_show_neighbor_line"
        const val KEY_NEIGHBOR_ALIGNMENT = AppPreferences.Keys.FLOATING_NEIGHBOR_ALIGNMENT
        const val KEY_WORD_HIGHLIGHT = AppPreferences.Keys.FLOATING_WORD_HIGHLIGHT

        fun from(prefs: SharedPreferences): FloatingLyricsDisplayConfig {
            return FloatingLyricsDisplayConfig(
                secondaryTextModes = readSecondaryTextModes(prefs),
                showSecondLine = readShowSecondLine(prefs),
                neighborAlignment = FloatingLyricsNeighborAlignment.from(
                    prefs.getString(KEY_NEIGHBOR_ALIGNMENT, FloatingLyricsNeighborAlignment.CENTER.value)
                ),
                wordHighlight = prefs.getBoolean(KEY_WORD_HIGHLIGHT, true)
            )
        }

        /**
         * 读取第二行内容的多选优先级列表。
         * 首次使用（尚未写入新键）时，将旧版「显示模式 + 双行歌词」配置迁移为新的优先级列表。
         */
        fun readSecondaryTextModes(prefs: SharedPreferences): List<SecondaryTextMode> {
            if (prefs.getString(KEY_SECONDARY_TEXT_MODES, null) != null) {
                return SecondaryTextMode.read(prefs, KEY_SECONDARY_TEXT_MODES)
            }
            val legacyModes = FloatingLyricsDisplayMode.from(prefs.all[KEY_DISPLAY_MODE])
            val modes = LinkedHashSet<SecondaryTextMode>()
            if (FloatingLyricsDisplayMode.TRANSLATION in legacyModes) {
                modes.add(SecondaryTextMode.TRANSLATION)
            }
            if (FloatingLyricsDisplayMode.ROMANIZATION in legacyModes) {
                modes.add(SecondaryTextMode.ROMANIZATION)
            }
            if (readShowSecondLine(prefs) || modes.isEmpty()) {
                modes.add(SecondaryTextMode.NEXT_LYRIC)
            }
            return modes.toList().ifEmpty { SecondaryTextMode.DEFAULT }
        }

        fun readShowSecondLine(prefs: SharedPreferences): Boolean {
            return if (prefs.contains(KEY_SHOW_NEIGHBOR_LINE)) {
                prefs.all[KEY_SHOW_NEIGHBOR_LINE] as? Boolean ?: false
            } else {
                FloatingLyricsDisplayMode.hasLegacyNeighborLine(prefs.all[KEY_DISPLAY_MODE])
            }
        }

        /** 旧版键名兼容读取（迁移前仍可被旧逻辑引用）。 */
        fun readShowNeighborLine(prefs: SharedPreferences): Boolean = readShowSecondLine(prefs)
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
