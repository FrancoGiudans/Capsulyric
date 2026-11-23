package com.example.islandlyrics.feature.main.miuix

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.ui.overlay.model.WordProgressCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsPlaybackStateTest {
    @Test
    fun lyricBlurKeepsTheMaximumRadiusBeyondTheThirdLine() {
        assertEquals(0f, playbackLyricBlurRadius(0), 0f)
        assertEquals(1.2f, playbackLyricBlurRadius(1), 0f)
        assertEquals(2.2f, playbackLyricBlurRadius(2), 0f)
        assertEquals(3f, playbackLyricBlurRadius(3), 0f)
        assertEquals(3f, playbackLyricBlurRadius(4), 0f)
        assertEquals(3f, playbackLyricBlurRadius(20), 0f)

        assertEquals(0f, playbackLyricBlurRadius(0, landscape = true), 0f)
        assertEquals(1.1f, playbackLyricBlurRadius(1, landscape = true), 0f)
        assertEquals(1.9f, playbackLyricBlurRadius(2, landscape = true), 0f)
        assertEquals(2.5f, playbackLyricBlurRadius(3, landscape = true), 0f)
        assertEquals(2.5f, playbackLyricBlurRadius(4, landscape = true), 0f)
    }

    @Test
    fun changingTheBlurStrengthScalesEveryNonFocusDistance() {
        assertEquals(0f, playbackLyricBlurRadius(0, strength = 0.5f), 0f)
        assertEquals(0.6f, playbackLyricBlurRadius(1, strength = 0.5f), 0f)
        assertEquals(1.1f, playbackLyricBlurRadius(2, strength = 0.5f), 0f)
        assertEquals(1.5f, playbackLyricBlurRadius(4, strength = 0.5f), 0f)
        assertEquals(1.25f, playbackLyricBlurRadius(4, landscape = true, strength = 0.5f), 0f)
    }

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
            PlaybackLyricDepth(0f, 0f, 1f, 0.68f),
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
        assertEquals(PlaybackLyricDepth(0f, 0f, 1f, 0.68f), playbackLyricDepth(0f, landscape = true))
        assertEquals(PlaybackLyricDepth(1.1f, 0.7f, 0.50f, 0.28f), playbackLyricDepth(1f, landscape = true))
        assertEquals(PlaybackLyricDepth(1.9f, 1.2f, 0.42f, 0.22f), playbackLyricDepth(2f, landscape = true))
        assertEquals(PlaybackLyricDepth(2.5f, 1.5f, 0.35f, 0.18f), playbackLyricDepth(4f, landscape = true))
    }

    @Test
    fun interruptedTransitionContinuesFromTheInFlightActivationAndDistance() {
        val midFirstSwitch = PlaybackLyricFrame(focusedLine = 2, previousLine = 1, progress = 0.4f)
        val interruptedStart = midFirstSwitch.asInterruptedStart()
        val secondSwitchAtStart = PlaybackLyricFrame(
            focusedLine = 3,
            previousLine = midFirstSwitch.focusedLine,
            progress = 0f,
            interruptedFrom = interruptedStart,
        )

        assertEquals(midFirstSwitch.activation(1), secondSwitchAtStart.activation(1), 0.0001f)
        assertEquals(midFirstSwitch.activation(2), secondSwitchAtStart.activation(2), 0.0001f)
        assertEquals(midFirstSwitch.activation(3), secondSwitchAtStart.activation(3), 0.0001f)
        assertEquals(midFirstSwitch.distance(1), secondSwitchAtStart.distance(1), 0.0001f)
        assertEquals(midFirstSwitch.distance(2), secondSwitchAtStart.distance(2), 0.0001f)
        assertEquals(midFirstSwitch.distance(3), secondSwitchAtStart.distance(3), 0.0001f)

        val secondSwitchMid = secondSwitchAtStart.copy(progress = 0.5f)
        assertEquals(0.3f, secondSwitchMid.activation(1), 0.0001f)
        assertEquals(0.2f, secondSwitchMid.activation(2), 0.0001f)
        assertEquals(0.5f, secondSwitchMid.activation(3), 0.0001f)
    }

    @Test
    fun unsungTimedLyricsTransitionMonotonicallyWithoutMidSwitchFlash() {
        var previousOnScreenUnsung = PlaybackNeighborMainAlpha
        val steps = 20
        for (step in 0..steps) {
            val progress = step.toFloat() / steps
            val frame = PlaybackLyricFrame(focusedLine = 1, previousLine = 0, progress = progress)
            val activation = frame.activation(1)
            val depth = playbackLyricDepth(frame.distance(1))
            val layerAlpha = playbackLyricMainAlpha(depth.mainAlpha, activation, following = true)
            val maskAlpha = playbackLyricUnsungMaskAlpha(depth.mainAlpha, activation, following = true)
            val onScreenUnsung = layerAlpha * maskAlpha

            assertTrue(
                "Expected monotonic unsung alpha at step $step, got $onScreenUnsung after $previousOnScreenUnsung",
                onScreenUnsung <= previousOnScreenUnsung + 0.0001f,
            )
            previousOnScreenUnsung = onScreenUnsung
        }
        assertEquals(WordProgressCalculator.UNSUNG_ALPHA, previousOnScreenUnsung, 0.0001f)
    }

    @Test
    fun waveDelayCascadesInTheScrollDirectionAndCapsDistantRows() {
        assertEquals(0L, playbackWaveDelayMillis(index = 3, pivotLine = 5, scrollDownwardInList = true))
        assertEquals(0L, playbackWaveDelayMillis(index = 4, pivotLine = 5, scrollDownwardInList = true))
        assertEquals(14L, playbackWaveDelayMillis(index = 5, pivotLine = 5, scrollDownwardInList = true))
        assertEquals(36L, playbackWaveDelayMillis(index = 6, pivotLine = 5, scrollDownwardInList = true))
        assertEquals(62L, playbackWaveDelayMillis(index = 7, pivotLine = 5, scrollDownwardInList = true))
        assertEquals(160L, playbackWaveDelayMillis(index = 20, pivotLine = 5, scrollDownwardInList = true))

        assertEquals(0L, playbackWaveDelayMillis(index = 6, pivotLine = 5, scrollDownwardInList = false))
        assertEquals(14L, playbackWaveDelayMillis(index = 5, pivotLine = 5, scrollDownwardInList = false))
        assertEquals(36L, playbackWaveDelayMillis(index = 4, pivotLine = 5, scrollDownwardInList = false))
        assertEquals(62L, playbackWaveDelayMillis(index = 3, pivotLine = 5, scrollDownwardInList = false))
    }

    @Test
    fun scaleActivationPreservesElasticOvershootAndInterruptedContinuity() {
        val overshooting = PlaybackLyricFrame(focusedLine = 2, previousLine = 1, progress = 1f, scaleProgress = 1.025f)
        assertEquals(1.025f, overshooting.scaleActivation(2), 0.0001f)
        assertEquals(-0.025f, overshooting.scaleActivation(1), 0.0001f)
        assertEquals(1f, overshooting.activation(2), 0.0001f)

        val interrupted = PlaybackLyricFrame(
            focusedLine = 3,
            previousLine = 2,
            progress = 0f,
            scaleProgress = 0f,
            interruptedFrom = overshooting.asInterruptedStart(),
        )
        assertEquals(1.025f, interrupted.scaleActivation(2), 0.0001f)
        assertEquals(-0.025f, interrupted.scaleActivation(1), 0.0001f)
        assertEquals(0f, interrupted.scaleActivation(3), 0.0001f)
    }
}
