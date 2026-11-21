package com.example.islandlyrics.feature.main.miuix

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsPlaybackStateTest {
    @Test
    fun timelineKeepsLyricLinesInOrderAndInsertsLongGaps() {
        val first = LyricLine(3_000L, 5_000L, "First line")
        val second = LyricLine(6_000L, 8_000L, "Second line")
        val third = LyricLine(13_000L, 15_000L, "Third line")

        val timeline = listOf(first, second, third).withInterludes()

        assertEquals(
            listOf(
                PlaybackTimelineItem.Interlude(0L, 3_000L),
                PlaybackTimelineItem.Verse(first),
                PlaybackTimelineItem.Verse(second),
                PlaybackTimelineItem.Interlude(9_200L, 13_000L),
                PlaybackTimelineItem.Verse(third),
            ),
            timeline,
        )
    }
}
