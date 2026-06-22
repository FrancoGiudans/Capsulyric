package com.example.islandlyrics.lyrics.state

internal class TrackChangeDetector {
    private var lastTrackId: String? = null

    fun didTrackChange(
        title: String,
        artist: String,
        packageName: String
    ): Boolean {
        val currentTrackId = trackId(title, artist, packageName)
        if (lastTrackId == currentTrackId) return false
        lastTrackId = currentTrackId
        return true
    }

    fun describe(
        title: String,
        artist: String,
        packageName: String
    ): String = trackId(title, artist, packageName)

    private fun trackId(
        title: String,
        artist: String,
        packageName: String
    ): String = "$title-$artist-$packageName"
}
