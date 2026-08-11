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

package com.example.islandlyrics.feature.main

import android.content.SharedPreferences
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.overlay.model.SecondaryTextMode
import com.example.islandlyrics.ui.overlay.model.SecondaryTextResolver

/**
 * 首页歌词的第二行内容，采用标准小米超级岛通知样式（模板2）的次要文本处理方式：
 * 多选并按列表顺序决定优先级，渲染时仅显示第一个可用内容。
 */
object HomeLyricPreviewDisplay {
    // 旧版（多选叠加）选项名，仅用于迁移
    private const val LEGACY_LYRIC = "lyric"
    private const val LEGACY_TRANSLATION = "translation"
    private const val LEGACY_ROMANIZATION = "romanization"

    val defaultModes: List<SecondaryTextMode> = SecondaryTextMode.DEFAULT

    fun read(prefs: SharedPreferences): List<SecondaryTextMode> {
        if (prefs.getString(AppPreferences.Keys.HOME_LYRIC_PREVIEW_SECONDARY_MODES, null) != null) {
            return SecondaryTextMode.read(
                prefs,
                AppPreferences.Keys.HOME_LYRIC_PREVIEW_SECONDARY_MODES
            )
        }
        val legacy = prefs.getStringSet(
            AppPreferences.Keys.HOME_LYRIC_PREVIEW_DISPLAY_MODES,
            null
        ) ?: return defaultModes
        val modes = LinkedHashSet<SecondaryTextMode>()
        if (LEGACY_TRANSLATION in legacy) modes.add(SecondaryTextMode.TRANSLATION)
        if (LEGACY_ROMANIZATION in legacy) modes.add(SecondaryTextMode.ROMANIZATION)
        if (LEGACY_LYRIC in legacy && modes.isEmpty()) modes.add(SecondaryTextMode.NEXT_LYRIC)
        return modes.toList().ifEmpty { defaultModes }
    }

    fun write(prefs: SharedPreferences, modes: List<SecondaryTextMode>) {
        SecondaryTextMode.write(
            prefs,
            AppPreferences.Keys.HOME_LYRIC_PREVIEW_SECONDARY_MODES,
            modes
        )
    }

    fun toggledModes(
        currentModes: List<SecondaryTextMode>,
        mode: SecondaryTextMode,
        checked: Boolean
    ): List<SecondaryTextMode>? {
        val sanitized = currentModes.filter { it in SecondaryTextMode.entries }
        if (checked) {
            if (mode !in sanitized) return sanitized + mode
            return sanitized
        }
        if (sanitized.size <= 1) return null
        return sanitized - mode
    }

    /**
     * 第一行固定为当前句歌词，第二行按多选优先级取第一个可用内容。
     */
    fun previewText(
        modes: List<SecondaryTextMode>,
        currentLine: OnlineLyricFetcher.LyricLine?,
        lyricInfo: LyricRepository.LyricInfo?,
        nextLine: OnlineLyricFetcher.LyricLine? = null
    ): String? {
        val primary = (currentLine?.text ?: lyricInfo?.lyric)
            ?.takeIf { !SecondaryTextResolver.isPlaceholder(it) }
            ?: return null
        val secondary = SecondaryTextResolver.resolve(
            modes = modes.ifEmpty { defaultModes },
            translation = currentLine?.translation ?: lyricInfo?.translation,
            romanization = currentLine?.roma ?: lyricInfo?.roma,
            nextLyric = nextLine?.text
        )
        return if (secondary != null) {
            "$primary\n$secondary"
        } else {
            primary
        }
    }
}
