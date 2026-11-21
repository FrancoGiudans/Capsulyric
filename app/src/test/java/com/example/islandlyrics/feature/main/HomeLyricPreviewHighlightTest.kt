package com.example.islandlyrics.feature.main

import androidx.compose.ui.graphics.Color
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.SyllableInfo
import com.example.islandlyrics.ui.overlay.model.SecondaryTextMode
import com.example.islandlyrics.ui.overlay.model.SecondaryTextResolver
import com.example.islandlyrics.ui.overlay.model.WordProgressCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLyricPreviewHighlightTest {
    private val syllables = listOf(
        SyllableInfo(0L, 1_000L, "甲"),
        SyllableInfo(1_000L, 2_000L, "乙"),
    )

    @Test
    fun onlyTheCurrentLineFollowsThePlaybackPosition() {
        val preview = HomeLyricPreviewDisplay.Preview(
            "甲乙",
            SecondaryTextResolver.Resolved(SecondaryTextMode.NEXT_LYRIC, "丙丁"),
        )
        val color = Color.White
        val progress = WordProgressCalculator.compute(preview.primary, 0L, 2_000L, syllables, 500L)
        val styled = highlightLyricPreview(preview, progress, color)

        assertEquals(preview.text, styled.text)
        assertEquals(listOf(0, 1, 3), styled.spanStyles.map { it.start })
        assertEquals(listOf(1, 2, 5), styled.spanStyles.map { it.end })
        val unsung = color.copy(alpha = WordProgressCalculator.UNSUNG_ALPHA)
        assertEquals(color.copy(alpha = WordProgressCalculator.highlightAlpha(0.5f)), styled.spanStyles[0].item.color)
        assertEquals(unsung, styled.spanStyles[1].item.color)
        assertEquals(unsung, styled.spanStyles[2].item.color)
    }

    @Test
    fun translationKeepsItsTextColorAfterTheCurrentLineFinishes() {
        val preview = HomeLyricPreviewDisplay.Preview(
            "甲乙",
            SecondaryTextResolver.Resolved(SecondaryTextMode.TRANSLATION, "Translation"),
        )
        val finished = WordProgressCalculator.compute(preview.primary, 0L, 2_000L, syllables, 2_000L)

        assertEquals(preview.text, highlightLyricPreview(preview, finished, Color.White).text)
        assertEquals(emptyList<Any>(), highlightLyricPreview(preview, finished, Color.White).spanStyles)
    }
}
