package com.example.islandlyrics.lyrics.online.selection

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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
        assertEquals(78, result.identityScore)
        assertTrue(result.identityEvidence?.contains("title=36") == true)
        assertTrue(result.score > result.identityScore)
        assertEquals("exact", attempts.first().queryVariant)
    }

    @Test
    fun exactTextCannotOverrideHardDurationConflict() {
        val result = OnlineLyricFetcher.LyricResult(
            api = "QQMusic",
            lyrics = "[00:01.00]歌词",
            parsedLines = listOf(OnlineLyricFetcher.LyricLine(1_000L, 2_000L, "歌词")),
            hasSyllable = false,
            provider = OnlineLyricProvider.QQMusic,
            matchedTitle = "Same Song",
            matchedArtist = "Same Artist",
            matchedAlbum = "Studio Album",
            matchedDurationMs = 245_000L,
            providerTrackId = "wrong-version"
        )
        val attempts = listOf(
            OnlineLyricFetcher.ProviderAttempt(
                provider = OnlineLyricProvider.QQMusic,
                result = result,
                durationMs = 100L,
                usedCleanTitleFallback = false
            )
        )

        assertNull(
            OnlineLyricSelector { it.trim() }.selectBestResult(
                attempts = attempts,
                targetTitle = "Same Song",
                targetArtist = "Same Artist",
                targetAlbum = "Studio Album",
                targetDurationMs = 181_000L,
                providerOrder = listOf(OnlineLyricProvider.QQMusic),
                useSmartSelection = true
            )
        )
    }

    @Test
    fun romanizedTitleIsAcceptedByFinalSelector() {
        val result = OnlineLyricFetcher.LyricResult(
            api = "QQMusic",
            lyrics = "[00:01.00]歌词",
            parsedLines = listOf(
                OnlineLyricFetcher.LyricLine(1_000L, 2_000L, "歌词")
            ),
            hasSyllable = false,
            provider = OnlineLyricProvider.QQMusic,
            matchedTitle = "さよなら",
            matchedArtist = "Example Artist"
        )

        val selected = OnlineLyricSelector { it.trim() }.selectBestResult(
            attempts = listOf(
                OnlineLyricFetcher.ProviderAttempt(
                    provider = OnlineLyricProvider.QQMusic,
                    result = result,
                    durationMs = 100L,
                    usedCleanTitleFallback = false
                )
            ),
            targetTitle = "sayonara",
            targetArtist = "Example Artist",
            providerOrder = listOf(OnlineLyricProvider.QQMusic),
            useSmartSelection = true
        )

        assertEquals(result, selected)
        assertTrue(result.identityEvidence?.contains("title=24") == true)
    }

    @Test
    fun mediumConfidenceResultDoesNotCancelSlowerProviders() {
        val result = OnlineLyricFetcher.LyricResult(
            api = "LrcApi",
            lyrics = "[00:01.00]lyrics",
            parsedLines = listOf(OnlineLyricFetcher.LyricLine(1_000L, 2_000L, "lyrics")),
            hasSyllable = false,
            provider = OnlineLyricProvider.LrcApi,
            matchedTitle = "Same Song",
            matchedArtist = null,
            providerTrackId = "candidate"
        )

        assertFalse(
            OnlineLyricSelector { it.trim() }.isPotentiallyMatching(
                result = result,
                targetTitle = "Same Song",
                targetArtist = "Same Artist",
                targetAlbum = "",
                targetDurationMs = 0L
            )
        )
    }

    @Test
    fun legacyNoMetadataCannotOverrideReliableTargetIdentity() {
        val result = OnlineLyricFetcher.LyricResult(
            api = "LrcApi",
            lyrics = "[00:01.00]unrelated lyrics",
            parsedLines = listOf(
                OnlineLyricFetcher.LyricLine(1_000L, 2_000L, "unrelated lyrics")
            ),
            hasSyllable = false,
            provider = OnlineLyricProvider.LrcApi
        )
        val attempts = listOf(
            OnlineLyricFetcher.ProviderAttempt(
                provider = OnlineLyricProvider.LrcApi,
                result = result,
                durationMs = 100L,
                usedCleanTitleFallback = false,
                queryTitle = "丽都假日",
                queryArtist = "Sān-Z & HOYO-MiX",
                queryVariant = "apple_alias:cn"
            )
        )

        assertNull(
            OnlineLyricSelector { it.trim() }.selectBestResult(
                attempts = attempts,
                targetTitle = "丽都假日",
                targetArtist = "Sān-Z & HOYO-MiX",
                targetAlbum = "丽都假日",
                targetDurationMs = 258_641L,
                providerOrder = listOf(OnlineLyricProvider.LrcApi),
                useSmartSelection = true
            )
        )
    }

    @Test
    fun exactPlainLrcOutranksWeakSubstringSyllableMatch() {
        val exactPlain = OnlineLyricFetcher.LyricResult(
            api = "Netease",
            lyrics = "[00:01.00]晴天",
            parsedLines = List(12) { i ->
                OnlineLyricFetcher.LyricLine(i * 1_000L, (i + 1) * 1_000L, "line $i")
            },
            hasSyllable = false,
            provider = OnlineLyricProvider.Netease,
            matchedTitle = "晴天",
            matchedArtist = "周杰伦"
        )
        val weakSubstringSyllable = OnlineLyricFetcher.LyricResult(
            api = "QQMusic",
            lyrics = "[00:01.00]晴天娃娃",
            parsedLines = List(12) { i ->
                OnlineLyricFetcher.LyricLine(
                    startTime = i * 1_000L,
                    endTime = (i + 1) * 1_000L,
                    text = "line $i",
                    syllables = listOf(OnlineLyricFetcher.SyllableInfo(i * 1_000L, (i + 1) * 1_000L, "line"))
                )
            },
            hasSyllable = true,
            provider = OnlineLyricProvider.QQMusic,
            matchedTitle = "晴天娃娃",
            matchedArtist = "周杰伦"
        )

        val selected = OnlineLyricSelector().selectBestResult(
            attempts = listOf(
                OnlineLyricFetcher.ProviderAttempt(OnlineLyricProvider.Netease, exactPlain, 100L, false),
                OnlineLyricFetcher.ProviderAttempt(OnlineLyricProvider.QQMusic, weakSubstringSyllable, 100L, false)
            ),
            targetTitle = "晴天",
            targetArtist = "周杰伦",
            providerOrder = listOf(OnlineLyricProvider.QQMusic, OnlineLyricProvider.Netease),
            useSmartSelection = true
        )

        assertEquals(exactPlain, selected)
        assertTrue(exactPlain.score > weakSubstringSyllable.score)
    }

    @Test
    fun sameDurationSyllableLyricOutscoresAlbumMatchedAndSubtitledPlainLrc() {
        // Plain LRC has exact subtitle + exact album, while word-by-word lyric
        // comes from a single/empty-album entry with whole-second duration truncation.
        val albumPlainLrc = OnlineLyricFetcher.LyricResult(
            api = "Netease",
            lyrics = "[00:01.00]僕は...",
            parsedLines = List(15) { i ->
                OnlineLyricFetcher.LyricLine(i * 1_000L, (i + 1) * 1_000L, "line $i")
            },
            hasSyllable = false,
            provider = OnlineLyricProvider.Netease,
            matchedTitle = "「僕は...」 (「我...」)",
            matchedArtist = "あたらよ",
            matchedAlbum = "朝露は木漏れ日に溶けて",
            matchedDurationMs = 239_761L
        )
        val singleSyllableKrc = OnlineLyricFetcher.LyricResult(
            api = "Kugou",
            lyrics = "[1000,1000]<0,500,0>僕<500,500,0>は",
            parsedLines = List(15) { i ->
                OnlineLyricFetcher.LyricLine(
                    startTime = i * 1_000L,
                    endTime = (i + 1) * 1_000L,
                    text = "line $i",
                    syllables = listOf(OnlineLyricFetcher.SyllableInfo(i * 1_000L, (i + 1) * 1_000L, "line"))
                )
            },
            hasSyllable = true,
            provider = OnlineLyricProvider.Kugou,
            matchedTitle = "「僕は...」",
            matchedArtist = "あたらよ",
            matchedAlbum = "「僕は...」",
            matchedDurationMs = 239_000L
        )

        val selected = OnlineLyricSelector().selectBestResult(
            attempts = listOf(
                OnlineLyricFetcher.ProviderAttempt(OnlineLyricProvider.Netease, albumPlainLrc, 100L, false),
                OnlineLyricFetcher.ProviderAttempt(OnlineLyricProvider.Kugou, singleSyllableKrc, 120L, false)
            ),
            targetTitle = "「僕は...」 (「我...」)",
            targetArtist = "あたらよ",
            targetAlbum = "朝露は木漏れ日に溶けて",
            targetDurationMs = 239_761L,
            providerOrder = listOf(OnlineLyricProvider.Netease, OnlineLyricProvider.Kugou),
            useSmartSelection = true
        )

        assertEquals(singleSyllableKrc, selected)
        assertTrue(
            "Expected syllable score (${singleSyllableKrc.score}) > plain LRC score (${albumPlainLrc.score})",
            singleSyllableKrc.score > albumPlainLrc.score
        )
    }

    @Test
    fun bilingualPrimaryTitleSyllableLyricOutscoresFullTitlePlainLrc() {
        val fullTitlePlain = OnlineLyricFetcher.LyricResult(
            api = "Lrclib",
            lyrics = "[00:01.00]lyric",
            parsedLines = List(12) { i ->
                OnlineLyricFetcher.LyricLine(i * 1_000L, (i + 1) * 1_000L, "line $i")
            },
            hasSyllable = false,
            provider = OnlineLyricProvider.Lrclib,
            matchedTitle = "无中生有 Ex Nihilo",
            matchedArtist = "HOYO-MiX/當山みれい",
            matchedDurationMs = 205_000L
        )
        val primaryTitleSyllable = OnlineLyricFetcher.LyricResult(
            api = "QQMusic",
            lyrics = "[1000,1000]词(1000,1000)",
            parsedLines = List(12) { i ->
                OnlineLyricFetcher.LyricLine(
                    startTime = i * 1_000L,
                    endTime = (i + 1) * 1_000L,
                    text = "line $i",
                    syllables = listOf(OnlineLyricFetcher.SyllableInfo(i * 1_000L, (i + 1) * 1_000L, "line"))
                )
            },
            hasSyllable = true,
            provider = OnlineLyricProvider.QQMusic,
            matchedTitle = "无中生有",
            matchedArtist = "HOYO-MiX/當山みれい",
            matchedDurationMs = 205_000L
        )

        val selected = OnlineLyricSelector().selectBestResult(
            attempts = listOf(
                OnlineLyricFetcher.ProviderAttempt(OnlineLyricProvider.Lrclib, fullTitlePlain, 80L, false),
                OnlineLyricFetcher.ProviderAttempt(OnlineLyricProvider.QQMusic, primaryTitleSyllable, 120L, false)
            ),
            targetTitle = "无中生有 Ex Nihilo",
            targetArtist = "HOYO-MiX/當山みれい",
            targetDurationMs = 205_000L,
            providerOrder = listOf(OnlineLyricProvider.Lrclib, OnlineLyricProvider.QQMusic),
            useSmartSelection = true
        )

        assertEquals(primaryTitleSyllable, selected)
        assertTrue(primaryTitleSyllable.score > fullTitlePlain.score)
    }

    @Test
    fun versionReferenceTitlePreservesLiveTagDuringCleanTitleSelection() {
        val liveMatch = OnlineLyricFetcher.LyricResult(
            api = "QQMusic",
            lyrics = "[00:01.00]live lyric",
            parsedLines = listOf(OnlineLyricFetcher.LyricLine(1_000L, 2_000L, "live lyric")),
            hasSyllable = false,
            provider = OnlineLyricProvider.QQMusic,
            matchedTitle = "Song (Live)",
            matchedArtist = "Artist",
            matchedDurationMs = 240_000L
        )

        val selected = OnlineLyricSelector().selectBestResult(
            attempts = listOf(
                OnlineLyricFetcher.ProviderAttempt(OnlineLyricProvider.QQMusic, liveMatch, 100L, false)
            ),
            targetTitle = "Song",
            targetArtist = "Artist",
            targetAlbum = "",
            targetDurationMs = 240_000L,
            providerOrder = listOf(OnlineLyricProvider.QQMusic),
            useSmartSelection = true,
            versionReferenceTitle = "Song (Live)"
        )

        assertEquals(liveMatch, selected)
    }

    @Test
    fun titlelessFallbackRejectsDifferentSongWithLooseDurationOnly() {
        val wrongSong = OnlineLyricFetcher.LyricResult(
            api = "QQMusic",
            lyrics = "[00:01.00]wrong song",
            parsedLines = listOf(OnlineLyricFetcher.LyricLine(1_000L, 2_000L, "wrong song")),
            hasSyllable = true,
            provider = OnlineLyricProvider.QQMusic,
            matchedTitle = "Totally Other Song",
            matchedArtist = "Artist",
            matchedAlbum = "Different Album",
            matchedDurationMs = 213_000L
        )

        val selected = OnlineLyricSelector().selectBestResult(
            attempts = listOf(
                OnlineLyricFetcher.ProviderAttempt(OnlineLyricProvider.QQMusic, wrongSong, 100L, true)
            ),
            targetTitle = "",
            targetArtist = "Artist",
            targetAlbum = "Target Album",
            targetDurationMs = 210_000L,
            providerOrder = listOf(OnlineLyricProvider.QQMusic),
            useSmartSelection = true
        )

        assertNull(selected)
    }

    @Test
    fun theFirstTakePlainLrcRejectsAndOutranksSameDurationStudioSyllableLyric() {
        val studioSyllable = OnlineLyricFetcher.LyricResult(
            api = "Kugou",
            lyrics = "[1000,1000]<0,500,0>残<500,500,0>響",
            parsedLines = List(15) { i ->
                OnlineLyricFetcher.LyricLine(
                    startTime = i * 1_000L,
                    endTime = (i + 1) * 1_000L,
                    text = "line $i",
                    syllables = listOf(OnlineLyricFetcher.SyllableInfo(i * 1_000L, (i + 1) * 1_000L, "line"))
                )
            },
            hasSyllable = true,
            provider = OnlineLyricProvider.Kugou,
            matchedTitle = "残響散歌",
            matchedArtist = "Aimer",
            matchedAlbum = "残響散歌",
            matchedDurationMs = 184_000L
        )
        val tftPlainLrc = OnlineLyricFetcher.LyricResult(
            api = "QQMusic",
            lyrics = "[00:18.00]誰が袖に咲く幻花",
            parsedLines = List(15) { i ->
                OnlineLyricFetcher.LyricLine(i * 1_000L, (i + 1) * 1_000L, "line $i")
            },
            hasSyllable = false,
            provider = OnlineLyricProvider.QQMusic,
            matchedTitle = "残響散歌 - From THE FIRST TAKE",
            matchedArtist = "Aimer",
            matchedAlbum = "残響散歌 - From THE FIRST TAKE",
            matchedDurationMs = 184_952L
        )

        // Even when only the studio word-by-word candidate is returned, TFT target rejects it.
        assertNull(
            OnlineLyricSelector().selectBestResult(
                attempts = listOf(
                    OnlineLyricFetcher.ProviderAttempt(OnlineLyricProvider.Kugou, studioSyllable, 80L, false)
                ),
                targetTitle = "残響散歌 - From THE FIRST TAKE",
                targetArtist = "Aimer",
                targetAlbum = "",
                targetDurationMs = 184_952L,
                providerOrder = listOf(OnlineLyricProvider.Kugou, OnlineLyricProvider.QQMusic),
                useSmartSelection = true
            )
        )

        // When both exist, TFT plain LRC is selected cleanly over studio word-by-word.
        val selected = OnlineLyricSelector().selectBestResult(
            attempts = listOf(
                OnlineLyricFetcher.ProviderAttempt(OnlineLyricProvider.Kugou, studioSyllable, 80L, false),
                OnlineLyricFetcher.ProviderAttempt(OnlineLyricProvider.QQMusic, tftPlainLrc, 120L, false)
            ),
            targetTitle = "残響散歌 - From THE FIRST TAKE",
            targetArtist = "Aimer",
            targetAlbum = "",
            targetDurationMs = 184_952L,
            providerOrder = listOf(OnlineLyricProvider.Kugou, OnlineLyricProvider.QQMusic),
            useSmartSelection = true
        )
        assertEquals(tftPlainLrc, selected)
    }

    @Test
    fun liveTargetPrefersLivePlainLrcOverSameDurationStudioSyllableLyric() {
        val studioSyllable = OnlineLyricFetcher.LyricResult(
            api = "Kugou",
            lyrics = "[1000,1000]<0,500,0>歌<500,500,0>词",
            parsedLines = List(15) { i ->
                OnlineLyricFetcher.LyricLine(
                    startTime = i * 1_000L,
                    endTime = (i + 1) * 1_000L,
                    text = "line $i",
                    syllables = listOf(OnlineLyricFetcher.SyllableInfo(i * 1_000L, (i + 1) * 1_000L, "line"))
                )
            },
            hasSyllable = true,
            provider = OnlineLyricProvider.Kugou,
            matchedTitle = "晴天",
            matchedArtist = "周杰伦",
            matchedDurationMs = 269_000L
        )
        val livePlainLrc = OnlineLyricFetcher.LyricResult(
            api = "Netease",
            lyrics = "[00:01.00]晴天 (Live)",
            parsedLines = List(15) { i ->
                OnlineLyricFetcher.LyricLine(i * 1_000L, (i + 1) * 1_000L, "line $i")
            },
            hasSyllable = false,
            provider = OnlineLyricProvider.Netease,
            matchedTitle = "晴天 (Live)",
            matchedArtist = "周杰伦",
            matchedDurationMs = 269_000L
        )

        val selected = OnlineLyricSelector().selectBestResult(
            attempts = listOf(
                OnlineLyricFetcher.ProviderAttempt(OnlineLyricProvider.Kugou, studioSyllable, 80L, false),
                OnlineLyricFetcher.ProviderAttempt(OnlineLyricProvider.Netease, livePlainLrc, 120L, false)
            ),
            targetTitle = "晴天 (Live)",
            targetArtist = "周杰伦",
            targetDurationMs = 269_000L,
            providerOrder = listOf(OnlineLyricProvider.Kugou, OnlineLyricProvider.Netease),
            useSmartSelection = true
        )

        assertEquals(livePlainLrc, selected)
        assertTrue(livePlainLrc.score > studioSyllable.score)
    }
}
