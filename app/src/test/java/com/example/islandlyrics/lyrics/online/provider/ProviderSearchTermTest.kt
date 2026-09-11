package com.example.islandlyrics.lyrics.online.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderSearchTermTest {
    @Test
    fun normalQueryUsesTitleAndArtist() {
        assertEquals(
            "Song Artist",
            ProviderSearchTerm.build("Song", "Artist", "Album")
        )
    }

    @Test
    fun crossLanguageFallbackUsesArtistAndAlbum() {
        assertEquals(
            "Artist Album",
            ProviderSearchTerm.build("", "Artist", "Album")
        )
    }
}
