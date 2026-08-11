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
 *  *
 *  *
 */

package com.example.islandlyrics.ui.overlay.model

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 次要文本（第二行歌词）内容类型。
 *
 * 采用标准小米超级岛通知样式（模板2）的次要文本处理方式：
 * 多选并按列表顺序决定优先级，渲染时返回第一个可用内容（而非全部拼接）。
 */
enum class SecondaryTextMode(val preferenceValue: String) {
    NEXT_LYRIC("next_lyric"),
    TRANSLATION("translation"),
    ROMANIZATION("romanization");

    companion object {
        val DEFAULT = listOf(NEXT_LYRIC)

        fun from(value: String?): SecondaryTextMode? =
            entries.firstOrNull { it.preferenceValue == value }

        fun read(
            prefs: SharedPreferences,
            key: String,
            default: List<SecondaryTextMode> = DEFAULT
        ): List<SecondaryTextMode> {
            val raw = prefs.getString(key, null)?.takeIf { it.isNotBlank() }
                ?: return default
            val modes = LinkedHashSet<SecondaryTextMode>()
            raw.split(',').forEach { token ->
                from(token.trim())?.let { modes.add(it) }
            }
            return modes.toList().ifEmpty { default }
        }

        fun write(prefs: SharedPreferences, key: String, modes: List<SecondaryTextMode>) {
            prefs.edit { putString(key, modes.joinToString(",") { it.preferenceValue }) }
        }
    }
}

/**
 * 按优先级顺序解析第二行歌词：第一个可用的选中类型胜出；
 * 全部不可用时兜底按 翻译→罗马音→下一句 的顺序取第一个可用内容。
 */
internal object SecondaryTextResolver {
    fun isPlaceholder(text: String?): Boolean =
        text == null || text.isBlank() || text.trim() == "♪"

    fun resolve(
        modes: List<SecondaryTextMode>,
        translation: String?,
        romanization: String?,
        nextLyric: String?
    ): String? {
        val translationText = translation?.takeIf { !isPlaceholder(it) }
        val romanizationText = romanization?.takeIf { !isPlaceholder(it) }
        val nextLyricText = nextLyric?.takeIf { !isPlaceholder(it) }

        for (mode in modes) {
            val value = when (mode) {
                SecondaryTextMode.TRANSLATION -> translationText
                SecondaryTextMode.ROMANIZATION -> romanizationText
                SecondaryTextMode.NEXT_LYRIC -> nextLyricText
            }
            if (value != null) return value
        }
        return sequenceOf(translationText, romanizationText, nextLyricText)
            .filterNotNull()
            .firstOrNull()
    }
}
