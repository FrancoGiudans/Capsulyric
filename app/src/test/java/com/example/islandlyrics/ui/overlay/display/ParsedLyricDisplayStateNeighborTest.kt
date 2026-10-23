package com.example.islandlyrics.ui.overlay.display

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.state.LyricRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsedLyricDisplayStateNeighborTest {
    private val lines = listOf(
        OnlineLyricFetcher.LyricLine(0L, 1_000L, "one"),
        OnlineLyricFetcher.LyricLine(1_000L, 2_000L, "two"),
        OnlineLyricFetcher.LyricLine(2_000L, 3_000L, "three")
    )

    @Test
    fun secondFollowingLineUsesTheSameCompleteTimeline() {
        val state = ParsedLyricDisplayState(
            lines = lines,
            timelineCapability = LyricRepository.TimelineCapability.MULTI_LINE
        )

        assertEquals("three", state.nextLine(index = 0, offset = 2)?.text)
    }

    @Test
    fun secondFollowingLineIsNullAtTailOrWithoutCompleteTimeline() {
        val complete = ParsedLyricDisplayState(
            lines = lines,
            timelineCapability = LyricRepository.TimelineCapability.MULTI_LINE
        )
        val single = ParsedLyricDisplayState(
            lines = lines,
            timelineCapability = LyricRepository.TimelineCapability.ACTIVE_LINE_ONLY
        )

        assertNull(complete.nextLine(index = 2, offset = 2))
        assertNull(single.nextLine(index = 0, offset = 2))
    }
}
