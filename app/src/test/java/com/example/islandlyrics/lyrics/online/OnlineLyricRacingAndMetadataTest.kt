package com.example.islandlyrics.lyrics.online

import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import com.example.islandlyrics.lyrics.online.selection.OnlineLyricSelector
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.runtime.metadata.isSameTrackForOnlineFetch
import com.example.islandlyrics.runtime.metadata.needsMetadataStabilizationDelay
import com.example.islandlyrics.runtime.metadata.shouldRefetchOnMetadataEnriched
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLyricRacingAndMetadataTest {

    @Test
    fun smartRaceWaitsForSlowerSyllableProviderInsteadOfCancellingAfterFastPlainLrc() = runBlocking {
        val fetcher = OnlineLyricFetcher()
        val query = OnlineLyricFetcher.LyricQuery(
            title = "打上花火",
            artist = "DAOKO × 米津玄師",
            album = "打上花火",
            durationMs = 289_000L
        )
        val providers = listOf(OnlineLyricProvider.Lrclib, OnlineLyricProvider.QQMusic)

        val attempts = fetcher.raceProviders(
            query = query,
            providerOrder = providers,
            usedCleanTitleFallback = false,
            queryVariant = "exact",
            collectAllResults = false,
            useSmartSelection = true,
            fetchTimeoutMs = 2_000L,
            syllableGracePeriodMs = 80L,
            lrcWaitForSyllableMs = 800L,
            plainNoSyllableGraceMs = 50L
        ) { provider ->
            when (provider) {
                OnlineLyricProvider.Lrclib -> {
                    delay(20L)
                    OnlineLyricFetcher.LyricResult(
                        api = "LRCLIB",
                        lyrics = "[00:01.00]あの日見渡した渚を",
                        parsedLines = listOf(OnlineLyricFetcher.LyricLine(1_000L, 4_000L, "あの日見渡した渚を")),
                        hasSyllable = false,
                        provider = OnlineLyricProvider.Lrclib,
                        matchedTitle = "打上花火",
                        matchedArtist = "DAOKO × 米津玄師",
                        matchedAlbum = "打上花火",
                        matchedDurationMs = 289_000L
                    )
                }
                OnlineLyricProvider.QQMusic -> {
                    delay(180L)
                    OnlineLyricFetcher.LyricResult(
                        api = "QQMusic",
                        lyrics = "[1000,3000]あ(1000,500)の(1500,500)日(2000,1000)",
                        parsedLines = listOf(
                            OnlineLyricFetcher.LyricLine(
                                startTime = 1_000L,
                                endTime = 4_000L,
                                text = "あの日",
                                syllables = listOf(
                                    OnlineLyricFetcher.SyllableInfo(1_000L, 1_500L, "あ"),
                                    OnlineLyricFetcher.SyllableInfo(1_500L, 2_000L, "の"),
                                    OnlineLyricFetcher.SyllableInfo(2_000L, 3_000L, "日")
                                )
                            )
                        ),
                        hasSyllable = true,
                        provider = OnlineLyricProvider.QQMusic,
                        matchedTitle = "打上花火",
                        matchedArtist = "DAOKO/米津玄师",
                        matchedAlbum = "打上花火",
                        matchedDurationMs = 289_000L
                    )
                }
                else -> null
            }
        }

        val selected = OnlineLyricSelector().selectBestResult(
            attempts = attempts,
            targetTitle = query.title,
            targetArtist = query.artist,
            providerOrder = providers,
            useSmartSelection = true,
            targetAlbum = query.album,
            targetDurationMs = query.durationMs
        )

        assertEquals(OnlineLyricProvider.QQMusic, selected?.provider)
        assertTrue(selected?.hasSyllable == true)
    }

    @Test
    fun priorityRaceNeverCancelsHigherPriorityProviderWhenLowerPriorityFinishesFirst() = runBlocking {
        val fetcher = OnlineLyricFetcher()
        val query = OnlineLyricFetcher.LyricQuery(
            title = "Song",
            artist = "Artist",
            album = "Album",
            durationMs = 200_000L
        )
        val providers = listOf(OnlineLyricProvider.QQMusic, OnlineLyricProvider.Lrclib)

        val attempts = fetcher.raceProviders(
            query = query,
            providerOrder = providers,
            usedCleanTitleFallback = false,
            queryVariant = "exact",
            collectAllResults = false,
            useSmartSelection = false,
            fetchTimeoutMs = 2_000L,
            prioritySidecarGraceMs = 50L
        ) { provider ->
            when (provider) {
                OnlineLyricProvider.Lrclib -> {
                    delay(20L)
                    OnlineLyricFetcher.LyricResult(
                        api = "LRCLIB",
                        lyrics = "[00:01.00]plain",
                        parsedLines = listOf(OnlineLyricFetcher.LyricLine(1_000L, 2_000L, "plain")),
                        hasSyllable = false,
                        provider = OnlineLyricProvider.Lrclib,
                        matchedTitle = "Song",
                        matchedArtist = "Artist",
                        matchedAlbum = "Album",
                        matchedDurationMs = 200_000L
                    )
                }
                OnlineLyricProvider.QQMusic -> {
                    delay(180L)
                    OnlineLyricFetcher.LyricResult(
                        api = "QQMusic",
                        lyrics = "[00:01.00]qq",
                        parsedLines = listOf(OnlineLyricFetcher.LyricLine(1_000L, 2_000L, "qq")),
                        hasSyllable = false,
                        provider = OnlineLyricProvider.QQMusic,
                        matchedTitle = "Song",
                        matchedArtist = "Artist",
                        matchedAlbum = "Album",
                        matchedDurationMs = 200_000L
                    )
                }
                else -> null
            }
        }

        val selected = OnlineLyricSelector().selectBestResult(
            attempts = attempts,
            targetTitle = query.title,
            targetArtist = query.artist,
            providerOrder = providers,
            useSmartSelection = false,
            targetAlbum = query.album,
            targetDurationMs = query.durationMs
        )

        assertEquals(OnlineLyricProvider.QQMusic, selected?.provider)
    }

    @Test
    fun delayedMetadataStabilizationAndEnrichmentRefetchRules() {
        val initialIncomplete = LyricRepository.MediaInfo(
            title = "打上花火",
            artist = "DAOKO × 米津玄師",
            packageName = "com.example.player",
            duration = 0L,
            album = ""
        )
        val enriched = initialIncomplete.copy(duration = 289_000L, album = "打上花火")
        val onlineParsed = LyricRepository.ParsedLyricsInfo(
            lines = listOf(OnlineLyricFetcher.LyricLine(1_000L, 2_000L, "line")),
            hasSyllable = false,
            sourceLabel = "Netease",
            apiPath = "Online API",
            timelineCapability = LyricRepository.TimelineCapability.MULTI_LINE
        )

        assertTrue(needsMetadataStabilizationDelay(initialIncomplete))
        assertFalse(needsMetadataStabilizationDelay(enriched))

        // Late mediaId enrichment ("" -> "900463142") on the same song must NOT drop the online fetch!
        val initialEmptyMediaId = LyricRepository.MediaInfo(
            title = "新地球",
            artist = "林俊杰",
            packageName = "com.tencent.freemusic",
            duration = 0L,
            mediaId = ""
        )
        val lateMediaIdPopulated = initialEmptyMediaId.copy(duration = 277_000L, mediaId = "900463142")
        assertTrue(isSameTrackForOnlineFetch(initialEmptyMediaId, lateMediaIdPopulated))
        assertFalse(isSameTrackForOnlineFetch(lateMediaIdPopulated, lateMediaIdPopulated.copy(mediaId = "999999")))

        // Active in-flight fetch must NOT be interrupted mid-flight
        assertFalse(
            shouldRefetchOnMetadataEnriched(
                previous = initialIncomplete,
                updated = enriched,
                isOnlineFetching = true,
                resolveState = LyricRepository.LyricResolveState.PENDING,
                currentParsed = null,
                lastMatchedDurationMs = null
            )
        )

        // Initial fetch failed without duration/album -> retry with enriched metadata
        assertTrue(
            shouldRefetchOnMetadataEnriched(
                previous = initialIncomplete,
                updated = enriched,
                isOnlineFetching = false,
                resolveState = LyricRepository.LyricResolveState.NOT_FOUND,
                currentParsed = null,
                lastMatchedDurationMs = null
            )
        )

        // Initial fetch picked a wrong-duration version (e.g. TV size 90s vs 289s) -> refetch
        assertTrue(
            shouldRefetchOnMetadataEnriched(
                previous = initialIncomplete,
                updated = enriched,
                isOnlineFetching = false,
                resolveState = LyricRepository.LyricResolveState.FOUND,
                currentParsed = onlineParsed,
                lastMatchedDurationMs = 90_000L
            )
        )

        // Initial fetch already matched the right duration -> no unnecessary refetch
        assertFalse(
            shouldRefetchOnMetadataEnriched(
                previous = initialIncomplete,
                updated = enriched,
                isOnlineFetching = false,
                resolveState = LyricRepository.LyricResolveState.FOUND,
                currentParsed = onlineParsed,
                lastMatchedDurationMs = 289_500L
            )
        )
    }
}
