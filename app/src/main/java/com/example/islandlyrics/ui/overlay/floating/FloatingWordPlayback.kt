package com.example.islandlyrics.ui.overlay.floating

import com.example.islandlyrics.ui.overlay.model.LyricPresentation

internal fun LyricPresentation.WordProgress.isFullySung(): Boolean =
    syllables.isNotEmpty() && syllables.all { it.progress >= 1f }

internal class FloatingWordPlayback {
    private var anchorPosition: Long? = null
    private var anchorTime = 0L
    private var playing = false
    private var previousSample: Long? = null

    fun acceptPosition(position: Long?, now: Long): Boolean {
        val previous = previousSample
        val movedBackward = position != null && previous != null && position < previous
        previousSample = position
        anchorPosition = position
        anchorTime = now
        return movedBackward
    }

    fun setPlaying(playing: Boolean, now: Long) {
        if (this.playing == playing) return
        anchorPosition = positionAt(now)
        anchorTime = now
        this.playing = playing
    }

    fun positionAt(now: Long): Long? =
        anchorPosition?.let { if (playing) it + (now - anchorTime).coerceAtLeast(0L) else it }
}
