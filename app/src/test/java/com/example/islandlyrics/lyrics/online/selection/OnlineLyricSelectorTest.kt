package com.example.islandlyrics.lyrics.online.selection

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLyricSelectorTest {

    @Test
    fun annotatesIdentityEvidenceSeparatelyFromQualityScore() {
        val result = OnlineLyricFetcher.LyricResult(
            api = "QQMusic",
            lyrics = "[00:01.00]歌词",
            parsedLines = listOf(
                OnlineLyricFetcher.LyricLine(1_000L, 2_000L, "歌词")
            ),
            hasSyllable = false,
            provider = OnlineLyricProvider.QQMusic,
            matchedTitle = "唯有追逐风的时候",
            matchedArtist = "示例歌手",
            matchedAlbum = "示例专辑",
            matchedDurationMs = 181_000L,
            providerTrackId = "song-1"
        )
        val attempts = listOf(
            OnlineLyricFetcher.ProviderAttempt(
                provider = OnlineLyricProvider.QQMusic,
                result = result,
                durationMs = 120L,
                usedCleanTitleFallback = false,
                queryTitle = "唯有追逐风的时候",
                queryArtist = "示例歌手",
                queryVariant = "exact"
            )
        )

        val selected = OnlineLyricSelector { it.trim() }.selectBestResult(
            attempts = attempts,
            targetTitle = "唯有追逐风的时候",
            targetArtist = "示例歌手",
            targetAlbum = "示例专辑",
            targetDurationMs = 181_000L,
            providerOrder = listOf(OnlineLyricProvider.QQMusic),
            useSmartSelection = true
        )

        assertEquals(result, selected)
        assertEquals(129, result.identityScore)
        assertTrue(result.identityEvidence?.contains("title=36") == true)
        assertTrue(result.score > result.identityScore)
        assertEquals("exact", attempts.first().queryVariant)
    }
}
