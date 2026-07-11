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
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandDualLineMode

internal data class SuperIslandDualLineText(
    val primary: String,
    val secondary: String?
) {
    val signature: String = "$primary\u0000${secondary.orEmpty()}"
}

internal object SuperIslandDualLineTextResolver {
    fun resolve(state: UIState, mode: String): SuperIslandDualLineText {
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

        val secondary = resolveSecondary(state, mode)

        return SuperIslandDualLineText(primary = primary, secondary = secondary)
    }

    private fun resolveSecondary(state: UIState, mode: String): String? {
        val currentLine = state.lyricPresentation.currentLine

        fun candidate(text: String?): String? =
            text?.takeIf { !SuperIslandTextResolver.isPlaceholder(it) }

        val translation = candidate(currentLine?.translation)
        val romanization = candidate(currentLine?.romanization)
        val nextLyric = candidate(state.lyricPresentation.nextLine?.text)

        return when (mode) {
            SuperIslandDualLineMode.TRANSLATION ->
                translation ?: romanization ?: nextLyric
            SuperIslandDualLineMode.ROMANIZATION ->
                romanization ?: translation ?: nextLyric
            SuperIslandDualLineMode.NEXT_LYRIC ->
                nextLyric ?: translation ?: romanization
            else -> translation ?: romanization ?: nextLyric
        }
    }
}
