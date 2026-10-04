package com.example.islandlyrics.lyrics.online.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineLyricTrackIdTest {
    @Test
    fun acceptsQqNumericIdsAndPreservesMidCase() {
        assertEquals("204422870", OnlineLyricProvider.QQMusic.normalizeTrackId(" 204422870 "))
        assertEquals("001RaE0n4RrGX9", OnlineLyricProvider.QQMusic.normalizeTrackId("\n001RaE0n4RrGX9\n"))
    }

    @Test
    fun kugouRequiresACompleteHashInsteadOfANumericSongId() {
        val hash = "0123456789abcdef0123456789ABCDEF"
        assertEquals(hash.uppercase(), OnlineLyricProvider.Kugou.normalizeTrackId(" $hash "))
        assertNull(OnlineLyricProvider.Kugou.normalizeTrackId("123456789"))
        assertNull(OnlineLyricProvider.Kugou.normalizeTrackId(hash.dropLast(1)))
        assertNull(OnlineLyricProvider.Kugou.normalizeTrackId("g" + hash.drop(1)))
    }

    @Test
    fun keepsLongSodaIdsAsExactStrings() {
        assertEquals("7139795201807091714", OnlineLyricProvider.SodaMusic.normalizeTrackId("7139795201807091714"))
    }

    @Test
    fun numericPlatformsRejectMidsAndNonAsciiDigits() {
        listOf(
            OnlineLyricProvider.Netease, OnlineLyricProvider.SodaMusic,
            OnlineLyricProvider.Lrclib, OnlineLyricProvider.AppleMusic, OnlineLyricProvider.Musixmatch
        ).forEach { provider ->
            assertEquals("12345", provider.normalizeTrackId(" 12345 "))
            assertNull(provider.normalizeTrackId("001RaE0n4RrGX9"))
            assertNull(provider.normalizeTrackId("１２３４５"))
        }
    }

    @Test
    fun neteaseRejectsIdsThatCannotBeRepresentedByItsApi() {
        assertNull(OnlineLyricProvider.Netease.normalizeTrackId("9223372036854775808"))
    }

    @Test
    fun rejectsLinksEmptyIdsAndQueryFragmentsForEveryProvider() {
        OnlineLyricProvider.entries.forEach { provider ->
            listOf("", " ", "0", "000", "-123", "12 34", "1/2", "1&x=2", "https://example.com/song/123")
                .forEach { input -> assertNull("$provider: $input", provider.normalizeTrackId(input)) }
        }
    }

    @Test
    fun lrcApiIsNotExposedAsAnIdProvider() {
        assertFalse(OnlineLyricProvider.LrcApi.supportsTrackId)
        assertNull(OnlineLyricProvider.LrcApi.normalizeTrackId("12345"))
        assertEquals(7, OnlineLyricProvider.entries.count { it.supportsTrackId })
    }
}
