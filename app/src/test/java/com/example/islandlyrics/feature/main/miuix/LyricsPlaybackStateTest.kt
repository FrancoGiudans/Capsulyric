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
        assertNull(at(7_999L))
        assertEquals(PlaybackInterlude(2, 8_000L, 13_000L), at(8_000L))
        assertEquals(PlaybackInterlude(2, 8_000L, 13_000L), at(12_999L))
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

    @Test
    fun interludeChargesFor250MsThenExits400MsBeforeTheNextLine() {
        val gap = PlaybackInterlude(1, 8_000L, 13_000L)

        assertEquals(12_350L, gap.chargeStartTime)
        assertEquals(12_600L, gap.exitStartTime)
        assertEquals(0.5f, gap.chargeProgress(12_475L), 0f)
        assertEquals(1f, gap.chargeProgress(12_600L), 0f)
        assertEquals(0f, gap.exitProgress(12_599L), 0f)
        assertEquals(0.5f, gap.exitProgress(12_800L), 0f)
        assertEquals(1f, gap.exitProgress(13_000L), 0f)
    }

    @Test
    fun interludeDotsFinishLightingBeforeTheCharge() {
        val gap = PlaybackInterlude(1, 8_000L, 13_000L)

        assertEquals(0f, gap.dotProgress(0, gap.startTime), 0f)
        assertEquals(0.5f, gap.dotProgress(0, 8_725L), 0.0001f)
        assertEquals(0f, gap.dotProgress(1, 8_725L), 0f)
        assertEquals(0.5f, gap.dotProgress(2, 11_625L), 0.0001f)
        repeat(3) { index ->
            assertEquals(1f, gap.dotProgress(index, gap.chargeStartTime), 0f)
        }
    }

    @Test
    fun lyricDepthUsesTheConfiguredStepsAndCapsDistantRows() {
        val stops = listOf(
            PlaybackLyricDepth(0f, 0f, 1f, 0.55f),
            PlaybackLyricDepth(1.2f, 0.8f, 0.50f, 0.28f),
            PlaybackLyricDepth(2.2f, 1.4f, 0.42f, 0.22f),
            PlaybackLyricDepth(3f, 1.8f, 0.35f, 0.18f),
        )
        stops.forEachIndexed { index, expected ->
            assertEquals(expected, playbackLyricDepth(index.toFloat()))
        }
        assertEquals(stops.last(), playbackLyricDepth(12f))
    }

    @Test
    fun lyricDepthEasesBetweenStepsAsTheFocusMoves() {
        val progress = 0.15625f // smoothstep(0.25)
        val depth = playbackLyricDepth(1.25f)
        assertEquals(1.2f + (2.2f - 1.2f) * progress, depth.mainBlur, 0.0001f)
        assertEquals(0.8f + (1.4f - 0.8f) * progress, depth.secondaryBlur, 0.0001f)
        assertEquals(0.50f + (0.42f - 0.50f) * progress, depth.mainAlpha, 0.0001f)
        assertEquals(0.28f + (0.22f - 0.28f) * progress, depth.secondaryAlpha, 0.0001f)
    }

    @Test
    fun landscapeDistanceFollowsTheViewportAcrossPaddingAndHeightChanges() {
        // These viewports have 39% leading padding, so their focus is at item offset zero.
        assertEquals(0f, landscapeLyricDistance(0f, -117, 183), 0.0001f)
        assertEquals(0f, landscapeLyricDistance(0.5f, -117, 183), 0.0001f)
        assertEquals(1f, landscapeLyricDistance(-66f, -117, 183), 0.0001f)
        assertEquals(1f, landscapeLyricDistance(66f, -117, 183), 0.0001f)
        assertEquals(1f, landscapeLyricDistance(132f, -234, 366), 0.0001f)
        assertEquals(1f, landscapeLyricDistance(101f, -82, 218), 0.0001f)
    }

    @Test
    fun landscapeDepthKeepsTheAnchorSharpAndCapsTheTwoBlurRadiiSeparately() {
        assertEquals(PlaybackLyricDepth(0f, 0f, 1f, 0.55f), playbackLyricDepth(0f, landscape = true))
        assertEquals(PlaybackLyricDepth(1.1f, 0.7f, 0.50f, 0.28f), playbackLyricDepth(1f, landscape = true))
        assertEquals(PlaybackLyricDepth(1.9f, 1.2f, 0.42f, 0.22f), playbackLyricDepth(2f, landscape = true))
        assertEquals(PlaybackLyricDepth(2.5f, 1.5f, 0.35f, 0.18f), playbackLyricDepth(4f, landscape = true))
    }
}
