package com.example.islandlyrics.ui.overlay.superisland.render

import com.example.islandlyrics.ui.overlay.model.LyricPresentation
import com.example.islandlyrics.ui.overlay.model.UIState
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandTemplate18TextMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperIslandTemplate18TextResolverTest {
    @Test
    fun selectedOrderSkipsUnavailableSourcesAndUsesFirstTwoAvailableRows() {
        val state = UIState(
            fullLyric = "完整当前句，不使用胶囊截断文本",
            displayLyric = "胶囊截断文本",
            title = "歌名",
            artist = "歌手",
            lyricPresentation = LyricPresentation(
                currentLine = LyricPresentation.DisplayLine(
                    text = "当前句",
                    translation = null,
                    romanization = "拼音"
                ),
                nextLine = LyricPresentation.DisplayLine("下一句"),
                nextNextLine = LyricPresentation.DisplayLine("下二句")
            )
        )

        val result = SuperIslandTemplate18TextResolver.resolve(
            state,
            listOf(
                SuperIslandTemplate18TextMode.TRANSLATION,
                SuperIslandTemplate18TextMode.ROMANIZATION,
                SuperIslandTemplate18TextMode.NEXT_LYRIC,
                SuperIslandTemplate18TextMode.NEXT_NEXT_LYRIC
            )
        )

        assertEquals("完整当前句，不使用胶囊截断文本", result.currentLyric)
        assertEquals(listOf("拼音", "下一句"), result.secondaryTexts)
        assertTrue(result.canRenderThreeLines)
    }

    @Test
    fun repeatedAdjacentLyricTextKeepsBothTimelineRows() {
        val state = UIState(
            displayLyric = "副歌",
            lyricPresentation = LyricPresentation(
                nextLine = LyricPresentation.DisplayLine("副歌"),
                nextNextLine = LyricPresentation.DisplayLine("副歌")
            )
        )

        val result = SuperIslandTemplate18TextResolver.resolve(
            state,
            listOf(
                SuperIslandTemplate18TextMode.NEXT_LYRIC,
                SuperIslandTemplate18TextMode.NEXT_NEXT_LYRIC
            )
        )

        assertEquals(listOf("副歌", "副歌"), result.secondaryTexts)
        assertTrue(result.canRenderThreeLines)
    }

    @Test
    fun unavailableSelectedSourcesRequestTwoLineFallback() {
        val result = SuperIslandTemplate18TextResolver.resolve(
            UIState(displayLyric = "当前句"),
            listOf(
                SuperIslandTemplate18TextMode.NEXT_LYRIC,
                SuperIslandTemplate18TextMode.NEXT_NEXT_LYRIC
            )
        )

        assertEquals(emptyList<String>(), result.secondaryTexts)
        assertFalse(result.canRenderThreeLines)
    }
}
