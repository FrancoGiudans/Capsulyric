package com.example.islandlyrics.ui.overlay.floating

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.SyllableInfo
import com.example.islandlyrics.ui.overlay.model.WordProgressCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingWordPlaybackTest {
    @Test
    fun finalSyllableCompletesBeforeTheLineEndsAndReopensOnSeek() {
        val clock = FloatingWordPlayback()
        clock.acceptPosition(1_400, 10_000)
        clock.setPlaying(true, 10_000)

        assertFalse(progress(clock, 10_099))
        assertTrue(progress(clock, 10_100))
        assertTrue(progress(clock, 10_400))
        clock.acceptPosition(1_200, 10_450)
        assertFalse(progress(clock, 10_450))
        assertTrue(progress(clock, 10_750))
    }

    @Test
    fun pauseFreezesAtTheLastFrameAndResumeDoesNotAddPausedTime() {
        val clock = FloatingWordPlayback()
        clock.acceptPosition(1_200, 10_000)
        clock.setPlaying(true, 10_000)
        clock.setPlaying(false, 10_100)

        assertEquals(1_300L, clock.positionAt(30_000))
        assertFalse(progress(clock, 30_000))
        clock.setPlaying(true, 30_000)
        assertTrue(progress(clock, 30_200))
    }

    private fun progress(clock: FloatingWordPlayback, now: Long): Boolean {
        return checkNotNull(
            WordProgressCalculator.compute(
                text = "唱完！",
                startTime = 1_000,
                endTime = 2_000,
                syllables = listOf(SyllableInfo(1_000, 1_200, "唱"), SyllableInfo(1_250, 1_500, "完")),
                position = checkNotNull(clock.positionAt(now))
            )
        ).isFullySung()
    }
}
