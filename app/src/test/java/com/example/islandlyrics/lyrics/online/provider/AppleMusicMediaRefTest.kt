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
        assertEquals("123456789", AppleMusicMediaRef.extractSongId("apple:song:123456789"))
    }

    @Test
    fun ignoresNonAppleUrisAndMalformedIds() {
        assertNull(AppleMusicMediaRef.extractStorefront("https://example.com/jp/song/123"))
        assertNull(AppleMusicMediaRef.extractSongId("https://music.apple.com/jp/song/example/not-a-number"))
    }

    @Test
    fun infersJapaneseAndKoreanStorefrontsFromNativeScripts() {
        assertEquals("jp", AppleMusicStorefrontHint.infer("ただ風を追いかけて", "Robin"))
        assertEquals("kr", AppleMusicStorefrontHint.infer("바람을 따라서", "Robin"))
        assertNull(AppleMusicStorefrontHint.infer("Only by Chasing the Wind", "Robin"))
    }

    @Test
    fun keepsExplicitStorefrontFirstAndAddsSafeFallbacks() {
        assertEquals(
            listOf("us", "jp", "cn"),
            AppleMusicStorefrontHint.candidates(
                sourceStorefront = "US",
                configuredStorefront = "cn",
                title = "ただ風を追いかけて",
                artist = "Robin, HOYO-MiX & Chevy",
                album = ""
            )
        )
        assertEquals(
            listOf("jp", "cn", "us"),
            AppleMusicStorefrontHint.candidates(
                sourceStorefront = null,
                configuredStorefront = "cn",
                title = "ただ風を追いかけて",
                artist = "Robin, HOYO-MiX & Chevy",
                album = ""
            )
        )
    }
}
