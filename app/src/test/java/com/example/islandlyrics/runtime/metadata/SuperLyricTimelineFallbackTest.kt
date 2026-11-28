package com.example.islandlyrics.runtime.metadata

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.lyrics.state.LyricRepository
import org.junit.Assert.*
import org.junit.Test

class SuperLyricTimelineFallbackTest {
    private val track = LyricRepository.MediaInfo("Song", "Artist", "player", 10000, mediaId = "1")
    private val timeline = LyricRepository.ParsedLyricsInfo(
        listOf(LyricLine(1000, 2000, "line")), false, "player", "SuperLyric",
        LyricRepository.TimelineCapability.MULTI_LINE
    )

    @Test fun timedWholeDocumentCanAvoidNetworkEvenWithoutWordTiming() {
        assertTrue(hasCurrentSuperLyricTimeline(track, track, timeline))
    }

    @Test fun singleLegacyLineAndUntimedDocumentsStillAllowFallback() {
        assertFalse(hasCurrentSuperLyricTimeline(track, track,
            timeline.copy(timelineCapability = LyricRepository.TimelineCapability.ACTIVE_LINE_ONLY)))
        assertFalse(hasCurrentSuperLyricTimeline(track, track, timeline.copy(lines = listOf(LyricLine(0, 0, "text")))))
    }

    @Test fun staleTrackOrOtherSourceCannotSuppressThisTracksNetworkRequest() {
        assertFalse(hasCurrentSuperLyricTimeline(track, track.copy(mediaId = "2"), timeline))
        assertFalse(hasCurrentSuperLyricTimeline(track, track.copy(packageName = "other"), timeline))
        assertFalse(hasCurrentSuperLyricTimeline(track, track, timeline.copy(apiPath = "Lyricon")))
        assertFalse(hasCurrentSuperLyricTimeline(track, null, timeline))
        assertFalse(hasCurrentSuperLyricTimeline(track, track, null))
    }

    @Test fun lyriconTimelineSuppressesRedundantNetworkRequestForSameTrack() {
        val lyriconTimeline = timeline.copy(apiPath = "Lyricon")
        assertTrue(hasCurrentLyriconTimeline(track, track, lyriconTimeline))
        assertFalse(hasCurrentLyriconTimeline(track, track.copy(mediaId = "2"), lyriconTimeline))
        assertFalse(hasCurrentLyriconTimeline(track, track.copy(packageName = "other"), lyriconTimeline))
        assertFalse(hasCurrentLyriconTimeline(track, track, timeline))
        assertFalse(hasCurrentLyriconTimeline(track, track, lyriconTimeline.copy(
            timelineCapability = LyricRepository.TimelineCapability.ACTIVE_LINE_ONLY
        )))
        assertFalse(hasCurrentLyriconTimeline(track, null, lyriconTimeline))
    }
}
