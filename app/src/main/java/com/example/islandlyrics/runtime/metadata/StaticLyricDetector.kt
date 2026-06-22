package com.example.islandlyrics.runtime.metadata

import com.example.islandlyrics.lyrics.state.LyricRepository

object StaticLyricDetector {
    fun isStaticMetadataLyric(
        lyric: String,
        metadata: LyricRepository.MediaInfo?
    ): Boolean {
        if (metadata == null) return false
        val title = metadata.title
        val artist = metadata.artist
        return lyric.equals(title, ignoreCase = true) ||
            lyric.equals(artist, ignoreCase = true) ||
            lyric.equals("$title - $artist", ignoreCase = true) ||
            lyric.equals("$artist - $title", ignoreCase = true)
    }
}
