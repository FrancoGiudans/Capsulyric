package com.example.islandlyrics.lyrics.source

import org.junit.Assert.*
import org.junit.Test

class SuperLyricSessionTest {
    private val track = SuperLyricTrack("player", "Song", "Artist", "id")

    @Test fun callbackQueuedBeforeStopCannotWriteAfterRestart() {
        val session = SuperLyricSession()
        val old = session.begin()
        session.end()
        val next = session.begin()
        assertFalse(session.accepts(old, track, track))
        assertTrue(session.accepts(next, track, track))
    }

    @Test fun callbackFromOldBinderCannotWriteAfterReconnection() {
        val session = SuperLyricSession()
        val old = session.begin()
        val next = session.begin()
        assertFalse(session.accepts(old, track, track))
        assertTrue(session.accepts(next, track, track))
    }

    @Test fun queuedCallbackIsDiscardedWhenTrackChanges() {
        val session = SuperLyricSession()
        val token = session.begin()
        assertFalse(session.accepts(token, track, track.copy(mediaId = "another-id")))
        assertFalse(session.accepts(token, track, track.copy(packageName = "other")))
    }

    @Test fun stoppedAndMissingSessionNeverAcceptCallbacks() {
        val session = SuperLyricSession()
        val token = session.begin()
        assertFalse(session.accepts(token, null, track))
        assertFalse(session.accepts(token, track, null))
        session.end()
        assertFalse(session.accepts(token, track, track))
    }
}
