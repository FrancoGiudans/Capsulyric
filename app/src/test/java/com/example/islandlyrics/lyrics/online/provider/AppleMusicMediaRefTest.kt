package com.example.islandlyrics.lyrics.online.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppleMusicMediaRefTest {

    @Test
    fun extractsStorefrontFromAppleMusicUri() {
        assertEquals(
            "jp",
            AppleMusicMediaRef.extractStorefront(
                "https://music.apple.com/jp/album/example/123456789?i=987654321"
            )
        )
    }

    @Test
    fun extractsSongIdFromQueryAndSongPath() {
        assertEquals(
            "987654321",
            AppleMusicMediaRef.extractSongId("https://music.apple.com/jp/album/example/123456789?i=987654321")
        )
        assertEquals(
            "123456789",
            AppleMusicMediaRef.extractSongId("https://music.apple.com/jp/song/example/123456789")
        )
    }

    @Test
    fun ignoresNonAppleUrisAndMalformedIds() {
        assertNull(AppleMusicMediaRef.extractStorefront("https://example.com/jp/song/123"))
        assertNull(AppleMusicMediaRef.extractSongId("https://music.apple.com/jp/song/example/not-a-number"))
    }
}
