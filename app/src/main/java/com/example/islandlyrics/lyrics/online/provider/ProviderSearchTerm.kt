package com.example.islandlyrics.lyrics.online.provider

/** Builds a bounded provider keyword while preserving the fields used for matching. */
internal object ProviderSearchTerm {
    fun build(title: String, artist: String, album: String = ""): String {
        val parts = if (title.isNotBlank()) {
            listOf(title, artist)
        } else {
            // Cross-language fallback: an artist-only top-10 search is usually
            // too broad. Album narrows recall while duration remains the final
            // identity check inside CandidateMatcher.
            listOf(artist, album)
        }
        return parts.map(String::trim).filter(String::isNotBlank).joinToString(" ")
    }
}
