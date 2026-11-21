package com.example.islandlyrics.feature.main.miuix

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsPlaybackStateTest {
    @Test
    fun interludeAppearsOnlyWithinItsWaitInterval() {
        val lines = listOf(
            LyricLine(3_000L, 5_000L, "First line"),
            LyricLine(6_000L, 8_000L, "Second line"),
            LyricLine(13_000L, 15_000L, "Third line"),
        )
        val interludes = lines.playbackInterludes()

        fun at(time: Long) = interludes.activeInterlude(lines.currentPlaybackLine(time), time)

        assertEquals(PlaybackInterlude(0, 0L, 3_000L), at(0L))
        assertNull(at(3_000L))
        assertNull(at(5_500L))
        assertNull(at(9_199L))
        assertEquals(PlaybackInterlude(2, 9_200L, 13_000L), at(9_200L))
        assertEquals(PlaybackInterlude(2, 9_200L, 13_000L), at(12_999L))
        assertNull(at(13_000L))
        assertEquals(2, lines.currentPlaybackLine(13_000L))
        assertNull(at(20_000L))
    }

    @Test
    fun overlappingVocalsDelayTheInterludeUntilAllLinesEnd() {
        val lines = listOf(
            LyricLine(0L, 8_000L, "Long line"),
            LyricLine(2_000L, 4_000L, "Overlapping line"),
            LyricLine(10_000L, 12_000L, "Next line"),
        )

        assertEquals(emptyMap<Int, PlaybackInterlude>(), lines.playbackInterludes())
    }
}
