package com.example.islandlyrics.lyrics.online.parser

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import com.example.islandlyrics.rules.ParserRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun filtersSidecarCopyrightAndPlaceholdersWhenEnabled() {
        val resultWithNoise = OnlineLyricFetcher.LyricResult(
            api = "QQMusic",
            lyrics = "[00:00.80]First sung line\n[00:05.00]Second sung line",
            parsedLines = listOf(
                OnlineLyricFetcher.LyricLine(startTime = 800L, endTime = 4_000L, text = "First sung line"),
                OnlineLyricFetcher.LyricLine(startTime = 5_000L, endTime = 8_000L, text = "Second sung line")
            ),
            hasSyllable = false,
            provider = OnlineLyricProvider.QQMusic,
            translationLyrics = """
                [00:00.00]腾讯音乐享有本翻译作品的著作权
                [00:00.80]//
                [00:05.00]第二句翻译
            """.trimIndent()
        )
        val rule = ParserRule(
            packageName = "test.music",
            receiveOnlineTranslation = true
        )

        val filtered = OnlineLyricSidecarMerger.withSidecars(
            result = resultWithNoise,
            rule = rule,
            filterInfoLines = true
        )

        assertNull(filtered[0].translation)
        assertEquals("第二句翻译", filtered[1].translation)
    }

    @Test
    fun doesNotFallbackToCoverRomanizationWhenOriginalHasNoRomanization() {
        val mainOriginal = OnlineLyricFetcher.LyricResult(
            api = "QQMusic",
            lyrics = "[00:01.00]あの日見渡した渚を",
            parsedLines = listOf(
                OnlineLyricFetcher.LyricLine(1_000L, 4_000L, "あの日見渡した渚を")
            ),
            hasSyllable = true,
            provider = OnlineLyricProvider.QQMusic,
            matchedTitle = "打上花火",
            matchedArtist = "DAOKO/米津玄师",
            matchedAlbum = "打上花火",
            matchedDurationMs = 289_000L,
            translationLyrics = "[00:01.00]那天眺望过的海岸"
        )
        val coverWithRoman = OnlineLyricFetcher.LyricResult(
            api = "Netease",
            lyrics = "[00:01.00]あの日見渡した渚を",
            parsedLines = listOf(
                OnlineLyricFetcher.LyricLine(1_000L, 4_000L, "あの日見渡した渚を")
            ),
            hasSyllable = false,
            provider = OnlineLyricProvider.Netease,
            matchedTitle = "打上花火",
            matchedArtist = "某翻唱歌手",
            matchedAlbum = "翻唱合集",
            matchedDurationMs = 289_000L,
            romanLyrics = "[00:01.00]cover roma"
        )
        val rule = ParserRule(
            packageName = "test.music",
            receiveOnlineTranslation = true,
            receiveOnlineRomanization = true
        )

        val merged = OnlineLyricSidecarMerger.mergeSidecarsFromAttempts(
            main = mainOriginal,
            attempts = listOf(attempt(mainOriginal), attempt(coverWithRoman)),
            rule = rule,
            targetTitle = "打上花火",
            targetArtist = "DAOKO × 米津玄師",
            targetAlbum = "打上花火",
            targetDurationMs = 289_000L
        )

        assertEquals("[00:01.00]那天眺望过的海岸", merged.translationLyrics)
        assertNull(merged.romanLyrics)
    }

    @Test
    fun mergesOriginalRomanizationAcrossTraditionalArtistAndMultiplicationSeparator() {
        val mainOriginal = OnlineLyricFetcher.LyricResult(
            api = "QQMusic",
            lyrics = "[00:01.00]あの日見渡した渚を",
            parsedLines = listOf(
                OnlineLyricFetcher.LyricLine(1_000L, 4_000L, "あの日見渡した渚を")
            ),
            hasSyllable = true,
            provider = OnlineLyricProvider.QQMusic,
            matchedTitle = "打上花火",
            matchedArtist = "DAOKO/米津玄师",
            matchedDurationMs = 289_000L,
            translationLyrics = "[00:01.00]那天眺望过的海岸"
        )
        val neteaseOriginalWithRoman = OnlineLyricFetcher.LyricResult(
            api = "Netease",
            lyrics = "[00:01.00]あの日見渡した渚を",
            parsedLines = listOf(
                OnlineLyricFetcher.LyricLine(1_000L, 4_000L, "あの日見渡した渚を")
            ),
            hasSyllable = false,
            provider = OnlineLyricProvider.Netease,
            matchedTitle = "打上花火",
            matchedArtist = "DAOKO / 米津玄師",
            matchedDurationMs = 289_500L,
            romanLyrics = "[00:01.00]a no hi mi wa ta shi ta"
        )
        val rule = ParserRule(
            packageName = "test.music",
            receiveOnlineTranslation = true,
            receiveOnlineRomanization = true
        )

        val merged = OnlineLyricSidecarMerger.mergeSidecarsFromAttempts(
            main = mainOriginal,
            attempts = listOf(attempt(mainOriginal), attempt(neteaseOriginalWithRoman)),
            rule = rule,
            targetTitle = "打上花火",
            targetArtist = "DAOKO × 米津玄師",
            targetDurationMs = 289_000L
        )

        assertEquals("[00:01.00]a no hi mi wa ta shi ta", merged.romanLyrics)
    }

    @Test
    fun rejectsSidecarWithLargeDurationDriftAndPreservesArtistsContainingXOrFt() {
        val main = OnlineLyricFetcher.LyricResult(
            api = "QQMusic",
            lyrics = "[00:01.00]Lyric",
            parsedLines = listOf(OnlineLyricFetcher.LyricLine(1_000L, 2_000L, "Lyric")),
            hasSyllable = true,
            provider = OnlineLyricProvider.QQMusic,
            matchedTitle = "Cruel Summer",
            matchedArtist = "Taylor Swift",
            matchedDurationMs = 178_000L
        )
        val driftedSidecar = OnlineLyricFetcher.LyricResult(
            api = "Netease",
            lyrics = "[00:01.00]Lyric",
            parsedLines = listOf(OnlineLyricFetcher.LyricLine(1_000L, 2_000L, "Lyric")),
            hasSyllable = false,
            provider = OnlineLyricProvider.Netease,
            matchedTitle = "Cruel Summer",
            matchedArtist = "Taylor Swift",
            matchedDurationMs = 210_000L,
            translationLyrics = "[00:01.00]错版翻译"
        )
        val alignedSidecar = driftedSidecar.copy(
            provider = OnlineLyricProvider.Kugou,
            api = "Kugou",
            matchedDurationMs = 179_000L,
            translationLyrics = "[00:01.00]正确翻译"
        )
        val rule = ParserRule(
            packageName = "test.music",
            receiveOnlineTranslation = true
        )

        val merged = OnlineLyricSidecarMerger.mergeSidecarsFromAttempts(
            main = main,
            attempts = listOf(attempt(main), attempt(driftedSidecar), attempt(alignedSidecar)),
            rule = rule,
            targetTitle = "Cruel Summer",
            targetArtist = "Taylor Swift",
            targetDurationMs = 178_000L
        )

        assertEquals("[00:01.00]正确翻译", merged.translationLyrics)
    }

    @Test
    fun doesNotMergeStudioSidecarsIntoTheFirstTakeMainLyricEvenWithSameDuration() {
        val tftMain = OnlineLyricFetcher.LyricResult(
            api = "QQMusic",
            lyrics = "[00:18.00]誰が袖に咲く幻花",
            parsedLines = listOf(OnlineLyricFetcher.LyricLine(18_000L, 22_000L, "誰が袖に咲く幻花")),
            hasSyllable = false,
            provider = OnlineLyricProvider.QQMusic,
            matchedTitle = "残響散歌 - From THE FIRST TAKE",
            matchedArtist = "Aimer",
            matchedAlbum = "残響散歌 - From THE FIRST TAKE",
            matchedDurationMs = 184_952L
        )
        val studioSidecar = OnlineLyricFetcher.LyricResult(
            api = "Netease",
            lyrics = "[00:01.00]誰が袖に咲く幻花",
            parsedLines = listOf(OnlineLyricFetcher.LyricLine(1_000L, 5_000L, "誰が袖に咲く幻花")),
            hasSyllable = false,
            provider = OnlineLyricProvider.Netease,
            matchedTitle = "残響散歌",
            matchedArtist = "Aimer",
            matchedAlbum = "残響散歌",
            matchedDurationMs = 184_000L,
            translationLyrics = "[00:01.00]错轴录音室版翻译",
            romanLyrics = "[00:01.00]ta ga so de ni sa ku"
        )
        val rule = ParserRule(
            packageName = "test.music",
            receiveOnlineTranslation = true,
            receiveOnlineRomanization = true
        )

        val merged = OnlineLyricSidecarMerger.mergeSidecarsFromAttempts(
            main = tftMain,
            attempts = listOf(attempt(tftMain), attempt(studioSidecar)),
            rule = rule,
            targetTitle = "残響散歌 - From THE FIRST TAKE",
            targetArtist = "Aimer",
            targetDurationMs = 184_952L
        )

        assertNull(merged.translationLyrics)
        assertNull(merged.romanLyrics)
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
