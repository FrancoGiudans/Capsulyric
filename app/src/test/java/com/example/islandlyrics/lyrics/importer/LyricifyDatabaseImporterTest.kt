package com.example.islandlyrics.lyrics.importer

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import org.junit.Assert.*
import org.junit.Test

class LyricifyDatabaseImporterTest {
    private val original = result("Original")
    private val incoming = result("Imported")
    private val oldSelection = LyricifyImportSelection(listOf(original), original)
    private val newSelection = LyricifyImportSelection(listOf(incoming), incoming)

    private fun result(text: String) = OnlineLyricFetcher.LyricResult(
        api = "Test", lyrics = "[00:01.000]$text", hasSyllable = false, provider = OnlineLyricProvider.Netease,
        parsedLines = listOf(OnlineLyricFetcher.LyricLine(1_000L, 2_000L, text)), providerTrackId = "123"
    )

    @Test fun mapsForeignProviderNamesToExistingProviders() {
        assertEquals(OnlineLyricProvider.QQMusic, LyricifyDatabaseImporter.providerFor("QQMusic"))
        assertEquals(OnlineLyricProvider.Kugou, LyricifyDatabaseImporter.providerFor("KugouMusic"))
        assertEquals(OnlineLyricProvider.Netease, LyricifyDatabaseImporter.providerFor("NeteaseMusic"))
        assertEquals(OnlineLyricProvider.Lrclib, LyricifyDatabaseImporter.providerFor("LRCLIB"))
        assertNull(LyricifyDatabaseImporter.providerFor("UnknownProvider"))
    }

    @Test fun platformIdCanMatchLocalizedMetadata() {
        assertTrue(LyricifyDatabaseImporter.sameTrack(
            LyricifyTrackIdentity("标题", "歌手", providerIds = mapOf("netease" to "123")),
            LyricifyTrackIdentity("Title", "Artist", providerIds = mapOf("netease" to "123"))
        ))
    }

    @Test fun qqNumericIdsAndMidsHaveSeparateNamespaces() {
        val numeric = LyricifyDatabaseImporter.identityIds(OnlineLyricProvider.QQMusic, "462188")
        val mid = LyricifyDatabaseImporter.identityIds(OnlineLyricProvider.QQMusic, "0010BrWk2SucQr")
        assertEquals(mapOf("qq_music" to "462188"), numeric)
        assertEquals(mapOf("qq_music:mid" to "0010BrWk2SucQr"), mid)
        assertTrue(LyricifyDatabaseImporter.sameTrack(
            LyricifyTrackIdentity("标题", "歌手", providerIds = numeric + mid),
            LyricifyTrackIdentity("Title", "Artist", providerIds = mid)
        ))
    }

    @Test fun equalNumbersInDifferentPlatformsAreNotTheSameId() {
        assertFalse(LyricifyDatabaseImporter.sameTrack(
            LyricifyTrackIdentity("One", "A", providerIds = mapOf("netease" to "123")),
            LyricifyTrackIdentity("Two", "B", providerIds = mapOf("qq_music" to "123"))
        ))
    }

    @Test fun metadataCanMatchAcrossProvidersAndNearbyDurations() {
        assertTrue(LyricifyDatabaseImporter.sameTrack(
            LyricifyTrackIdentity(" Title ", "ARTIST", "Album", 180_000, mapOf("netease" to "123")),
            LyricifyTrackIdentity("title", "artist", "album", 182_000, mapOf("qq_music" to "456"))
        ))
    }

    @Test fun versionSuffixesAlbumsAndDurationsPreventFalseMerges() {
        val track = LyricifyTrackIdentity("Title", "Artist", "Album", 180_000)
        assertFalse(LyricifyDatabaseImporter.sameTrack(track, track.copy(title = "Title (Live)")))
        assertFalse(LyricifyDatabaseImporter.sameTrack(track, track.copy(album = "Live Album")))
        assertFalse(LyricifyDatabaseImporter.sameTrack(track, track.copy(durationMs = 240_000)))
    }

    @Test fun missingEvidenceDoesNotForceAMerge() {
        val track = LyricifyTrackIdentity("Title", "Artist")
        assertFalse(LyricifyDatabaseImporter.sameTrack(track, track))
    }

    @Test fun contradictoryPlatformIdsAreKeptSeparate() {
        val track = LyricifyTrackIdentity("Title", "Artist", "Album", 180_000, mapOf("netease" to "123"))
        assertFalse(LyricifyDatabaseImporter.sameTrack(track, track.copy(providerIds = mapOf("netease" to "456"))))
    }

    @Test fun overwriteChangesTheSelectedLyrics() {
        val selection = LyricifyDatabaseImporter.resolveSelection(oldSelection, newSelection, LyricifyImportMode.OVERWRITE)!!
        assertEquals(incoming, selection.selected)
    }

    @Test fun mergeKeepsSelectionAndBothVersionsOfTheSameProviderAndId() {
        val selection = LyricifyDatabaseImporter.resolveSelection(oldSelection, newSelection, LyricifyImportMode.MERGE)!!
        assertEquals(original, selection.selected)
        assertEquals(listOf(original, incoming), selection.candidates)
    }

    @Test fun skipLeavesAConflictingSongUntouched() {
        assertNull(LyricifyDatabaseImporter.resolveSelection(oldSelection, newSelection, LyricifyImportMode.SKIP))
    }

    @Test fun allModesImportSongsWithoutAnExistingSelection() {
        for (mode in LyricifyImportMode.entries) {
            assertEquals(newSelection, LyricifyDatabaseImporter.resolveSelection(null, newSelection, mode))
        }
    }

    @Test fun repeatedMergeDoesNotDuplicateCandidates() {
        val first = LyricifyDatabaseImporter.resolveSelection(oldSelection, newSelection, LyricifyImportMode.MERGE)
        val second = LyricifyDatabaseImporter.resolveSelection(first, newSelection, LyricifyImportMode.MERGE)!!
        assertEquals(2, second.candidates.size)
        assertEquals(original, second.selected)
    }

    @Test fun importsLrcWithoutFractionalSeconds() {
        val lines = LyricifyDatabaseImporter.parseDocument("Lrc", "[0:01]Hello\n[00:02.5]World\n[00:03:10]Again")
        assertEquals(listOf(1_000L, 2_500L, 3_100L), lines.map { it.startTime })
        assertEquals(listOf("Hello", "World", "Again"), lines.map { it.text })
    }

    @Test fun reusesWordLevelParsers() {
        val documents = mapOf(
            "Qrc" to "[1000,1000]你(1000,500)好(1500,500)",
            "Krc" to "[1000,1000]<0,500,0>你<500,500,0>好",
            "Yrc" to "[1000,1000](1000,500,0)你(1500,500,0)好"
        )
        for ((format, text) in documents) {
            val line = LyricifyDatabaseImporter.parseDocument(format, text).single()
            assertEquals(format, "你好", line.text)
            assertEquals(format, listOf(1_000L, 1_500L), line.syllables!!.map { it.startTime })
        }
    }

    @Test fun shiftsLineAndWordTimingTogether() {
        val line = OnlineLyricFetcher.LyricLine(1_000, 2_000, "Word", listOf(OnlineLyricFetcher.SyllableInfo(1_000, 2_000, "Word")))
        val shifted = LyricifyDatabaseImporter.shiftLines(listOf(line), 1_500).single()
        assertEquals(2_500L, shifted.startTime)
        assertEquals(3_500L, shifted.endTime)
        assertEquals(2_500L, shifted.syllables!!.single().startTime)
        assertEquals(3_500L, shifted.syllables!!.single().endTime)
    }
}
