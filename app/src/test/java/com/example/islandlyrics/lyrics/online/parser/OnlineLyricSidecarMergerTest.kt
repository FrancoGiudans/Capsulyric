package com.example.islandlyrics.lyrics.online.parser

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import com.example.islandlyrics.rules.ParserRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OnlineLyricSidecarMergerTest {

    @Test
    fun mergesSidecarsFromProvidersDifferentFromMainLyricSource() {
        val main = result(
            provider = OnlineLyricProvider.LrcApi,
            api = "LrcApi"
        )
        val translation = result(
            provider = OnlineLyricProvider.Netease,
            api = "Netease",
            translationLyrics = "[00:01.00]翻译歌词"
        )
        val romanization = result(
            provider = OnlineLyricProvider.QQMusic,
            api = "QQMusic",
            romanLyrics = "[00:01.20]pin yin"
        )
        val attempts = listOf(
            attempt(main),
            attempt(translation),
            attempt(romanization)
        )
        val rule = ParserRule(
            packageName = "test.music",
            receiveOnlineTranslation = true,
            receiveOnlineRomanization = true
        )

        val merged = OnlineLyricSidecarMerger.mergeSidecarsFromAttempts(
            main = main,
            attempts = attempts,
            rule = rule,
            targetTitle = "Song",
            targetArtist = "Artist"
        )
        val lines = OnlineLyricSidecarMerger.withSidecars(
            result = merged,
            rule = rule
        )

        assertEquals("[00:01.00]翻译歌词", merged.translationLyrics)
        assertEquals("[00:01.20]pin yin", merged.romanLyrics)
        assertNotNull(lines.single().translation)
        assertEquals("翻译歌词", lines.single().translation)
        assertEquals("pin yin", lines.single().roma)
    }

    private fun result(
        provider: OnlineLyricProvider,
        api: String,
        translationLyrics: String? = null,
        romanLyrics: String? = null
    ) = OnlineLyricFetcher.LyricResult(
        api = api,
        lyrics = "[00:01.00]Song lyric",
        parsedLines = listOf(
            OnlineLyricFetcher.LyricLine(
                startTime = 1_000L,
                endTime = 2_000L,
                text = "Song lyric"
            )
        ),
        hasSyllable = false,
        provider = provider,
        matchedTitle = "Song",
        matchedArtist = "Artist",
        translationLyrics = translationLyrics,
        romanLyrics = romanLyrics
    )

    private fun attempt(result: OnlineLyricFetcher.LyricResult) =
        OnlineLyricFetcher.ProviderAttempt(
            provider = result.provider,
            result = result,
            durationMs = 10L,
            usedCleanTitleFallback = false,
            queryTitle = "Song",
            queryArtist = "Artist",
            queryVariant = "exact"
        )
}
