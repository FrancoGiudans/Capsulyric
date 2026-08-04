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

package com.example.islandlyrics.ui.overlay.superisland.render

import com.example.islandlyrics.ui.overlay.model.UIState
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandSecondaryTextMode

internal data class SuperIslandDualLineText(
    val primary: String,
    val secondary: String?
) {
    val signature: String = "$primary\u0000${secondary.orEmpty()}"
}

internal object SuperIslandDualLineTextResolver {
    fun resolve(
        state: UIState,
        modes: List<SuperIslandSecondaryTextMode>
    ): SuperIslandDualLineText {
        val currentLine = state.lyricPresentation.currentLine
        val primary = sequenceOf(
            currentLine?.text,
            state.fullLyric,
            state.displayLyric,
            state.title
        )
            .filterNotNull()
            .firstOrNull { !SuperIslandTextResolver.isPlaceholder(it) }
            ?: "♪"

        val secondary = resolveSecondary(state, modes)

        return SuperIslandDualLineText(primary = primary, secondary = secondary)
    }

    internal fun resolveSecondary(
        state: UIState,
        modes: List<SuperIslandSecondaryTextMode>
    ): String? {
        val currentLine = state.lyricPresentation.currentLine

        fun candidate(text: String?): String? =
            text?.takeIf { !SuperIslandTextResolver.isPlaceholder(it) }

        val translation = candidate(currentLine?.translation)
        val romanization = candidate(currentLine?.romanization)
        val nextLyric = candidate(state.lyricPresentation.nextLine?.text)

        val parts = LinkedHashSet<String>()
        for (mode in modes) {
            val value = when (mode) {
                SuperIslandSecondaryTextMode.TRANSLATION -> translation
                SuperIslandSecondaryTextMode.ROMANIZATION -> romanization
                SuperIslandSecondaryTextMode.NEXT_LYRIC -> nextLyric
            }
            if (value != null) parts.add(value)
        }
        // 兜底：所选内容均不可用时，按翻译→罗马音→下一句的顺序取第一个可用内容
        if (parts.isEmpty()) {
            sequenceOf(translation, romanization, nextLyric)
                .filterNotNull()
                .firstOrNull()
                ?.let { parts.add(it) }
        }
        return parts.joinToString(" / ").ifEmpty { null }
    }
}
