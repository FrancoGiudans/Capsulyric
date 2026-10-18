package com.example.islandlyrics.ui.overlay.superisland.render

import com.example.islandlyrics.ui.overlay.model.UIState
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandTemplate18TextMode

internal data class SuperIslandTemplate18Content(
    val currentLyric: String,
    val secondaryTexts: List<String>
) {
    val canRenderThreeLines: Boolean
        get() = secondaryTexts.size >= SuperIslandTemplate18TextMode.MIN_SELECTED
}

internal object SuperIslandTemplate18TextResolver {
    fun resolve(
        state: UIState,
        modes: List<SuperIslandTemplate18TextMode>
    ): SuperIslandTemplate18Content {
        val presentation = state.lyricPresentation
        val currentLine = presentation.currentLine
        val currentLyric = SuperIslandTextResolver.primaryText(state)

        val playingInfo = listOf(state.title, state.artist)
            .filter(String::isNotBlank)
            .joinToString(" - ")
            .takeIf(String::isNotBlank)

        val available = modes.distinct().mapNotNull { mode ->
            val candidate = when (mode) {
                SuperIslandTemplate18TextMode.TRANSLATION -> currentLine?.translation
                SuperIslandTemplate18TextMode.ROMANIZATION -> currentLine?.romanization
                SuperIslandTemplate18TextMode.NEXT_LYRIC -> presentation.nextLine?.text
                SuperIslandTemplate18TextMode.NEXT_NEXT_LYRIC -> presentation.nextNextLine?.text
                SuperIslandTemplate18TextMode.PLAYING_INFO -> playingInfo
            }
            candidate?.takeUnless(SuperIslandTextResolver::isPlaceholder)
        }

        return SuperIslandTemplate18Content(
            currentLyric = currentLyric,
            secondaryTexts = available.take(2)
        )
    }
}
