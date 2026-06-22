package com.example.islandlyrics.ui.overlay.superisland.render
import com.example.islandlyrics.ui.overlay.model.UIState
internal object SuperIslandTextResolver {
    fun isPlaceholder(text: String): Boolean {
        return text.isBlank() || text.trim() == "♪"
    }

    fun primaryText(state: UIState): String {
        val candidates = if (state.isTimingGapPlaceholder && !state.preferMetadataLayout) {
            sequenceOf(state.fullLyric, state.displayLyric)
        } else {
            sequenceOf(state.fullLyric, state.displayLyric, state.title)
        }
        return candidates
            .firstOrNull { !isPlaceholder(it) }
            ?: "♪"
    }

    fun compactText(state: UIState): String {
        val candidates = if (state.isTimingGapPlaceholder && !state.preferMetadataLayout) {
            sequenceOf(state.displayLyric)
        } else {
            sequenceOf(state.displayLyric, state.title)
        }
        return candidates
            .firstOrNull { !isPlaceholder(it) }
            ?: "♪"
    }

    fun shareContent(state: UIState, format: String): String {
        val primary = primaryText(state)
        val artist = if (state.artist.isNotBlank()) state.artist else "未知歌手"
        val song = state.title.ifEmpty { "未知歌曲" }
        return when (format) {
            "format_2" -> "$primary -$artist\uff0c$song"
            "format_3" -> "$primary\n$artist\uff0c$song"
            else -> "$primary\n$song by $artist"
        }
    }
}
