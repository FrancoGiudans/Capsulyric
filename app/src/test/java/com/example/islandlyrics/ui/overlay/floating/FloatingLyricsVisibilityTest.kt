package com.example.islandlyrics.ui.overlay.floating

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingLyricsVisibilityTest {
    private val song = FloatingLyricsTrack("player", "one", "", "First song", "Artist")

    @Test
    fun closeForCurrentTrackSurvivesResumeAndIncompleteMetadata() {
        val hidden = FloatingLyricsSession().withPlayback(FloatingLyricsPlayback.PLAYING, song)
            .copy(dismissal = FloatingLyricsDismissal.UNTIL_NEXT_TRACK)

        val resumed = hidden.withPlayback(FloatingLyricsPlayback.PAUSED, song.copy(mediaId = ""))
            .withPlayback(FloatingLyricsPlayback.INACTIVE, null)
            .withPlayback(FloatingLyricsPlayback.PLAYING, null)
            .withPlayback(FloatingLyricsPlayback.PLAYING, song)

        assertTrue(resumed.isHidden)
        assertFalse(resumed.shouldShow(true, true, true, false))
        assertTrue(resumed.copy(dismissal = FloatingLyricsDismissal.VISIBLE).shouldShow(true, true, true, false))
    }

    @Test
    fun nextTrackRestoresOnlyWhenShowingIsEnabled() {
        val hidden = FloatingLyricsSession().withPlayback(FloatingLyricsPlayback.PLAYING, song)
            .copy(dismissal = FloatingLyricsDismissal.UNTIL_NEXT_TRACK)
        val next = hidden.withPlayback(FloatingLyricsPlayback.PLAYING, song.copy(mediaId = "two"))

        assertFalse(next.isHidden)
        assertTrue(next.shouldShow(true, false, true, false))
        assertFalse(next.shouldShow(false, false, true, false))
    }

    @Test
    fun firstTrackIdentityDoesNotRestoreANextTrackDismissal() {
        val hidden = FloatingLyricsSession().hide(FloatingLyricsRecovery.NEXT_TRACK)
        val current = hidden.withPlayback(FloatingLyricsPlayback.PLAYING, song)
        assertTrue(current.isHidden)
        assertFalse(current.withPlayback(FloatingLyricsPlayback.PLAYING, song.copy(mediaId = "two")).isHidden)
    }

    @Test
    fun resumePolicyRequiresActualResumeOfTheSameMediaSession() {
        val hidden = FloatingLyricsSession().withPlayback(FloatingLyricsPlayback.PLAYING, song, "session")
            .hide(FloatingLyricsRecovery.ON_RESUME)
        val continuousPlayback = hidden.withPlayback(FloatingLyricsPlayback.PLAYING, song.copy(mediaId = "two"), "session")
        assertTrue(continuousPlayback.isHidden)

        val paused = hidden.withPlayback(FloatingLyricsPlayback.PAUSED, song, "session")
        val buffering = paused.withPlayback(FloatingLyricsPlayback.PLAYING, song, "session", isActuallyPlaying = false)
        assertTrue(buffering.isHidden)
        assertFalse(buffering.withPlayback(FloatingLyricsPlayback.PLAYING, song, "session").isHidden)
        assertTrue(paused.withPlayback(FloatingLyricsPlayback.PLAYING, song, "other").isHidden)
        assertTrue(paused.endMediaSession("session")
            .withPlayback(FloatingLyricsPlayback.PLAYING, song, "session").isHidden)
    }

    @Test
    fun hidingAnAlreadyPausedWindowRestoresOnSameSessionResume() {
        val hidden = FloatingLyricsSession().withPlayback(FloatingLyricsPlayback.PAUSED, song, "session")
            .hide(FloatingLyricsRecovery.ON_RESUME)
        val interrupted = hidden.withPlayback(FloatingLyricsPlayback.INACTIVE, null)
        assertFalse(interrupted.withPlayback(FloatingLyricsPlayback.PLAYING, song, "session").isHidden)
    }

    @Test
    fun metadataCanConfirmTrackChangeWhenThePlayerHasNoMediaId() {
        val original = song.copy(mediaId = "")
        val hidden = FloatingLyricsSession().withPlayback(FloatingLyricsPlayback.PLAYING, original)
            .copy(dismissal = FloatingLyricsDismissal.UNTIL_NEXT_TRACK)

        assertTrue(hidden.withPlayback(FloatingLyricsPlayback.PLAYING, original.copy(mediaId = "one")).isHidden)
        assertFalse(hidden.withPlayback(FloatingLyricsPlayback.PLAYING, original.copy(title = "Second song")).isHidden)
    }

    @Test
    fun manualDismissalSurvivesChangesOfTrackAndPlaybackSession() {
        val hidden = FloatingLyricsSession(dismissal = FloatingLyricsDismissal.UNTIL_MANUAL)
            .withPlayback(FloatingLyricsPlayback.PLAYING, song)
            .withPlayback(FloatingLyricsPlayback.INACTIVE, null)
            .withPlayback(FloatingLyricsPlayback.PLAYING, song.copy(mediaId = "two"))

        assertTrue(hidden.isHidden)
        assertFalse(hidden.shouldShow(true, true, true, false))
        assertTrue(hidden.copy(dismissal = FloatingLyricsDismissal.VISIBLE).shouldShow(true, true, true, false))
    }

    @Test
    fun pauseAndWindowAvailabilityFollowTheVisibilitySwitches() {
        val playing = FloatingLyricsSession().withPlayback(FloatingLyricsPlayback.PLAYING, song)
        val paused = playing.withPlayback(FloatingLyricsPlayback.PAUSED, song)

        assertFalse(paused.shouldShow(true, false, true, false))
        assertTrue(paused.shouldShow(true, true, true, false))
        assertFalse(paused.shouldShow(true, true, false, false))
        assertFalse(paused.shouldShow(true, true, true, true))
        assertFalse(paused.copy(dismissal = FloatingLyricsDismissal.UNTIL_NEXT_TRACK).shouldShow(true, true, true, false))
        assertFalse(paused.withPlayback(FloatingLyricsPlayback.INACTIVE, null).shouldShow(true, true, true, false))
        assertFalse(playing.withPlayback(FloatingLyricsPlayback.PAUSED, song, wasVisible = false)
            .shouldShow(true, true, true, false))
        assertFalse(FloatingLyricsSession().withPlayback(FloatingLyricsPlayback.PAUSED, song)
            .shouldShow(true, true, true, false))
    }
}
